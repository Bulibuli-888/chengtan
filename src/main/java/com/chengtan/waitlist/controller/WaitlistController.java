package com.chengtan.waitlist.controller;

import com.chengtan.common.Result;
import com.chengtan.order.dto.CreateOrderVO;
import com.chengtan.waitlist.dto.WaitlistClaimRequest;
import com.chengtan.waitlist.dto.WaitlistJoinRequest;
import com.chengtan.waitlist.dto.WaitlistVO;
import com.chengtan.waitlist.service.WaitlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "候补")
@RestController
@RequestMapping("/api/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    @Operation(summary = "加入候补（仅剩票不足时可用）")
    @PostMapping
    public Result<WaitlistVO> join(@Valid @RequestBody WaitlistJoinRequest request) {
        return Result.ok(waitlistService.join(request));
    }

    @Operation(summary = "我的候补列表（含队列位置）")
    @GetMapping("/my")
    public Result<List<WaitlistVO>> my() {
        return Result.ok(waitlistService.my());
    }

    @Operation(summary = "取消候补（已补位的会释放预留库存并顺延下一位）")
    @DeleteMapping("/{id}")
    public Result<Void> cancel(@PathVariable("id") Long id) {
        waitlistService.cancel(id);
        return Result.ok();
    }

    @Operation(summary = "补位后转订单（10 分钟内有效）")
    @PostMapping("/{id}/claim")
    public Result<CreateOrderVO> claim(@PathVariable("id") Long id,
                                       @Valid @RequestBody WaitlistClaimRequest request) {
        return Result.ok(waitlistService.claim(id, request));
    }
}
