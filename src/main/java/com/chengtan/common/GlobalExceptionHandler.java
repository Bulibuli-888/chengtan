package com.chengtan.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：业务异常返回可读文案，未知异常统一兜底并打印堆栈。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getErrorCode().getCode(), e.getMessage());
        return Result.fail(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValidException(Exception e) {
        FieldError fieldError = null;
        if (e instanceof MethodArgumentNotValidException ex) {
            fieldError = ex.getBindingResult().getFieldError();
        } else if (e instanceof BindException ex) {
            fieldError = ex.getBindingResult().getFieldError();
        }
        String message = fieldError == null
                ? ErrorCode.PARAM_ERROR.getMessage()
                : fieldError.getField() + " " + fieldError.getDefaultMessage();
        return Result.fail(ErrorCode.PARAM_ERROR, message);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public Result<Void> handleBadRequest(Exception e) {
        log.warn("请求参数错误: {}", e.getMessage());
        return Result.fail(ErrorCode.PARAM_ERROR);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResource(NoResourceFoundException e) {
        return Result.fail(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("未预期异常, uri={}", request.getRequestURI(), e);
        return Result.fail(ErrorCode.SYSTEM_ERROR);
    }
}
