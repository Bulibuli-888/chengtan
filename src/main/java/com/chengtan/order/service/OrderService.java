package com.chengtan.order.service;

import com.chengtan.common.PageResult;
import com.chengtan.mq.OrderCreateMessage;
import com.chengtan.order.dto.CreateOrderRequest;
import com.chengtan.order.dto.CreateOrderVO;
import com.chengtan.order.dto.OrderCardVO;
import com.chengtan.order.dto.OrderDetailVO;
import com.chengtan.order.dto.RefundRequest;
import com.chengtan.order.dto.RefundVO;
import com.chengtan.reschedule.dto.RescheduleRequest;
import com.chengtan.reschedule.dto.RescheduleVO;

public interface OrderService {

    CreateOrderVO createOrder(CreateOrderRequest request);

    OrderDetailVO detail(String orderNo);

    PageResult<OrderCardVO> myOrders(long page, long size);

    void pay(String orderNo);

    void cancelByUser(String orderNo);

    RefundVO refund(String orderNo, RefundRequest request);

    RescheduleVO reschedule(String orderNo, RescheduleRequest request);

    CreateHandleResult handleCreateMessage(OrderCreateMessage message);

    void cancelExpired(String orderNo);

    void compensateRedis(OrderCreateMessage message);
}
