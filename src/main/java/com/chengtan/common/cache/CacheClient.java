package com.chengtan.common.cache;

import cn.hutool.core.lang.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 缓存工具：空值缓存防穿透、互斥锁防击穿、TTL 随机偏移防雪崩。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheClient {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long LOCK_TTL_SECONDS = 10;
    private static final int MAX_LOCK_RETRY = 3;

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public <T> T get(String key, Class<T> type) {
        String json = redis.opsForValue().get(key);
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("缓存反序列化失败, key={}", key, e);
            return null;
        }
    }

    public <T> List<T> getList(String key, Class<T> elementType) {
        String json = redis.opsForValue().get(key);
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (Exception e) {
            log.warn("缓存列表反序列化失败, key={}", key, e);
            return null;
        }
    }

    public <T> T getOrLoad(String key, Class<T> type, Supplier<T> loader,
                           Duration ttl, Duration nullTtl) {
        T cached = get(key, type);
        if (cached != null) {
            return cached;
        }
        if (Boolean.TRUE.equals(redis.hasKey(key))) {
            return null;
        }
        String lockKey = "lock:cache:" + key;
        String token = UUID.randomUUID().toString(true);
        for (int i = 0; i < MAX_LOCK_RETRY; i++) {
            if (tryLock(lockKey, token)) {
                try {
                    cached = get(key, type);
                    if (cached != null) {
                        return cached;
                    }
                    if (Boolean.TRUE.equals(redis.hasKey(key))) {
                        return null;
                    }
                    T value = loader.get();
                    if (value == null) {
                        redis.opsForValue().set(key, "", nullTtl);
                    } else {
                        set(key, value, ttl);
                    }
                    return value;
                } finally {
                    unlock(lockKey, token);
                }
            }
            sleepQuietly(50);
        }
        // 抢锁失败降级为直接查库，不写缓存，避免请求被拖死
        return loader.get();
    }

    public <T> List<T> getOrLoadList(String key, Class<T> elementType, Supplier<List<T>> loader, Duration ttl) {
        List<T> cached = getList(key, elementType);
        if (cached != null) {
            return cached;
        }
        String lockKey = "lock:cache:" + key;
        String token = UUID.randomUUID().toString(true);
        for (int i = 0; i < MAX_LOCK_RETRY; i++) {
            if (tryLock(lockKey, token)) {
                try {
                    cached = getList(key, elementType);
                    if (cached != null) {
                        return cached;
                    }
                    List<T> value = loader.get();
                    set(key, value, ttl);
                    return value;
                } finally {
                    unlock(lockKey, token);
                }
            }
            sleepQuietly(50);
        }
        return loader.get();
    }

    public void set(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            long seconds = Math.max(1, ttl.toSeconds());
            long jitter = RANDOM.nextInt(30);
            redis.opsForValue().set(key, json, seconds + jitter, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写缓存失败, key={}", key, e);
        }
    }

    public void delete(String key) {
        redis.delete(key);
    }

    private boolean tryLock(String key, String token) {
        Boolean ok = redis.opsForValue().setIfAbsent(key, token, LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    private void unlock(String key, String token) {
        try {
            redis.execute(UNLOCK_SCRIPT, List.of(key), token);
        } catch (Exception e) {
            log.warn("释放缓存锁失败, key={}", key, e);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
