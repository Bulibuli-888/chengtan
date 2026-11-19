package com.chengtan.waitlist.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WaitlistJoinRequest {

    @NotNull(message = "场次不能为空")
    private Long sessionId;

    @NotNull(message = "候补数量不能为空")
    @Min(value = 1, message = "至少候补 1 张")
    @Max(value = 3, message = "每单最多候补 3 张")
    private Integer quantity;
}
