package com.chengtan.waitlist.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class WaitlistClaimRequest {

    @NotEmpty(message = "请选择出行人")
    private List<Long> attendeeIds;
}
