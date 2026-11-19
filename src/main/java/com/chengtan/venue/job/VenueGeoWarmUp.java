package com.chengtan.venue.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengtan.common.cache.CacheKeys;
import com.chengtan.venue.entity.Venue;
import com.chengtan.venue.mapper.VenueMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 启动时把场馆坐标写入 Redis GEO。场馆数量少，直接全量重建，简单可靠。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VenueGeoWarmUp implements ApplicationRunner {

    private final VenueMapper venueMapper;
    private final StringRedisTemplate redis;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Venue> venues = venueMapper.selectList(
                    new LambdaQueryWrapper<Venue>().eq(Venue::getStatus, 1));
            Set<String> keys = new HashSet<>();
            keys.add(CacheKeys.VENUE_GEO + "all");
            venues.forEach(v -> keys.add(CacheKeys.VENUE_GEO + v.getTypeId()));
            redis.delete(keys);

            for (Venue venue : venues) {
                if (venue.getX() == null || venue.getY() == null) {
                    continue;
                }
                Point point = new Point(venue.getX(), venue.getY());
                String member = venue.getId().toString();
                redis.opsForGeo().add(CacheKeys.VENUE_GEO + "all", point, member);
                redis.opsForGeo().add(CacheKeys.VENUE_GEO + venue.getTypeId(), point, member);
            }
            log.info("场馆 GEO 预热完成, venues={}", venues.size());
        } catch (Exception e) {
            log.warn("场馆 GEO 预热失败，不影响应用启动", e);
        }
    }
}
