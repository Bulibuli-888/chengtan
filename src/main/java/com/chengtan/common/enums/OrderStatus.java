package com.chengtan.common.enums;

import com.chengtan.common.BizException;
import com.chengtan.common.ErrorCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态机。
 * 10待支付 → 20已支付 → 30已完成
 * 10待支付 → 40已取消 / 60已过期
 * 20已支付 → 50已退款 / 60已过期
 */
@Getter
public enum OrderStatus {

    PENDING_PAY(10, "待支付"),
    PAID(20, "已支付"),
    COMPLETED(30, "已完成"),
    CANCELLED(40, "已取消"),
    REFUNDED(50, "已退款"),
    EXPIRED(60, "已过期");

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            PENDING_PAY, EnumSet.of(PAID, CANCELLED, EXPIRED),
            PAID, EnumSet.of(COMPLETED, REFUNDED, EXPIRED)
    );

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public boolean canTransitionTo(OrderStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public void assertTransitionTo(OrderStatus target) {
        if (!canTransitionTo(target)) {
            throw new BizException(ErrorCode.ORDER_STATUS_ERROR,
                    "订单不能从「" + this.desc + "」变更为「" + target.desc + "」");
        }
    }

    public static OrderStatus of(int code) {
        return Arrays.stream(values())
                .filter(s -> s.code == code)
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.ORDER_STATUS_ERROR, "未知订单状态: " + code));
    }
}
