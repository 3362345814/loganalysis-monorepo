package com.evelin.loganalysis.logprocessing.service;

import com.evelin.loganalysis.loganalysisai.analysis.service.AnalysisService;
import com.evelin.loganalysis.logprocessing.dto.AggregationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动分析触发器
 * 当检测到重大错误时，自动触发 AI 分析
 *
 * @author Evelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoAnalysisTrigger {

    private final AnalysisService analysisService;

    /**
     * 已触发的分析（避免重复分析）
     */
    private final Set<String> analyzedGroups = ConcurrentHashMap.newKeySet();

    /**
     * 是否启用自动分析
     */
    private boolean enabled = true;

    /**
     * 自动触发的严重程度阈值（ERROR 及以上）
     */
    private String minSeverity = "ERROR";

    /**
     * 触发自动分析
     *
     * @param aggregationResult 聚合结果
     */
    public void triggerAutoAnalysis(AggregationResult aggregationResult) {
        if (!enabled) {
            return;
        }

        if (aggregationResult == null) {
            return;
        }

        String severity = aggregationResult.getSeverity();
        String groupId = aggregationResult.getGroupId();

        // 检查严重程度
        if (!shouldAnalyze(severity)) {
            return;
        }

        // 检查是否已分析
        if (analyzedGroups.contains(groupId)) {
            log.debug("聚合组 {} 已触发过分析，跳过", groupId);
            return;
        }

        // 标记为已分析
        analyzedGroups.add(groupId);

        // 异步触发分析
        asyncAnalyze(aggregationResult);
    }

    /**
     * 判断是否应该分析
     */
    private boolean shouldAnalyze(String severity) {
        if (severity == null) {
            return false;
        }

        int severityPriority = getSeverityPriority(severity);
        int minPriority = getSeverityPriority(minSeverity);

        return severityPriority >= minPriority;
    }

    /**
     * 获取严重程度优先级
     */
    private int getSeverityPriority(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> 3;
            case "ERROR" -> 2;
            case "WARNING" -> 1;
            default -> 0;
        };
    }

    /**
     * 异步执行分析
     */
    @Async
    public void asyncAnalyze(AggregationResult aggregationResult) {
        String groupId = aggregationResult.getGroupId();
        
        try {
            log.info("自动触发 AI 分析，聚合组: {}, 严重程度: {}", 
                    groupId, aggregationResult.getSeverity());

            // 构建分析数据
            Map<String, Object> analysisData = new HashMap<>();
            analysisData.put("groupId", groupId);
            analysisData.put("aggregationId", aggregationResult.getAggregationId());
            analysisData.put("representativeLog", aggregationResult.getRepresentativeLog());
            analysisData.put("eventCount", aggregationResult.getEventCount());
            analysisData.put("severity", aggregationResult.getSeverity());
            analysisData.put("aggregationType", aggregationResult.getAggregationType());
            analysisData.put("triggerType", "AUTO");

            // 调用分析服务
            analysisService.analyze(analysisData);

            log.info("自动分析触发成功，聚合组: {}", groupId);

        } catch (Exception e) {
            log.error("自动分析失败，聚合组: {}, 错误: {}", groupId, e.getMessage(), e);
            // 失败时移除标记，允许重试
            analyzedGroups.remove(groupId);
        }
    }

    /**
     * 设置是否启用自动分析
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 设置最小严重程度
     */
    public void setMinSeverity(String minSeverity) {
        this.minSeverity = minSeverity;
    }

    /**
     * 清理已分析的标记（定时任务）
     */
    public void cleanup() {
        // 保留最近 1000 个已分析的标记
        if (analyzedGroups.size() > 1000) {
            analyzedGroups.clear();
        }
    }
}
