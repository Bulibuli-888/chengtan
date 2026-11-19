package com.chengtan.session.controller;

import com.chengtan.common.Result;
import com.chengtan.session.dto.SessionVO;
import com.chengtan.session.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "场次")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @Operation(summary = "场次详情")
    @GetMapping("/sessions/{id}")
    public Result<SessionVO> detail(@PathVariable("id") Long id) {
        return Result.ok(sessionService.detail(id));
    }

    @Operation(summary = "场馆下的可售场次")
    @GetMapping("/venues/{venueId}/sessions")
    public Result<List<SessionVO>> listByVenue(@PathVariable("venueId") Long venueId) {
        return Result.ok(sessionService.listByVenue(venueId));
    }
}
