package com.evelin.loganalysis.loganalysisai.llm.config;

import lombok.Data;

/**
 * 动态 LLM 配置（从数据库加载）
 *
 * @author Evelin
 */
@Data
public class DynamicLlmConfig {
    
    /**
     * API Key
     */
    private String apiKey;
    
    /**
     * 模型
     */
    private String model;
    
    /**
     * 最大 Token
     */
    private int maxTokens = 2000;
    
    /**
     * 温度
     */
    private double temperature = 0.3;
    
    /**
     * 超时（秒）
     */
    private int timeout = 30;

    /**
     * 是否开启思考模式
     */
    private boolean thinkingEnabled = true;

    /**
     * 思考强度
     * 可选值：none/minimal/low/medium/high/xhigh
     */
    private String reasoningEffort = "medium";
    
    /**
     * API 端点（必填）
     */
    private String endpoint;
    
    /**
     * 配置 ID
     */
    private String configId;
    
    /**
     * 是否有效
     */
    public boolean isValid() {
        return apiKey != null && !apiKey.isEmpty()
            && model != null && !model.isEmpty()
            && endpoint != null && !endpoint.isEmpty();
    }
}
