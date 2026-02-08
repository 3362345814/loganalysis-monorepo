package com.evelin.loganalysis.logcommon.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 采集指标实体
 *
 * @author Evelin
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "collection_metric", indexes = {
        @Index(name = "idx_collection_metric_source_id", columnList = "source_id"),
        @Index(name = "idx_collection_metric_collected_at", columnList = "collected_at")
})
public class CollectionMetric extends BaseEntity {

    /**
     * 日志源ID
     */
    @Column(name = "source_id", nullable = false)
    private String sourceId;

    /**
     * 日志源名称
     */
    @Column(name = "source_name", length = 100)
    private String sourceName;

    /**
     * 采集时间
     */
    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    /**
     * 采集行数
     */
    @Column(name = "lines_collected", nullable = false)
    @Builder.Default
    private Long linesCollected = 0L;

    /**
     * 采集字节数
     */
    @Column(name = "bytes_collected", nullable = false)
    @Builder.Default
    private Long bytesCollected = 0L;

    /**
     * 采集耗时（毫秒）
     */
    @Column(name = "duration_ms")
    private Long durationMs;

    /**
     * 处理行数
     */
    @Column(name = "lines_processed", nullable = false)
    @Builder.Default
    private Long linesProcessed = 0L;

    /**
     * 错误数量
     */
    @Column(name = "error_count", nullable = false)
    @Builder.Default
    private Integer errorCount = 0;
}
