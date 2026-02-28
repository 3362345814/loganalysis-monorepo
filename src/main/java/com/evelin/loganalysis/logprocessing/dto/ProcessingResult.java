package com.evelin.loganalysis.logprocessing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 处理管道结果DTO
 *
 * @author Evelin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingResult {

    /**
     * 原始日志ID
     */
    private String rawLogId;

    /**
     * 是否处理成功
     */
    private boolean success;

    /**
     * 处理阶段: PARSED/DETECTED/CONTEXT_AGGREGATED/DESENSITIZED/STORED
     */
    private String stage;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 处理耗时（毫秒）
     */
    private long processTimeMs;

    /**
     * 解析后的日志事件
     */
    private ParsedLogEvent parsedEvent;

    /**
     * 事件检测结果
     */
    private DetectionResult detectionResult;

    /**
     * 上下文信息
     */
    private ContextInfo contextInfo;

    /**
     * 聚合结果
     */
    private AggregationResult aggregationResult;

    /**
     * 处理时间戳
     */
    private long timestamp;
}
