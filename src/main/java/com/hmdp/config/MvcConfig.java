package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import com.hmdp.utils.SystemConstants;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录拦截器
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login",
                        "/",
                        // 静态资源
                        "/**/*.html",
                        "/**/*.css",
                        "/**/*.js",
                        "/**/*.png",
                        "/**/*.jpg",
                        "/**/*.jpeg",
                        "/**/*.gif",
                        "/**/*.ico",
                        "/**/*.woff",
                        "/**/*.ttf",
                        // swagger
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/swagger-resources/**",
                        "/webjars/**"
                ).order(1);
        // token刷新的拦截器
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 上传图片映射到项目运行目录下的 imgs
        registry.addResourceHandler("/imgs/**")
                .addResourceLocations("file:" + SystemConstants.IMAGE_UPLOAD_DIR);
    }
}
