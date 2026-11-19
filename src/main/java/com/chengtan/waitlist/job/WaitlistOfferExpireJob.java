package com.chengtan.waitlist.job;

import com.chengtan.waitlist.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 补位有效期 10 分钟，过期后释放预留库存并顺延下一位。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaitlistOfferExpireJob {

    private final WaitlistService waitlistService;

    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void scan() {
        try {
            waitlistService.expireOffers();
        } catch (Exception e) {
            log.error("候补补位过期扫描失败", e);
        }
    }
}
