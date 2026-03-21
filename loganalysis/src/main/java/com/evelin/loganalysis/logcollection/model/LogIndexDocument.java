package com.evelin.loganalysis.logcollection.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Elasticsearch 日志索引文档
 *
 * 用于存储日志的 Elasticsearch 文档结构
 *
 * @author Evelin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LogIndexDocument {

    /**
     * 文档ID (映射 eventId)
     */
    private String id;

    /**
     * 数据库主键ID
     */
    private String dbId;

    /**
     * 日志源ID
     */
    private UUID sourceId;

    /**
     * 日志源名称
     */
    private String sourceName;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 原始日志内容 (text 类型, 支持全文搜索)
     */
    private String rawContent;

    /**
     * 脱敏后的内容
     */
    private String desensitizedContent;

    /**
     * 日志行号
     */
    private Long lineNumber;

    /**
     * 采集时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime collectionTime;

    /**
     * 日志原始生成时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime originalLogTime;

    /**
     * 日志级别 (keyword 类型, 用于精确过滤和聚合)
     */
    private String logLevel;

    /**
     * 日志类型标识
     */
    private String logType;

    /**
     * TraceId (用于链路追踪)
     */
    private String traceId;

    /**
     * 关联的聚合组ID
     */
    private String aggregationGroupId;

    /**
     * 解析后的字段 (JSON)
     */
    private Map<String, Object> parsedFields;

    /**
     * 从 RawLogEventEntity 转换
     */
    public static LogIndexDocument fromEntity(
            com.evelin.loganalysis.logcollection.model.entity.RawLogEventEntity entity) {
        return LogIndexDocument.builder()
                .id(entity.getEventId())
                .dbId(entity.getId() != null ? entity.getId().toString() : null)
                .sourceId(entity.getSourceId())
                .sourceName(entity.getSourceName())
                .filePath(entity.getFilePath())
                .rawContent(entity.getRawContent())
                .desensitizedContent(entity.getDesensitizedContent())
                .lineNumber(entity.getLineNumber())
                .collectionTime(entity.getCollectionTime())
                .originalLogTime(entity.getOriginalLogTime())
                .logLevel(entity.getLogLevel())
                .logType(entity.getLogType())
                .traceId(entity.getTraceId())
                .aggregationGroupId(entity.getAggregationGroupId())
                .parsedFields(entity.getParsedFields())
                .build();
    }
}
