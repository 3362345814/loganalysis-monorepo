package com.evelin.loganalysis.loganalysisai.llm.config;

import com.evelin.loganalysis.loganalysisai.config.entity.LlmConfigEntity;
import com.evelin.loganalysis.loganalysisai.config.repository.LlmConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 动态 LLM 配置服务
 * 从数据库中获取 LLM 配置
 *
 * @author Evelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicLlmConfigService {
    
    private final LlmConfigRepository llmConfigRepository;
    
    /**
     * 获取当前活跃的 LLM 配置
     *
     * @return 动态配置
     */
    public DynamicLlmConfig getActiveConfig() {
        // 优先获取默认配置
        Optional<LlmConfigEntity> defaultConfig = llmConfigRepository.findByIsDefaultTrue();
        
        if (defaultConfig.isPresent() && defaultConfig.get().getEnabled()) {
            return toDynamicConfig(defaultConfig.get());
        }
        
        // 否则获取第一个启用的配置
        Optional<LlmConfigEntity> enabledConfig = llmConfigRepository.findByEnabledTrue().stream().findFirst();
        
        if (enabledConfig.isPresent()) {
            return toDynamicConfig(enabledConfig.get());
        }
        
        log.warn("未找到活跃的 LLM 配置");
        return null;
    }
    
    /**
     * 根据配置ID获取配置
     *
     * @param configId 配置ID
     * @return 动态配置
     */
    public DynamicLlmConfig getConfigById(String configId) {
        return llmConfigRepository.findById(configId)
                .filter(LlmConfigEntity::getEnabled)
                .map(this::toDynamicConfig)
                .orElse(null);
    }
    
    /**
     * 转换为动态配置
     */
    private DynamicLlmConfig toDynamicConfig(LlmConfigEntity entity) {
        DynamicLlmConfig config = new DynamicLlmConfig();
        config.setConfigId(entity.getId());
        config.setApiKey(entity.getApiKey());
        config.setModel(entity.getModel());
        config.setMaxTokens(entity.getMaxTokens() != null ? entity.getMaxTokens() : 2000);
        config.setTemperature(entity.getTemperature() != null ? entity.getTemperature() : 0.3);
        config.setTimeout(entity.getTimeout() != null ? entity.getTimeout() : 30);
        config.setThinkingEnabled(entity.getThinkingEnabled() == null || entity.getThinkingEnabled());
        config.setReasoningEffort(entity.getReasoningEffort() != null && !entity.getReasoningEffort().isBlank()
                ? entity.getReasoningEffort()
                : "medium");
        config.setEndpoint(entity.getEndpoint());
        return config;
    }
}
