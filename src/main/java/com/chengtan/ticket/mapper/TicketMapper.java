package com.chengtan.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengtan.ticket.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {

    @Select("SELECT user_id FROM ct_ticket WHERE session_id = #{sessionId} AND status IN (10, 20)")
    List<Long> selectActiveUserIds(@Param("sessionId") Long sessionId);

    @Select("SELECT attendee_id FROM ct_ticket WHERE session_id = #{sessionId} AND status IN (10, 20)")
    List<Long> selectActiveAttendeeIds(@Param("sessionId") Long sessionId);
}
