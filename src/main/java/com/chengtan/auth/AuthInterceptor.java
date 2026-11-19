package com.chengtan.auth;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.lang.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 解析 token → 用户上下文；每次请求顺带续期，实现滑动会话。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String TOKEN_KEY_PREFIX = "login:token:";
    private static final long TOKEN_TTL_MINUTES = 120;

    private final StringRedisTemplate redis;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = resolveToken(request);
        if (StrUtil.isBlank(token)) {
            return true;
        }
        String key = TOKEN_KEY_PREFIX + token;
        Map<Object, Object> map = redis.opsForHash().entries(key);
        if (map.isEmpty()) {
            return true;
        }
        UserPrincipal principal = new UserPrincipal(
                Long.valueOf(String.valueOf(map.get("id"))),
                String.valueOf(map.get("phone")),
                String.valueOf(map.get("nickName")),
                Integer.valueOf(String.valueOf(map.get("role"))));
        UserContext.set(principal);
        redis.expire(key, TOKEN_TTL_MINUTES, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    public static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StrUtil.isBlank(header)) {
            header = request.getHeader("authorization");
        }
        if (StrUtil.isBlank(header)) {
            return null;
        }
        return header.startsWith("Bearer ") ? header.substring(7) : header;
    }

    public static String newToken() {
        return UUID.randomUUID().toString(true);
    }
}
