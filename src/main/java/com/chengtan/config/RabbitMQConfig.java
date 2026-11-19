package com.chengtan.config;

import com.chengtan.mq.MqNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * RabbitMQ 拓扑：
 * 下单消息 → 创建队列（失败进 DLQ）
 * 延迟队列（TTL 15 分钟）→ 死信 → 超时队列
 * 候补通知队列单独一个交换机，避免和订单链路互相影响
 */
@Configuration
@EnableRabbit
public class RabbitMQConfig {

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(MqNames.ORDER_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(MqNames.DLX_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange waitlistExchange() {
        return new DirectExchange(MqNames.WAITLIST_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderCreateQueue() {
        return QueueBuilder.durable(MqNames.ORDER_CREATE_QUEUE)
                .withArgument("x-dead-letter-exchange", MqNames.DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", MqNames.ORDER_DLQ_ROUTING)
                .build();
    }

    @Bean
    public Queue orderDelayQueue(@Value("${app.seckill.order-timeout-seconds:900}") long timeoutSeconds) {
        return QueueBuilder.durable(MqNames.ORDER_DELAY_QUEUE)
                .withArgument("x-message-ttl", timeoutSeconds * 1000)
                .withArgument("x-dead-letter-exchange", MqNames.ORDER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", MqNames.ORDER_TIMEOUT_ROUTING)
                .build();
    }

    @Bean
    public Queue orderTimeoutQueue() {
        return QueueBuilder.durable(MqNames.ORDER_TIMEOUT_QUEUE)
                .withArgument("x-dead-letter-exchange", MqNames.DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", MqNames.ORDER_DLQ_ROUTING)
                .build();
    }

    @Bean
    public Queue orderDlq() {
        return QueueBuilder.durable(MqNames.ORDER_DLQ).build();
    }

    @Bean
    public Queue waitlistNotifyQueue() {
        return QueueBuilder.durable(MqNames.WAITLIST_NOTIFY_QUEUE)
                .withArgument("x-dead-letter-exchange", MqNames.DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", MqNames.ORDER_DLQ_ROUTING)
                .build();
    }

    @Bean
    public Binding orderCreateBinding(Queue orderCreateQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(orderCreateQueue).to(orderExchange).with(MqNames.ORDER_CREATE_ROUTING);
    }

    @Bean
    public Binding orderDelayBinding(Queue orderDelayQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(orderDelayQueue).to(orderExchange).with(MqNames.ORDER_DELAY_ROUTING);
    }

    @Bean
    public Binding orderTimeoutBinding(Queue orderTimeoutQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(orderTimeoutQueue).to(orderExchange).with(MqNames.ORDER_TIMEOUT_ROUTING);
    }

    @Bean
    public Binding orderDlqBinding(Queue orderDlq, DirectExchange dlxExchange) {
        return BindingBuilder.bind(orderDlq).to(dlxExchange).with(MqNames.ORDER_DLQ_ROUTING);
    }

    @Bean
    public Binding waitlistNotifyBinding(Queue waitlistNotifyQueue, DirectExchange waitlistExchange) {
        return BindingBuilder.bind(waitlistNotifyQueue).to(waitlistExchange).with(MqNames.WAITLIST_NOTIFY_ROUTING);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
