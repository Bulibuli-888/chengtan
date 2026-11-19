package com.chengtan.session.job;

import com.chengtan.session.service.SessionCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时预热场次库存。Redis 被清空后也能从 MySQL 恢复。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionStockWarmUp implements ApplicationRunner {

    private final SessionCacheService sessionCacheService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            sessionCacheService.warmAll();
        } catch (Exception e) {
            log.warn("场次库存预热失败，不影响应用启动", e);
        }
    }
}
