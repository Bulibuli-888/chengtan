package com.chengtan.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("ct_ticket")
public class Ticket implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String ticketNo;

    private String qrToken;

    private Long orderId;

    private String orderNo;

    private Long userId;

    private Long venueId;

    private Long sessionId;

    private Long attendeeId;

    /**
     * 10待使用 20已核销 30已退款 40已作废
     */
    private Integer status;

    private LocalDateTime verifyTime;

    private Long verifyBy;

    private LocalDateTime refundTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
