# 故障演练报告

> 目的不是"证明系统很稳"，而是**主动把组件打死，看它会不会丢单、会不会超卖、恢复后能不能自愈**。
> 下面每一条都是本机真实执行的结果，命令都在文里，可以照着复现。

环境：单机 Windows，MySQL 8 / Redis 7 / RabbitMQ 3.13.7，应用 8081。

---

## 场景 1：消费端整体停机，消息会不会丢？

### 怎么注入

消费者和 Web 在同一个进程里，所以用配置把消费容器关掉，模拟"消费者挂了但网关还活着"：

```bash
set SPRING_RABBITMQ_LISTENER_SIMPLE_AUTO_STARTUP=false
mvn spring-boot:run
```

### 观测到的

30 个用户抢 30 张票，接口全部返回成功：

| 指标 | 值 |
|---|---|
| 接口成功 | 30 |
| Redis 库存 | 0 |
| 队列 ready / unacked / consumers | **30 / 0 / 0** |
| DB 订单数 | **0** |

也就是说：**库存已经扣了，消息躺在队列里，没有任何人消费。**

### 恢复

正常启动应用（消费者回来），实测结果：

| 指标 | 值 |
|---|---|
| 队列 ready | 30 → **0** |
| 消费者数 | 0 → **3** |
| DB 订单数 | 0 → **30**（启动后 1 秒内） |
| 票数 / 订单号去重 | 30 / 30 |
| 场次 sold / status | 30 / 2（售罄） |

**结论**：消息不丢、不重、不超卖。因为可靠性不依赖"消费者在线"，而依赖"消息先落 MQ + 消费端按订单号幂等"。

---

## 场景 2：RabbitMQ 宕机，本地消息表能不能兜住？

### 怎么注入

```powershell
rabbitmqctl.bat -n rabbit@localhost stop
```

### 观测到的

MQ 停掉后发 10 单：

| 指标 | 值 |
|---|---|
| 接口成功 | 10（总耗时 0.32s，连接被拒是快速失败） |
| DB 订单数 | 0 |
| `ct_order_outbox` status=0 | **10** |
| Redis 库存 | 0 |

接口没有报错，因为 `createOrder` 里发布失败只会让消息留在本地消息表 —— 这正是 Outbox 的意义。

### 这里发现了一个真实缺陷

Relay 每秒扫一次 `status=0` 的消息重投。原本的退避策略是：

```java
outbox.setStatus(retryCount >= 5 ? 2 : 0);
long delaySeconds = Math.min(60, 1L << Math.min(retryCount, 6));  // 2,4,8,16,32,60...
```

**MQ 停掉约 65 秒后，10 条消息全部 `retry_count=5`、`status=2`。**

而 Relay 只查 `status=0`：

```sql
WHERE status = 0 AND next_retry_time <= now()
```

于是 **RabbitMQ 恢复后，这 10 条永远不会再被投递**，订单永久停在 0。实测：MQ 恢复 15 秒后查询，`orders=0`，`status=2` 依旧是 10 条。

当时的日志（控制台 GBK，这里按 UTF-8 还原）：

```
WARN  OrderOutboxRelay - Outbox 重试 5/5, orderNo=T20261120083728000006, next=2026-11-20T08:38:34
```

对一个票务系统来说，"MQ 抖一分钟就永久丢单"是不能接受的。**这个缺陷是演练的唯一收获，也是演练值得做的原因。**

### 修复

| 改动 | 内容 |
|---|---|
| 重试窗口 | `MAX_RETRY` 5 → **20**，退避仍封顶 60s，覆盖约 15 分钟 |
| 耗尽处理 | 进入 `status=2` 时打 `ERROR` 日志，并累加指标 `chengtan_outbox_dead_total`（应配告警） |
| 人工兜底 | 新增 `POST /api/admin/outbox/replay`（仅运营角色），把 `status=2` 重置为待发送 |
| 可观测 | 新增 `GET /api/admin/outbox/stats` 返回 `{pending, dead}` |

### 验证 A：人工重放

修复前积压的 10 条死信，调用重放接口：

