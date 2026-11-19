package com.chengtan.auth.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.ReUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengtan.auth.AuthInterceptor;
import com.chengtan.auth.UserContext;
import com.chengtan.auth.UserPrincipal;
import com.chengtan.auth.dto.LoginRequest;
import com.chengtan.auth.dto.LoginVO;
import com.chengtan.auth.service.AuthService;
import com.chengtan.common.BizException;
import com.chengtan.common.ErrorCode;
import com.chengtan.user.entity.User;
import com.chengtan.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String CODE_KEY = "login:code:";
    private static final String CODE_LIMIT_KEY = "login:code:limit:";
    private static final String CODE_FAIL_KEY = "login:code:fail:";
    private static final Duration CODE_TTL = Duration.ofMinutes(2);
    private static final int MAX_FAIL = 5;

    private final StringRedisTemplate redis;
    private final UserMapper userMapper;

    @Override
    public void sendCode(String phone) {
        if (!ReUtil.isMatch("^1[3-9]\\d{9}$", phone)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "手机号格式不正确");
        }
        Boolean first = redis.opsForValue()
                .setIfAbsent(CODE_LIMIT_KEY + phone, "1", 60, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(first)) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "验证码发送过于频繁，请 60 秒后再试");
        }
        String code = RandomUtil.randomNumbers(6);
        redis.opsForValue().set(CODE_KEY + phone, code, CODE_TTL);
        // 演示环境没有接短信通道，验证码直接打日志，前端可提示用户看控制台
        log.info("【城探票务】验证码 phone={}, code={}", phone, code);
    }

    @Override
    @Transactional
    public LoginVO login(LoginRequest request) {
        String phone = request.getPhone();
        String codeKey = CODE_KEY + phone;
        String cached = redis.opsForValue().get(codeKey);
        if (cached == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "验证码已过期，请重新获取");
        }
        if (!cached.equals(request.getCode())) {
            Long fails = redis.opsForValue().increment(CODE_FAIL_KEY + phone);
            redis.expire(CODE_FAIL_KEY + phone, CODE_TTL);
            if (fails != null && fails >= MAX_FAIL) {
                redis.delete(codeKey);
                throw new BizException(ErrorCode.PARAM_ERROR, "验证码错误次数过多，请重新获取");
            }
            throw new BizException(ErrorCode.PARAM_ERROR, "验证码不正确");
        }
        redis.delete(codeKey);
        redis.delete(CODE_FAIL_KEY + phone);

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user == null) {
            user = createUser(phone);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }

        String token = AuthInterceptor.newToken();
        Map<String, String> map = new HashMap<>(8);
        map.put("id", user.getId().toString());
        map.put("phone", user.getPhone());
        map.put("nickName", user.getNickName());
        map.put("role", String.valueOf(user.getRole() == null ? 0 : user.getRole()));
        String tokenKey = AuthInterceptor.TOKEN_KEY_PREFIX + token;
        redis.opsForHash().putAll(tokenKey, map);
        redis.expire(tokenKey, 120, TimeUnit.MINUTES);

        return new LoginVO(token, user.getId(), user.getNickName(),
                user.getRole() == null ? 0 : user.getRole());
    }

    private User createUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("游客" + RandomUtil.randomNumbers(4));
        user.setIcon("");
        user.setRole(0);
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    @Override
    public void logout(String token) {
        if (token != null) {
            redis.delete(AuthInterceptor.TOKEN_KEY_PREFIX + token);
        }
        UserContext.clear();
    }

    @Override
    public UserPrincipal current() {
        UserPrincipal principal = UserContext.get();
        if (principal == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }
}
