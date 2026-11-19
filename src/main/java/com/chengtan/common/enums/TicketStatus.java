package com.chengtan.common.enums;

import com.chengtan.common.BizException;
import com.chengtan.common.ErrorCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 票状态机。一张票独立核销、独立退款。
 * 10待使用 → 20已核销 / 30已退款 / 40已作废
 */
@Getter
public enum TicketStatus {

    UNUSED(10, "待使用"),
    VERIFIED(20, "已核销"),
    REFUNDED(30, "已退款"),
    VOID(40, "已作废");

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = Map.of(
            UNUSED, EnumSet.of(VERIFIED, REFUNDED, VOID)
    );

    private final int code;
    private final String desc;

    TicketStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public boolean canTransitionTo(TicketStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public void assertTransitionTo(TicketStatus target) {
        if (!canTransitionTo(target)) {
            throw new BizException(ErrorCode.TICKET_NOT_REFUNDABLE,
                    "票不能从「" + this.desc + "」变更为「" + target.desc + "」");
        }
    }

    public static TicketStatus of(int code) {
        return Arrays.stream(values())
                .filter(s -> s.code == code)
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.TICKET_NOT_FOUND, "未知票状态: " + code));
    }
}
