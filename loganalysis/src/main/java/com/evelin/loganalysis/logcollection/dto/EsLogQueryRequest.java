package com.evelin.loganalysis.logcollection.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * ES 日志查询请求 DTO
 *
 * @author Evelin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EsLogQueryRequest {

    /**
     * 关键字搜索 (全文搜索 rawContent)
     */
    private String keyword;

    /**
     * 正则表达式匹配 rawContent
     */
    private String regex;

    /**
     * 日志源ID
     */
    private UUID sourceId;

    /**
     * 日志级别列表
     */
    private List<String> logLevels;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 文件路径 (模糊匹配)
     */
    private String filePath;

    /**
     * traceId (精确匹配)
     */
    private String traceId;

    /**
     * 聚合组ID
     */
    private String aggregationGroupId;

    /**
     * 聚合字段 (logLevel / sourceName / logType)
     */
    private String aggregationField;

    /**
     * 时间直方图间隔 (minute / hour / day)
     */
    private String timeInterval;

    /**
     * 是否需要高亮
     */
    @Builder.Default
    private boolean highlight = true;

    /**
     * 页码 (从 0 开始)
     */
    @Min(0)
    @Builder.Default
    private Integer page = 0;

    /**
     * 每页大小
     */
    @Min(1)
    @Max(1000)
    @Builder.Default
    private Integer size = 20;

    /**
     * 排序字段
     */
    @Builder.Default
    private String sortField = "originalLogTime";

    /**
     * 排序方向 (asc / desc)
     */
    @Builder.Default
    private String sortOrder = "desc";
}
