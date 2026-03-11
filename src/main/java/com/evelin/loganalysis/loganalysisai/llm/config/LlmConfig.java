package com.evelin.loganalysis.loganalysisai.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 配置类
 *
 * @author Evelin
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.llm")
public class LlmConfig {
    
    /**
     * LLM 提供商：openai / deepseek / azure / local
     */
    private String provider = "openai";
    
    /**
     * API 密钥
     */
    private String apiKey;
    
    /**
     * 使用的模型
     */
    private String model = "gpt-4";
    
    /**
     * 最大 token 数
     */
    private int maxTokens = 2000;
    
    /**
     * 温度参数 (0-2)
     */
    private double temperature = 0.3;
    
    /**
     * 超时时间 (秒)
     */
    private int timeout = 30;
    
    /**
     * 请求端点 (可选，用于自定义或代理)
     */
    private String endpoint;
    
    /**
     * 组织 ID (OpenAI 可选)
     */
    private String organization;
}
