package com.chengtan.common.lock;

import cn.hutool.core.lang.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 轻量分布式锁：SET NX PX + token 校验删除。
 * 适用于耗时短、能接受 TTL 内自动释放的场景；长事务请用 Redisson 或数据库行锁。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLock {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    public String tryLock(String key, long ttlSeconds) {
        String token = UUID.randomUUID().toString(true);
        Boolean ok = redis.opsForValue().setIfAbsent(key, token, ttlSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    public void unlock(String key, String token) {
        if (token == null) {
            return;
        }
        try {
            redis.execute(UNLOCK_SCRIPT, List.of(key), token);
        } catch (Exception e) {
            log.warn("释放锁失败, key={}", key, e);
        }
    }
}
