package com.evelin.loganalysis.loganalysisai.llm;

/**
 * LLM 提供商接口
 *
 * @author Evelin
 */
public interface LlmProvider {
    
    /**
     * 获取提供商名称
     */
    String getName();
    
    /**
     * 聊天完成
     */
    LlmResponse chat(LlmRequest request);
    
    /**
     * 检查 API 密钥是否有效
     */
    boolean validateApiKey();
}
