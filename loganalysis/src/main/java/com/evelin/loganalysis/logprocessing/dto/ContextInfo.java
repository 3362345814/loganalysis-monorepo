package com.evelin.loganalysis.logprocessing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 上下文信息DTO
 *
 * @author Evelin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextInfo {

    /**
     * 异常事件ID
     */
    private String eventId;

    /**
     * 异常事件
     */
    private ParsedLogEvent event;

    /**
     * 异常前的日志列表
     */
    private List<ParsedLogEvent> beforeLogs;

    /**
     * 异常后的日志列表
     */
    private List<ParsedLogEvent> afterLogs;

    /**
     * 堆栈跟踪信息
     */
    private String stackTrace;

    /**
     * 解析后的堆栈信息
     */
    private Map<String, Object> parsedStackTrace;

    /**
     * 链路追踪ID
     */
    private String traceId;

    /**
     * 相关日志总数
     */
    private int relatedLogCount;

    /**
     * 提取时间
     */
    private LocalDateTime extractedAt;
}
