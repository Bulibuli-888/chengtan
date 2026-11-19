package com.chengtan.common.enums;

import com.chengtan.common.BizException;
import com.chengtan.common.ErrorCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 场次状态机。
 * 0待开售 → 1售票中 → 2售罄
 * 1/2 → 3已结束 / 4已取消
 * 2售罄 → 1售票中（有人退票时）
 */
@Getter
public enum SessionStatus {

    PENDING(0, "待开售"),
    ON_SALE(1, "售票中"),
    SOLD_OUT(2, "售罄"),
    CLOSED(3, "已结束"),
    CANCELLED(4, "已取消");

    private static final Map<SessionStatus, Set<SessionStatus>> ALLOWED = Map.of(
            PENDING, EnumSet.of(ON_SALE, CANCELLED),
            ON_SALE, EnumSet.of(SOLD_OUT, CLOSED, CANCELLED),
            SOLD_OUT, EnumSet.of(ON_SALE, CLOSED, CANCELLED)
    );

    private final int code;
    private final String desc;

    SessionStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public boolean canTransitionTo(SessionStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public void assertTransitionTo(SessionStatus target) {
        if (!canTransitionTo(target)) {
            throw new BizException(ErrorCode.SESSION_NOT_ON_SALE,
                    "场次不能从「" + this.desc + "」变更为「" + target.desc + "」");
        }
    }

    public static SessionStatus of(int code) {
        return Arrays.stream(values())
                .filter(s -> s.code == code)
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.SESSION_NOT_ON_SALE, "未知场次状态: " + code));
    }
}
