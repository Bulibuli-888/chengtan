package com.chengtan.controller;

import com.chengtan.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "系统")
@RestController
@RequestMapping("/api")
public class HealthController {

    @Value("${spring.application.name}")
    private String appName;

    @Value("${spring.profiles.active:dev}")
    private String profile;

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app", appName);
        data.put("version", "2.0.0");
        data.put("profile", profile);
        data.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return Result.ok(data);
    }
}
