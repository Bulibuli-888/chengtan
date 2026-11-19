package com.chengtan.attendee.dto;

import com.chengtan.attendee.entity.Attendee;
import lombok.Data;

import java.io.Serializable;

@Data
public class AttendeeVO implements Serializable {

    private Long id;
    private String name;
    private String idCardMask;
    private String phone;

    public static AttendeeVO from(Attendee attendee) {
        AttendeeVO vo = new AttendeeVO();
        vo.setId(attendee.getId());
        vo.setName(attendee.getName());
        vo.setIdCardMask(attendee.getIdCardMask());
        vo.setPhone(attendee.getPhone());
        return vo;
    }
}
