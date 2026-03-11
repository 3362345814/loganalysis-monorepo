package com.evelin.loganalysis.loganalysisai.analysis.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * AI 分析结果 DTO
 *
 * @author Evelin
 */
@Data
public class AnalysisResultDTO {
    
    /**
     * 分析结果ID
     */
    private String id;
    
    /**
     * 聚合组ID
     */
    private String aggregationId;
    
    /**
     * 聚合组名称
     */
    private String aggregationName;
    
    /**
     * 根因分析
     */
    private String rootCause;
    
    /**
     * 根因分类
     */
    private String rootCauseCategory;
    
    /**
     * 分析详情
     */
    private String analysisDetail;
    
    /**
     * 置信度 (0-1)
     */
    private Double confidence;
    
    /**
     * 影响范围
     */
    private String impactScope;
    
    /**
     * 影响严重程度
     */
    private String impactSeverity;
    
    /**
     * 使用的模型
     */
    private String modelName;
    
    /**
     * 请求Token数
     */
    private Integer requestTokens;
    
    /**
     * 响应Token数
     */
    private Integer responseTokens;
    
    /**
     * 处理时间 (毫秒)
     */
    private Long processingTimeMs;
    
    /**
     * 状态
     */
    private String status;
    
    /**
     * 状态消息
     */
    private String statusMessage;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 完成时间
     */
    private LocalDateTime completedAt;
}
