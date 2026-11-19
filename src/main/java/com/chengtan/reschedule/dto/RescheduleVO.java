package com.chengtan.reschedule.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class RescheduleVO implements Serializable {

    private String orderNo;
    private Long fromSessionId;
    private Long toSessionId;
    private Integer ticketCount;
    private BigDecimal priceDiff;
    private Integer orderStatus;
    private String statusDesc;
}
