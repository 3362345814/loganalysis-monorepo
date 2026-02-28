package com.evelin.loganalysis.logprocessing.parser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 解析结果
 *
 * @author Evelin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParseResult {

    /**
     * 解析是否成功
     */
    private boolean success;

    /**
     * 日志时间
     */
    private LocalDateTime timestamp;

    /**
     * 日志级别
     */
    private String level;

    /**
     * Logger名称
     */
    private String logger;

    /**
     * 线程名称
     */
    private String thread;

    /**
     * 类名
     */
    private String className;

    /**
     * 方法名
     */
    private String methodName;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 行号
     */
    private Integer lineNumber;

    /**
     * 日志消息
     */
    private String message;

    /**
     * 堆栈跟踪信息
     */
    private String stackTrace;

    /**
     * 解析后的字段
     */
    @Builder.Default
    private Map<String, Object> fields = new HashMap<>();

    /**
     * 异常类型
     */
    private String exceptionType;

    /**
     * 异常消息
     */
    private String exceptionMessage;

    /**
     * 错误消息
     */
    private String errorMessage;
}
