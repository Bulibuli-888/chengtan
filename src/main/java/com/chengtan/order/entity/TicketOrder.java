package com.chengtan.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ct_ticket_order")
public class TicketOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private String orderNo;

    private Long userId;

    private Long venueId;

    private Long sessionId;

    private Integer quantity;

    private BigDecimal unitPrice;

    private BigDecimal totalAmount;

    private BigDecimal refundAmount;

    /**
     * 10待支付 20已支付 30已完成 40已取消 50已退款 60已过期
     */
    private Integer status;

    private LocalDateTime expireTime;

    private LocalDateTime payTime;

    private LocalDateTime cancelTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
