package com.chengtan.attendee.controller;

import com.chengtan.attendee.dto.AttendeeCreateRequest;
import com.chengtan.attendee.dto.AttendeeVO;
import com.chengtan.attendee.service.AttendeeService;
import com.chengtan.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "出行人")
@RestController
@RequestMapping("/api/attendees")
@RequiredArgsConstructor
public class AttendeeController {

    private final AttendeeService attendeeService;

    @Operation(summary = "新增出行人（证件号只存哈希和掩码）")
    @PostMapping
    public Result<AttendeeVO> create(@Valid @RequestBody AttendeeCreateRequest request) {
        return Result.ok(attendeeService.create(request));
    }

    @Operation(summary = "我的出行人列表")
    @GetMapping
    public Result<List<AttendeeVO>> listMine() {
        return Result.ok(attendeeService.listMine());
    }
}
