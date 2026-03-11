package com.evelin.loganalysis.loganalysisai.analysis;

import com.evelin.loganalysis.loganalysisai.analysis.dto.AnalysisResultDTO;
import com.evelin.loganalysis.loganalysisai.llm.LlmClient;
import com.evelin.loganalysis.loganalysisai.llm.LlmRequest;
import com.evelin.loganalysis.loganalysisai.llm.LlmResponse;
import com.evelin.loganalysis.loganalysisai.prompt.PromptTemplateManager;
import com.evelin.loganalysis.logcommon.utils.IdGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 根因分析服务
 *
 * @author Evelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RootCauseAnalyzer {
    
    private final PromptTemplateManager promptTemplateManager;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 分析日志异常，返回根因分析结果
     *
     * @param aggregationData 聚合组数据
     * @return 分析结果
     */
    public AnalysisResultDTO analyze(Map<String, Object> aggregationData) {
        long startTime = System.currentTimeMillis();
        
        // 1. 构建 Prompt
        Map<String, Object> params = buildAnalysisParams(aggregationData);
        String prompt = promptTemplateManager.buildRootCausePrompt(params);
        
        // 2. 调用 LLM
        LlmRequest request = new LlmRequest();
        request.setPrompt(prompt);
        request.setSystemPrompt(promptTemplateManager.getSystemPrompt());
        
        LlmResponse response = invokeLlm(request);
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        // 3. 解析结果
        return parseAnalysisResult(aggregationData, response, processingTime);
    }
    
    /**
     * 构建分析参数
     */
    private Map<String, Object> buildAnalysisParams(Map<String, Object> aggregationData) {
        Map<String, Object> params = new HashMap<>();
        
        // 聚合组基本信息
        params.put("group_id", aggregationData.getOrDefault("groupId", "N/A"));
        params.put("event_count", aggregationData.getOrDefault("eventCount", 0));
        params.put("severity", aggregationData.getOrDefault("severity", "UNKNOWN"));
        
        // 时间范围
        Object startTime = aggregationData.get("startTime");
        Object endTime = aggregationData.get("endTime");
        String timeRange = "N/A";
        if (startTime != null && endTime != null) {
            timeRange = startTime + " ~ " + endTime;
        }
        params.put("time_range", timeRange);
        
        // 代表日志
        params.put("representative_log", aggregationData.getOrDefault("representativeLog", ""));
        
        // 相关日志
        List<Map<String, Object>> relatedLogs = (List<Map<String, Object>>) aggregationData.get("relatedLogs");
        if (relatedLogs != null) {
            params.put("log_count", relatedLogs.size());
            params.put("related_logs", formatRelatedLogs(relatedLogs));
        } else {
            params.put("log_count", 0);
            params.put("related_logs", "");
        }
        
        // 上下文信息
        List<Map<String, Object>> contextBefore = (List<Map<String, Object>>) aggregationData.get("contextBefore");
        List<Map<String, Object>> contextAfter = (List<Map<String, Object>>) aggregationData.get("contextAfter");
        
        params.put("before_count", contextBefore != null ? contextBefore.size() : 0);
        params.put("context_before", formatContextLogs(contextBefore));
        params.put("after_count", contextAfter != null ? contextAfter.size() : 0);
        params.put("context_after", formatContextLogs(contextAfter));
        
        // 堆栈信息
        params.put("stack_trace", aggregationData.getOrDefault("stackTrace", "无"));
        
        return params;
    }
    
    /**
     * 格式化相关日志
     */
    private String formatRelatedLogs(List<Map<String, Object>> logs) {
        if (logs == null || logs.isEmpty()) {
            return "无";
        }
        
        StringBuilder sb = new StringBuilder();
        int count = Math.min(logs.size(), 10); // 最多显示10条
        
        for (int i = 0; i < count; i++) {
            Map<String, Object> log = logs.get(i);
            sb.append(String.format("%d. [%s] %s - %s\n", 
                    i + 1,
                    log.getOrDefault("logTime", ""),
                    log.getOrDefault("logLevel", ""),
                    log.getOrDefault("message", "")));
        }
        
        if (logs.size() > count) {
            sb.append(String.format("\n... 还有 %d 条日志\n", logs.size() - count));
        }
        
        return sb.toString();
    }
    
    /**
     * 格式化上下文日志
     */
    private String formatContextLogs(List<Map<String, Object>> logs) {
        if (logs == null || logs.isEmpty()) {
            return "无";
        }
        
        return formatRelatedLogs(logs);
    }
    
    /**
     * 调用 LLM
     */
    private LlmResponse invokeLlm(LlmRequest request) {
        log.info("调用 LLM 进行根因分析");
        
        // 调用真正的 LLM 客户端
        LlmResponse response = llmClient.chat(request);
        
        if (!response.isSuccess()) {
            log.error("LLM 调用失败: {}, 错误信息: {}", response.getStatus(), response.getErrorMessage());
        } else {
            log.info("LLM 调用成功，模型: {}, 耗时: {}ms", response.getModel(), response.getResponseTimeMs());
        }
        
        return response;
    }
    
    /**
     * 解析分析结果
     */
    private AnalysisResultDTO parseAnalysisResult(Map<String, Object> aggregationData, 
                                                   LlmResponse response, 
                                                   long processingTime) {
        AnalysisResultDTO result = new AnalysisResultDTO();
        result.setId(IdGenerator.nextId());
        result.setAggregationId(String.valueOf(aggregationData.getOrDefault("groupId", "")));
        result.setAggregationName(String.valueOf(aggregationData.getOrDefault("name", "")));
        result.setProcessingTimeMs(processingTime);
        result.setCreatedAt(LocalDateTime.now());
        
        if (response.isSuccess()) {
            try {
                // 解析 JSON 响应
                String content = response.getContent();
                // 清理可能存在的 markdown 代码块标记
                content = content.replaceAll("```json", "").replaceAll("```", "").trim();
                
                JsonNode jsonNode = objectMapper.readTree(content);
                
                result.setRootCause(getTextValue(jsonNode, "root_cause"));
                result.setRootCauseCategory(getTextValue(jsonNode, "root_cause_category"));
                result.setAnalysisDetail(getTextValue(jsonNode, "analysis_detail"));
                result.setConfidence(getDoubleValue(jsonNode, "confidence"));
                result.setImpactScope(getTextValue(jsonNode, "impact_scope"));
                result.setImpactSeverity(getTextValue(jsonNode, "impact_severity"));
                
                result.setStatus("COMPLETED");
                result.setCompletedAt(LocalDateTime.now());
                
            } catch (Exception e) {
                log.error("解析 LLM 响应失败: {}", e.getMessage(), e);
                result.setStatus("PARSE_ERROR");
                result.setStatusMessage("解析响应失败: " + e.getMessage());
            }
        } else {
            result.setStatus("FAILED");
            result.setStatusMessage(response.getErrorMessage());
        }
        
        result.setModelName(response.getModel());
        result.setRequestTokens(response.getPromptTokens());
        result.setResponseTokens(response.getCompletionTokens());
        
        return result;
    }
    
    /**
     * 获取文本值
     */
    private String getTextValue(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        return fieldNode != null ? fieldNode.asText() : null;
    }
    
    /**
     * 获取 Double 值
     */
    private Double getDoubleValue(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        return fieldNode != null ? fieldNode.asDouble() : null;
    }
}
