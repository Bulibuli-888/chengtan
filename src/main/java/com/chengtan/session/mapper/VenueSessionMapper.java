package com.chengtan.session.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengtan.session.entity.VenueSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface VenueSessionMapper extends BaseMapper<VenueSession> {

    /**
     * 改签时按 id 顺序锁定两个场次，避免死锁。
     */
    @Select("SELECT * FROM ct_venue_session WHERE id = #{id} FOR UPDATE")
    VenueSession selectForUpdate(@Param("id") Long id);
}
