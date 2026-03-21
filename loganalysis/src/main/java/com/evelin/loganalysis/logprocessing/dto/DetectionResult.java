package com.evelin.loganalysis.logprocessing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 事件检测结果DTO
 *
 * @author Evelin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetectionResult {

    /**
     * 是否为异常事件
     */
    private boolean anomaly;

    /**
     * 异常评分 (0-1)
     */
    private Double anomalyScore;

    /**
     * 异常原因
     */
    private String anomalyReason;

    /**
     * 匹配的规则ID列表
     */
    private List<String> matchedRuleIds;

    /**
     * 匹配的规则名称列表
     */
    private List<String> matchedRuleNames;

    /**
     * 事件级别
     */
    private String eventLevel;

    /**
     * 事件分类
     */
    private String eventCategory;

    /**
     * 事件标签
     */
    private Map<String, String> tags;

    /**
     * 检测时间
     */
    private LocalDateTime detectedAt;

    /**
     * 添加匹配的规则
     */
    public void addMatchedRule(String ruleId, String ruleName) {
        if (matchedRuleIds == null) {
            matchedRuleIds = new java.util.ArrayList<>();
            matchedRuleNames = new java.util.ArrayList<>();
        }
        matchedRuleIds.add(ruleId);
        matchedRuleNames.add(ruleName);
    }
}
