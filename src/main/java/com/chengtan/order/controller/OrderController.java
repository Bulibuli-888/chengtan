package com.chengtan.order.controller;

import com.chengtan.common.PageResult;
import com.chengtan.common.Result;
import com.chengtan.order.dto.CreateOrderRequest;
import com.chengtan.order.dto.CreateOrderVO;
import com.chengtan.order.dto.OrderCardVO;
import com.chengtan.order.dto.OrderDetailVO;
import com.chengtan.order.dto.RefundRequest;
import com.chengtan.order.dto.RefundVO;
import com.chengtan.order.service.OrderService;
import com.chengtan.reschedule.dto.RescheduleRequest;
import com.chengtan.reschedule.dto.RescheduleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "订单")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "抢票下单（异步创建，返回订单号后轮询详情）")
    @PostMapping
    public Result<CreateOrderVO> create(@Valid @RequestBody CreateOrderRequest request) {
        return Result.ok(orderService.createOrder(request));
    }

    @Operation(summary = "订单详情（含每张票的核销码）")
    @GetMapping("/{orderNo}")
    public Result<OrderDetailVO> detail(@PathVariable("orderNo") String orderNo) {
        return Result.ok(orderService.detail(orderNo));
    }

    @Operation(summary = "我的订单")
    @GetMapping
    public Result<PageResult<OrderCardVO>> myOrders(
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "size", defaultValue = "10") long size) {
        return Result.ok(orderService.myOrders(page, size));
    }

    @Operation(summary = "模拟支付")
    @PostMapping("/{orderNo}/pay")
    public Result<Void> pay(@PathVariable("orderNo") String orderNo) {
        orderService.pay(orderNo);
        return Result.ok();
    }

    @Operation(summary = "按票退款（未核销的票可退，支持部分退款）")
    @PostMapping("/{orderNo}/refund")
    public Result<RefundVO> refund(@PathVariable("orderNo") String orderNo,
                                   @Valid @RequestBody RefundRequest request) {
        return Result.ok(orderService.refund(orderNo, request));
    }

    @Operation(summary = "改签到同场馆其他场次")
    @PostMapping("/{orderNo}/reschedule")
    public Result<RescheduleVO> reschedule(@PathVariable("orderNo") String orderNo,
                                           @Valid @RequestBody RescheduleRequest request) {
        return Result.ok(orderService.reschedule(orderNo, request));
    }

    @Operation(summary = "取消未支付订单")
    @PostMapping("/{orderNo}/cancel")
    public Result<Void> cancel(@PathVariable("orderNo") String orderNo) {
        orderService.cancelByUser(orderNo);
        return Result.ok();
    }
}
