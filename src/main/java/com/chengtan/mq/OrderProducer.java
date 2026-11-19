package com.chengtan.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 生产者：使用 Publisher Confirms 同步等待 broker 确认。
 * 发送失败不抛给用户，由本地消息表 Relay 重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderProducer {

    private static final long CONFIRM_TIMEOUT_MILLIS = 3000;

    private final RabbitTemplate rabbitTemplate;

    public boolean publishOrderCreate(OrderCreateMessage message) {
        return send(MqNames.ORDER_EXCHANGE, MqNames.ORDER_CREATE_ROUTING, message);
    }

    public boolean publishOrderTimeout(OrderTimeoutMessage message) {
        return send(MqNames.ORDER_EXCHANGE, MqNames.ORDER_DELAY_ROUTING, message);
    }

    private boolean send(String exchange, String routingKey, Object payload) {
        try {
            Boolean confirmed = rabbitTemplate.invoke(operations -> {
                operations.convertAndSend(exchange, routingKey, payload);
                return operations.waitForConfirms(CONFIRM_TIMEOUT_MILLIS);
            });
            return Boolean.TRUE.equals(confirmed);
        } catch (Exception e) {
            log.warn("RabbitMQ 发送失败, exchange={}, routingKey={}", exchange, routingKey, e);
            return false;
        }
    }
}
