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
    private static final int MAX_PARSE_RETRIES = 2;
    
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
        request.setResponseFormatType("json_schema");
        request.setResponseFormatSchemaName("root_cause_analysis_response");
        request.setResponseFormatSchema(buildRootCauseResponseSchema());
        
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
        List<Map<String, Object>> traceChainLogs = (List<Map<String, Object>>) aggregationData.get("traceChainLogs");

        params.put("before_count", contextBefore != null ? contextBefore.size() : 0);
        params.put("context_before", formatContextLogs(contextBefore));
        params.put("after_count", contextAfter != null ? contextAfter.size() : 0);
        params.put("context_after", formatContextLogs(contextAfter));

        params.put("trace_id", aggregationData.getOrDefault("traceId", "无"));
        if (traceChainLogs != null) {
            params.put("trace_log_count", traceChainLogs.size());
            params.put("trace_chain_logs", formatRelatedLogs(traceChainLogs));
        } else {
            params.put("trace_log_count", 0);
            params.put("trace_chain_logs", "无");
        }
        
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
                JsonNode jsonNode = parseAndRepairJson(response, aggregationData);
                validateAnalysisJson(jsonNode);
                
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

    private JsonNode parseAndRepairJson(LlmResponse response, Map<String, Object> aggregationData) throws Exception {
        Exception lastError = null;
        String content = response.getContent();

        for (int attempt = 0; attempt <= MAX_PARSE_RETRIES; attempt++) {
            try {
                String cleaned = cleanupJsonContent(content);
                JsonNode jsonNode = objectMapper.readTree(cleaned);
                if (jsonNode == null || !jsonNode.isObject()) {
                    throw new IllegalArgumentException("响应不是合法 JSON 对象");
                }
                return jsonNode;
            } catch (Exception ex) {
                lastError = ex;
                if (attempt >= MAX_PARSE_RETRIES) {
                    break;
                }
                log.warn("第 {} 次解析 AI JSON 失败，尝试自动修复重试: {}", attempt + 1, ex.getMessage());
                LlmResponse repaired = requestJsonRepair(content, aggregationData, attempt + 1);
                if (repaired == null || !repaired.isSuccess() || repaired.getContent() == null || repaired.getContent().isBlank()) {
                    throw new IllegalStateException("JSON 修复请求失败: " + (repaired != null ? repaired.getErrorMessage() : "empty response"));
                }
                content = repaired.getContent();
            }
        }
        throw new IllegalStateException("LLM 返回内容无法解析为合法 JSON: " + (lastError != null ? lastError.getMessage() : "unknown"));
    }

    private LlmResponse requestJsonRepair(String invalidContent, Map<String, Object> aggregationData, int round) {
        LlmRequest repairRequest = new LlmRequest();
        repairRequest.setSystemPrompt("你是一个严格的 JSON 修复器。你只能输出合法 JSON，不允许输出任何解释或 markdown。");
        repairRequest.setPrompt("""
                请将下面内容修复为合法 JSON，并严格符合指定字段要求。
                只返回 JSON 对象本身，不要返回其他任何内容。

                字段要求：
                - root_cause: string, 非空
                - root_cause_category: string, 只能是 DATABASE/NETWORK/MEMORY/CODE/CONFIG/UNKNOWN
                - confidence: number, 范围 [0,1]
                - analysis_detail: string, 非空
                - impact_scope: string, 非空
                - impact_severity: string, 只能是 HIGH/MEDIUM/LOW

                原始内容：
                %s
                """.formatted(invalidContent != null ? invalidContent : ""));
        repairRequest.setResponseFormatType("json_schema");
        repairRequest.setResponseFormatSchemaName("root_cause_analysis_response_repair_" + round);
        repairRequest.setResponseFormatSchema(buildRootCauseResponseSchema());
        return invokeLlm(repairRequest);
    }

    private String cleanupJsonContent(String content) {
        if (content == null) {
            return "";
        }
        return content.replaceAll("```json", "").replaceAll("```", "").trim();
    }

    private void validateAnalysisJson(JsonNode jsonNode) {
        ensureTextField(jsonNode, "root_cause");
        String category = ensureTextField(jsonNode, "root_cause_category").toUpperCase(Locale.ROOT);
        if (!Set.of("DATABASE", "NETWORK", "MEMORY", "CODE", "CONFIG", "UNKNOWN").contains(category)) {
            throw new IllegalArgumentException("root_cause_category 不在允许枚举内: " + category);
        }
        JsonNode confidenceNode = jsonNode.get("confidence");
        if (confidenceNode == null || !confidenceNode.isNumber()) {
            throw new IllegalArgumentException("confidence 缺失或不是数字");
        }
        double confidence = confidenceNode.asDouble();
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence 超出范围[0,1]: " + confidence);
        }
        ensureTextField(jsonNode, "analysis_detail");
        ensureTextField(jsonNode, "impact_scope");
        String impactSeverity = ensureTextField(jsonNode, "impact_severity").toUpperCase(Locale.ROOT);
        if (!Set.of("HIGH", "MEDIUM", "LOW").contains(impactSeverity)) {
            throw new IllegalArgumentException("impact_severity 不在允许枚举内: " + impactSeverity);
        }
    }

    private String ensureTextField(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode == null || !fieldNode.isTextual()) {
            throw new IllegalArgumentException(fieldName + " 缺失或不是字符串");
        }
        String text = fieldNode.asText();
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 为空");
        }
        return text.trim();
    }

    private Map<String, Object> buildRootCauseResponseSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("root_cause", Map.of("type", "string"));
        properties.put("root_cause_category", Map.of(
                "type", "string",
                "enum", List.of("DATABASE", "NETWORK", "MEMORY", "CODE", "CONFIG", "UNKNOWN")
        ));
        properties.put("confidence", Map.of(
                "type", "number",
                "minimum", 0,
                "maximum", 1
        ));
        properties.put("analysis_detail", Map.of("type", "string"));
        properties.put("impact_scope", Map.of("type", "string"));
        properties.put("impact_severity", Map.of(
                "type", "string",
                "enum", List.of("HIGH", "MEDIUM", "LOW")
        ));

        schema.put("properties", properties);
        schema.put("required", List.of(
                "root_cause",
                "root_cause_category",
                "confidence",
                "analysis_detail",
                "impact_scope",
                "impact_severity"
        ));
        schema.put("additionalProperties", false);
        return schema;
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
