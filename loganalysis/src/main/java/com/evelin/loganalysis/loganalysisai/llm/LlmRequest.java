package com.evelin.loganalysis.loganalysisai.llm;

import lombok.Data;

/**
 * LLM 请求对象
 *
 * @author Evelin
 */
@Data
public class LlmRequest {
    
    /**
     * 提示词
     */
    private String prompt;
    
    /**
     * 模型名称
     */
    private String model;
    
    /**
     * 温度参数 (0-2)，越高越有创意
     */
    private double temperature;
    
    /**
     * 最大 token 数
     */
    private int maxTokens;
    
    /**
     * 系统提示词
     */
    private String systemPrompt;
    
    /**
     * 历史消息
     */
    private java.util.List<ChatMessage> messages;

    /**
     * 响应格式类型：
     * json_schema / json_object / text
     */
    private String responseFormatType;

    /**
     * JSON Schema 名称（responseFormatType=json_schema 时使用）
     */
    private String responseFormatSchemaName;

    /**
     * JSON Schema（responseFormatType=json_schema 时使用）
     */
    private java.util.Map<String, Object> responseFormatSchema;
    
    @Data
    public static class ChatMessage {
        private String role;
        private String content;
        
        public ChatMessage() {}
        
        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
