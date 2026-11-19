package com.chengtan.order.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class OrderCardVO implements Serializable {

    private String orderNo;
    private String venueName;
    private LocalDate sessionDate;
    private LocalTime startTime;
    private Integer quantity;
    private BigDecimal totalAmount;
    private Integer status;
    private String statusDesc;
    private LocalDateTime createTime;
}
