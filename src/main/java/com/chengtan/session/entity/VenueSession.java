package com.chengtan.session.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 场次是票务的核心：库存按场次隔离，而不是按场馆共享。
 */
@Data
@TableName("ct_venue_session")
public class VenueSession implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long venueId;

    private LocalDate sessionDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer capacity;

    private Integer sold;

    private BigDecimal price;

    /**
     * 0待开售 1售票中 2售罄 3已结束 4已取消
     */
    private Integer status;

    private LocalDateTime saleStartTime;

    private LocalDateTime saleEndTime;

    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
