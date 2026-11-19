package com.chengtan.session.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengtan.common.BizException;
import com.chengtan.common.ErrorCode;
import com.chengtan.common.cache.CacheClient;
import com.chengtan.common.cache.CacheKeys;
import com.chengtan.session.dto.SessionVO;
import com.chengtan.session.entity.VenueSession;
import com.chengtan.session.mapper.VenueSessionMapper;
import com.chengtan.session.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final VenueSessionMapper venueSessionMapper;
    private final CacheClient cacheClient;

    @Override
    public SessionVO detail(Long id) {
        return cacheClient.getOrLoad(
                CacheKeys.SESSION_DETAIL + id,
                SessionVO.class,
                () -> {
                    VenueSession session = venueSessionMapper.selectById(id);
                    if (session == null) {
                        throw new BizException(ErrorCode.NOT_FOUND, "场次不存在");
                    }
                    return SessionVO.from(session);
                },
                Duration.ofSeconds(30),
                Duration.ofSeconds(5));
    }

    @Override
    public List<SessionVO> listByVenue(Long venueId) {
        List<VenueSession> sessions = venueSessionMapper.selectList(
                new LambdaQueryWrapper<VenueSession>()
                        .eq(VenueSession::getVenueId, venueId)
                        .in(VenueSession::getStatus, 0, 1, 2)
                        .ge(VenueSession::getSessionDate, LocalDate.now())
                        .orderByAsc(VenueSession::getSessionDate)
                        .orderByAsc(VenueSession::getStartTime));
        return sessions.stream().map(SessionVO::from).collect(Collectors.toList());
    }
}
