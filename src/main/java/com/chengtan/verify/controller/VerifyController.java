package com.chengtan.verify.controller;

import com.chengtan.common.Result;
import com.chengtan.verify.dto.VerifyRequest;
import com.chengtan.verify.dto.VerifyResultVO;
import com.chengtan.verify.service.VerifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "核销")
@RestController
@RequestMapping("/api/verify")
@RequiredArgsConstructor
public class VerifyController {

    private final VerifyService verifyService;

    @Operation(summary = "扫码核销（仅场馆运营角色，requestId 保证幂等）")
    @PostMapping
    public Result<VerifyResultVO> verify(@Valid @RequestBody VerifyRequest request) {
        return Result.ok(verifyService.verify(request));
    }
}
