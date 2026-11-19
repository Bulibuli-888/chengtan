package com.chengtan.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class RefundVO implements Serializable {

    private BigDecimal refundAmount;
    private Integer orderStatus;
    private String statusDesc;
}
