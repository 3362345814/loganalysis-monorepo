package com.evelin.loganalysis.logcommon.model;

import com.evelin.loganalysis.logcommon.enums.AlertLevel;
import com.evelin.loganalysis.logcommon.enums.AggregationStatus;
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

import java.time.LocalDateTime;

/**
 * 日志聚合实体
 *
 * @author Evelin
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "log_aggregation", indexes = {
        @Index(name = "idx_log_aggregation_group_id", columnList = "group_id", unique = true),
        @Index(name = "idx_log_aggregation_source_id", columnList = "source_id"),
        @Index(name = "idx_log_aggregation_status", columnList = "status"),
        @Index(name = "idx_log_aggregation_first_time", columnList = "first_time"),
        @Index(name = "idx_log_aggregation_last_time", columnList = "last_time")
})
public class LogAggregation extends BaseEntity {

    /**
     * 聚合组ID（业务ID）
     */
    @Column(name = "group_id", nullable = false, unique = true, length = 50)
    private String groupId;

    /**
     * 日志源ID
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private LogSource sourceId;

    /**
     * 代表性日志
     */
    @Column(name = "representative_log", columnDefinition = "TEXT")
    private String representativeLog;

    /**
     * 摘要
     */
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    /**
     * 事件数量
     */
    @Column(name = "event_count", nullable = false)
    @Builder.Default
    private Integer eventCount = 0;

    /**
     * 首次发生时间
     */
    @Column(name = "first_time", nullable = false)
    private LocalDateTime firstTime;

    /**
     * 最后发生时间
     */
    @Column(name = "last_time", nullable = false)
    private LocalDateTime lastTime;

    /**
     * 严重程度
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20)
    private AlertLevel severity;

    /**
     * 状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AggregationStatus status = AggregationStatus.ACTIVE;

    /**
     * 是否已分析
     */
    @Column(name = "is_analyzed", nullable = false)
    @Builder.Default
    private Boolean isAnalyzed = false;

    /**
     * 分析结果ID
     */
    @Column(name = "analysis_result_id")
    private String analysisResultId;

    /**
     * 备注
     */
    @Column(name = "remark", length = 500)
    private String remark;
}
