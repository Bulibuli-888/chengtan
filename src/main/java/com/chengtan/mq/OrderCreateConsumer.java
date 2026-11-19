package com.chengtan.mq;

import com.chengtan.order.service.CreateHandleResult;
import com.chengtan.order.service.OrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 创建订单消费者：手动 ACK；库存不一致时回补 Redis；未知异常交给 Spring Retry + DLQ。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreateConsumer {

    private final OrderService orderService;

    @RabbitListener(queues = MqNames.ORDER_CREATE_QUEUE)
    public void onMessage(OrderCreateMessage message,
                          Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws Exception {
        try {
            CreateHandleResult result = orderService.handleCreateMessage(message);
            if (result == CreateHandleResult.STOCK_FAIL) {
                orderService.compensateRedis(message);
            }
            channel.basicAck(deliveryTag, false);
            log.info("订单消息处理完成, orderNo={}, result={}", message.getOrderNo(), result);
        } catch (DuplicateKeyException e) {
            log.warn("订单或票重复，回补 Redis, orderNo={}", message.getOrderNo(), e);
            orderService.compensateRedis(message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("创建订单失败，交给重试/死信, orderNo={}", message.getOrderNo(), e);
            throw e;
        }
    }
}
