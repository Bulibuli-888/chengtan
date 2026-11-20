package com.chengtan.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengtan.metrics.TicketMetrics;
import com.chengtan.order.entity.OrderOutbox;
import com.chengtan.order.mapper.OrderOutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 本地消息表 Relay：扫描待发送消息，投递 RabbitMQ。
 * Redis 锁保证多实例下同一条消息只有一个实例在发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class OrderOutboxRelay {

    /**
     * 最大重试次数。退避上限 60s，20 次大约覆盖 15 分钟，足够跨过一次 MQ 重启。
     * 超过之后进入 status=2（人工处理），由 POST /api/admin/outbox/replay 重放。
     */
    private static final int MAX_RETRY = 20;

    private final OrderOutboxMapper outboxMapper;
    private final OrderProducer producer;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final TicketMetrics metrics;

    @Scheduled(fixedDelay = 1000, initialDelay = 3000)
    public void relay() {
        List<OrderOutbox> list = outboxMapper.selectList(new LambdaQueryWrapper<OrderOutbox>()
                .eq(OrderOutbox::getStatus, 0)
                .le(OrderOutbox::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(OrderOutbox::getId)
                .last("LIMIT 50"));
        for (OrderOutbox outbox : list) {
            String lockKey = "lock:outbox:" + outbox.getId();
            Boolean locked = redis.opsForValue().setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(locked)) {
                continue;
            }
            try {
                OrderCreateMessage message = objectMapper.readValue(
                        outbox.getPayload(), OrderCreateMessage.class);
                if (producer.publishOrderCreate(message)) {
                    outbox.setStatus(1);
                    outbox.setUpdateTime(LocalDateTime.now());
                    outboxMapper.updateById(outbox);
                } else {
                    retry(outbox);
                }
            } catch (Exception e) {
                log.error("Outbox 投递异常, id={}, orderNo={}", outbox.getId(), outbox.getOrderNo(), e);
                retry(outbox);
            } finally {
                redis.delete(lockKey);
            }
        }
    }

    private void retry(OrderOutbox outbox) {
        int retryCount = outbox.getRetryCount() == null ? 0 : outbox.getRetryCount();
        retryCount++;
        boolean dead = retryCount >= MAX_RETRY;
        outbox.setRetryCount(retryCount);
        outbox.setStatus(dead ? 2 : 0);
        long delaySeconds = Math.min(60, 1L << Math.min(retryCount, 6));
        outbox.setNextRetryTime(LocalDateTime.now().plusSeconds(delaySeconds));
        outbox.setUpdateTime(LocalDateTime.now());
        outboxMapper.updateById(outbox);
        if (dead) {
            metrics.outboxDead();
            log.error("Outbox 重试耗尽，需人工重放, orderNo={}, retry={}",
                    outbox.getOrderNo(), retryCount);
        } else {
            log.warn("Outbox 重试 {}/{}, orderNo={}, next={}",
                    retryCount, MAX_RETRY, outbox.getOrderNo(), outbox.getNextRetryTime());
        }
    }
}
