package com.chengtan.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    /**
     * 客户端生成的请求 ID，用于下单幂等。
     * 同一个 requestId 重复提交只会创建一笔订单；不传则退化为无幂等保护。
     */
    @Size(max = 64, message = "requestId 过长")
    private String requestId;
}
