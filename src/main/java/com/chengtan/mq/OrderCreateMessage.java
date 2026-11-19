package com.chengtan.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 下单消息。messageId 用于消费端幂等，orderNo 用于业务唯一约束。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String messageId;
    private String orderNo;
    private Long orderId;
    private Long userId;
    private Long venueId;
    private Long sessionId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private List<Long> attendeeIds;
    private Long createdAt;
    private Long waitlistId;
    private Boolean reserved;
}
