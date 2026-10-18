# 城探（chengtan）

基于 Spring Boot + Redis 的城市生活探索平台，涵盖店铺浏览、优惠券秒杀、博客社区、用户签到等核心业务。项目重点实现了**高并发秒杀场景**，通过 Redis Lua 脚本、消息队列、分布式锁、乐观锁等技术保证并发安全，并通过 JMeter 1000 并发压测验证不超卖。

## 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 基础框架 | Spring Boot | 2.3.12 |
| ORM | MyBatis-Plus | 3.4.3 |
| 数据库 | MySQL | 5.7+ |
| 缓存 | Redis（Spring Data Redis + Lettuce） | 5.0+ |
| 分布式锁 | Redisson | 3.13.6 |
| 接口文档 | Springfox（Swagger OpenAPI 3.0） | 3.0.0 |
| 工具库 | Hutool | 5.7.17 |
| 前端 | Vue.js + Element UI + Axios | - |
| 压测 | Apache JMeter | 5.6.3 |
| JDK | Java | 1.8 |

## 项目结构

```
chengtan/
├── src/main/java/com/hmdp/
│   ├── config/          # 配置类（Mvc、Mybatis、Redisson、Swagger、全局异常）
│   ├── controller/      # 控制层（9个模块）
│   ├── dto/             # 数据传输对象
│   ├── entity/          # 实体类（10张表）
│   ├── mapper/          # 数据访问层
│   ├── service/         # 业务逻辑层（接口+实现）
│   └── utils/           # 工具类（缓存客户端、分布式锁、拦截器、ID生成器等）
├── src/main/resources/
│   ├── static/          # 前端静态资源（已并入后端，无需 Nginx）
│   ├── db/hmdp.sql      # 数据库初始化脚本
│   ├── seckill.lua      # 秒杀 Lua 脚本
│   ├── unlock.lua       # 分布式锁解锁 Lua 脚本
│   └── application.yaml # 应用配置
└── pom.xml
```

## 核心功能模块

### 1. 用户模块
- **手机号验证码登录**：验证码存 Redis（String，TTL 2分钟），登录成功生成 token，用户信息存 Redis（Hash，TTL 36000分钟）
- **双拦截器机制**：`RefreshTokenInterceptor`（order=0，刷新 token 有效期）+ `LoginInterceptor`（order=1，登录校验）
- **退出登录**：删除 Redis 中的 token，清除 ThreadLocal
- **每日签到**：基于 Redis Bitmap 实现，`SETBIT` 签到，`BITFIELD` + 位运算统计连续签到天数

### 2. 店铺模块
- **店铺分类查询**：首页分类列表，Redis 缓存
- **店铺详情**：基于 Redis 缓存，支持缓存穿透（空值缓存）、缓存击穿（互斥锁）、缓存雪崩（TTL 随机偏移）
- **附近店铺**：基于 Redis GEO 实现，`GEORADIUS` 按距离排序（兼容 Redis 5.0，使用 `radius()` 而非 `GEOSEARCH`）

### 3. 优惠券秒杀模块（核心）
- **秒杀下单**：
  - Redis Lua 脚本原子操作：判断库存 → 一人一单判断 → 扣减库存 → 写入 Stream 消息队列
  - 异步下单：后台单线程消费者从 Redis Stream 消费消息，创建订单
  - 并发安全：Redisson 分布式锁（一人一单）+ 数据库乐观锁（`stock > 0` 扣减库存）
- **模拟支付**：乐观锁更新订单状态（`status=1 → 2`），支付成功后从延迟队列移除
- **超时自动取消**：
  - Redis ZSet 实现延迟队列，下单时 `ZADD order:delay {orderId} {now+15min}`
  - 后台线程每 5 秒轮询 `ZRANGEBYSCORE` 超时订单
  - 取消订单：乐观锁更新状态（`status=1 → 4`）→ 回补数据库库存 → 回补 Redis 库存 → 清除一人一单标记

### 4. 博客社区模块
- **发布博客**：上传图片 + 保存博客，推送到粉丝 Feed 流（Redis ZSet）
- **点赞功能**：Redis Set 实现点赞/取消点赞，按点赞数排序
- **Feed 流**：基于 Redis ZSet 实现关注用户博客推送，支持分页滚动加载
- **博客评论**：评论列表、发表评论

### 5. 关注模块
- **关注/取消关注**：Redis Set 实现关注关系
- **共同关注**：两个 Set 的交集运算
- **是否已关注**：`SISMEMBER` 判断

### 6. 文件上传
- 图片上传到项目运行目录下的 `imgs/`，通过 `/imgs/**` 静态资源映射访问

## 技术亮点

### 1. 高并发秒杀方案
```
用户请求 → Lua脚本(原子判断库存+一人一单+扣库存) → Redis Stream消息队列 → 异步消费者创建订单
                                                          ↓
                                              Redisson分布式锁 + 数据库乐观锁
```
- **Lua 脚本**：保证库存判断、一人一单、扣减库存的原子性，减少 Redis 往返
- **Redis Stream**：替代传统阻塞队列，支持消息持久化、消费组、ACK 确认、pending-list 异常重试
- **异步下单**：请求快速返回，订单异步创建，提升吞吐量
- **双层并发控制**：Redis 层（Lua + Set）+ 数据库层（乐观锁），双重保证不超卖

