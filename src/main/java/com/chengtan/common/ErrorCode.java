package com.chengtan.common;

import lombok.Getter;

/**
 * 业务错误码。A 类为通用错误，B 类为票务领域错误，C 类为系统错误。
 */
@Getter
public enum ErrorCode {

    OK("0", "成功"),

    PARAM_ERROR("A0400", "参数错误"),
    UNAUTHORIZED("A0301", "请先登录"),
    FORBIDDEN("A0303", "没有权限"),
    NOT_FOUND("A0404", "资源不存在"),
    CONFLICT("A0409", "状态冲突"),
    TOO_MANY_REQUESTS("A0429", "操作过于频繁"),

    STOCK_NOT_ENOUGH("B1001", "余票不足"),
    LIMIT_EXCEEDED("B1002", "超过限购数量"),
    SESSION_NOT_ON_SALE("B1003", "场次未开售"),
    SESSION_ENDED("B1004", "场次已结束"),

    ORDER_NOT_FOUND("B2001", "订单不存在"),
    ORDER_STATUS_ERROR("B2002", "订单状态不允许该操作"),
    ORDER_EXPIRED("B2003", "订单已超时"),
    ORDER_AMOUNT_ERROR("B2004", "订单金额异常"),

    TICKET_NOT_FOUND("B3001", "票不存在"),
    TICKET_ALREADY_VERIFIED("B3002", "票已核销"),
    TICKET_NOT_REFUNDABLE("B3003", "该票不支持退款"),

    ATTENDEE_NOT_FOUND("B4001", "出行人不存在"),
    ID_CARD_DUPLICATED("B4002", "该证件在本场次已有有效票"),

    WAITLIST_DUPLICATED("B5001", "已在候补队列中"),
    WAITLIST_NOT_FOUND("B5002", "候补记录不存在"),
    WAITLIST_STATUS_ERROR("B5003", "候补状态不允许该操作"),

    RESCHEDULE_NOT_ALLOWED("B6001", "当前订单不支持改签"),
    RESCHEDULE_SAME_SESSION("B6002", "目标场次与原场次相同"),

    SYSTEM_ERROR("C0500", "系统繁忙，请稍后重试");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
