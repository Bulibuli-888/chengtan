package com.chengtan.venue.service;

import com.chengtan.common.PageResult;
import com.chengtan.venue.dto.VenueCardVO;
import com.chengtan.venue.dto.VenueDetailVO;
import com.chengtan.venue.entity.VenueType;

import java.util.List;

public interface VenueService {

    List<VenueType> listTypes();

    VenueDetailVO detail(Long id);

    PageResult<VenueCardVO> pageByType(Integer typeId, long page, long size);

    List<VenueCardVO> nearby(double x, double y, double radiusKm, Integer typeId);
}