```
POST /api/admin/outbox/replay  →  {"success":true,"data":10}
```

2 秒后：

| 指标 | 值 |
|---|---|
| DB 订单数 | 0 → **10** |
| outbox 状态 | 全部 status=1（已发送） |
| stats | `{"pending":0,"dead":0}` |

### 验证 B：短时断连能自动恢复

再停一次 MQ，发 5 单，停机 30 秒时观察：`retry_count=5`，**仍是 `status=0`**（阈值已提高到 20，不会进终态）。

恢复 MQ（17 秒起监听）后不人工干预：

| 指标 | 值 |
|---|---|
| 自动补投完成 | 恢复后 **32 秒** |
| DB 订单数 | **5** |
| outbox | `status=1, retry_count=6` |

**结论**：MQ 短时抖动（15 分钟内）可自愈；超过窗口才需要人工重放，且重放有接口、有指标、有日志。

---

## 场景 3：改签写到一半数据库报错，两个场次会不会各扣一半？

改签要同时动两个场次、订单、票、改签记录，是最容易出"半成品数据"的地方。

### 怎么注入

不改一行业务代码，用 MySQL 触发器制造写入失败：改签更新票的 `session_id` 时，把 `ticket_no` 置空（非空约束报错）。

```sql
CREATE TRIGGER trg_fail_reschedule BEFORE UPDATE ON ct_ticket
FOR EACH ROW
SET NEW.ticket_no = IF(NEW.session_id <> OLD.session_id, NULL, NEW.ticket_no);
```

初始状态：订单在 994001（已支付，45 元），目标场次 994002（55 元），`sold` 1 / 0，Redis 库存 9 / 10。

### 观测到的

```
POST /api/orders/DRILL20261120001/reschedule {"targetSessionId":994002}
→ {"success":false,"code":"C0500","message":"系统繁忙，请稍后重试"}
```

> HTTP 状态是 200，业务码 C0500 —— 这是项目统一的返回结构，非预期异常统一收敛成"系统繁忙"，堆栈只进日志。

回滚校验：

| 检查项 | 期望 | 实际 |
|---|---|---|
| 订单 `session_id` / 单价 | 994001 / 45.00 | 994001 / 45.00 |
| 票 `session_id` | 994001 | 994001 |
| 场次 `sold` | 1 / 0 | 1 / 0 |
| 改签记录条数 | 0 | **0** |
| Redis 库存 | 9 / 10 | 9 / 10 |

### 对照组

删掉触发器，同一个请求再打一次：

```
→ {"success":true,"data":{"fromSessionId":994001,"toSessionId":994002,"priceDiff":10.00}}
```

| 检查项 | 结果 |
|---|---|
| 订单 | 994002，单价 55.00，金额 55.00 |
| 票 | 994002 |
| `sold` | 994001: 1→0，994002: 0→1 |
| 改签记录 | 1 条，差价 +10.00 |
| Redis 库存 | 10 / 9 |

**结论**：失败时零残留，成功时两边库存严格守恒；Redis 预扣在异常分支也被 `incrStockCap` 回补，不会出现"DB 回滚了但 Redis 少一张"。

---

## 汇总

| 场景 | 注入方式 | 结果 | 是否发现问题 |
|---|---|---|---|
| 消费端停机 | 关闭 listener 容器 | 30 条积压，恢复后 30 单落库，不丢不重 | 否 |
| MQ 宕机 | `rabbitmqctl stop` | 接口不报错、消息进 Outbox | **是：5 次重试后进终态，恢复不补投** |
| 改签写库失败 | MySQL 触发器 | 订单/票/两场次库存/Redis 全部回滚 | 否（对照组正常） |

### 还没覆盖的

- Redis 宕机：缓存全挂时接口会大面积失败，尚未设计降级（比如只读走 DB）
- 多实例：目前单机，`XACK`/Outbox 锁是按多实例设计的，但没有真的起两个进程验证
- 网络分区 / 时钟漂移：未模拟

这三项写进 README 的"已知限制"，比装作没有更安全 —— 面试官问"Redis 挂了呢"，答案是"我还没做降级，但我知道会在哪一层挂"。
