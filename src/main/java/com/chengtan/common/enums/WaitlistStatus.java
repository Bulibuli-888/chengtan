package com.chengtan.common.enums;

import com.chengtan.common.BizException;
import com.chengtan.common.ErrorCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 候补状态机。
 * 0排队中 → 1已通知待支付 → 2已转订单
 * 0排队中 → 3已取消
 * 1已通知待支付 → 4已过期 / 3已取消
 */
@Getter
public enum WaitlistStatus {

    QUEUING(0, "排队中"),
    OFFERED(1, "已通知待支付"),
    CONVERTED(2, "已转订单"),
    CANCELLED(3, "已取消"),
    EXPIRED(4, "已过期");

    private static final Map<WaitlistStatus, Set<WaitlistStatus>> ALLOWED = Map.of(
            QUEUING, EnumSet.of(OFFERED, CANCELLED),
            OFFERED, EnumSet.of(CONVERTED, EXPIRED, CANCELLED)
    );

    private final int code;
    private final String desc;

    WaitlistStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public boolean canTransitionTo(WaitlistStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public void assertTransitionTo(WaitlistStatus target) {
        if (!canTransitionTo(target)) {
            throw new BizException(ErrorCode.WAITLIST_STATUS_ERROR,
                    "候补不能从「" + this.desc + "」变更为「" + target.desc + "」");
        }
    }

    public static WaitlistStatus of(int code) {
        return Arrays.stream(values())
                .filter(s -> s.code == code)
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.WAITLIST_NOT_FOUND, "未知候补状态: " + code));
    }
}
