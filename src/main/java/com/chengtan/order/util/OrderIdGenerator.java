package com.chengtan.order.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 订单 ID 与订单号生成。
 * ID：毫秒时间戳左移 20 位 + 秒内自增序列；订单号：T + yyyyMMddHHmmss + 6 位序列。
 */
@Component
@RequiredArgsConstructor
public class OrderIdGenerator {

    private static final long BEGIN_TIMESTAMP = 1704067200000L;
    private static final int SEQUENCE_BITS = 20;
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;
    private static final DateTimeFormatter ORDER_NO_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final StringRedisTemplate redis;

    public long nextId() {
        long now = System.currentTimeMillis();
        String key = "icr:order:id:" + (now / 1000);
        Long seq = redis.opsForValue().increment(key);
        redis.expire(key, 2, TimeUnit.DAYS);
        long sequence = seq == null ? 0 : (seq & SEQUENCE_MASK);
        return ((now - BEGIN_TIMESTAMP) << SEQUENCE_BITS) | sequence;
    }

    public String nextOrderNo() {
        String time = LocalDateTime.now().format(ORDER_NO_TIME);
        String key = "icr:order:no:" + time;
        Long seq = redis.opsForValue().increment(key);
        redis.expire(key, 2, TimeUnit.DAYS);
        long sequence = seq == null ? 1 : (seq % 1_000_000);
        return "T" + time + String.format("%06d", sequence);
    }
}
