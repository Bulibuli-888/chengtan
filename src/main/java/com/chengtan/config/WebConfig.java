package com.chengtan.config;

import com.chengtan.auth.AuthInterceptor;
import com.chengtan.auth.LoginInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 开发期跨域 + 登录拦截。公开接口只有场馆、场次、登录、健康检查。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final LoginInterceptor loginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .order(0);
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns(
                        "/api/orders/**",
                        "/api/attendees/**",
                        "/api/waitlist/**",
                        "/api/verify/**",
                        "/api/auth/logout",
                        "/api/auth/me")
                .order(1);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
