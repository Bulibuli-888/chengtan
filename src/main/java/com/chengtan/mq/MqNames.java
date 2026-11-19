package com.chengtan.mq;

/**
 * RabbitMQ 拓扑常量。集中管理，避免生产者和消费者写错字符串。
 */
public final class MqNames {

    private MqNames() {
    }

    public static final String ORDER_EXCHANGE = "ticket.order.exchange";
    public static final String DLX_EXCHANGE = "ticket.dlx.exchange";

    public static final String ORDER_CREATE_QUEUE = "ticket.order.create.queue";
    public static final String ORDER_CREATE_ROUTING = "order.create";

    public static final String ORDER_DELAY_QUEUE = "ticket.order.delay.queue";
    public static final String ORDER_DELAY_ROUTING = "order.delay";

    public static final String ORDER_TIMEOUT_QUEUE = "ticket.order.timeout.queue";
    public static final String ORDER_TIMEOUT_ROUTING = "order.timeout";

    public static final String ORDER_DLQ = "ticket.order.dlq";
    public static final String ORDER_DLQ_ROUTING = "order.dead";

    public static final String WAITLIST_EXCHANGE = "ticket.waitlist.exchange";
    public static final String WAITLIST_NOTIFY_QUEUE = "ticket.waitlist.notify.queue";
    public static final String WAITLIST_NOTIFY_ROUTING = "waitlist.notify";
}
