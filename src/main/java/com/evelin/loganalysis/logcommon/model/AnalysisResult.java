package com.evelin.loganalysis.logcommon.model;

import com.evelin.loganalysis.logcommon.enums.AlertLevel;
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
 * AI分析结果实体
 *
 * @author Evelin
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "analysis_result", indexes = {
        @Index(name = "idx_analysis_result_aggregation_id", columnList = "aggregation_id"),
        @Index(name = "idx_analysis_result_created_at", columnList = "created_at")
})
public class AnalysisResult extends BaseEntity {

    /**
     * 聚合组ID
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aggregation_id", nullable = false)
    private LogAggregation aggregationId;

    /**
     * 根因分析结果
     */
    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;

    /**
     * 详细分析
     */
    @Column(name = "analysis_detail", columnDefinition = "TEXT")
    private String analysisDetail;

    /**
     * 修复建议
     */
    @Column(name = "suggestion", columnDefinition = "TEXT")
    private String suggestion;

    /**
     * 影响评估
     */
    @Column(name = "impact", columnDefinition = "TEXT")
    private String impact;

    /**
     * 严重程度
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20)
    private AlertLevel severity;

    /**
     * 置信度 (0-1)
     */
    @Column(name = "confidence")
    private Double confidence;

    /**
     * 分类
     */
    @Column(name = "category", length = 100)
    private String category;

    /**
     * 使用的模型名称
     */
    @Column(name = "model_name", length = 100)
    private String modelName;

    /**
     * 原始响应（JSON格式）
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "jsonb")
    private Map<String, Object> rawResponse;

    /**
     * 分析耗时（毫秒）
     */
    @Column(name = "analysis_duration")
    private Long analysisDuration;

    /**
     * 分析时间
     */
    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;
}
