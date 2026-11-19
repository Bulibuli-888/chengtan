package com.chengtan.venue.controller;

import com.chengtan.common.PageResult;
import com.chengtan.common.Result;
import com.chengtan.venue.dto.VenueCardVO;
import com.chengtan.venue.dto.VenueDetailVO;
import com.chengtan.venue.entity.VenueType;
import com.chengtan.venue.service.VenueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "场馆")
@RestController
@RequestMapping("/api/venues")
@RequiredArgsConstructor
public class VenueController {

    private final VenueService venueService;

    @Operation(summary = "场馆分类列表")
    @GetMapping("/types")
    public Result<List<VenueType>> types() {
        return Result.ok(venueService.listTypes());
    }

    @Operation(summary = "场馆详情（含近期场次）")
    @GetMapping("/{id}")
    public Result<VenueDetailVO> detail(@PathVariable("id") Long id) {
        return Result.ok(venueService.detail(id));
    }

    @Operation(summary = "按分类分页查询场馆")
    @GetMapping
    public Result<PageResult<VenueCardVO>> page(
            @RequestParam(value = "typeId", required = false) Integer typeId,
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "size", defaultValue = "10") long size) {
        return Result.ok(venueService.pageByType(typeId, page, size));
    }

    @Operation(summary = "附近场馆（按距离排序）")
    @GetMapping("/nearby")
    public Result<List<VenueCardVO>> nearby(
            @RequestParam("x") double x,
            @RequestParam("y") double y,
            @RequestParam(value = "radiusKm", defaultValue = "5") double radiusKm,
            @RequestParam(value = "typeId", required = false) Integer typeId) {
        return Result.ok(venueService.nearby(x, y, radiusKm, typeId));
    }
}
