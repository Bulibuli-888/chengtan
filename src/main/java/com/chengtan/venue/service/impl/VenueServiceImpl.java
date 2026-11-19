package com.chengtan.venue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chengtan.common.BizException;
import com.chengtan.common.ErrorCode;
import com.chengtan.common.PageResult;
import com.chengtan.common.cache.CacheClient;
import com.chengtan.common.cache.CacheKeys;
import com.chengtan.session.dto.SessionVO;
import com.chengtan.session.entity.VenueSession;
import com.chengtan.session.mapper.VenueSessionMapper;
import com.chengtan.venue.dto.VenueCardVO;
import com.chengtan.venue.dto.VenueDetailVO;
import com.chengtan.venue.entity.Venue;
import com.chengtan.venue.entity.VenueType;
import com.chengtan.venue.mapper.VenueMapper;
import com.chengtan.venue.mapper.VenueTypeMapper;
import com.chengtan.venue.service.VenueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VenueServiceImpl extends ServiceImpl<VenueMapper, Venue> implements VenueService {

    private final VenueTypeMapper venueTypeMapper;
    private final VenueSessionMapper venueSessionMapper;
    private final StringRedisTemplate redis;
    private final CacheClient cacheClient;

    @Override
    public List<VenueType> listTypes() {
        return cacheClient.getOrLoadList(
                CacheKeys.VENUE_TYPES,
                VenueType.class,
                () -> venueTypeMapper.selectList(
                        new LambdaQueryWrapper<VenueType>().orderByAsc(VenueType::getSort)),
                Duration.ofHours(2));
    }

    @Override
    public VenueDetailVO detail(Long id) {
        VenueDetailVO vo = cacheClient.getOrLoad(
                CacheKeys.VENUE_DETAIL + id,
                VenueDetailVO.class,
                () -> loadDetail(id),
                Duration.ofMinutes(30),
                Duration.ofMinutes(2));
        if (vo == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "场馆不存在或已下架");
        }
        return vo;
    }

    private VenueDetailVO loadDetail(Long id) {
        Venue venue = getById(id);
        if (venue == null || venue.getStatus() == null || venue.getStatus() != 1) {
            return null;
        }
        List<VenueSession> sessions = venueSessionMapper.selectList(
                new LambdaQueryWrapper<VenueSession>()
                        .eq(VenueSession::getVenueId, id)
                        .in(VenueSession::getStatus, 0, 1, 2)
                        .ge(VenueSession::getSessionDate, LocalDate.now())
                        .orderByAsc(VenueSession::getSessionDate)
                        .orderByAsc(VenueSession::getStartTime));
        VenueDetailVO vo = VenueDetailVO.from(venue);
        vo.setSessions(sessions.stream().map(SessionVO::from).collect(Collectors.toList()));
        return vo;
    }

    @Override
    public PageResult<VenueCardVO> pageByType(Integer typeId, long page, long size) {
        Page<Venue> p = new Page<>(page, size);
        LambdaQueryWrapper<Venue> query = new LambdaQueryWrapper<Venue>()
                .eq(Venue::getStatus, 1)
                .eq(typeId != null, Venue::getTypeId, typeId)
                .orderByDesc(Venue::getRating);
        page(p, query);
        List<VenueCardVO> records = p.getRecords().stream()
                .map(VenueCardVO::from)
                .collect(Collectors.toList());
        return new PageResult<>(records, p.getTotal(), p.getCurrent(), p.getSize());
    }

    @Override
    public List<VenueCardVO> nearby(double x, double y, double radiusKm, Integer typeId) {
        String geoKey = CacheKeys.VENUE_GEO + (typeId == null ? "all" : typeId);
        Circle circle = new Circle(new Point(x, y), new Distance(radiusKm, Metrics.KILOMETERS));
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redis.opsForGeo().radius(
                geoKey,
                circle,
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                        .includeDistance()
                        .sortAscending()
                        .limit(50));
        if (results == null || results.getContent().isEmpty()) {
            return List.of();
        }

        Map<Long, Double> distanceMap = new LinkedHashMap<>();
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results.getContent()) {
            distanceMap.put(Long.valueOf(result.getContent().getName()),
                    Math.round(result.getDistance().getValue() * 100.0) / 100.0);
        }
        if (distanceMap.isEmpty()) {
            return List.of();
        }

        Set<Long> ids = distanceMap.keySet();
        Map<Long, Venue> venueMap = listByIds(ids).stream()
                .collect(Collectors.toMap(Venue::getId, v -> v));

        List<VenueCardVO> list = new ArrayList<>(distanceMap.size());
        for (Map.Entry<Long, Double> entry : distanceMap.entrySet()) {
            Venue venue = venueMap.get(entry.getKey());
            if (venue == null || venue.getStatus() == null || venue.getStatus() != 1) {
                continue;
            }
            VenueCardVO vo = VenueCardVO.from(venue);
            vo.setDistance(entry.getValue());
            list.add(vo);
        }
        return list;
    }
}
