package com.evelin.loganalysis.loganalysisai.config.service;

import com.evelin.loganalysis.loganalysisai.config.dto.LlmConfigDTO;
import com.evelin.loganalysis.loganalysisai.config.entity.LlmConfigEntity;
import com.evelin.loganalysis.loganalysisai.config.repository.LlmConfigRepository;
import com.evelin.loganalysis.logcommon.utils.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * LLM 配置服务
 *
 * @author Evelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmConfigService {
    
    private final LlmConfigRepository llmConfigRepository;
    
    /**
     * 获取所有配置
     */
    public List<LlmConfigDTO> getAllConfigs() {
        return llmConfigRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取启用的配置
     */
    public List<LlmConfigDTO> getEnabledConfigs() {
        return llmConfigRepository.findByEnabledTrue().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取默认配置
     */
    public LlmConfigDTO getDefaultConfig() {
        return llmConfigRepository.findByIsDefaultTrue()
                .map(this::toDTO)
                .orElse(null);
    }
    
    /**
     * 获取当前活跃的配置（优先默认配置）
     */
    public LlmConfigDTO getActiveConfig() {
        // 优先获取默认配置
        LlmConfigEntity defaultConfig = llmConfigRepository.findByIsDefaultTrue()
                .orElse(null);
        
        if (defaultConfig != null && defaultConfig.getEnabled()) {
            return toDTO(defaultConfig);
        }
        
        // 否则获取第一个启用的配置
        return llmConfigRepository.findByEnabledTrue().stream()
                .findFirst()
                .map(this::toDTO)
                .orElse(null);
    }
    
    /**
     * 根据 ID 获取配置
     */
    public LlmConfigDTO getConfigById(String id) {
        return llmConfigRepository.findById(id)
                .map(this::toDTO)
                .orElse(null);
    }
    
    /**
     * 创建配置
     */
    @Transactional
    public LlmConfigDTO createConfig(LlmConfigDTO dto) {
        // 如果设为默认，先清除其他默认
        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            clearOtherDefaults();
        }
        
        LlmConfigEntity entity = toEntity(dto);
        entity.setId(IdGenerator.nextId());
        
        LlmConfigEntity saved = llmConfigRepository.save(entity);
        log.info("创建 LLM 配置成功: {}", saved.getName());
        
        return toDTO(saved);
    }
    
    /**
     * 更新配置
     */
    @Transactional
    public LlmConfigDTO updateConfig(String id, LlmConfigDTO dto) {
        LlmConfigEntity existing = llmConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("配置不存在: " + id));
        
        // 如果设为默认，先清除其他默认
        if (Boolean.TRUE.equals(dto.getIsDefault()) && !Boolean.TRUE.equals(existing.getIsDefault())) {
            clearOtherDefaults();
        }
        
        // 更新字段
        existing.setName(dto.getName());
        if (dto.getApiKey() != null && !dto.getApiKey().isEmpty()) {
            existing.setApiKey(dto.getApiKey());
        }
        existing.setModel(dto.getModel());
        existing.setMaxTokens(dto.getMaxTokens());
        existing.setTemperature(dto.getTemperature());
        existing.setTimeout(dto.getTimeout());
        existing.setEndpoint(dto.getEndpoint());
        existing.setEnabled(dto.getEnabled());
        existing.setIsDefault(dto.getIsDefault());
        existing.setRemark(dto.getRemark());
        
        LlmConfigEntity saved = llmConfigRepository.save(existing);
        log.info("更新 LLM 配置成功: {}", saved.getName());
        
        return toDTO(saved);
    }
    
    /**
     * 删除配置
     */
    @Transactional
    public void deleteConfig(String id) {
        llmConfigRepository.deleteById(id);
        log.info("删除 LLM 配置成功: {}", id);
    }
    
    /**
     * 验证 API Key 是否有效
     */
    public boolean validateApiKey(String id) {
        LlmConfigEntity config = llmConfigRepository.findById(id)
                .orElse(null);
        
        if (config == null || config.getApiKey() == null) {
            return false;
        }
        
        // 这里可以调用 LLM 提供商验证 API Key
        // 暂时返回 true
        return true;
    }
    
    /**
     * 清除其他默认配置
     */
    private void clearOtherDefaults() {
        llmConfigRepository.findByIsDefaultTrue()
                .ifPresent(config -> {
                    config.setIsDefault(false);
                    llmConfigRepository.save(config);
                });
    }
    
    /**
     * 转换为 DTO
     */
    private LlmConfigDTO toDTO(LlmConfigEntity entity) {
        LlmConfigDTO dto = new LlmConfigDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setModel(entity.getModel());
        dto.setMaxTokens(entity.getMaxTokens());
        dto.setTemperature(entity.getTemperature());
        dto.setTimeout(entity.getTimeout());
        dto.setEndpoint(entity.getEndpoint());
        dto.setEnabled(entity.getEnabled());
        dto.setIsDefault(entity.getIsDefault());
        dto.setRemark(entity.getRemark());

        // 返回脱敏后的 API Key，前端可据此判断是否有保存的 Key
        String rawApiKey = entity.getApiKey();
        if (rawApiKey != null && !rawApiKey.isEmpty()) {
            dto.setMaskedApiKey("******" + rawApiKey.substring(rawApiKey.length() - 4));
        } else {
            dto.setMaskedApiKey(null);
        }

        return dto;
    }
    
    /**
     * 转换为实体
     */
    private LlmConfigEntity toEntity(LlmConfigDTO dto) {
        LlmConfigEntity entity = new LlmConfigEntity();
        entity.setName(dto.getName());
        entity.setApiKey(dto.getApiKey());
        entity.setModel(dto.getModel());
        entity.setMaxTokens(dto.getMaxTokens());
        entity.setTemperature(dto.getTemperature());
        entity.setTimeout(dto.getTimeout());
        entity.setEndpoint(dto.getEndpoint());
        entity.setEnabled(dto.getEnabled());
        entity.setIsDefault(dto.getIsDefault());
        entity.setRemark(dto.getRemark());
        return entity;
    }
}
