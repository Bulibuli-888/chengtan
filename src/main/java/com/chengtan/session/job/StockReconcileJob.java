package com.chengtan.session.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengtan.common.cache.CacheKeys;
import com.chengtan.session.entity.VenueSession;
import com.chengtan.session.mapper.VenueSessionMapper;
import com.chengtan.session.service.SessionCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 库存对账：
 * Redis 库存 > 数据库可售时单向修正为数据库值，优先保证不超卖；
 * Redis 库存 < 数据库可售时只告警（可能是候补预扣或消息在途），不自动加回。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.reconcile.enabled", havingValue = "true", matchIfMissing = true)
public class StockReconcileJob {

    private final VenueSessionMapper sessionMapper;
    private final StringRedisTemplate redis;
    private final SessionCacheService sessionCacheService;

    @Scheduled(fixedDelayString = "${app.reconcile.fixed-delay-seconds:300}000", initialDelay = 60_000)
    public void reconcile() {
        List<VenueSession> sessions = sessionMapper.selectList(new LambdaQueryWrapper<VenueSession>()
                .in(VenueSession::getStatus, 1, 2)
                .ge(VenueSession::getSessionDate, LocalDate.now()));
        int fixed = 0;
        int warned = 0;
        for (VenueSession session : sessions) {
            int dbRemaining = Math.max(0, session.getCapacity() - session.getSold());
            String key = CacheKeys.SECKILL_STOCK + session.getId();
            String value = redis.opsForValue().get(key);
            if (value == null) {
                sessionCacheService.warm(session);
                continue;
            }
            int redisRemaining;
            try {
                redisRemaining = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                log.warn("库存值非法, sessionId={}, value={}", session.getId(), value);
                continue;
            }
            if (redisRemaining > dbRemaining) {
                redis.opsForValue().set(key, String.valueOf(dbRemaining));
                fixed++;
                log.warn("库存对账修正, sessionId={}, redis={}, db={}",
                        session.getId(), redisRemaining, dbRemaining);
            } else if (redisRemaining < dbRemaining) {
                warned++;
                log.info("库存低于数据库（可能是候补预扣/消息在途）, sessionId={}, redis={}, db={}",
                        session.getId(), redisRemaining, dbRemaining);
            }
        }
        if (fixed > 0 || warned > 0) {
            log.info("库存对账完成, sessions={}, fixed={}, warned={}", sessions.size(), fixed, warned);
        }
    }
}
