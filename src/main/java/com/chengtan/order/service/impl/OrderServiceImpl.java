package com.chengtan.order.service.impl;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chengtan.attendee.entity.Attendee;
import com.chengtan.attendee.mapper.AttendeeMapper;
import com.chengtan.attendee.service.AttendeeService;
import com.chengtan.auth.UserContext;
import com.chengtan.common.BizException;
import com.chengtan.common.ErrorCode;
import com.chengtan.common.PageResult;
import com.chengtan.common.cache.CacheKeys;
import com.chengtan.common.enums.OrderStatus;
import com.chengtan.common.enums.TicketStatus;
import com.chengtan.common.enums.WaitlistStatus;
import com.chengtan.common.lock.RedisLock;
import com.chengtan.metrics.TicketMetrics;
import com.chengtan.mq.MqNames;
import com.chengtan.mq.OrderCreateMessage;
import com.chengtan.mq.OrderProducer;
import com.chengtan.mq.OrderTimeoutMessage;
import com.chengtan.order.dto.CreateOrderRequest;
import com.chengtan.order.dto.CreateOrderVO;
import com.chengtan.order.dto.OrderCardVO;
import com.chengtan.order.dto.OrderDetailVO;
import com.chengtan.order.dto.RefundRequest;
import com.chengtan.order.dto.RefundVO;
import com.chengtan.order.dto.TicketVO;
import com.chengtan.order.entity.OrderOutbox;
import com.chengtan.order.entity.TicketOrder;
import com.chengtan.order.mapper.OrderOutboxMapper;
import com.chengtan.order.mapper.TicketOrderMapper;
import com.chengtan.order.service.CreateHandleResult;
import com.chengtan.order.service.OrderService;
import com.chengtan.order.util.OrderIdGenerator;
import com.chengtan.reschedule.dto.RescheduleRequest;
import com.chengtan.reschedule.dto.RescheduleVO;
import com.chengtan.reschedule.entity.Reschedule;
import com.chengtan.reschedule.mapper.RescheduleMapper;
import com.chengtan.session.entity.VenueSession;
import com.chengtan.session.mapper.VenueSessionMapper;
import com.chengtan.session.service.SessionCacheService;
import com.chengtan.ticket.entity.Ticket;
import com.chengtan.ticket.mapper.TicketMapper;
import com.chengtan.venue.entity.Venue;
import com.chengtan.venue.mapper.VenueMapper;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT;
    private static final DefaultRedisScript<Long> RESERVE_STOCK;
    private static final DefaultRedisScript<Long> INCR_STOCK_CAP;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill_ticket.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);

        ROLLBACK_SCRIPT = new DefaultRedisScript<>();
        ROLLBACK_SCRIPT.setLocation(new ClassPathResource("lua/rollback_ticket.lua"));
        ROLLBACK_SCRIPT.setResultType(Long.class);

        RESERVE_STOCK = new DefaultRedisScript<>();
        RESERVE_STOCK.setLocation(new ClassPathResource("lua/reserve_stock.lua"));
        RESERVE_STOCK.setResultType(Long.class);

        INCR_STOCK_CAP = new DefaultRedisScript<>();
        INCR_STOCK_CAP.setLocation(new ClassPathResource("lua/incr_stock_cap.lua"));
        INCR_STOCK_CAP.setResultType(Long.class);
    }

    private final VenueSessionMapper sessionMapper;
    private final VenueMapper venueMapper;
    private final TicketOrderMapper orderMapper;
    private final TicketMapper ticketMapper;
    private final OrderOutboxMapper outboxMapper;
    private final AttendeeMapper attendeeMapper;
    private final AttendeeService attendeeService;
    private final SessionCacheService sessionCacheService;
    private final OrderIdGenerator idGenerator;
    private final OrderProducer producer;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final WaitlistMapper waitlistMapper;
    private final WaitlistService waitlistService;
    private final RescheduleMapper rescheduleMapper;
    private final RedisLock redisLock;
    private final TransactionTemplate transactionTemplate;
    private final TicketMetrics metrics;

    @Value("${app.seckill.order-timeout-seconds:900}")
    private long orderTimeoutSeconds;

    @Value("${app.seckill.per-user-limit:3}")
    private int perUserLimit;

    @Override
    public CreateOrderVO createOrder(CreateOrderRequest request) {
        Long userId = UserContext.requireUserId();
        Long sessionId = request.getSessionId();
        Integer quantity = request.getQuantity();
        List<Long> attendeeIds = request.getAttendeeIds();

        if (attendeeIds == null || attendeeIds.size() != quantity) {
            throw new BizException(ErrorCode.PARAM_ERROR, "购票数量与出行人数量不一致");
        }
        if (new HashSet<>(attendeeIds).size() != quantity) {
            throw new BizException(ErrorCode.PARAM_ERROR, "出行人不能重复");
        }

        String metaKey = CacheKeys.SECKILL_META + sessionId;
        if (!Boolean.TRUE.equals(redis.hasKey(metaKey))) {
            sessionCacheService.warm(sessionId);
        }
        Map<Object, Object> meta = redis.opsForHash().entries(metaKey);
        if (meta.isEmpty()) {
            throw new BizException(ErrorCode.SESSION_NOT_ON_SALE, "场次不存在或未开售");
        }

        long orderId = idGenerator.nextId();
        String orderNo = idGenerator.nextOrderNo();

        List<String> args = new ArrayList<>();
        args.add(userId.toString());
        args.add(quantity.toString());
        args.add(String.valueOf(System.currentTimeMillis()));
        args.add(String.valueOf(perUserLimit));
        attendeeIds.forEach(id -> args.add(id.toString()));

        List<String> keys = List.of(
                CacheKeys.SECKILL_STOCK + sessionId,
                CacheKeys.SECKILL_USER_COUNT + sessionId,
                CacheKeys.SECKILL_ATTENDEE + sessionId,
                metaKey);

        Long result = redis.execute(SECKILL_SCRIPT, keys, args.toArray());
        int code = result == null ? -1 : result.intValue();
        if (code != 0) {
            metrics.seckill(metricLabel(code));
            throw mapSeckillError(code);
        }

        try {
            // 只有真正抢到名额的请求才查库校验出行人归属，避免把数据库打到热点上
            attendeeService.getOwned(userId, attendeeIds);

            BigDecimal price = new BigDecimal(String.valueOf(meta.get("price")));
            Long venueId = Long.valueOf(String.valueOf(meta.get("venueId")));
            OrderCreateMessage message = OrderCreateMessage.builder()
                    .messageId(UUID.randomUUID().toString(true))
                    .orderNo(orderNo)
                    .orderId(orderId)
                    .userId(userId)
                    .venueId(venueId)
                    .sessionId(sessionId)
                    .quantity(quantity)
                    .unitPrice(price)
                    .attendeeIds(attendeeIds)
                    .createdAt(System.currentTimeMillis())
                    .build();

            saveOutbox(message);
            boolean sent = producer.publishOrderCreate(message);
            if (sent) {
                markOutboxSent(message.getMessageId());
            }
            metrics.seckill("success");
            return new CreateOrderVO(orderNo, "CREATING");
        } catch (BizException e) {
            rollbackRedis(sessionId, userId, quantity, attendeeIds);
            throw e;
        } catch (Exception e) {
            log.error("下单落库失败, orderNo={}", orderNo, e);
            rollbackRedis(sessionId, userId, quantity, attendeeIds);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "下单失败，请重试");
        }
    }

    private String metricLabel(int code) {
        return switch (code) {
            case 1 -> "sold_out";
            case 2 -> "limit";
            case 3 -> "not_on_sale";
            case 4 -> "not_started";
            case 5 -> "ended";
            case 6 -> "not_init";
            case 7 -> "id_dup";
            default -> "error";
        };
    }

    private BizException mapSeckillError(int code) {
        return switch (code) {
            case 1 -> new BizException(ErrorCode.STOCK_NOT_ENOUGH);
            case 2 -> new BizException(ErrorCode.LIMIT_EXCEEDED, "每人限购 " + perUserLimit + " 张");
            case 3 -> new BizException(ErrorCode.SESSION_NOT_ON_SALE);
            case 4 -> new BizException(ErrorCode.SESSION_NOT_ON_SALE, "还未到开售时间");
            case 5 -> new BizException(ErrorCode.SESSION_ENDED);
            case 6 -> new BizException(ErrorCode.SESSION_NOT_ON_SALE, "场次库存未初始化");
            case 7 -> new BizException(ErrorCode.ID_CARD_DUPLICATED);
            default -> new BizException(ErrorCode.SYSTEM_ERROR, "抢票失败，请重试");
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

    @Override
    @Transactional
    public CreateHandleResult handleCreateMessage(OrderCreateMessage message) {
        TicketOrder exists = orderMapper.selectOne(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getOrderNo, message.getOrderNo()));
        if (exists != null) {
            return CreateHandleResult.ALREADY_EXISTS;
        }

        LocalDateTime now = LocalDateTime.now();
        TicketOrder order = new TicketOrder();
        order.setId(message.getOrderId());
        order.setOrderNo(message.getOrderNo());
        order.setUserId(message.getUserId());
        order.setVenueId(message.getVenueId());
        order.setSessionId(message.getSessionId());
        order.setQuantity(message.getQuantity());
        order.setUnitPrice(message.getUnitPrice());
        order.setTotalAmount(message.getUnitPrice()
                .multiply(BigDecimal.valueOf(message.getQuantity())));
        order.setRefundAmount(BigDecimal.ZERO);
        order.setStatus(OrderStatus.PENDING_PAY.getCode());
        order.setExpireTime(now.plusSeconds(orderTimeoutSeconds));
        order.setCreateTime(now);
        order.setUpdateTime(now);
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // 并发重复投递：另一个消费者已经插入同一订单号，唯一索引兜底，直接当作已存在
            log.info("订单已存在（并发重复投递）, orderNo={}", message.getOrderNo());
            return CreateHandleResult.ALREADY_EXISTS;
        }

        int updated = sessionMapper.update(null, new LambdaUpdateWrapper<VenueSession>()
                .eq(VenueSession::getId, message.getSessionId())
                .eq(VenueSession::getStatus, 1)
                .apply("sold + {0} <= capacity", message.getQuantity())
                .setSql("sold = sold + " + message.getQuantity()));
        if (updated == 0) {
            order.setStatus(OrderStatus.CANCELLED.getCode());
            order.setCancelTime(now);
            order.setUpdateTime(now);
            orderMapper.updateById(order);
            log.warn("数据库库存不足，订单作废, orderNo={}", message.getOrderNo());
            return CreateHandleResult.STOCK_FAIL;
        }

        sessionMapper.update(null, new LambdaUpdateWrapper<VenueSession>()
                .eq(VenueSession::getId, message.getSessionId())
                .eq(VenueSession::getStatus, 1)
                .apply("sold >= capacity")
                .set(VenueSession::getStatus, 2));

        for (int i = 0; i < message.getAttendeeIds().size(); i++) {
            Ticket ticket = new Ticket();
            ticket.setTicketNo(message.getOrderNo() + "-" + (i + 1));
            ticket.setQrToken(UUID.randomUUID().toString(true));
            ticket.setOrderId(message.getOrderId());
            ticket.setOrderNo(message.getOrderNo());
            ticket.setUserId(message.getUserId());
            ticket.setVenueId(message.getVenueId());
            ticket.setSessionId(message.getSessionId());
            ticket.setAttendeeId(message.getAttendeeIds().get(i));
            ticket.setStatus(TicketStatus.UNUSED.getCode());
            ticket.setCreateTime(now);
            ticket.setUpdateTime(now);
            ticketMapper.insert(ticket);
        }

        if (message.getWaitlistId() != null) {
            waitlistMapper.update(null, new LambdaUpdateWrapper<Waitlist>()
                    .eq(Waitlist::getId, message.getWaitlistId())
                    .eq(Waitlist::getStatus, WaitlistStatus.OFFERED.getCode())
                    .set(Waitlist::getStatus, WaitlistStatus.CONVERTED.getCode())
                    .set(Waitlist::getUpdateTime, now));
        }

        afterCommit(() -> publishTimeout(message));
        metrics.orderCreated();
        return CreateHandleResult.SUCCESS;
    }

    private void publishTimeout(OrderCreateMessage message) {
        OrderTimeoutMessage timeout = OrderTimeoutMessage.builder()
                .orderNo(message.getOrderNo())
                .sessionId(message.getSessionId())
                .userId(message.getUserId())
                .quantity(message.getQuantity())
                .attendeeIds(message.getAttendeeIds())
                .build();
        if (!producer.publishOrderTimeout(timeout)) {
            log.warn("延迟消息发送失败，将由兜底任务处理, orderNo={}", message.getOrderNo());
        }
    }

    @Override
    public OrderDetailVO detail(String orderNo) {
        Long userId = UserContext.requireUserId();
        TicketOrder order = orderMapper.selectOne(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getOrderNo, orderNo)
                .eq(TicketOrder::getUserId, userId));
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }

        List<Ticket> tickets = ticketMapper.selectList(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getOrderId, order.getId())
                .orderByAsc(Ticket::getId));
        List<Long> attendeeIds = tickets.stream()
                .map(Ticket::getAttendeeId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Attendee> attendeeMap = attendeeIds.isEmpty()
                ? Map.of()
                : attendeeMapper.selectBatchIds(attendeeIds).stream()
                        .collect(Collectors.toMap(Attendee::getId, a -> a));

        Venue venue = venueMapper.selectById(order.getVenueId());
        VenueSession session = sessionMapper.selectById(order.getSessionId());

        OrderDetailVO vo = new OrderDetailVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setStatusDesc(OrderStatus.of(order.getStatus()).getDesc());
        vo.setVenueId(order.getVenueId());
        vo.setVenueName(venue == null ? "" : venue.getName());
        vo.setSessionId(order.getSessionId());
        if (session != null) {
            vo.setSessionDate(session.getSessionDate());
            vo.setStartTime(session.getStartTime());
            vo.setEndTime(session.getEndTime());
        }
        vo.setQuantity(order.getQuantity());
        vo.setUnitPrice(order.getUnitPrice());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setRefundAmount(order.getRefundAmount());
        vo.setExpireTime(order.getExpireTime());
        vo.setCreateTime(order.getCreateTime());
        vo.setPayTime(order.getPayTime());
        vo.setTickets(tickets.stream().map(ticket -> {
            TicketVO tv = new TicketVO();
            tv.setId(ticket.getId());
            tv.setTicketNo(ticket.getTicketNo());
            tv.setAttendeeId(ticket.getAttendeeId());
            Attendee attendee = attendeeMap.get(ticket.getAttendeeId());
            if (attendee != null) {
                tv.setAttendeeName(attendee.getName());
                tv.setIdCardMask(attendee.getIdCardMask());
            }
            tv.setStatus(ticket.getStatus());
            tv.setStatusDesc(TicketStatus.of(ticket.getStatus()).getDesc());
            tv.setQrToken(ticket.getQrToken());
            tv.setVerifyTime(ticket.getVerifyTime());
            return tv;
        }).collect(Collectors.toList()));
        return vo;
    }

    @Override
    public PageResult<OrderCardVO> myOrders(long page, long size) {
        Long userId = UserContext.requireUserId();
        Page<TicketOrder> p = new Page<>(page, size);
        orderMapper.selectPage(p, new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getUserId, userId)
                .orderByDesc(TicketOrder::getCreateTime));

        List<OrderCardVO> records = p.getRecords().stream().map(order -> {
            OrderCardVO vo = new OrderCardVO();
            vo.setOrderNo(order.getOrderNo());
            vo.setQuantity(order.getQuantity());
            vo.setTotalAmount(order.getTotalAmount());
            vo.setStatus(order.getStatus());
            vo.setStatusDesc(OrderStatus.of(order.getStatus()).getDesc());
            vo.setCreateTime(order.getCreateTime());
            Venue venue = venueMapper.selectById(order.getVenueId());
            vo.setVenueName(venue == null ? "" : venue.getName());
            VenueSession session = sessionMapper.selectById(order.getSessionId());
            if (session != null) {
                vo.setSessionDate(session.getSessionDate());
                vo.setStartTime(session.getStartTime());
            }
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(records, p.getTotal(), p.getCurrent(), p.getSize());
    }

    @Override
    @Transactional
    public void cancelByUser(String orderNo) {
        Long userId = UserContext.requireUserId();
        TicketOrder order = orderMapper.selectOne(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getOrderNo, orderNo)
                .eq(TicketOrder::getUserId, userId));
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAY.getCode()) {
            throw new BizException(ErrorCode.ORDER_STATUS_ERROR, "当前状态不可取消");
        }
        cancelInternal(order);
    }

    @Override
    @Transactional
    public void cancelExpired(String orderNo) {
        TicketOrder order = orderMapper.selectOne(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getOrderNo, orderNo));
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAY.getCode()) {
            return;
        }
        cancelInternal(order);
    }

    @Override
    @Transactional
    public void pay(String orderNo) {
        Long userId = UserContext.requireUserId();
        TicketOrder order = orderMapper.selectOne(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getOrderNo, orderNo)
                .eq(TicketOrder::getUserId, userId));
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() == OrderStatus.PAID.getCode()) {
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING_PAY.getCode()) {
            throw new BizException(ErrorCode.ORDER_STATUS_ERROR, "当前状态不可支付");
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<TicketOrder>()
                .eq(TicketOrder::getId, order.getId())
                .eq(TicketOrder::getStatus, OrderStatus.PENDING_PAY.getCode())
                .set(TicketOrder::getStatus, OrderStatus.PAID.getCode())
                .set(TicketOrder::getPayTime, now)
                .set(TicketOrder::getUpdateTime, now));
        if (updated == 0) {
            throw new BizException(ErrorCode.ORDER_STATUS_ERROR, "订单状态已变更，请刷新");
        }
        log.info("订单支付成功, orderNo={}", orderNo);
    }

    @Override
    @Transactional
    public RefundVO refund(String orderNo, RefundRequest request) {
        Long userId = UserContext.requireUserId();
        TicketOrder order = orderMapper.selectOne(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getOrderNo, orderNo)
                .eq(TicketOrder::getUserId, userId));
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() == OrderStatus.CANCELLED.getCode()
                || order.getStatus() == OrderStatus.EXPIRED.getCode()) {
            throw new BizException(ErrorCode.ORDER_STATUS_ERROR, "订单已关闭，不能退款");
        }

        List<Long> ticketIds = request.getTicketIds();
        List<Ticket> tickets = ticketMapper.selectList(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getOrderId, order.getId())
                .in(Ticket::getId, ticketIds));
        if (tickets.size() != ticketIds.size()) {
            throw new BizException(ErrorCode.TICKET_NOT_FOUND);
        }
        long alreadyRefunded = tickets.stream()
                .filter(t -> t.getStatus() == TicketStatus.REFUNDED.getCode())
                .count();
        if (alreadyRefunded == tickets.size()) {
            return new RefundVO(BigDecimal.ZERO, order.getStatus(),
                    OrderStatus.of(order.getStatus()).getDesc());
        }
        if (tickets.stream().anyMatch(t -> t.getStatus() != TicketStatus.UNUSED.getCode())) {
            throw new BizException(ErrorCode.TICKET_NOT_REFUNDABLE, "已核销或已作废的票不能退款");
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = 0;
        for (Ticket ticket : tickets) {
            updated += ticketMapper.update(null, new LambdaUpdateWrapper<Ticket>()
                    .eq(Ticket::getId, ticket.getId())
                    .eq(Ticket::getStatus, TicketStatus.UNUSED.getCode())
                    .set(Ticket::getStatus, TicketStatus.REFUNDED.getCode())
                    .set(Ticket::getRefundTime, now)
                    .set(Ticket::getUpdateTime, now));
        }
        if (updated != tickets.size()) {
            throw new BizException(ErrorCode.TICKET_NOT_REFUNDABLE, "票状态已变更，请刷新后重试");
        }

        BigDecimal amount = order.getUnitPrice().multiply(BigDecimal.valueOf(updated));
        long remaining = ticketMapper.selectCount(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getOrderId, order.getId())
                .in(Ticket::getStatus, TicketStatus.UNUSED.getCode(), TicketStatus.VERIFIED.getCode()));
        long verified = ticketMapper.selectCount(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getOrderId, order.getId())
                .eq(Ticket::getStatus, TicketStatus.VERIFIED.getCode()));
        int newStatus = order.getStatus();
        if (remaining == 0) {
            newStatus = verified > 0
                    ? OrderStatus.COMPLETED.getCode()
                    : OrderStatus.REFUNDED.getCode();
        }

        orderMapper.update(null, new LambdaUpdateWrapper<TicketOrder>()
                .eq(TicketOrder::getId, order.getId())
                .setSql("refund_amount = refund_amount + " + amount.toPlainString())
                .set(TicketOrder::getStatus, newStatus)
                .set(TicketOrder::getUpdateTime, now));

        sessionMapper.update(null, new LambdaUpdateWrapper<VenueSession>()
                .eq(VenueSession::getId, order.getSessionId())
                .ge(VenueSession::getSold, updated)
                .setSql("sold = sold - " + updated));
        VenueSession session = sessionMapper.selectById(order.getSessionId());
        if (session != null && session.getStatus() != null && session.getStatus() == 2
                && session.getSold() < session.getCapacity()) {
            sessionMapper.update(null, new LambdaUpdateWrapper<VenueSession>()
                    .eq(VenueSession::getId, session.getId())
                    .eq(VenueSession::getStatus, 2)
                    .set(VenueSession::getStatus, 1));
        }

        List<Long> attendeeIds = tickets.stream().map(Ticket::getAttendeeId)
                .collect(Collectors.toList());
        int refundedCount = updated;
        afterCommit(() -> {
            rollbackRedis(order.getSessionId(), userId, refundedCount, attendeeIds);
            waitlistService.onStockReleased(order.getSessionId());
        });
        log.info("订单退款成功, orderNo={}, count={}, amount={}", orderNo, updated, amount);
        return new RefundVO(amount, newStatus, OrderStatus.of(newStatus).getDesc());
    }

    @Override
    public RescheduleVO reschedule(String orderNo, RescheduleRequest request) {
        Long userId = UserContext.requireUserId();
        TicketOrder order = orderMapper.selectOne(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getOrderNo, orderNo)
                .eq(TicketOrder::getUserId, userId));
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() != OrderStatus.PAID.getCode()) {
            throw new BizException(ErrorCode.RESCHEDULE_NOT_ALLOWED, "只有已支付且未核销的订单可以改签");
        }
        Long targetId = request.getTargetSessionId();
        if (Objects.equals(targetId, order.getSessionId())) {
            throw new BizException(ErrorCode.RESCHEDULE_SAME_SESSION);
        }
        VenueSession target = sessionMapper.selectById(targetId);
        if (target == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "目标场次不存在");
        }
        if (!Objects.equals(target.getVenueId(), order.getVenueId())) {
            throw new BizException(ErrorCode.RESCHEDULE_NOT_ALLOWED, "只能改签到同一场馆的场次");
        }
        LocalDateTime now = LocalDateTime.now();
        if (target.getStatus() != 1
                || target.getSessionDate().isBefore(LocalDate.now())
                || now.isBefore(target.getSaleStartTime())
                || now.isAfter(target.getSaleEndTime())) {
            throw new BizException(ErrorCode.SESSION_NOT_ON_SALE, "目标场次不可售");
        }
        int quantity = order.getQuantity();
        if (target.getCapacity() - target.getSold() < quantity) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        List<Ticket> tickets = ticketMapper.selectList(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getOrderId, order.getId()));
        if (tickets.isEmpty()
                || tickets.stream().anyMatch(t -> t.getStatus() != TicketStatus.UNUSED.getCode())) {
            throw new BizException(ErrorCode.RESCHEDULE_NOT_ALLOWED, "有票已核销或已退款，不能改签");
        }
        List<Long> attendeeIds = tickets.stream().map(Ticket::getAttendeeId)
                .collect(Collectors.toList());
        long duplicated = ticketMapper.selectCount(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getSessionId, targetId)
                .in(Ticket::getAttendeeId, attendeeIds)
                .in(Ticket::getStatus, TicketStatus.UNUSED.getCode(), TicketStatus.VERIFIED.getCode()));
        if (duplicated > 0) {
            throw new BizException(ErrorCode.ID_CARD_DUPLICATED, "这些出行人在目标场次已有有效票");
        }

        String lockKey = CacheKeys.RESCHEDULE_LOCK + orderNo;
        String token = redisLock.tryLock(lockKey, 10);
        if (token == null) {
            throw new BizException(ErrorCode.CONFLICT, "改签处理中，请稍后重试");
        }
        boolean reserved = false;
        try {
            sessionCacheService.warm(targetId);
            Long result = redis.execute(RESERVE_STOCK,
                    List.of(CacheKeys.SECKILL_STOCK + targetId),
                    String.valueOf(quantity));
            if (result != null && result == -1) {
                sessionCacheService.warm(targetId);
                result = redis.execute(RESERVE_STOCK,
                        List.of(CacheKeys.SECKILL_STOCK + targetId),
                        String.valueOf(quantity));
            }
            if (result == null || result != 1) {
                throw new BizException(ErrorCode.STOCK_NOT_ENOUGH);
            }
            reserved = true;
            RescheduleVO vo = transactionTemplate.execute(status ->
                    doReschedule(order, targetId, quantity, attendeeIds, now));
            if (vo == null) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "改签失败，请重试");
            }
            reserved = false;
            incrStockCap(order.getSessionId(), quantity);
            return vo;
        } catch (RuntimeException e) {
            if (reserved) {
                incrStockCap(targetId, quantity);
            }
            throw e;
        } finally {
            redisLock.unlock(lockKey, token);
        }
    }

    private RescheduleVO doReschedule(TicketOrder order, Long targetId, int quantity,
                                      List<Long> attendeeIds, LocalDateTime now) {
        long first = Math.min(order.getSessionId(), targetId);
        long second = Math.max(order.getSessionId(), targetId);
        sessionMapper.selectForUpdate(first);
        sessionMapper.selectForUpdate(second);

        VenueSession source = sessionMapper.selectById(order.getSessionId());
        VenueSession target = sessionMapper.selectById(targetId);
        if (target == null || target.getStatus() != 1) {
            throw new BizException(ErrorCode.SESSION_NOT_ON_SALE);
        }
        if (target.getSold() + quantity > target.getCapacity()) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        int targetUpdated = sessionMapper.update(null, new LambdaUpdateWrapper<VenueSession>()
                .eq(VenueSession::getId, targetId)
                .eq(VenueSession::getStatus, 1)
                .apply("sold + {0} <= capacity", quantity)
                .setSql("sold = sold + " + quantity));
        if (targetUpdated == 0) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        int sourceUpdated = sessionMapper.update(null, new LambdaUpdateWrapper<VenueSession>()
                .eq(VenueSession::getId, order.getSessionId())
                .ge(VenueSession::getSold, quantity)
                .setSql("sold = sold - " + quantity));
        if (sourceUpdated == 0) {
            throw new BizException(ErrorCode.CONFLICT, "原场次库存异常，请稍后重试");
        }
        int ticketUpdated = ticketMapper.update(null, new LambdaUpdateWrapper<Ticket>()
                .eq(Ticket::getOrderId, order.getId())
                .eq(Ticket::getStatus, TicketStatus.UNUSED.getCode())
                .in(Ticket::getAttendeeId, attendeeIds)
                .set(Ticket::getSessionId, targetId)
                .set(Ticket::getUpdateTime, now));
        if (ticketUpdated != quantity) {
            throw new BizException(ErrorCode.CONFLICT, "票状态已变更，请刷新后重试");
        }
        BigDecimal priceDiff = target.getPrice().subtract(order.getUnitPrice())
                .multiply(BigDecimal.valueOf(quantity));
        BigDecimal newTotal = target.getPrice().multiply(BigDecimal.valueOf(quantity));
        int orderUpdated = orderMapper.update(null, new LambdaUpdateWrapper<TicketOrder>()
                .eq(TicketOrder::getId, order.getId())
                .eq(TicketOrder::getStatus, OrderStatus.PAID.getCode())
                .set(TicketOrder::getSessionId, targetId)
                .set(TicketOrder::getUnitPrice, target.getPrice())
                .set(TicketOrder::getTotalAmount, newTotal)
                .set(TicketOrder::getUpdateTime, now));
        if (orderUpdated == 0) {
            throw new BizException(ErrorCode.CONFLICT, "订单状态已变更，请刷新后重试");
        }
        Reschedule record = new Reschedule();
        record.setOrderId(order.getId());
        record.setOrderNo(order.getOrderNo());
        record.setFromSessionId(order.getSessionId());
        record.setToSessionId(targetId);
        record.setTicketCount(quantity);
        record.setPriceDiff(priceDiff);
        record.setStatus(1);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        rescheduleMapper.insert(record);

        if (target.getSold() + quantity >= target.getCapacity()) {
            sessionMapper.update(null, new LambdaUpdateWrapper<VenueSession>()
                    .eq(VenueSession::getId, targetId)
                    .eq(VenueSession::getStatus, 1)
                    .set(VenueSession::getStatus, 2));
        }
        if (source != null && source.getStatus() == 2
                && source.getSold() - quantity < source.getCapacity()) {
            sessionMapper.update(null, new LambdaUpdateWrapper<VenueSession>()
                    .eq(VenueSession::getId, source.getId())
                    .eq(VenueSession::getStatus, 2)
                    .set(VenueSession::getStatus, 1));
        }
        log.info("订单改签成功, orderNo={}, from={}, to={}, count={}, diff={}",
                order.getOrderNo(), order.getSessionId(), targetId, quantity, priceDiff);
        return new RescheduleVO(order.getOrderNo(), order.getSessionId(), targetId,
                quantity, priceDiff, OrderStatus.PAID.getCode(), OrderStatus.PAID.getDesc());
    }

    private void incrStockCap(Long sessionId, int quantity) {
        try {
            redis.execute(INCR_STOCK_CAP,
                    List.of(CacheKeys.SECKILL_STOCK + sessionId,
                            CacheKeys.SECKILL_META + sessionId),
                    String.valueOf(quantity));
        } catch (Exception e) {
            log.error("回补库存失败, sessionId={}, quantity={}", sessionId, quantity, e);
        }
    }

    private void cancelInternal(TicketOrder order) {
        LocalDateTime now = LocalDateTime.now();
        List<Ticket> tickets = ticketMapper.selectList(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getOrderId, order.getId()));

        int updated = orderMapper.update(null, new LambdaUpdateWrapper<TicketOrder>()
                .eq(TicketOrder::getId, order.getId())
                .eq(TicketOrder::getStatus, OrderStatus.PENDING_PAY.getCode())
                .set(TicketOrder::getStatus, OrderStatus.CANCELLED.getCode())
                .set(TicketOrder::getCancelTime, now)
                .set(TicketOrder::getUpdateTime, now));
        if (updated == 0) {
            return;
        }

        ticketMapper.update(null, new LambdaUpdateWrapper<Ticket>()
                .eq(Ticket::getOrderId, order.getId())
                .eq(Ticket::getStatus, TicketStatus.UNUSED.getCode())
                .set(Ticket::getStatus, TicketStatus.VOID.getCode())
                .set(Ticket::getUpdateTime, now));

        sessionMapper.update(null, new LambdaUpdateWrapper<VenueSession>()
                .eq(VenueSession::getId, order.getSessionId())
                .ge(VenueSession::getSold, order.getQuantity())
                .setSql("sold = sold - " + order.getQuantity()));

        VenueSession session = sessionMapper.selectById(order.getSessionId());
        if (session != null && session.getStatus() != null && session.getStatus() == 2
                && session.getSold() < session.getCapacity()) {
            sessionMapper.update(null, new LambdaUpdateWrapper<VenueSession>()
                    .eq(VenueSession::getId, session.getId())
                    .eq(VenueSession::getStatus, 2)
                    .set(VenueSession::getStatus, 1));
        }

        List<Long> attendeeIds = tickets.stream()
                .map(Ticket::getAttendeeId)
                .collect(Collectors.toList());
        afterCommit(() -> {
            rollbackRedis(order.getSessionId(), order.getUserId(),
                    order.getQuantity(), attendeeIds);
            waitlistService.onStockReleased(order.getSessionId());
        });
        log.info("订单已取消, orderNo={}", order.getOrderNo());
    }

    @Override
    public void compensateRedis(OrderCreateMessage message) {
        rollbackRedis(message.getSessionId(), message.getUserId(),
                message.getQuantity(), message.getAttendeeIds());
    }

    private void rollbackRedis(Long sessionId, Long userId, Integer quantity, List<Long> attendeeIds) {
        try {
            List<String> keys = List.of(
                    CacheKeys.SECKILL_STOCK + sessionId,
                    CacheKeys.SECKILL_USER_COUNT + sessionId,
                    CacheKeys.SECKILL_ATTENDEE + sessionId,
                    CacheKeys.SECKILL_META + sessionId);
            List<String> args = new ArrayList<>();
            args.add(userId.toString());
            args.add(quantity.toString());
            if (attendeeIds != null) {
                attendeeIds.forEach(id -> args.add(id.toString()));
            }
            redis.execute(ROLLBACK_SCRIPT, keys, args.toArray());
        } catch (Exception e) {
            log.error("回补 Redis 库存失败, sessionId={}, userId={}, quantity={}",
                    sessionId, userId, quantity, e);
        }
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
