package com.chengtan.waitlist.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class WaitlistVO implements Serializable {

    private Long id;
    private Long sessionId;
    private Long venueId;
    private String venueName;
    private LocalDate sessionDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal price;
    private Integer quantity;
    private Integer status;
    private String statusDesc;
    private Long position;
    private LocalDateTime notifyTime;
    private LocalDateTime offerExpireTime;
    private LocalDateTime createTime;
}
