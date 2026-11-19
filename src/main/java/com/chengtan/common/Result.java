package com.chengtan.common;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

/**
 * 统一返回结构。code 为业务码，HTTP 状态码始终为 200，方便前端统一处理。
 */
@Data
public class Result<T> implements Serializable {

    private boolean success;
    private String code;
    private String message;
    private T data;
    private long timestamp;

    public Result() {
        this.timestamp = Instant.now().toEpochMilli();
    }

    public Result(boolean success, String code, String message, T data) {
        this();
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok() {
        return new Result<>(true, ErrorCode.OK.getCode(), ErrorCode.OK.getMessage(), null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(true, ErrorCode.OK.getCode(), ErrorCode.OK.getMessage(), data);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(false, errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(false, errorCode.getCode(), message, null);
    }

    public static <T> Result<T> fail(String code, String message) {
        return new Result<>(false, code, message, null);
    }
}
