package com.chengtan.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chengtan.auth.UserContext;
import com.chengtan.common.Result;
import com.chengtan.order.entity.OrderOutbox;
import com.chengtan.order.mapper.OrderOutboxMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 本地消息表运维接口。
 *
 * status=2 表示重试耗尽、需要人工确认后才重放。
 * 故障演练时验证过：MQ 停机超过重试窗口后，消息会停在这里，不会自动恢复。
 */
@Slf4j
@Tag(name = "运维-本地消息表")
@RestController
@RequestMapping("/api/admin/outbox")
@RequiredArgsConstructor
public class OutboxAdminController {

    private final OrderOutboxMapper outboxMapper;

    @Operation(summary = "待处理/已发送消息条数")
    @GetMapping("/stats")
    public Result<OutboxStats> stats() {
        UserContext.requireOperator();
        long pending = count(0);
        long dead = count(2);
        return Result.ok(new OutboxStats(pending, dead));
    }

    @Operation(summary = "重放重试耗尽的消息")
    @PostMapping("/replay")
    public Result<Integer> replay() {
        UserContext.requireOperator();
        LocalDateTime now = LocalDateTime.now();
        int replayed = outboxMapper.update(null, new LambdaUpdateWrapper<OrderOutbox>()
                .eq(OrderOutbox::getStatus, 2)
                .set(OrderOutbox::getStatus, 0)
                .set(OrderOutbox::getRetryCount, 0)
                .set(OrderOutbox::getNextRetryTime, now)
                .set(OrderOutbox::getUpdateTime, now));
        log.warn("手动重放 Outbox, count={}, operator={}", replayed,
                UserContext.get() == null ? null : UserContext.get().getId());
        return Result.ok(replayed);
    }

    private long count(int status) {
        Long n = outboxMapper.selectCount(new LambdaQueryWrapper<OrderOutbox>()
                .eq(OrderOutbox::getStatus, status));
        return n == null ? 0L : n;
    }

    public record OutboxStats(long pending, long dead) {
    }
}
