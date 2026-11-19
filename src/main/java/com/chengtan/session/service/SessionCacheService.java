package com.chengtan.session.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengtan.common.cache.CacheKeys;
import com.chengtan.session.entity.VenueSession;
import com.chengtan.session.mapper.VenueSessionMapper;
import com.chengtan.ticket.mapper.TicketMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 场次缓存预热：库存、限购计数、证件集合、元信息。
 * 只在 key 不存在时写入，避免重启时把正在进行的抢票库存覆盖掉。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionCacheService {

    private static final int DEFAULT_LIMIT = 3;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final VenueSessionMapper sessionMapper;
    private final TicketMapper ticketMapper;
    private final StringRedisTemplate redis;

    public void warmAll() {
        List<VenueSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<VenueSession>()
                        .in(VenueSession::getStatus, 0, 1, 2)
                        .ge(VenueSession::getSessionDate, LocalDate.now()));
        sessions.forEach(this::warm);
        log.info("场次缓存预热完成, sessions={}", sessions.size());
    }

    public void warm(Long sessionId) {
        VenueSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            warm(session);
        }
    }

    public void warm(VenueSession session) {
        Long sessionId = session.getId();

        String metaKey = CacheKeys.SECKILL_META + sessionId;
        if (!hasKey(metaKey)) {
            Map<String, String> meta = new HashMap<>(8);
            meta.put("venueId", session.getVenueId().toString());
            meta.put("price", session.getPrice().toPlainString());
            meta.put("capacity", session.getCapacity().toString());
            meta.put("status", session.getStatus().toString());
            meta.put("saleStart", String.valueOf(toEpochMillis(session.getSaleStartTime())));
            meta.put("saleEnd", String.valueOf(toEpochMillis(session.getSaleEndTime())));
            meta.put("limit", String.valueOf(DEFAULT_LIMIT));
            redis.opsForHash().putAll(metaKey, meta);
        }

        String stockKey = CacheKeys.SECKILL_STOCK + sessionId;
        if (!hasKey(stockKey)) {
            int remaining = Math.max(0, session.getCapacity() - session.getSold());
            redis.opsForValue().set(stockKey, String.valueOf(remaining));
        }

        String boughtKey = CacheKeys.SECKILL_USER_COUNT + sessionId;
        if (!hasKey(boughtKey)) {
            List<Long> userIds = ticketMapper.selectActiveUserIds(sessionId);
            Map<Long, Long> counts = userIds.stream()
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
            counts.forEach((userId, count) ->
                    redis.opsForHash().put(boughtKey, userId.toString(), count.toString()));
        }

        String attendeeKey = CacheKeys.SECKILL_ATTENDEE + sessionId;
        if (!hasKey(attendeeKey)) {
            ticketMapper.selectActiveAttendeeIds(sessionId)
                    .forEach(attendeeId -> redis.opsForSet().add(attendeeKey, attendeeId.toString()));
        }
    }

    private long toEpochMillis(java.time.LocalDateTime time) {
        return time.atZone(ZONE).toInstant().toEpochMilli();
    }

    private boolean hasKey(String key) {
        return Boolean.TRUE.equals(redis.hasKey(key));
    }
}
