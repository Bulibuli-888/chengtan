package com.chengtan.auth;

import com.chengtan.common.BizException;
import com.chengtan.common.ErrorCode;

/**
 * 当前请求的登录用户。由拦截器写入，请求结束时清理，避免线程池复用导致串号。
 */
public final class UserContext {

    private static final ThreadLocal<UserPrincipal> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(UserPrincipal principal) {
        HOLDER.set(principal);
    }

    public static UserPrincipal get() {
        return HOLDER.get();
    }

    public static Long requireUserId() {
        UserPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return principal.getId();
    }

    public static UserPrincipal requireOperator() {
        UserPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (principal.getRole() == null || principal.getRole() != 1) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅场馆运营人员可操作");
        }
        return principal;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
