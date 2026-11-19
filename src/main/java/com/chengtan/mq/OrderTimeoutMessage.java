package com.chengtan.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 支付超时消息。由延迟队列 TTL 到期后投递到超时队列。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTimeoutMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderNo;
    private Long sessionId;
    private Long userId;
    private Integer quantity;
    private List<Long> attendeeIds;
}
