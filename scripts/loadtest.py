#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
城探票务抢票接口压测脚本（零第三方依赖）

它做的事：
  1. 用 mysql / redis-cli 准备一套隔离夹具：一个测试场次 + N 个用户 token
  2. 并发打 POST /api/orders，记录每个请求的耗时与业务码
  3. 统计 QPS、P50/P90/P99，并等异步消费者落库后校验「不超卖」
  4. 默认跑完自动清理夹具（--keep 可保留现场）

用法：
  set MYSQL_PASSWORD=xxx & set REDIS_PASSWORD=xxx
  python scripts/loadtest.py --users 300 --capacity 200

注意：
  - 夹具只写 ct_venue_session / ct_attendee / Redis token，不碰业务数据
  - 场次 ID 默认 990001，重复执行会先清理再重建
"""

import argparse
import json
import os
import statistics
import subprocess
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

BASE_URL = os.environ.get("BASE_URL", "http://127.0.0.1:8081")
MYSQL_DB = os.environ.get("MYSQL_DB", "chengtan_v2")
VENUE_ID = 1
USER_BASE = 6_000_000
ATTENDEE_BASE = 5_000_000
TOKEN_PREFIX = "lt"


def mysql(sql):
    env = dict(os.environ, MYSQL_PWD=os.environ.get("MYSQL_PASSWORD", ""))
    proc = subprocess.run(
        ["mysql", "-uroot", "-h127.0.0.1", "-P3306",
         "--default-character-set=utf8mb4", "-N", MYSQL_DB],
        input=sql, text=True, capture_output=True, env=env)
    if proc.returncode != 0:
        raise RuntimeError("mysql 执行失败: " + proc.stderr.strip())
    return proc.stdout


def redis(cmds):
    args = ["redis-cli", "-h", "127.0.0.1", "-p", "6379", "--no-auth-warning"]
    password = os.environ.get("REDIS_PASSWORD")
    if password:
        args += ["-a", password]
    proc = subprocess.run(args, input="\n".join(cmds) + "\n",
                          text=True, capture_output=True)
    if proc.returncode != 0:
        raise RuntimeError("redis-cli 执行失败: " + proc.stderr.strip())
    return proc.stdout


def http_post(path, body, token, timeout=15):
    data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(BASE_URL + path, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", "Bearer " + token)
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
        return payload, (time.perf_counter() - start) * 1000
    except urllib.error.HTTPError as e:
        return {"success": False, "code": "HTTP_" + str(e.code), "message": str(e)}, \
               (time.perf_counter() - start) * 1000
    except Exception as e:  # 连接被拒 / 超时
        return {"success": False, "code": "NETWORK", "message": str(e)}, \
               (time.perf_counter() - start) * 1000


def cleanup(session_id, users):
    order_nos = "SELECT order_no FROM ct_ticket_order WHERE session_id = {}".format(session_id)
    mysql(
        "DELETE FROM ct_order_outbox WHERE order_no IN ({0});\n"
        "DELETE FROM ct_verify_log WHERE ticket_id IN "
        "(SELECT id FROM ct_ticket WHERE session_id = {1});\n"
        "DELETE FROM ct_ticket WHERE session_id = {1};\n"
        "DELETE FROM ct_ticket_order WHERE session_id = {1};\n"
        "DELETE FROM ct_waitlist WHERE session_id = {1};\n"
        "DELETE FROM ct_venue_session WHERE id = {1};\n".format(order_nos, session_id)
    )
    ids = ",".join(str(ATTENDEE_BASE + i) for i in range(users))
    mysql("DELETE FROM ct_attendee WHERE id IN ({});".format(ids))
    keys = ["DEL ticket:stock:{0}".format(session_id),
            "DEL ticket:bought:{0}".format(session_id),
            "DEL ticket:attendee:{0}".format(session_id),
            "DEL ticket:session:{0}".format(session_id)]
    keys += ["DEL login:token:{}{}".format(TOKEN_PREFIX, i) for i in range(users)]
    redis(keys)


def prepare(session_id, capacity, users):
    now_ms = int(time.time() * 1000)
    sale_start = now_ms - 86400_000
    sale_end = now_ms + 7 * 86400_000
    venue_date = "DATE_ADD(CURDATE(), INTERVAL 400 DAY)"

    sql = [
        "INSERT INTO ct_venue_session (id, venue_id, session_date, start_time, end_time,"
        " capacity, sold, price, status, sale_start_time, sale_end_time) VALUES"
        " ({0}, {1}, {2}, '10:00:00', '12:00:00', {3}, 0, 100.00, 1, NOW() - INTERVAL 1 DAY,"
        " NOW() + INTERVAL 7 DAY);".format(session_id, VENUE_ID, venue_date, capacity)
    ]
    for i in range(users):
        sql.append(
            "INSERT INTO ct_attendee (id, user_id, name, id_card_mask, id_card_hash, phone)"
            " VALUES ({0}, {1}, '压测用户{2}', '3301**********1234', 'lt-{3}', '13900000000');"
            .format(ATTENDEE_BASE + i, USER_BASE + i, i, i))
    mysql("\n".join(sql))

    cmds = []
    for i in range(users):
        cmds.append("HSET login:token:{0}{1} id {2} phone 1390000{3:04d} nickName 压测用户{1} role 0"
                    .format(TOKEN_PREFIX, i, USER_BASE + i, i % 10000))
        cmds.append("EXPIRE login:token:{}{} 7200".format(TOKEN_PREFIX, i))
    # 预热场次缓存（等价于 SessionCacheService.warm 写入的内容）
    cmds.append("HSET ticket:session:{0} venueId {1} price 100.00 capacity {2} status 1"
                " saleStart {3} saleEnd {4} limit 3"
                .format(session_id, VENUE_ID, capacity, sale_start, sale_end))
    cmds.append("SET ticket:stock:{} {}".format(session_id, capacity))
    redis(cmds)


def percentile(sorted_values, p):
    if not sorted_values:
        return 0.0
    idx = min(len(sorted_values) - 1, int(round((p / 100.0) * (len(sorted_values) - 1))))
    return sorted_values[idx]


def run_load(session_id, users):
    results = []
    lock = __import__("threading").Lock()

    def one(i):
        token = "{}{}".format(TOKEN_PREFIX, i)
        body = {"sessionId": session_id, "quantity": 1, "attendeeIds": [ATTENDEE_BASE + i]}
        payload, cost = http_post("/api/orders", body, token)
        with lock:
            results.append((payload.get("success") is True,
                            payload.get("code"),
                            payload.get("message"),
                            cost))

    start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=users) as pool:
        list(pool.map(one, range(users)))
    wall = time.perf_counter() - start
    return results, wall


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--users", type=int, default=300)
    parser.add_argument("--capacity", type=int, default=200)
    parser.add_argument("--session-id", type=int, default=990001)
    parser.add_argument("--keep", action="store_true", help="保留夹具，便于排查")
    parser.add_argument("--skip-prepare", action="store_true")
    args = parser.parse_args()

    if args.users < 1 or args.capacity < 1:
        sys.exit("users / capacity 必须为正整数")

    print("压测目标: {}  场次={}  容量={}  并发用户={}"
          .format(BASE_URL, args.session_id, args.capacity, args.users))

    cleanup(args.session_id, args.users)
    if not args.skip_prepare:
        prepare(args.session_id, args.capacity, args.users)
    # 等消费者/缓存就绪
    time.sleep(0.5)

    results, wall = run_load(args.session_id, args.users)
    ok = [r for r in results if r[0]]
    codes = {}
    for r in results:
        if not r[0]:
            codes[r[1]] = codes.get(r[1], 0) + 1
    latencies = sorted(r[3] for r in results)

    print("\n=== 接口层 ===")
    print("总请求     : {}".format(len(results)))
    print("成功       : {}".format(len(ok)))
    print("被拒绝     : {}".format(len(results) - len(ok)))
    if codes:
        print("拒绝原因   : {}".format(", ".join("{}×{}".format(k, v) for k, v in codes.items())))
    print("总耗时     : {:.2f}s".format(wall))
    print("QPS        : {:.0f}".format(len(results) / wall))
    print("P50 / P90 / P99 / max : {:.0f} / {:.0f} / {:.0f} / {:.0f} ms".format(
        percentile(latencies, 50), percentile(latencies, 90),
        percentile(latencies, 99), latencies[-1] if latencies else 0))

    # 等异步消费落库：单条 SQL 同时读 sold 与订单数，避免两次查询之间的竞态
    deadline = time.time() + 30
    sold = orders = 0
    stable = 0
    while time.time() < deadline:
        row = mysql(
            "SELECT s.sold, (SELECT COUNT(*) FROM ct_ticket_order o WHERE o.session_id = s.id)"
            " FROM ct_venue_session s WHERE s.id = {};".format(args.session_id)).strip()
        parts = row.split()
        if len(parts) == 2:
            new_sold, new_orders = int(parts[0]), int(parts[1])
            stable = stable + 1 if (new_sold, new_orders) == (sold, orders) else 0
            sold, orders = new_sold, new_orders
            if stable >= 2 and orders >= len(ok):
                break
        time.sleep(1)

    stock = redis(["GET ticket:stock:{}".format(args.session_id)]).strip()
    print("\n=== 落库与一致性（异步消费完成后）===")
    print("DB 已售    : {}".format(sold))
    print("订单数     : {}".format(orders))
    print("Redis 库存 : {}".format(stock))
    print("超卖       : {}".format("是" if sold > args.capacity else "否"))

    if sold > args.capacity:
        print("\n[FAIL] 出现超卖")
    elif len(ok) != args.capacity:
        print("\n[WARN] 成功数 {} 与容量 {} 不一致".format(len(ok), args.capacity))
    else:
        print("\n[OK] 成功数 = 容量 = {}，无超卖".format(args.capacity))

    if not args.keep:
        cleanup(args.session_id, args.users)
        print("夹具已清理")


if __name__ == "__main__":
    main()
