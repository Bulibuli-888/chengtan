package com.chengtan.auth.controller;

import com.chengtan.auth.AuthInterceptor;
import com.chengtan.auth.UserPrincipal;
import com.chengtan.auth.dto.LoginRequest;
import com.chengtan.auth.dto.LoginVO;
import com.chengtan.auth.service.AuthService;
import com.chengtan.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "登录")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "发送手机验证码（演示环境验证码打印在服务端日志）")
    @PostMapping("/code")
    public Result<Void> sendCode(@RequestParam("phone") String phone) {
        authService.sendCode(phone);
        return Result.ok();
    }

    @Operation(summary = "手机验证码登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        authService.logout(AuthInterceptor.resolveToken(request));
        return Result.ok();
    }

    @Operation(summary = "当前登录用户")
    @GetMapping("/me")
    public Result<UserPrincipal> me() {
        return Result.ok(authService.current());
    }
}
