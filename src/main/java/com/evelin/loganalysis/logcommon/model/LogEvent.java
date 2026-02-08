package com.evelin.loganalysis.logcommon.model;

import com.evelin.loganalysis.logcommon.enums.LogLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 日志事件实体
 *
 * @author Evelin
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "log_event", indexes = {
        @Index(name = "idx_log_event_source_id", columnList = "source_id"),
        @Index(name = "idx_log_event_log_time", columnList = "log_time"),
        @Index(name = "idx_log_event_level", columnList = "log_level"),
        @Index(name = "idx_log_event_created_at", columnList = "created_at")
})
public class LogEvent extends BaseEntity {

    /**
     * 日志源ID
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private LogSource sourceId;

    /**
     * 日志源名称
     */
    @Column(name = "source_name", length = 100)
    private String sourceName;

    /**
     * 日志源类型
     */
    @Column(name = "source_type", length = 50)
    private String sourceType;

    /**
     * 日志级别
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "log_level", nullable = false, length = 20)
    private LogLevel logLevel;

    /**
     * 日志记录器名称
     */
    @Column(name = "logger_name", length = 255)
    private String loggerName;

    /**
     * 线程名称
     */
    @Column(name = "thread_name", length = 255)
    private String threadName;

    /**
     * 日志消息
     */
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    /**
     * 原始内容
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_content", columnDefinition = "jsonb")
    private Map<String, Object> rawContent;

    /**
     * 解析后的字段
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_fields", columnDefinition = "jsonb")
    private Map<String, Object> parsedFields;

    /**
     * 前置上下文
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_before", columnDefinition = "jsonb")
    private Map<String, Object> contextBefore;

    /**
     * 后置上下文
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_after", columnDefinition = "jsonb")
    private Map<String, Object> contextAfter;

    /**
     * 堆栈信息
     */
    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    /**
     * 日志时间
     */
    @Column(name = "log_time", nullable = false)
    private LocalDateTime logTime;

    /**
     * 是否为异常
     */
    @Column(name = "is_anomaly", nullable = false)
    @Builder.Default
    private Boolean isAnomaly = false;

    /**
     * 标签
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    private Map<String, Object> tags;

    /**
     * 所属聚合组ID
     */
    @Column(name = "aggregation_id")
    private String aggregationId;
}
