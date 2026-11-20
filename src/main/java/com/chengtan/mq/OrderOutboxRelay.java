package com.chengtan.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    private final OrderOutboxMapper outboxMapper;
    private final OrderProducer producer;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;

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
        outbox.setRetryCount(retryCount);
        outbox.setStatus(retryCount >= 5 ? 2 : 0);
        long delaySeconds = Math.min(60, 1L << Math.min(retryCount, 6));
        outbox.setNextRetryTime(LocalDateTime.now().plusSeconds(delaySeconds));
        outbox.setUpdateTime(LocalDateTime.now());
        outboxMapper.updateById(outbox);
        log.warn("Outbox 重试 {}/5, orderNo={}, next={}",
                retryCount, outbox.getOrderNo(), outbox.getNextRetryTime());
    }
}
