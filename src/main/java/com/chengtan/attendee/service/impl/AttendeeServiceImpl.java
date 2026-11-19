package com.chengtan.attendee.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengtan.attendee.dto.AttendeeCreateRequest;
import com.chengtan.attendee.dto.AttendeeVO;
import com.chengtan.attendee.entity.Attendee;
import com.chengtan.attendee.mapper.AttendeeMapper;
import com.chengtan.attendee.service.AttendeeService;
import com.chengtan.auth.UserContext;
import com.chengtan.common.BizException;
import com.chengtan.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendeeServiceImpl implements AttendeeService {

    private final AttendeeMapper attendeeMapper;

    @Value("${app.security.id-card-salt:chengtan-demo-salt}")
    private String idCardSalt;

    @Override
    public AttendeeVO create(AttendeeCreateRequest request) {
        Long userId = UserContext.requireUserId();
        String idCard = request.getIdCard().toUpperCase();

        Attendee attendee = new Attendee();
        attendee.setUserId(userId);
        attendee.setName(request.getName());
        attendee.setIdCardMask(mask(idCard));
        attendee.setIdCardHash(hash(userId, idCard));
        attendee.setPhone(request.getPhone() == null ? "" : request.getPhone());
        attendee.setCreateTime(LocalDateTime.now());
        try {
            attendeeMapper.insert(attendee);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.CONFLICT, "该证件已添加过出行人");
        }
        return AttendeeVO.from(attendee);
    }

    @Override
    public List<AttendeeVO> listMine() {
        Long userId = UserContext.requireUserId();
        return attendeeMapper.selectList(
                        new LambdaQueryWrapper<Attendee>()
                                .eq(Attendee::getUserId, userId)
                                .orderByDesc(Attendee::getId))
                .stream()
                .map(AttendeeVO::from)
                .collect(Collectors.toList());
    }

    @Override
    public List<Attendee> getOwned(Long userId, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择出行人");
        }
        List<Attendee> list = attendeeMapper.selectList(
                new LambdaQueryWrapper<Attendee>()
                        .eq(Attendee::getUserId, userId)
                        .in(Attendee::getId, ids));
        Set<Long> found = list.stream().map(Attendee::getId).collect(Collectors.toSet());
        if (found.size() != ids.size()) {
            throw new BizException(ErrorCode.ATTENDEE_NOT_FOUND);
        }
        return list;
    }

    private String hash(Long userId, String idCard) {
        return DigestUtil.sha256Hex(userId + ":" + idCard + ":" + idCardSalt);
    }

    private String mask(String idCard) {
        return idCard.substring(0, 4) + "**********" + idCard.substring(14);
    }
}
