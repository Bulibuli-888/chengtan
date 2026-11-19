package com.chengtan.order.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
public class OrderDetailVO implements Serializable {

    private String orderNo;
    private Integer status;
    private String statusDesc;
    private Long venueId;
    private String venueName;
    private Long sessionId;
    private LocalDate sessionDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private BigDecimal refundAmount;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private List<TicketVO> tickets;
}
