package com.evelin.loganalysis.loganalysisai.prompt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Prompt 模板管理器
 *
 * @author Evelin
 */
@Slf4j
@Component
public class PromptTemplateManager {
    
    /**
     * 系统提示词
     */
    private static final String SYSTEM_PROMPT = """
            你是一个专业的系统运维专家，擅长分析日志、诊断问题并提供解决方案。
            你的职责是：
            1. 分析日志中的异常信息，找出问题的根本原因
            2. 评估问题的影响范围和严重程度
            3. 提供具体可操作的修复建议
            4. 预测类似问题可能再次发生的预防措施
            
            请用中文回复，分析问题时要条理清晰，给出的建议要具体可操作。
            如果需要更多信息来进行准确判断，请在回复中明确指出。
            """;
    
    /**
     * 根因分析模板
     */
    private static final String ROOT_CAUSE_ANALYSIS_TEMPLATE = """
            ## 日志分析请求
            
            请分析以下日志异常，找出根本原因。
            
            ### 聚合组信息
            - 聚合组ID: {group_id}
            - 事件数量: {event_count}
            - 严重程度: {severity}
            - 时间范围: {time_range}
            
            ### 代表日志
            {representative_log}
            
            ### 相关日志（共 {log_count} 条）
            {related_logs}
            
            ### 上下文信息
            #### 前置日志（前 {before_count} 条）
            {context_before}
            
            #### 后置日志（后 {after_count} 条）
            {context_after}
            
            ### 堆栈信息
            {stack_trace}
            
            ### 分析要求
            请返回以下格式的JSON分析结果：
            ```json
            {
                "root_cause": "根本原因描述",
                "root_cause_category": "问题分类，如：DATABASE/NETWORK/MEMORY/CODE/CONFIG/UNKNOWN",
                "confidence": 0.85,
                "analysis_detail": "详细分析过程",
                "impact_scope": "影响范围",
                "impact_severity": "HIGH/MEDIUM/LOW"
            }
            ```
            
            注意：只返回JSON，不要返回其他内容。
            """;
    
    /**
     * 修复建议生成模板
     */
    private static final String SUGGESTION_GENERATION_TEMPLATE = """
            ## 修复建议请求
            
            基于以下分析结果，请生成具体的修复建议。
            
            ### 根因分析结果
            - 根本原因: {root_cause}
            - 问题分类: {root_cause_category}
            - 置信度: {confidence}
            - 详细分析: {analysis_detail}
            
            ### 相关日志
            {related_logs}
            
            ### 修复建议要求
            请返回以下格式的JSON建议：
            ```json
            {
                "suggestions": [
                    {
                        "title": "建议标题",
                        "content": "详细建议内容",
                        "priority": "HIGH/MEDIUM/LOW",
                        "action_type": "CONFIG/CODE/INFRA/DEBUG",
                        "estimated_effort": "预估工作量"
                    }
                ],
                "prevention_measures": [
                    "预防措施1",
                    "预防措施2"
                ]
            }
            ```
            
            注意：只返回JSON，不要返回其他内容。
            """;
    
    /**
     * 批量分析模板
     */
    private static final String BATCH_ANALYSIS_TEMPLATE = """
            ## 批量日志分析请求
            
            请分析以下多个日志异常组。
            
            ### 日志组列表
            {aggregation_list}
            
            ### 分析要求
            请为每个日志组分别进行分析，返回以下格式的JSON结果：
            ```json
            {
                "results": [
                    {
                        "group_id": "聚合组ID",
                        "root_cause": "根本原因",
                        "confidence": 0.85,
                        "severity": "HIGH/MEDIUM/LOW"
                    }
                ]
            }
            ```
            
            注意：只返回JSON，不要返回其他内容。
            """;
    
    /**
     * 上下文模板变量
     */
    private final Map<String, String> templates = new HashMap<>();
    
    public PromptTemplateManager() {
        templates.put("system", SYSTEM_PROMPT);
        templates.put("root_cause", ROOT_CAUSE_ANALYSIS_TEMPLATE);
        templates.put("suggestion", SUGGESTION_GENERATION_TEMPLATE);
        templates.put("batch", BATCH_ANALYSIS_TEMPLATE);
    }
    
    /**
     * 获取系统提示词
     */
    public String getSystemPrompt() {
        return templates.get("system");
    }
    
    /**
     * 获取根因分析模板
     */
    public String getRootCauseTemplate() {
        return templates.get("root_cause");
    }
    
    /**
     * 获取修复建议模板
     */
    public String getSuggestionTemplate() {
        return templates.get("suggestion");
    }
    
    /**
     * 获取批量分析模板
     */
    public String getBatchTemplate() {
        return templates.get("batch");
    }
    
    /**
     * 构建根因分析 Prompt
     *
     * @param params 参数Map
     * @return 完整的Prompt
     */
    public String buildRootCausePrompt(Map<String, Object> params) {
        String template = getRootCauseTemplate();
        
        return template
                .replace("{group_id}", getOrDefault(params, "group_id", "N/A"))
                .replace("{event_count}", getOrDefault(params, "event_count", "0"))
                .replace("{severity}", getOrDefault(params, "severity", "UNKNOWN"))
                .replace("{time_range}", getOrDefault(params, "time_range", "N/A"))
                .replace("{representative_log}", getOrDefault(params, "representative_log", ""))
                .replace("{log_count}", getOrDefault(params, "log_count", "0"))
                .replace("{related_logs}", getOrDefault(params, "related_logs", ""))
                .replace("{before_count}", getOrDefault(params, "before_count", "0"))
                .replace("{context_before}", getOrDefault(params, "context_before", "无"))
                .replace("{after_count}", getOrDefault(params, "after_count", "0"))
                .replace("{context_after}", getOrDefault(params, "context_after", "无"))
                .replace("{stack_trace}", getOrDefault(params, "stack_trace", "无"));
    }
    
    /**
     * 构建修复建议 Prompt
     *
     * @param params 参数Map
     * @return 完整的Prompt
     */
    public String buildSuggestionPrompt(Map<String, Object> params) {
        String template = getSuggestionTemplate();
        
        return template
                .replace("{root_cause}", getOrDefault(params, "root_cause", ""))
                .replace("{root_cause_category}", getOrDefault(params, "root_cause_category", "UNKNOWN"))
                .replace("{confidence}", getOrDefault(params, "confidence", "0"))
                .replace("{analysis_detail}", getOrDefault(params, "analysis_detail", ""))
                .replace("{related_logs}", getOrDefault(params, "related_logs", ""));
    }
    
    /**
     * 获取默认值
     */
    private String getOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
