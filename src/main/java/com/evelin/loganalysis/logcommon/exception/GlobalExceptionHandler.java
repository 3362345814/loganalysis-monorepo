package com.evelin.loganalysis.logcommon.exception;

import com.evelin.loganalysis.logcommon.constant.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 *
 * @author Evelin
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     *
     * @param e 业务异常
     * @return 统一返回结果
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> handleBusinessException(BusinessException e) {
        log.warn("Business error: code={}, message={}", e.getCode(), e.getMessage());
        return buildFailedResult(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常（@Valid）
     *
     * @param e 参数校验异常
     * @return 统一返回结果
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.warn("Validation error: {}", errors);
        Map<String, Object> result = buildFailedResult(ResultCode.PARAM_ERROR.getCode(), "参数校验失败");
        result.put("errors", errors);
        return result;
    }

    /**
     * 处理参数绑定异常
     *
     * @param e 参数绑定异常
     * @return 统一返回结果
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleBindException(BindException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.warn("Bind error: {}", errors);
        Map<String, Object> result = buildFailedResult(ResultCode.PARAM_ERROR.getCode(), "参数绑定失败");
        result.put("errors", errors);
        return result;
    }

    /**
     * 处理非法参数异常
     *
     * @param e 非法参数异常
     * @return 统一返回结果
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());
        return buildFailedResult(ResultCode.PARAM_ERROR.getCode(), e.getMessage());
    }

    /**
     * 处理其他未知异常
     *
     * @param e 异常
     * @return 统一返回结果
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleException(Exception e) {
        log.error("Unknown error: ", e);
        return buildFailedResult(ResultCode.INTERNAL_ERROR.getCode(), "系统繁忙，请稍后重试");
    }

    /**
     * 构建失败结果
     *
     * @param code    错误码
     * @param message 错误信息
     * @return 统一返回结果
     */
    private Map<String, Object> buildFailedResult(int code, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("code", code);
        result.put("message", message);
        return result;
    }
}
