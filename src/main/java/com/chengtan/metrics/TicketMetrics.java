package com.chengtan.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 业务指标。命名遵循 Prometheus 约定：<域>_<对象>_total。
 * 只记结果计数，不做高基数标签（不把 userId / sessionId 放进 tag）。
 */
@Component
@RequiredArgsConstructor
public class TicketMetrics {

    public static final String SECKILL = "chengtan_seckill_total";
    /**
     * 注意：名字里不要用 _created，Prometheus 把它当保留后缀，Micrometer 会裁掉，
     * 最终会变成 chengtan_order_total 这种看不懂的名字。
     */
    public static final String ORDER_SUCCESS = "chengtan_order_success_total";
    public static final String VERIFY = "chengtan_ticket_verify_total";
    public static final String OUTBOX_DEAD = "chengtan_outbox_dead_total";

    private final MeterRegistry registry;

    /**
     * @param result success / sold_out / limit / not_on_sale / ended / not_init / id_dup / error
     */
    public void seckill(String result) {
        Counter.builder(SECKILL)
                .description("抢票请求结果计数")
                .tag("result", result)
                .register(registry)
                .increment();
    }

    public void orderCreated() {
        Counter.builder(ORDER_SUCCESS)
                .description("消费端成功创建的订单数")
                .register(registry)
                .increment();
    }

    /**
     * @param result ok / idempotent
     */
    public void verify(String result) {
        Counter.builder(VERIFY)
                .description("核销结果计数")
                .tag("result", result)
                .register(registry)
                .increment();
    }

    /**
     * Outbox 重试耗尽、进入人工处理，应该配告警。
     */
    public void outboxDead() {
        Counter.builder(OUTBOX_DEAD)
                .description("本地消息表重试耗尽条数")
                .register(registry)
                .increment();
    }
}
