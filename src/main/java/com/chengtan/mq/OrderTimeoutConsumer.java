package com.chengtan.mq;

import com.chengtan.order.service.OrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 超时取消消费者：状态机 + 条件更新保证幂等，重复消息直接 ACK。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutConsumer {

    private final OrderService orderService;

    @RabbitListener(queues = MqNames.ORDER_TIMEOUT_QUEUE)
    public void onMessage(OrderTimeoutMessage message,
                          Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws Exception {
        try {
            orderService.cancelExpired(message.getOrderNo());
            channel.basicAck(deliveryTag, false);
            log.info("超时订单处理完成, orderNo={}", message.getOrderNo());
        } catch (Exception e) {
            log.error("超时订单处理失败，交给重试/死信, orderNo={}", message.getOrderNo(), e);
            throw e;
        }
    }
}
