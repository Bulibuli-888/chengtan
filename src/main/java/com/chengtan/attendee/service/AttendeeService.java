package com.chengtan.attendee.service;

import com.chengtan.attendee.dto.AttendeeCreateRequest;
import com.chengtan.attendee.dto.AttendeeVO;
import com.chengtan.attendee.entity.Attendee;

import java.util.Collection;
import java.util.List;

public interface AttendeeService {

    AttendeeVO create(AttendeeCreateRequest request);

    List<AttendeeVO> listMine();

    List<Attendee> getOwned(Long userId, Collection<Long> ids);
}
