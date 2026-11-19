package com.chengtan.verify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class VerifyResultVO implements Serializable {

    private String ticketNo;
    private String orderNo;
    private LocalDateTime verifyTime;
    private boolean idempotent;
}