### 2. 订单超时自动取消
- Redis ZSet 延迟队列，score = 下单时间 + 15分钟
- 后台线程轮询超时订单，乐观锁取消（防止与支付并发冲突）
- 完整回补：数据库库存 + Redis 库存 + 一人一单标记

### 3. Redis 多数据结构实战
| 数据结构 | 应用场景 |
|---------|---------|
| String | 验证码、token、库存、店铺缓存 |
| Hash | 用户登录信息 |
| Set | 一人一单判断、点赞、关注关系 |
| ZSet | 延迟队列（超时取消）、Feed 流 |
| Bitmap | 每日签到、连续签到统计 |
| GEO | 附近店铺查询 |
| Stream | 秒杀消息队列 |

### 4. 缓存治理
- `CacheClient` 工具类封装：缓存穿透（空值缓存）、缓存击穿（互斥锁/逻辑过期）、缓存雪崩（TTL 随机偏移）
- 店铺详情缓存：Redis + MySQL 双写一致性

### 5. 分布式锁
- 自定义 `SimpleRedisLock`：基于 Redis SETNX + Lua 脚本解锁，支持可重入
- Redisson 分布式锁：秒杀一人一单场景，支持自动续期

### 6. 工程化
- Swagger OpenAPI 3.0 接口文档自动生成
- 全局异常处理（`WebExceptionAdvice`）
- 统一返回结果（`Result`）
- 雪花算法 ID 生成器（`RedisIdWorker`，基于 Redis 自增）
- 后台线程优雅关闭（`@PreDestroy` + `volatile` 标志位）

## 数据库设计

共 10 张表：

| 表名 | 说明 |
|------|------|
| tb_user | 用户表（手机号、昵称、头像） |
| tb_user_info | 用户详情表（城市、职业、性别、生日） |
| tb_shop | 店铺表（名称、类型、坐标、评分、销量） |
| tb_shop_type | 店铺分类表（美食、KTV、丽人美发等） |
| tb_voucher | 优惠券表（普通券+秒杀券） |
| tb_seckill_voucher | 秒杀券表（库存、抢购时间） |
| tb_voucher_order | 优惠券订单表（用户、券、状态、支付信息） |
| tb_blog | 博客表（标题、内容、图片、点赞数） |
| tb_blog_comments | 博客评论表 |
| tb_follow | 关注关系表 |

**订单状态**：1=未支付，2=已支付，3=已核销，4=已取消，5=退款中，6=已退款

## 本地启动

### 环境要求
- JDK 1.8+
- Maven 3.6+
- MySQL 5.7+
- Redis 5.0+

### 启动步骤
1. **创建数据库**：执行 `src/main/resources/db/hmdp.sql` 初始化数据库和表结构
2. **修改配置**：修改 `src/main/resources/application.yaml` 中的 MySQL 和 Redis 连接信息
3. **启动 Redis**：确保 Redis 服务运行
4. **启动 MySQL**：确保 MySQL 服务运行
5. **启动项目**：
   ```bash
   mvn spring-boot:run
   ```
   或在 IDEA 中运行 `ChengtanApplication`
6. **访问前端**：浏览器打开 `http://localhost:8081/index.html`

### 测试账号
- 手机号：任意合法手机号，点击"获取验证码"后，验证码可在 Redis 中查看（`login:code:{phone}`）

## 接口文档

启动后访问 Swagger UI：
- **文档页面**：`http://localhost:8081/swagger-ui/index.html`
- **OpenAPI JSON**：`http://localhost:8081/v3/api-docs`

包含 9 个模块、30+ 接口，支持在线调试。

## 压测结果

使用 Apache JMeter 5.6.3 进行高并发压测，秒杀券库存 100，1000 个独立用户 token。

| 指标 | 500 并发 | 1000 并发 |
|------|---------|----------|
| 吞吐量 (QPS) | 95.8 | **200.3** |
| 平均响应时间 | 1736ms | **1264ms** |
| P90 响应时间 | 4457ms | **2681ms** |
| P99 响应时间 | 4570ms | **3665ms** |
| 成功订单数 | 100 | 100 |
| 不超卖 | ✅ | ✅ |
| 一人一单 | ✅ | ✅ |
| MySQL/Redis 库存一致 | ✅ | ✅ |

**结论**：在 1000 并发下，秒杀系统稳定运行，未出现超卖或重复下单，Redis 与数据库库存最终一致。

## 后续优化方向

- [ ] 接入消息队列（RabbitMQ/RocketMQ）替代 Redis Stream，支持更复杂的消息路由和持久化
- [ ] 网关层限流（Sentinel/Hystrix），保护后端不被打满
- [ ] 数据库读写分离，查询走从库
- [ ] Elasticsearch 实现店铺全文搜索
- [ ] Docker 容器化部署，docker-compose 一键启动
- [ ] 单元测试 + 集成测试覆盖核心业务
- [ ] 订单状态机重构，统一管理状态流转

## License

MIT License
