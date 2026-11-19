package com.chengtan.order.service;

/**
 * 消费端创建订单的结果，决定是否需要回补 Redis。
 */
public enum CreateHandleResult {
    SUCCESS,
    ALREADY_EXISTS,
    STOCK_FAIL
}
