package com.chengtan.reschedule.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RescheduleRequest {

    @NotNull(message = "目标场次不能为空")
    private Long targetSessionId;
}
