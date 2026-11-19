package com.chengtan.session.service;

import com.chengtan.session.dto.SessionVO;

import java.util.List;

public interface SessionService {

    SessionVO detail(Long id);

    List<SessionVO> listByVenue(Long venueId);
}
