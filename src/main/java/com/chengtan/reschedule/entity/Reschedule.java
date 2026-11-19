package com.chengtan.reschedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ct_reschedule")
public class Reschedule implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String orderNo;

    private Long fromSessionId;

    private Long toSessionId;

    private Integer ticketCount;

    private BigDecimal priceDiff;

    /**
     * 0处理中 1成功 2失败
     */
    private Integer status;

    private String failReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
