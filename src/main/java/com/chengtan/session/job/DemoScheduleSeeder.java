package com.chengtan.session.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.chengtan.common.cache.CacheKeys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 演示数据滚动排期：保证「今天起 N 天」每天都有场次，演示时不会出现空列表。
 *
 * 真实业务里场次由运营排期，不该由系统自己生成；这个类只为演示环境存在，
 * 通过 app.demo.auto-schedule 控制（测试环境已关闭）。
 *
 * 幂等：依赖 ct_venue_session 的唯一键 (venue_id, session_date, start_time) + INSERT IGNORE，
 * 重复执行只会插入缺失的那些，不会产生重复场次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo.auto-schedule", havingValue = "true", matchIfMissing = true)
public class DemoScheduleSeeder implements ApplicationRunner {

    private static final String TEMPLATE =
            "INSERT IGNORE INTO ct_venue_session " +
            "(venue_id, session_date, start_time, end_time, capacity, sold, price, status, " +
            " sale_start_time, sale_end_time) " +
            "SELECT v.id, DATE_ADD(CURDATE(), INTERVAL d.n DAY), t.start_time, t.end_time, " +
            "       t.capacity, 0, t.price, 1, " +
            "       DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY) " +
            "FROM ct_venue v " +
            "CROSS JOIN (%s) d " +
            "CROSS JOIN (SELECT '10:00:00' AS start_time, '12:00:00' AS end_time, " +
            "                   120 AS capacity, 88.00 AS price " +
            "            UNION ALL SELECT '14:00:00', '16:00:00', 100, 68.00) t";

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;

    @Value("${app.demo.schedule-days:7}")
    private int days;

    @Override
    public void run(ApplicationArguments args) {
        seed("启动");
    }

    @Scheduled(cron = "${app.demo.schedule-cron:0 10 0 * * ?}")
    public void daily() {
        seed("每日");
    }

    private void seed(String trigger) {
        try {
            int inserted = jdbc.update(buildSql());
            if (inserted > 0) {
                evictVenueDetailCache();
                log.info("演示场次补齐完成（{}），新增 {} 场，覆盖今天起 {} 天", trigger, inserted, days);
            }
        } catch (Exception e) {
            log.warn("演示场次补齐失败（{}），不影响主流程", trigger, e);
        }
    }

    /**
     * 场馆详情缓存里可能还留着「没有场次」的旧结果，补完数据必须清掉，否则页面要等 TTL 到期才更新。
     * 演示环境场馆数量很少，这里直接用 keys 匹配即可。
     */
    private void evictVenueDetailCache() {
        try {
            Set<String> keys = redis.keys(CacheKeys.VENUE_DETAIL + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
                log.info("已清除场馆详情缓存 {} 个", keys.size());
            }
        } catch (Exception e) {
            log.warn("清除场馆详情缓存失败", e);
        }
    }

    private String buildSql() {
        // 派生表必须给列起别名，否则外层无法引用 d.n（MySQL 1248）
        String dayList = IntStream.rangeClosed(1, Math.max(1, days))
                .mapToObj(n -> n == 1 ? "SELECT 1 AS n" : "SELECT " + n)
                .collect(Collectors.joining(" UNION ALL "));
        return String.format(TEMPLATE, dayList);
    }
}
