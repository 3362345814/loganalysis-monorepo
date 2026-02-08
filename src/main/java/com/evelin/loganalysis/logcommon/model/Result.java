package com.evelin.loganalysis.logcommon.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一返回结果类
 *
 * @author Evelin
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Result<T> {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 错误码
     */
    private int code;

    /**
     * 消息
     */
    private String message;

    /**
     * 数据
     */
    private T data;

    /**
     * 时间戳
     */
    private long timestamp;

    /**
     * 成功返回
     *
     * @param <T> 数据类型
     * @return 统一返回结果
     */
    public static <T> Result<T> success() {
        return Result.<T>builder()
                .success(true)
                .code(0)
                .message("成功")
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 成功返回（带数据）
     *
     * @param data 数据
     * @param <T>  数据类型
     * @return 统一返回结果
     */
    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
                .success(true)
                .code(0)
                .message("成功")
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 成功返回（带消息）
     *
     * @param message 消息
     * @param <T>     数据类型
     * @return 统一返回结果
     */
    public static <T> Result<T> success(String message) {
        return Result.<T>builder()
                .success(true)
                .code(0)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 成功返回（带数据和消息）
     *
     * @param data    数据
     * @param message 消息
     * @param <T>     数据类型
     * @return 统一返回结果
     */
    public static <T> Result<T> success(T data, String message) {
        return Result.<T>builder()
                .success(true)
                .code(0)
                .message(message)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 失败返回
     *
     * @param code    错误码
     * @param message 错误消息
     * @param <T>     数据类型
     * @return 统一返回结果
     */
    public static <T> Result<T> failed(int code, String message) {
        return Result.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 失败返回（带数据）
     *
     * @param code    错误码
     * @param message 错误消息
     * @param data    数据
     * @param <T>     数据类型
     * @return 统一返回结果
     */
    public static <T> Result<T> failed(int code, String message, T data) {
        return Result.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
