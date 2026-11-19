package com.chengtan.common;

import lombok.Getter;

/**
 * 可预期的业务异常。全局异常处理器会把它转换成 Result，不打印堆栈。
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
