package com.evelin.loganalysis.loganalysisai.llm;

import com.evelin.loganalysis.loganalysisai.llm.config.DynamicLlmConfig;
import com.evelin.loganalysis.loganalysisai.llm.provider.OpenAiProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 提供商工厂
 *
 * @author Evelin
 */
@Slf4j
@Component
public class LlmProviderFactory {
    
    private static OpenAiProvider openAiProvider;
    private static final Map<String, LlmProvider> providerCache = new ConcurrentHashMap<>();
    
    public LlmProviderFactory(OpenAiProvider openAiProvider) {
        LlmProviderFactory.openAiProvider = openAiProvider;
    }
    
    /**
     * 获取 LLM 提供商
     *
     * @param dynamicConfig 动态配置
     * @return LLM 提供商实例
     */
    public static LlmProvider getProvider(DynamicLlmConfig dynamicConfig) {
        String providerName = inferProvider(dynamicConfig);
        
        return providerCache.computeIfAbsent(providerName, name -> {
            log.info("创建 LLM 提供商: {}", name);
            return openAiProvider;
        });
    }
    
    private static String inferProvider(DynamicLlmConfig config) {
        if (config == null || config.getEndpoint() == null || config.getEndpoint().isEmpty()) {
            return "openai";
        }
        String endpoint = config.getEndpoint().toLowerCase();
        if (endpoint.contains("deepseek")) {
            return "deepseek";
        } else if (endpoint.contains("volces.com") || endpoint.contains("ark.cn-beijing")) {
            return "doubao";
        } else if (endpoint.contains("azure")) {
            return "azure";
        }
        return "openai";
    }
    
    /**
     * 获取所有可用的提供商
     *
     * @return 提供商列表
     */
    public List<String> getAvailableProviders() {
        return List.of("openai", "deepseek", "doubao", "豆包", "ark", "azure", "local");
    }
}
