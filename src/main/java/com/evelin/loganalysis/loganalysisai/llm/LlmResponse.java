package com.evelin.loganalysis.loganalysisai.llm;

import lombok.Data;
import java.util.Map;

/**
 * LLM 响应对象
 *
 * @author Evelin
 */
@Data
public class LlmResponse {
    
    /**
     * 响应内容
     */
    private String content;
    
    /**
     * 使用的模型
     */
    private String model;
    
    /**
     * 使用的 token 数量
     */
    private int tokensUsed;
    
    /**
     * 请求的 token 数量
     */
    private int promptTokens;
    
    /**
     * 响应的 token 数量
     */
    private int completionTokens;
    
    /**
     * 响应时间 (毫秒)
     */
    private long responseTimeMs;
    
    /**
     * 状态
     */
    private String status;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 原始响应
     */
    private Map<String, Object> rawResponse;
    
    /**
     * 是否成功
     */
    public boolean isSuccess() {
        return "success".equalsIgnoreCase(status) && content != null;
    }
    
    /**
     * 静态工厂方法
     */
    public static LlmResponse success(String content) {
        LlmResponse response = new LlmResponse();
        response.setContent(content);
        response.setStatus("success");
        return response;
    }
    
    public static LlmResponse error(String message) {
        LlmResponse response = new LlmResponse();
        response.setErrorMessage(message);
        response.setStatus("error");
        return response;
    }
}
