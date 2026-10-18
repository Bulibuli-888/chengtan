package com.hmdp.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;


import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 */
@Api(tags = "秒杀订单模块")
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    @ApiOperation("秒杀优惠券下单")

    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    @PostMapping("pay/{orderId}")
    @ApiOperation("模拟支付订单")

    public Result pay(@PathVariable("orderId") Long orderId) {
        return voucherOrderService.pay(orderId);
    }
}
