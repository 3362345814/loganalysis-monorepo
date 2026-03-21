package com.evelin.loganalysis.loganalysisai.analysis.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * AI 分析结果实体
 *
 * @author Evelin
 */
@Data
@Entity
@Table(name = "analysis_result")
public class AnalysisResultEntity {
    
    @Id
    @Column(name = "id", length = 50)
    private String id;
    
    @Column(name = "aggregation_id", length = 50, nullable = false)
    private String aggregationId;
    
    @Column(name = "aggregation_name", length = 200)
    private String aggregationName;
    
    /**
     * 根因分析
     */
    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;
    
    /**
     * 根因分类
     */
    @Column(name = "root_cause_category", length = 100)
    private String rootCauseCategory;
    
    /**
     * 分析详情
     */
    @Column(name = "analysis_detail", columnDefinition = "TEXT")
    private String analysisDetail;
    
    /**
     * 置信度
     */
    @Column(name = "confidence")
    private Double confidence;
    
    /**
     * 影响范围
     */
    @Column(name = "impact_scope", length = 500)
    private String impactScope;
    
    /**
     * 影响严重程度
     */
    @Column(name = "impact_severity", length = 20)
    private String impactSeverity;
    
    /**
     * 使用的模型
     */
    @Column(name = "model_name", length = 100)
    private String modelName;
    
    /**
     * 请求Token数
     */
    @Column(name = "request_tokens")
    private Integer requestTokens;
    
    /**
     * 响应Token数
     */
    @Column(name = "response_tokens")
    private Integer responseTokens;
    
    /**
     * 处理时间 (毫秒)
     */
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
    
    /**
     * 状态
     */
    @Column(name = "status", length = 20)
    private String status;
    
    /**
     * 状态消息
     */
    @Column(name = "status_message", length = 500)
    private String statusMessage;
    
    /**
     * 原始响应
     */
    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;
    
    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    /**
     * 完成时间
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
