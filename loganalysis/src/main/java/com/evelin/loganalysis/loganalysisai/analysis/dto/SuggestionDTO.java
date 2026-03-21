package com.evelin.loganalysis.loganalysisai.analysis.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 修复建议 DTO
 *
 * @author Evelin
 */
@Data
public class SuggestionDTO {
    
    /**
     * 建议ID
     */
    private String id;
    
    /**
     * 分析结果ID
     */
    private String resultId;
    
    /**
     * 聚合组ID
     */
    private String aggregationId;
    
    /**
     * 建议标题
     */
    private String title;
    
    /**
     * 建议内容
     */
    private String content;
    
    /**
     * 建议类型
     */
    private String suggestionType;
    
    /**
     * 优先级
     */
    private String priority;
    
    /**
     * 操作类型
     */
    private String actionType;
    
    /**
     * 预估工作量
     */
    private String estimatedEffort;
    
    /**
     * 是否可自动应用
     */
    private Boolean isAutoApplicable;
    
    /**
     * 状态
     */
    private String status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
