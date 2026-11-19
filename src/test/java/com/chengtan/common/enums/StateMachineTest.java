package com.chengtan.common.enums;

import com.chengtan.common.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 状态机是订单/票/候补三条主线的地基，这里把合法与非法流转都钉住。
 */
class StateMachineTest {

    @Test
    void orderStatus_shouldAllowOnlyLegalTransitions() {
        assertThat(OrderStatus.PENDING_PAY.canTransitionTo(OrderStatus.PAID)).isTrue();
        assertThat(OrderStatus.PENDING_PAY.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.PENDING_PAY.canTransitionTo(OrderStatus.EXPIRED)).isTrue();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.COMPLETED)).isTrue();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.REFUNDED)).isTrue();

        assertThat(OrderStatus.COMPLETED.canTransitionTo(OrderStatus.PAID)).isFalse();
        assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.PAID)).isFalse();
        assertThat(OrderStatus.REFUNDED.canTransitionTo(OrderStatus.COMPLETED)).isFalse();
    }

    @Test
    void orderStatus_shouldRejectIllegalTransitionWithBizException() {
        assertThatThrownBy(() -> OrderStatus.COMPLETED.assertTransitionTo(OrderStatus.PAID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已完成");
    }

    @Test
    void ticketStatus_shouldAllowVerifyRefundVoidOnlyFromUnused() {
        assertThat(TicketStatus.UNUSED.canTransitionTo(TicketStatus.VERIFIED)).isTrue();
        assertThat(TicketStatus.UNUSED.canTransitionTo(TicketStatus.REFUNDED)).isTrue();
        assertThat(TicketStatus.UNUSED.canTransitionTo(TicketStatus.VOID)).isTrue();

        assertThat(TicketStatus.VERIFIED.canTransitionTo(TicketStatus.REFUNDED)).isFalse();
        assertThat(TicketStatus.REFUNDED.canTransitionTo(TicketStatus.VERIFIED)).isFalse();
    }

    @Test
    void waitlistStatus_shouldFollowOfferLifecycle() {
        assertThat(WaitlistStatus.QUEUING.canTransitionTo(WaitlistStatus.OFFERED)).isTrue();
        assertThat(WaitlistStatus.OFFERED.canTransitionTo(WaitlistStatus.CONVERTED)).isTrue();
        assertThat(WaitlistStatus.OFFERED.canTransitionTo(WaitlistStatus.EXPIRED)).isTrue();

        assertThat(WaitlistStatus.CONVERTED.canTransitionTo(WaitlistStatus.OFFERED)).isFalse();
        assertThat(WaitlistStatus.EXPIRED.canTransitionTo(WaitlistStatus.CONVERTED)).isFalse();
    }

    @Test
    void sessionStatus_shouldReopenWhenStockReturns() {
        assertThat(SessionStatus.ON_SALE.canTransitionTo(SessionStatus.SOLD_OUT)).isTrue();
        assertThat(SessionStatus.SOLD_OUT.canTransitionTo(SessionStatus.ON_SALE)).isTrue();
        assertThat(SessionStatus.CLOSED.canTransitionTo(SessionStatus.ON_SALE)).isFalse();
    }

    @Test
    void of_shouldResolveByCode() {
        assertThat(OrderStatus.of(10)).isEqualTo(OrderStatus.PENDING_PAY);
        assertThat(TicketStatus.of(20)).isEqualTo(TicketStatus.VERIFIED);
        assertThat(WaitlistStatus.of(1)).isEqualTo(WaitlistStatus.OFFERED);
        assertThat(SessionStatus.of(3)).isEqualTo(SessionStatus.CLOSED);
    }
}
