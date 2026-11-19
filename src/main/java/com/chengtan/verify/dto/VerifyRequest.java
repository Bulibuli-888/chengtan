package com.chengtan.verify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyRequest {

    @NotBlank(message = "核销码不能为空")
    private String qrToken;

    /**
     * 客户端生成的请求 ID，用于核销幂等。重复提交同一个 requestId 只会核销一次。
     */
    @NotBlank(message = "requestId 不能为空")
    @Size(max = 32, message = "requestId 过长")
    private String requestId;
}
