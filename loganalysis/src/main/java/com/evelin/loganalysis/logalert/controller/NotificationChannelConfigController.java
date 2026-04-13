package com.evelin.loganalysis.logalert.controller;


import com.evelin.loganalysis.logalert.model.NotificationChannelConfig;
import com.evelin.loganalysis.logalert.repository.NotificationChannelConfigRepository;
import com.evelin.loganalysis.logcommon.constant.ResultCode;
import com.evelin.loganalysis.logcommon.exception.BusinessException;
import com.evelin.loganalysis.logcommon.model.Result;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 通知渠道配置Controller
 *
 * @author Evelin
 */
@RestController
@RequestMapping("/api/v1/alert/channel-configs")
@RequiredArgsConstructor
public class NotificationChannelConfigController {

    private static final String MASK_PREFIX = "******";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password",
            "secret",
            "apiKey",
            "api_key",
            "token",
            "accessToken",
            "access_token",
            "webhookSecret",
            "webhook_secret",
            "privateKey",
            "private_key"
    );

    private final NotificationChannelConfigRepository configRepository;
    private final ObjectMapper objectMapper;

    /**
     * 获取所有通知渠道配置
     */
    @GetMapping
    public Result<List<NotificationChannelConfig>> getAll() {
        return Result.success(
                configRepository.findAllByOrderByChannelAsc()
                        .stream()
                        .map(this::sanitizeConfig)
                        .toList()
        );
    }

    /**
     * 获取已启用的通知渠道配置
     */
    @GetMapping("/enabled")
    public Result<List<NotificationChannelConfig>> getEnabled() {
        return Result.success(
                configRepository.findByEnabledTrue()
                        .stream()
                        .map(this::sanitizeConfig)
                        .toList()
        );
    }

    /**
     * 创建或更新通知渠道配置
     */
    @PostMapping
    public Result<NotificationChannelConfig> save(@RequestBody NotificationChannelConfig config) {
        NotificationChannelConfig saved = saveWithSecretMerge(config);
        return Result.success(sanitizeConfig(saved));
    }

    /**
     * 批量更新渠道配置
     */
    @PostMapping("/batch")
    public Result<List<NotificationChannelConfig>> batchSave(@RequestBody List<NotificationChannelConfig> configs) {
        List<NotificationChannelConfig> saved = configs.stream()
                .map(this::saveWithSecretMerge)
                .map(this::sanitizeConfig)
                .toList();
        return Result.success(saved);
    }

    /**
     * 批量保存或更新渠道配置（UPSERT语义：存在则更新，不存在则新增）
     */
    @PostMapping("/batch-upsert")
    public Result<List<NotificationChannelConfig>> batchUpsert(@RequestBody List<NotificationChannelConfig> configs) {
        List<NotificationChannelConfig> results = configs.stream()
                .map(this::saveWithSecretMerge)
                .map(this::sanitizeConfig)
                .toList();
        return Result.success(results);
    }

    /**
     * 删除渠道配置
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable UUID id) {
        configRepository.deleteById(id);
        return Result.success(null);
    }

    private NotificationChannelConfig saveWithSecretMerge(NotificationChannelConfig config) {
        NotificationChannelConfig existing = findExistingConfig(config);
        NotificationChannelConfig target = existing != null ? existing : new NotificationChannelConfig();

        if (config.getChannel() != null) {
            target.setChannel(config.getChannel());
        }
        if (config.getEnabled() != null) {
            target.setEnabled(config.getEnabled());
        } else if (existing == null) {
            target.setEnabled(false);
        }
        target.setDescription(config.getDescription());
        target.setConfigParams(mergeConfigParams(
                config.getConfigParams(),
                existing != null ? existing.getConfigParams() : null
        ));

        return configRepository.save(target);
    }

    private NotificationChannelConfig findExistingConfig(NotificationChannelConfig config) {
        if (config.getId() != null) {
            return configRepository.findById(config.getId()).orElse(null);
        }
        if (config.getChannel() != null) {
            return configRepository.findByChannel(config.getChannel()).orElse(null);
        }
        return null;
    }

    private String mergeConfigParams(String incomingJson, String existingJson) {
        if (incomingJson == null || incomingJson.isBlank()) {
            return existingJson;
        }

        Map<String, Object> incomingMap = parseJson(incomingJson);
        if (incomingMap.isEmpty()) {
            return existingJson;
        }

        Map<String, Object> existingMap = parseJson(existingJson);
        Map<String, Object> merged = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : incomingMap.entrySet()) {
            String key = entry.getKey();
            Object incomingValue = entry.getValue();

            if (isSensitiveKey(key) && shouldKeepExistingSecret(incomingValue)) {
                if (existingMap.containsKey(key)) {
                    merged.put(key, existingMap.get(key));
                }
                continue;
            }
            merged.put(key, incomingValue);
        }

        return writeJson(merged);
    }

    private NotificationChannelConfig sanitizeConfig(NotificationChannelConfig source) {
        NotificationChannelConfig sanitized = new NotificationChannelConfig();
        sanitized.setId(source.getId());
        sanitized.setCreatedAt(source.getCreatedAt());
        sanitized.setUpdatedAt(source.getUpdatedAt());
        sanitized.setCreatedBy(source.getCreatedBy());
        sanitized.setUpdatedBy(source.getUpdatedBy());
        sanitized.setDeletedAt(source.getDeletedAt());
        sanitized.setRemark(source.getRemark());
        sanitized.setChannel(source.getChannel());
        sanitized.setEnabled(source.getEnabled());
        sanitized.setDescription(source.getDescription());
        sanitized.setConfigParams(maskSensitiveConfigParams(source.getConfigParams()));
        return sanitized;
    }

    private String maskSensitiveConfigParams(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }

        Map<String, Object> params = parseJson(json);
        if (params.isEmpty()) {
            return json;
        }

        Map<String, Object> masked = new LinkedHashMap<>(params);
        for (Map.Entry<String, Object> entry : masked.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!isSensitiveKey(key) || value == null) {
                continue;
            }
            String valueStr = String.valueOf(value);
            if (!valueStr.isBlank()) {
                entry.setValue(maskSecret(valueStr));
            }
        }
        return writeJson(masked);
    }

    private boolean isSensitiveKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        for (String sensitiveKey : SENSITIVE_KEYS) {
            if (sensitiveKey.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldKeepExistingSecret(Object incomingValue) {
        if (incomingValue == null) {
            return true;
        }
        if (!(incomingValue instanceof String value)) {
            return false;
        }
        return value.isBlank() || isMaskedSecret(value);
    }

    private boolean isMaskedSecret(String value) {
        return value != null && value.startsWith(MASK_PREFIX);
    }

    private String maskSecret(String value) {
        int visibleCount = Math.min(4, value.length());
        return MASK_PREFIX + value.substring(value.length() - visibleCount);
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "通知渠道配置 JSON 解析失败");
        }
    }

    private String writeJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "通知渠道配置 JSON 序列化失败");
        }
    }
}
