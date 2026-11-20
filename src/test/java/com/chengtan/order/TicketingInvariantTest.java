package com.chengtan.order;

import com.chengtan.auth.UserContext;
import com.chengtan.auth.UserPrincipal;
import com.chengtan.common.BizException;
import com.chengtan.common.enums.OrderStatus;
import com.chengtan.common.enums.TicketStatus;
import com.chengtan.mq.OrderCreateMessage;
import com.chengtan.mq.OrderProducer;
import com.chengtan.order.dto.CreateOrderRequest;
import com.chengtan.order.dto.RefundRequest;
import com.chengtan.order.dto.RefundVO;
import com.chengtan.order.service.CreateHandleResult;
import com.chengtan.order.service.OrderService;
import com.chengtan.session.service.SessionCacheService;
import com.chengtan.verify.dto.VerifyRequest;
import com.chengtan.verify.dto.VerifyResultVO;
import com.chengtan.verify.service.VerifyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票务核心不变量测试：这些断言全部跑真实 MySQL + Redis，不是 mock。
 *
 * 覆盖：
 * 1. 100 并发抢 20 张 —— 不超卖
 * 2. 同一消息并发重复投递 —— 只生成 1 个订单
 * 3. 超时取消 —— 票作废、DB/Redis 库存同时回补
 * 4. 按票退款 —— 库存回补、订单状态收敛
 * 5. 核销幂等 —— 同一 requestId 只核销一次
 * 6. 一证一票 —— 同一场次同一证件不能重复购票
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TicketingInvariantTest {

    private static final long VENUE_ID = 1L;
    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private OrderService orderService;
    @Autowired
    private VerifyService verifyService;
    @Autowired
    private SessionCacheService sessionCacheService;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 生产环境的 MQ 在测试里替换成 mock：createOrder 只落本地消息表，不真的发消息，
     * 这样出站消息不会被正在运行的开发实例消费，测试之间互不干扰。
     */
    @MockitoBean
    private OrderProducer orderProducer;

    private final Set<Long> sessionIds = new HashSet<>();
    private final Set<Long> attendeeIds = new HashSet<>();
    private final Set<String> orderNos = new HashSet<>();

    @AfterEach
    void cleanUp() {
        orderNos.forEach(orderNo -> jdbc.update(
                "DELETE FROM ct_order_outbox WHERE order_no = ?", orderNo));
        sessionIds.forEach(sessionId -> {
            jdbc.update("DELETE FROM ct_verify_log WHERE ticket_id IN " +
                    "(SELECT id FROM ct_ticket WHERE session_id = ?)", sessionId);
            jdbc.update("DELETE FROM ct_reschedule WHERE from_session_id = ? OR to_session_id = ?",
                    sessionId, sessionId);
            jdbc.update("DELETE FROM ct_ticket WHERE session_id = ?", sessionId);
            jdbc.update("DELETE FROM ct_ticket_order WHERE session_id = ?", sessionId);
            jdbc.update("DELETE FROM ct_waitlist WHERE session_id = ?", sessionId);
            jdbc.update("DELETE FROM ct_venue_session WHERE id = ?", sessionId);
            redis.delete(List.of(
                    "ticket:stock:" + sessionId,
                    "ticket:bought:" + sessionId,
                    "ticket:attendee:" + sessionId,
                    "ticket:session:" + sessionId));
        });
        attendeeIds.forEach(attendeeId ->
                jdbc.update("DELETE FROM ct_attendee WHERE id = ?", attendeeId));
        sessionIds.clear();
        attendeeIds.clear();
        orderNos.clear();
    }

    // ------------------------------------------------------------------
    // 1. 不超卖
    // ------------------------------------------------------------------
    @Test
    @Order(1)
    @DisplayName("100 个用户并发抢 20 张票：不超卖、不少卖、票数与订单一致")
    void concurrentGrabNeverOversells() throws Exception {
        int capacity = 20;
        int users = 100;
        long sessionId = createSession(capacity);

        List<long[]> buyers = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            long userId = 7_000_000L + SEQ.incrementAndGet();
            buyers.add(new long[]{userId, createAttendee(userId)});
        }

        List<String> succeeded = Collections.synchronizedList(new ArrayList<>());
        List<String> rejected = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(users);
        CountDownLatch fire = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(users);
        for (long[] buyer : buyers) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    fire.await();
                    succeeded.add(createOrder(buyer[0], sessionId, List.of(buyer[1])));
                } catch (BizException e) {
                    rejected.add(e.getMessage());
                } catch (Exception e) {
                    rejected.add("UNEXPECTED:" + e.getMessage());
                }
            });
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS), "线程未全部就绪");
        fire.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "并发下单超时");

        assertEquals(capacity, succeeded.size(), "成功下单数应等于库存");
        assertEquals(users - capacity, rejected.size(), "其余请求应被拒绝");
        assertEquals(0, rejected.stream().filter(r -> r.startsWith("UNEXPECTED")).count(),
                "不应出现非业务预期的异常: " + rejected);
        assertEquals("0", redis.opsForValue().get("ticket:stock:" + sessionId),
                "Redis 库存应正好扣到 0");

        // 模拟消费者把消息落库
        for (String orderNo : succeeded) {
            assertEquals(CreateHandleResult.SUCCESS,
                    orderService.handleCreateMessage(loadMessage(orderNo)));
        }

        assertEquals(capacity, count("SELECT COUNT(*) FROM ct_ticket_order WHERE session_id = ?", sessionId),
                "订单数应等于库存");
        assertEquals(capacity, count("SELECT COUNT(*) FROM ct_ticket WHERE session_id = ?", sessionId),
                "票数应等于库存");
        assertEquals(capacity, count("SELECT sold FROM ct_venue_session WHERE id = ?", sessionId),
                "DB 已售应等于库存");
        assertEquals(2, count("SELECT status FROM ct_venue_session WHERE id = ?", sessionId),
                "售罄后场次状态应变为 2");
    }

    // ------------------------------------------------------------------
    // 2. 重复投递幂等
    // ------------------------------------------------------------------
    @Test
    @Order(2)
    @DisplayName("同一条下单消息并发重复投递 8 次：只生成 1 个订单、1 张票、扣 1 次库存")
    void duplicateDeliveryCreatesSingleOrder() throws Exception {
        long sessionId = createSession(5);
        long userId = 7_100_000L + SEQ.incrementAndGet();
        long attendeeId = createAttendee(userId);
        String orderNo = createOrder(userId, sessionId, List.of(attendeeId));
        OrderCreateMessage message = loadMessage(orderNo);

        int deliveries = 8;
        CountDownLatch fire = new CountDownLatch(1);
        List<CreateHandleResult> results = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(deliveries);
        for (int i = 0; i < deliveries; i++) {
            pool.submit(() -> {
                try {
                    fire.await();
                    results.add(orderService.handleCreateMessage(message));
                } catch (Exception e) {
                    results.add(null);
                }
            });
        }
        fire.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "重复投递处理超时");

        assertEquals(1, results.stream().filter(r -> r == CreateHandleResult.SUCCESS).count(),
                "只能有一次真正创建成功: " + results);
        assertEquals(1, count("SELECT COUNT(*) FROM ct_ticket_order WHERE order_no = ?", orderNo));
        assertEquals(1, count("SELECT COUNT(*) FROM ct_ticket WHERE order_no = ?", orderNo));
        assertEquals(1, count("SELECT sold FROM ct_venue_session WHERE id = ?", sessionId),
                "库存只能扣一次");
    }

    // ------------------------------------------------------------------
    // 3. 超时取消回补
    // ------------------------------------------------------------------
    @Test
    @Order(3)
    @DisplayName("未支付订单超时取消：订单关闭、票作废、DB 与 Redis 库存同时回补")
    void timeoutCancelRestoresStock() {
        long sessionId = createSession(3);
        long userId = 7_200_000L + SEQ.incrementAndGet();
        long attendeeId = createAttendee(userId);
        String orderNo = createOrder(userId, sessionId, List.of(attendeeId));
        assertEquals(CreateHandleResult.SUCCESS,
                orderService.handleCreateMessage(loadMessage(orderNo)));

        assertEquals("2", redis.opsForValue().get("ticket:stock:" + sessionId));
        assertEquals(1, count("SELECT sold FROM ct_venue_session WHERE id = ?", sessionId));

        orderService.cancelExpired(orderNo);

        assertEquals(OrderStatus.CANCELLED.getCode(),
                count("SELECT status FROM ct_ticket_order WHERE order_no = ?", orderNo));
        assertEquals(TicketStatus.VOID.getCode(),
                count("SELECT status FROM ct_ticket WHERE order_no = ?", orderNo));
        assertEquals(0, count("SELECT sold FROM ct_venue_session WHERE id = ?", sessionId),
                "DB 库存应回补");
        assertEquals("3", redis.opsForValue().get("ticket:stock:" + sessionId),
                "Redis 库存应回补");
        // 回补脚本在剩余张数为 0 时会直接删掉 hash 字段，因此这里允许 null 或 "0"
        Object bought = redis.opsForHash()
                .get("ticket:bought:" + sessionId, String.valueOf(userId));
        assertTrue(bought == null || "0".equals(bought.toString()),
                "限购计数应清零，实际: " + bought);
        assertEquals(Boolean.FALSE, redis.opsForSet()
                        .isMember("ticket:attendee:" + sessionId, String.valueOf(attendeeId)),
                "证件占用应释放");
    }

    // ------------------------------------------------------------------
    // 4. 按票退款
    // ------------------------------------------------------------------
    @Test
    @Order(4)
    @DisplayName("已支付订单按票退款：票置退款、订单收敛为已退款、库存与限购计数回补")
    void refundRestoresStockAndStatus() {
        long sessionId = createSession(4);
        long userId = 7_300_000L + SEQ.incrementAndGet();
        long attendeeId = createAttendee(userId);
        String orderNo = createOrder(userId, sessionId, List.of(attendeeId));
        assertEquals(CreateHandleResult.SUCCESS,
                orderService.handleCreateMessage(loadMessage(orderNo)));

        asUser(userId, () -> orderService.pay(orderNo));
        assertEquals(OrderStatus.PAID.getCode(),
                count("SELECT status FROM ct_ticket_order WHERE order_no = ?", orderNo));

        Long ticketId = jdbc.queryForObject(
                "SELECT id FROM ct_ticket WHERE order_no = ?", Long.class, orderNo);
        RefundRequest request = new RefundRequest();
        request.setTicketIds(List.of(ticketId));
        RefundVO vo = asUser(userId, () -> orderService.refund(orderNo, request));

        assertNotNull(vo);
        assertEquals(0, new BigDecimal("100.00").compareTo(vo.getRefundAmount()));
        assertEquals(OrderStatus.REFUNDED.getCode(),
                count("SELECT status FROM ct_ticket_order WHERE order_no = ?", orderNo));
        assertEquals(TicketStatus.REFUNDED.getCode(),
                count("SELECT status FROM ct_ticket WHERE order_no = ?", orderNo));
        assertEquals(0, count("SELECT sold FROM ct_venue_session WHERE id = ?", sessionId));
        assertEquals("4", redis.opsForValue().get("ticket:stock:" + sessionId));
        assertEquals(Boolean.FALSE, redis.opsForSet()
                        .isMember("ticket:attendee:" + sessionId, String.valueOf(attendeeId)),
                "退款后证件可再次购票");
    }

    // ------------------------------------------------------------------
    // 5. 核销幂等
    // ------------------------------------------------------------------
    @Test
    @Order(5)
    @DisplayName("同一 requestId 重复核销：只有第一次生效，其余幂等返回")
    void verifyIsIdempotent() {
        long sessionId = createSession(2);
        long userId = 7_400_000L + SEQ.incrementAndGet();
        long attendeeId = createAttendee(userId);
        String orderNo = createOrder(userId, sessionId, List.of(attendeeId));
        assertEquals(CreateHandleResult.SUCCESS,
                orderService.handleCreateMessage(loadMessage(orderNo)));
        asUser(userId, () -> orderService.pay(orderNo));

        String qrToken = jdbc.queryForObject(
                "SELECT qr_token FROM ct_ticket WHERE order_no = ?", String.class, orderNo);
        VerifyRequest request = new VerifyRequest();
        request.setQrToken(qrToken);
        request.setRequestId("it-req-" + orderNo);

        VerifyResultVO first = asOperator(1L, () -> verifyService.verify(request));
        VerifyResultVO second = asOperator(1L, () -> verifyService.verify(request));

        assertEquals(false, first.isIdempotent(), "第一次应真正核销");
        assertEquals(true, second.isIdempotent(), "重复提交应幂等返回");
        assertEquals(TicketStatus.VERIFIED.getCode(),
                count("SELECT status FROM ct_ticket WHERE order_no = ?", orderNo));
        assertEquals(1, count("SELECT COUNT(*) FROM ct_verify_log WHERE request_id = ?",
                request.getRequestId()), "核销日志只能有一条");
        assertEquals(OrderStatus.COMPLETED.getCode(),
                count("SELECT status FROM ct_ticket_order WHERE order_no = ?", orderNo),
                "全部票核销后订单应完成");
    }

    // ------------------------------------------------------------------
    // 6. 一证一票
    // ------------------------------------------------------------------
    @Test
    @Order(6)
    @DisplayName("同一场次同一证件重复购买：被 Lua 原子拦截，库存不被二次扣减")
    void sameIdCardCannotBuyTwice() {
        long sessionId = createSession(2);
        long userId = 7_500_000L + SEQ.incrementAndGet();
        long attendeeId = createAttendee(userId);

        createOrder(userId, sessionId, List.of(attendeeId));
        assertEquals("1", redis.opsForValue().get("ticket:stock:" + sessionId));

        BizException e = assertThrows(BizException.class,
                () -> createOrder(userId, sessionId, List.of(attendeeId)));
        assertTrue(e.getMessage().contains("证件"), "应提示证件重复: " + e.getMessage());
        assertEquals("1", redis.opsForValue().get("ticket:stock:" + sessionId),
                "被拦截的请求不能扣库存");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private long createSession(int capacity) {
        long sessionId = 900_000_000L + SEQ.incrementAndGet();
        LocalDate date = LocalDate.now().plusDays(60 + SEQ.get() % 200);
        jdbc.update("INSERT INTO ct_venue_session " +
                        "(id, venue_id, session_date, start_time, end_time, capacity, sold, price, " +
                        " status, sale_start_time, sale_end_time) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                sessionId, VENUE_ID, Date.valueOf(date), Time.valueOf("10:00:00"),
                Time.valueOf("12:00:00"), capacity, 0, new BigDecimal("100.00"),
                1, Timestamp.valueOf(LocalDateTime.now().minusDays(1)),
                Timestamp.valueOf(LocalDateTime.now().plusDays(1)));
        sessionIds.add(sessionId);
        sessionCacheService.warm(sessionId);
        return sessionId;
    }

    private long createAttendee(long userId) {
        long attendeeId = 800_000_000L + SEQ.incrementAndGet();
        jdbc.update("INSERT INTO ct_attendee " +
                        "(id, user_id, name, id_card_mask, id_card_hash, phone) VALUES (?,?,?,?,?,?)",
                attendeeId, userId, "测试出行人" + attendeeId, "3301**********1234",
                "test-hash-" + attendeeId, "13900000000");
        attendeeIds.add(attendeeId);
        return attendeeId;
    }

    private String createOrder(long userId, long sessionId, List<Long> attendeeIds) {
        return asUser(userId, () -> {
            CreateOrderRequest request = new CreateOrderRequest();
            request.setSessionId(sessionId);
            request.setQuantity(attendeeIds.size());
            request.setAttendeeIds(attendeeIds);
            String orderNo = orderService.createOrder(request).getOrderNo();
            synchronized (orderNos) {
                orderNos.add(orderNo);
            }
            return orderNo;
        });
    }

    private OrderCreateMessage loadMessage(String orderNo) {
        String payload = jdbc.queryForObject(
                "SELECT payload FROM ct_order_outbox WHERE order_no = ?", String.class, orderNo);
        try {
            return objectMapper.readValue(payload, OrderCreateMessage.class);
        } catch (Exception e) {
            throw new IllegalStateException("本地消息表 payload 解析失败", e);
        }
    }

    private int count(String sql, Object arg) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arg);
        return value == null ? 0 : value;
    }

    private <T> T asUser(long userId, java.util.function.Supplier<T> action) {
        UserContext.set(new UserPrincipal(userId, "1390000" + userId % 10000, "测试用户", 0));
        try {
            return action.get();
        } finally {
            UserContext.clear();
        }
    }

    private void asUser(long userId, Runnable action) {
        asUser(userId, () -> {
            action.run();
            return null;
        });
    }

    private <T> T asOperator(long userId, java.util.function.Supplier<T> action) {
        UserContext.set(new UserPrincipal(userId, "13800000000", "运营", 1));
        try {
            return action.get();
        } finally {
            UserContext.clear();
        }
    }
}
