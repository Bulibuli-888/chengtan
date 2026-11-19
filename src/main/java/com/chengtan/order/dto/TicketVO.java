package com.chengtan.order.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class TicketVO implements Serializable {

    private Long id;
    private String ticketNo;
    private Long attendeeId;
    private String attendeeName;
    private String idCardMask;
    private Integer status;
    private String statusDesc;
    private String qrToken;
    private LocalDateTime verifyTime;
}
