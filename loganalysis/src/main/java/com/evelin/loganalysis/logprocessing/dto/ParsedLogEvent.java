package com.evelin.loganalysis.logprocessing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 解析结果DTO
 *
 * @author Evelin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedLogEvent {

    /**
     * 日志ID
     */
    private String id;

    /**
     * 日志源ID
     */
    private String sourceId;

    /**
     * 日志源名称
     */
    private String sourceName;

    /**
     * 日志时间
     */
    private LocalDateTime logTime;

    /**
     * 日志级别
     */
    private String logLevel;

    /**
     * Logger名称
     */
    private String loggerName;

    /**
     * 线程名称
     */
    private String threadName;

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
     * 原始日志内容
     */
    private String rawContent;

    /**
     * 解析后的字段
     */
    private Map<String, Object> parsedFields;

    /**
     * 堆栈跟踪信息
     */
    private String stackTrace;

    /**
     * 异常类型
     */
    private String exceptionType;

    /**
     * 异常消息
     */
    private String exceptionMessage;

    /**
     * 是否为异常事件
     */
    private boolean isAnomaly;

    /**
     * 异常评分 (0-1)
     */
    private Double anomalyScore;

    /**
     * 异常原因
     */
    private String anomalyReason;

    /**
     * 日志分类
     */
    private String category;

    /**
     * 标签
     */
    private Map<String, String> tags;

    /**
     * 链路追踪ID
     */
    private String traceId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
