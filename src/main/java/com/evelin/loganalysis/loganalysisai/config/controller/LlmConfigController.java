package com.evelin.loganalysis.loganalysisai.config.controller;

import com.evelin.loganalysis.loganalysisai.config.dto.LlmConfigDTO;
import com.evelin.loganalysis.loganalysisai.config.service.LlmConfigService;
import com.evelin.loganalysis.logcommon.model.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * LLM 配置 Controller
 *
 * @author Evelin
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/llm-config")
@RequiredArgsConstructor
public class LlmConfigController {
    
    private final LlmConfigService llmConfigService;
    
    /**
     * 获取所有配置
     */
    @GetMapping
    public Result<List<LlmConfigDTO>> getAllConfigs() {
        List<LlmConfigDTO> configs = llmConfigService.getAllConfigs();
        return Result.success(configs);
    }
    
    /**
     * 获取启用的配置
     */
    @GetMapping("/enabled")
    public Result<List<LlmConfigDTO>> getEnabledConfigs() {
        List<LlmConfigDTO> configs = llmConfigService.getEnabledConfigs();
        return Result.success(configs);
    }
    
    /**
     * 获取默认配置
     */
    @GetMapping("/default")
    public Result<LlmConfigDTO> getDefaultConfig() {
        LlmConfigDTO config = llmConfigService.getDefaultConfig();
        return Result.success(config);
    }
    
    /**
     * 获取当前活跃配置
     */
    @GetMapping("/active")
    public Result<LlmConfigDTO> getActiveConfig() {
        LlmConfigDTO config = llmConfigService.getActiveConfig();
        return Result.success(config);
    }
    
    /**
     * 根据 ID 获取配置
     */
    @GetMapping("/{id}")
    public Result<LlmConfigDTO> getConfigById(@PathVariable String id) {
        LlmConfigDTO config = llmConfigService.getConfigById(id);
        if (config == null) {
            return Result.failed(404, "配置不存在");
        }
        return Result.success(config);
    }
    
    /**
     * 创建配置
     */
    @PostMapping
    public Result<LlmConfigDTO> createConfig(@RequestBody LlmConfigDTO dto) {
        try {
            LlmConfigDTO created = llmConfigService.createConfig(dto);
            return Result.success(created);
        } catch (Exception e) {
            log.error("创建配置失败: {}", e.getMessage(), e);
            return Result.failed(500, "创建失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新配置
     */
    @PutMapping("/{id}")
    public Result<LlmConfigDTO> updateConfig(@PathVariable String id, @RequestBody LlmConfigDTO dto) {
        try {
            LlmConfigDTO updated = llmConfigService.updateConfig(id, dto);
            return Result.success(updated);
        } catch (Exception e) {
            log.error("更新配置失败: {}", e.getMessage(), e);
            return Result.failed(500, "更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除配置
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteConfig(@PathVariable String id) {
        try {
            llmConfigService.deleteConfig(id);
            return Result.success(null);
        } catch (Exception e) {
            log.error("删除配置失败: {}", e.getMessage(), e);
            return Result.failed(500, "删除失败: " + e.getMessage());
        }
    }
    
    /**
     * 验证 API Key
     */
    @PostMapping("/{id}/validate")
    public Result<Boolean> validateApiKey(@PathVariable String id) {
        boolean valid = llmConfigService.validateApiKey(id);
        return Result.success(valid);
    }
}
