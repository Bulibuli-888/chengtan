package com.chengtan.venue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ct_venue")
public class Venue implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    private Integer typeId;

    private String images;

    private String address;

    private Double x;

    private Double y;

    private BigDecimal rating;

    private Integer status;

    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
