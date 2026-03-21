package com.evelin.loganalysis.logalert.controller;


import com.evelin.loganalysis.logalert.model.NotificationChannelConfig;
import com.evelin.loganalysis.logalert.repository.NotificationChannelConfigRepository;
import com.evelin.loganalysis.logcommon.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通知渠道配置Controller
 *
 * @author Evelin
 */
@RestController
@RequestMapping("/api/v1/alert/channel-configs")
@RequiredArgsConstructor
public class NotificationChannelConfigController {

    private final NotificationChannelConfigRepository configRepository;

    /**
     * 获取所有通知渠道配置
     */
    @GetMapping
    public Result<List<NotificationChannelConfig>> getAll() {
        return Result.success(configRepository.findAllByOrderByChannelAsc());
    }

    /**
     * 获取已启用的通知渠道配置
     */
    @GetMapping("/enabled")
    public Result<List<NotificationChannelConfig>> getEnabled() {
        return Result.success(configRepository.findByEnabledTrue());
    }

    /**
     * 创建或更新通知渠道配置
     */
    @PostMapping
    public Result<NotificationChannelConfig> save(@RequestBody NotificationChannelConfig config) {
        NotificationChannelConfig saved = configRepository.save(config);
        return Result.success(saved);
    }

    /**
     * 批量更新渠道配置
     */
    @PostMapping("/batch")
    public Result<List<NotificationChannelConfig>> batchSave(@RequestBody List<NotificationChannelConfig> configs) {
        List<NotificationChannelConfig> saved = configRepository.saveAll(configs);
        return Result.success(saved);
    }

    /**
     * 批量保存或更新渠道配置（UPSERT语义：存在则更新，不存在则新增）
     */
    @PostMapping("/batch-upsert")
    public Result<List<NotificationChannelConfig>> batchUpsert(@RequestBody List<NotificationChannelConfig> configs) {
        List<NotificationChannelConfig> results = configs.stream().map(config -> {
            return configRepository.findByChannel(config.getChannel())
                    .map(existing -> {
                        existing.setEnabled(config.getEnabled());
                        existing.setConfigParams(config.getConfigParams());
                        existing.setDescription(config.getDescription());
                        return configRepository.save(existing);
                    })
                    .orElseGet(() -> configRepository.save(config));
        }).toList();
        return Result.success(results);
    }

    /**
     * 删除渠道配置
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        configRepository.deleteById(id);
        return Result.success(null);
    }
}
