package com.chengtan.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {

    @NotNull(message = "场次不能为空")
    private Long sessionId;

    @NotNull(message = "购票数量不能为空")
    @Min(value = 1, message = "至少购买 1 张")
    @Max(value = 3, message = "每单最多购买 3 张")
    private Integer quantity;

    @NotEmpty(message = "请选择出行人")
    private List<Long> attendeeIds;
}
