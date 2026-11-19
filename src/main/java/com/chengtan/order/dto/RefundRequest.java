package com.chengtan.order.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class RefundRequest {

    @NotEmpty(message = "请选择要退款的票")
    private List<Long> ticketIds;
}
