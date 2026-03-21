package com.evelin.loganalysis.loganalysisai.llm;

import com.evelin.loganalysis.loganalysisai.llm.config.DynamicLlmConfig;
import com.evelin.loganalysis.loganalysisai.llm.config.DynamicLlmConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LLM 客户端
 *
 * @author Evelin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmClient {
    
    private final DynamicLlmConfigService dynamicConfigService;
    
    /**
     * 调用 LLM 提供商进行聊天
     *
     * @param request 请求对象
     * @return 响应对象
     */
    public LlmResponse chat(LlmRequest request) {
        DynamicLlmConfig dynamicConfig = dynamicConfigService.getActiveConfig();
        
        if (dynamicConfig == null) {
            log.error("未配置 LLM，无法进行分析");
            LlmResponse errorResponse = new LlmResponse();
            errorResponse.setStatus("error");
            errorResponse.setErrorMessage("未配置 LLM，无法进行分析");
            return errorResponse;
        }
        
        if (!dynamicConfig.isValid()) {
            log.error("LLM 配置无效，请检查配置");
            LlmResponse errorResponse = new LlmResponse();
            errorResponse.setStatus("error");
            errorResponse.setErrorMessage("LLM 配置无效，请检查配置");
            return errorResponse;
        }
        
        String model = request.getModel() != null ? request.getModel() : dynamicConfig.getModel();
        int maxTokens = request.getMaxTokens() > 0 ? request.getMaxTokens() : dynamicConfig.getMaxTokens();
        
        log.info("调用 LLM API, 模型: {}, 最大Token: {}", model, maxTokens);
        
        try {
            LlmProvider provider = LlmProviderFactory.getProvider(dynamicConfig);
            return provider.chat(request);
        } catch (Exception e) {
            log.error("LLM 调用失败: {}", e.getMessage(), e);
            LlmResponse errorResponse = new LlmResponse();
            errorResponse.setStatus("error");
            errorResponse.setErrorMessage(e.getMessage());
            return errorResponse;
        }
    }
    
    /**
     * 发送简单的聊天请求
     *
     * @param prompt 提示词
     * @return 响应内容
     */
    public String chat(String prompt) {
        LlmRequest request = new LlmRequest();
        request.setPrompt(prompt);
        LlmResponse response = chat(request);
        return response.getContent();
    }
    
    /**
     * 发送聊天请求并指定模型
     *
     * @param prompt 提示词
     * @param model 模型名称
     * @return 响应内容
     */
    public String chat(String prompt, String model) {
        LlmRequest request = new LlmRequest();
        request.setPrompt(prompt);
        request.setModel(model);
        LlmResponse response = chat(request);
        return response.getContent();
    }
}
