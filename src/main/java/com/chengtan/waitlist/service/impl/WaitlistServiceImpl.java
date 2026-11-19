package com.chengtan.waitlist.service.impl;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chengtan.attendee.service.AttendeeService;
import com.chengtan.auth.UserContext;
import com.chengtan.common.BizException;
import com.chengtan.common.ErrorCode;
import com.chengtan.common.cache.CacheKeys;
import com.chengtan.common.enums.WaitlistStatus;
import com.chengtan.common.lock.RedisLock;
import com.chengtan.mq.MqNames;
import com.chengtan.mq.OrderCreateMessage;
import com.chengtan.mq.OrderProducer;
import com.chengtan.order.dto.CreateOrderVO;
import com.chengtan.order.entity.OrderOutbox;
import com.chengtan.order.mapper.OrderOutboxMapper;
import com.chengtan.order.util.OrderIdGenerator;
import com.chengtan.session.entity.VenueSession;
import com.chengtan.session.mapper.VenueSessionMapper;
import com.chengtan.session.service.SessionCacheService;
import com.chengtan.venue.entity.Venue;
import com.chengtan.venue.mapper.VenueMapper;
import com.chengtan.waitlist.dto.WaitlistClaimRequest;
import com.chengtan.waitlist.dto.WaitlistJoinRequest;
import com.chengtan.waitlist.dto.WaitlistVO;
import com.chengtan.waitlist.entity.Waitlist;
import com.chengtan.waitlist.mapper.WaitlistMapper;
import com.chengtan.waitlist.service.WaitlistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistServiceImpl implements WaitlistService {

    private static final DefaultRedisScript<Long> RESERVE_STOCK;
    private static final DefaultRedisScript<Long> INCR_STOCK_CAP;
    private static final DefaultRedisScript<Long> CLAIM_TICKET;
    private static final DefaultRedisScript<Long> ROLLBACK_CLAIM;

    static {
        RESERVE_STOCK = new DefaultRedisScript<>();
        RESERVE_STOCK.setLocation(new ClassPathResource("lua/reserve_stock.lua"));
        RESERVE_STOCK.setResultType(Long.class);

        INCR_STOCK_CAP = new DefaultRedisScript<>();
        INCR_STOCK_CAP.setLocation(new ClassPathResource("lua/incr_stock_cap.lua"));
        INCR_STOCK_CAP.setResultType(Long.class);

        CLAIM_TICKET = new DefaultRedisScript<>();
        CLAIM_TICKET.setLocation(new ClassPathResource("lua/claim_ticket.lua"));
        CLAIM_TICKET.setResultType(Long.class);

        ROLLBACK_CLAIM = new DefaultRedisScript<>();
        ROLLBACK_CLAIM.setLocation(new ClassPathResource("lua/rollback_claim.lua"));
        ROLLBACK_CLAIM.setResultType(Long.class);
    }

    private final WaitlistMapper waitlistMapper;
    private final VenueSessionMapper sessionMapper;
    private final VenueMapper venueMapper;
    private final AttendeeService attendeeService;
    private final OrderOutboxMapper outboxMapper;
    private final OrderProducer producer;
    private final OrderIdGenerator idGenerator;
    private final SessionCacheService sessionCacheService;
    private final RedisLock redisLock;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${app.seckill.waitlist-offer-seconds:600}")
    private long offerSeconds;

    @Value("${app.seckill.per-user-limit:3}")
    private int perUserLimit;

    @Override
    public WaitlistVO join(WaitlistJoinRequest request) {
        Long userId = UserContext.requireUserId();
        VenueSession session = sessionMapper.selectById(request.getSessionId());
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "场次不存在");
        }
        if (session.getStatus() != 1 && session.getStatus() != 2) {
            throw new BizException(ErrorCode.SESSION_NOT_ON_SALE);
        }
        if (session.getSessionDate().isBefore(LocalDate.now())) {
            throw new BizException(ErrorCode.SESSION_ENDED);
        }
        int remaining = Math.max(0, session.getCapacity() - session.getSold());
        if (remaining >= request.getQuantity()) {
            throw new BizException(ErrorCode.CONFLICT, "当前仍有余票，请直接购买");
        }

        LocalDateTime now = LocalDateTime.now();
        Waitlist waitlist = new Waitlist();
        waitlist.setSessionId(request.getSessionId());
        waitlist.setUserId(userId);
        waitlist.setQuantity(request.getQuantity());
        waitlist.setStatus(WaitlistStatus.QUEUING.getCode());
        waitlist.setCreateTime(now);
        waitlist.setUpdateTime(now);
        try {
            waitlistMapper.insert(waitlist);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.WAITLIST_DUPLICATED);
        }
        redis.opsForZSet().add(queueKey(session.getId()),
                waitlist.getId().toString(), System.currentTimeMillis());
        return toVO(waitlist, session);
    }

    @Override
    public List<WaitlistVO> my() {
        Long userId = UserContext.requireUserId();
        List<Waitlist> list = waitlistMapper.selectList(new LambdaQueryWrapper<Waitlist>()
                .eq(Waitlist::getUserId, userId)
                .in(Waitlist::getStatus,
                        WaitlistStatus.QUEUING.getCode(),
                        WaitlistStatus.OFFERED.getCode(),
                        WaitlistStatus.CONVERTED.getCode())
                .orderByDesc(Waitlist::getCreateTime));
        return list.stream()
                .map(w -> toVO(w, sessionMapper.selectById(w.getSessionId())))
                .collect(Collectors.toList());
    }

    @Override
    public void cancel(Long id) {
        Long userId = UserContext.requireUserId();
        Waitlist waitlist = waitlistMapper.selectOne(new LambdaQueryWrapper<Waitlist>()
                .eq(Waitlist::getId, id)
                .eq(Waitlist::getUserId, userId));
        if (waitlist == null) {
            throw new BizException(ErrorCode.WAITLIST_NOT_FOUND);
        }
        if (waitlist.getStatus() == WaitlistStatus.CONVERTED.getCode()) {
            throw new BizException(ErrorCode.WAITLIST_STATUS_ERROR, "已转订单，不能取消候补");
        }
        if (waitlist.getStatus() == WaitlistStatus.CANCELLED.getCode()
                || waitlist.getStatus() == WaitlistStatus.EXPIRED.getCode()) {
            return;
        }
        int updated = waitlistMapper.update(null, new LambdaUpdateWrapper<Waitlist>()
                .eq(Waitlist::getId, id)
                .eq(Waitlist::getUserId, userId)
                .in(Waitlist::getStatus,
                        WaitlistStatus.QUEUING.getCode(),
                        WaitlistStatus.OFFERED.getCode())
                .set(Waitlist::getStatus, WaitlistStatus.CANCELLED.getCode())
                .set(Waitlist::getUpdateTime, LocalDateTime.now()));
        redis.opsForZSet().remove(queueKey(waitlist.getSessionId()), id.toString());
        if (updated > 0 && waitlist.getStatus() == WaitlistStatus.OFFERED.getCode()) {
            incrStockCap(waitlist.getSessionId(), waitlist.getQuantity());
            onStockReleased(waitlist.getSessionId());
        }
    }

    @Override
    public CreateOrderVO claim(Long id, WaitlistClaimRequest request) {
        Long userId = UserContext.requireUserId();
        Waitlist waitlist = waitlistMapper.selectOne(new LambdaQueryWrapper<Waitlist>()
                .eq(Waitlist::getId, id)
                .eq(Waitlist::getUserId, userId));
        if (waitlist == null) {
            throw new BizException(ErrorCode.WAITLIST_NOT_FOUND);
        }
        if (waitlist.getStatus() != WaitlistStatus.OFFERED.getCode()) {
            throw new BizException(ErrorCode.WAITLIST_STATUS_ERROR, "候补尚未补位或已失效");
        }
        if (waitlist.getOfferExpireTime() != null
                && waitlist.getOfferExpireTime().isBefore(LocalDateTime.now())) {
            expireOffer(waitlist);
            throw new BizException(ErrorCode.WAITLIST_STATUS_ERROR, "补位已过期，已顺延给下一位");
        }
        List<Long> attendeeIds = request.getAttendeeIds();
        if (attendeeIds == null || attendeeIds.size() != waitlist.getQuantity()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "出行人数量与候补数量不一致");
        }
        attendeeService.getOwned(userId, attendeeIds);

        VenueSession session = sessionMapper.selectById(waitlist.getSessionId());
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "场次不存在");
        }

        long orderId = idGenerator.nextId();
        String orderNo = idGenerator.nextOrderNo();

        List<String> keys = List.of(
                CacheKeys.SECKILL_USER_COUNT + session.getId(),
                CacheKeys.SECKILL_ATTENDEE + session.getId());
        List<String> args = new ArrayList<>();
        args.add(userId.toString());
        args.add(waitlist.getQuantity().toString());
        args.add(String.valueOf(perUserLimit));
        attendeeIds.forEach(aid -> args.add(aid.toString()));
        Long result = redis.execute(CLAIM_TICKET, keys, args.toArray());
        int code = result == null ? -1 : result.intValue();
        if (code != 0) {
            throw mapClaimError(code);
        }

        OrderCreateMessage message = OrderCreateMessage.builder()
                .messageId(UUID.randomUUID().toString(true))
                .orderNo(orderNo)
                .orderId(orderId)
                .userId(userId)
                .venueId(session.getVenueId())
                .sessionId(session.getId())
                .quantity(waitlist.getQuantity())
                .unitPrice(session.getPrice())
                .attendeeIds(attendeeIds)
                .createdAt(System.currentTimeMillis())
                .waitlistId(waitlist.getId())
                .reserved(true)
                .build();
        try {
            saveOutbox(message);
        } catch (Exception e) {
            rollbackClaim(session.getId(), userId, waitlist.getQuantity(), attendeeIds);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "下单失败，请重试");
        }
        if (producer.publishOrderCreate(message)) {
            markOutboxSent(message.getMessageId());
        }
        return new CreateOrderVO(orderNo, "CREATING");
    }

    @Override
    public void onStockReleased(Long sessionId) {
        String lockKey = CacheKeys.WAITLIST_OFFER_LOCK + sessionId;
        String token = redisLock.tryLock(lockKey, 10);
        if (token == null) {
            return;
        }
        try {
            for (int i = 0; i < 3; i++) {
                Set<String> first = redis.opsForZSet().range(queueKey(sessionId), 0, 0);
                if (first == null || first.isEmpty()) {
                    return;
                }
                String idStr = first.iterator().next();
                Waitlist waitlist = waitlistMapper.selectById(Long.valueOf(idStr));
                if (waitlist == null || waitlist.getStatus() != WaitlistStatus.QUEUING.getCode()) {
                    redis.opsForZSet().remove(queueKey(sessionId), idStr);
                    continue;
                }
                Long reserved = redis.execute(RESERVE_STOCK,
                        List.of(CacheKeys.SECKILL_STOCK + sessionId),
                        String.valueOf(waitlist.getQuantity()));
                if (reserved == null || reserved != 1) {
                    if (reserved != null && reserved == -1) {
                        sessionCacheService.warm(sessionId);
                    }
                    return;
                }
                LocalDateTime now = LocalDateTime.now();
                int updated = waitlistMapper.update(null, new LambdaUpdateWrapper<Waitlist>()
                        .eq(Waitlist::getId, waitlist.getId())
                        .eq(Waitlist::getStatus, WaitlistStatus.QUEUING.getCode())
                        .set(Waitlist::getStatus, WaitlistStatus.OFFERED.getCode())
                        .set(Waitlist::getNotifyTime, now)
                        .set(Waitlist::getOfferExpireTime, now.plusSeconds(offerSeconds))
                        .set(Waitlist::getUpdateTime, now));
                if (updated == 0) {
                    incrStockCap(sessionId, waitlist.getQuantity());
                    redis.opsForZSet().remove(queueKey(sessionId), idStr);
                    continue;
                }
                redis.opsForZSet().remove(queueKey(sessionId), idStr);
                log.info("候补补位成功, sessionId={}, userId={}, waitlistId={}",
                        sessionId, waitlist.getUserId(), waitlist.getId());
            }
        } finally {
            redisLock.unlock(lockKey, token);
        }
    }

    @Override
    public void expireOffers() {
        List<Waitlist> list = waitlistMapper.selectList(new LambdaQueryWrapper<Waitlist>()
                .eq(Waitlist::getStatus, WaitlistStatus.OFFERED.getCode())
                .lt(Waitlist::getOfferExpireTime, LocalDateTime.now())
                .orderByAsc(Waitlist::getId)
                .last("LIMIT 100"));
        for (Waitlist waitlist : list) {
            expireOffer(waitlist);
        }
    }

    private void expireOffer(Waitlist waitlist) {
        int updated = waitlistMapper.update(null, new LambdaUpdateWrapper<Waitlist>()
                .eq(Waitlist::getId, waitlist.getId())
                .eq(Waitlist::getStatus, WaitlistStatus.OFFERED.getCode())
                .set(Waitlist::getStatus, WaitlistStatus.EXPIRED.getCode())
                .set(Waitlist::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            return;
        }
        incrStockCap(waitlist.getSessionId(), waitlist.getQuantity());
        onStockReleased(waitlist.getSessionId());
        log.info("候补补位过期, waitlistId={}, sessionId={}", waitlist.getId(), waitlist.getSessionId());
    }

    private WaitlistVO toVO(Waitlist waitlist, VenueSession session) {
        WaitlistVO vo = new WaitlistVO();
        vo.setId(waitlist.getId());
        vo.setSessionId(waitlist.getSessionId());
        vo.setQuantity(waitlist.getQuantity());
        vo.setStatus(waitlist.getStatus());
        vo.setStatusDesc(WaitlistStatus.of(waitlist.getStatus()).getDesc());
        vo.setNotifyTime(waitlist.getNotifyTime());
        vo.setOfferExpireTime(waitlist.getOfferExpireTime());
        vo.setCreateTime(waitlist.getCreateTime());
        if (session != null) {
            vo.setVenueId(session.getVenueId());
            vo.setSessionDate(session.getSessionDate());
            vo.setStartTime(session.getStartTime());
            vo.setEndTime(session.getEndTime());
            vo.setPrice(session.getPrice());
            Venue venue = venueMapper.selectById(session.getVenueId());
            vo.setVenueName(venue == null ? "" : venue.getName());
        }
        if (waitlist.getStatus() == WaitlistStatus.QUEUING.getCode()) {
            Long rank = redis.opsForZSet().rank(queueKey(waitlist.getSessionId()),
                    waitlist.getId().toString());
            vo.setPosition(rank == null ? null : rank + 1);
        }
        return vo;
    }

    private BizException mapClaimError(int code) {
        return switch (code) {
            case 2 -> new BizException(ErrorCode.LIMIT_EXCEEDED, "每人限购 " + perUserLimit + " 张");
            case 7 -> new BizException(ErrorCode.ID_CARD_DUPLICATED);
            default -> new BizException(ErrorCode.SYSTEM_ERROR, "候补下单失败，请重试");
        };
    }

    private void saveOutbox(OrderCreateMessage message) throws Exception {
        OrderOutbox outbox = new OrderOutbox();
        outbox.setMessageId(message.getMessageId());
        outbox.setOrderNo(message.getOrderNo());
        outbox.setExchangeName(MqNames.ORDER_EXCHANGE);
        outbox.setRoutingKey(MqNames.ORDER_CREATE_ROUTING);
        outbox.setPayload(objectMapper.writeValueAsString(message));
        outbox.setStatus(0);
        outbox.setRetryCount(0);
        outbox.setNextRetryTime(LocalDateTime.now());
        outbox.setCreateTime(LocalDateTime.now());
        outbox.setUpdateTime(LocalDateTime.now());
        outboxMapper.insert(outbox);
    }

    private void markOutboxSent(String messageId) {
        outboxMapper.update(null, new LambdaUpdateWrapper<OrderOutbox>()
                .eq(OrderOutbox::getMessageId, messageId)
                .set(OrderOutbox::getStatus, 1)
                .set(OrderOutbox::getUpdateTime, LocalDateTime.now()));
    }

    private void rollbackClaim(Long sessionId, Long userId, Integer quantity, List<Long> attendeeIds) {
        try {
            List<String> keys = List.of(
                    CacheKeys.SECKILL_USER_COUNT + sessionId,
                    CacheKeys.SECKILL_ATTENDEE + sessionId);
            List<String> args = new ArrayList<>();
            args.add(userId.toString());
            args.add(quantity.toString());
            attendeeIds.forEach(aid -> args.add(aid.toString()));
            redis.execute(ROLLBACK_CLAIM, keys, args.toArray());
        } catch (Exception e) {
            log.error("回滚候补限购计数失败, sessionId={}, userId={}", sessionId, userId, e);
        }
    }

    private void incrStockCap(Long sessionId, Integer quantity) {
        try {
            redis.execute(INCR_STOCK_CAP,
                    List.of(CacheKeys.SECKILL_STOCK + sessionId,
                            CacheKeys.SECKILL_META + sessionId),
                    String.valueOf(quantity));
        } catch (Exception e) {
            log.error("回补库存失败, sessionId={}, quantity={}", sessionId, quantity, e);
        }
    }

    private String queueKey(Long sessionId) {
        return CacheKeys.WAITLIST_QUEUE + sessionId;
    }
}
