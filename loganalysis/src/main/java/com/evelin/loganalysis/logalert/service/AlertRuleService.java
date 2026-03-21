package com.evelin.loganalysis.logalert.service;

import com.evelin.loganalysis.logalert.dto.AlertRuleCreateRequest;
import com.evelin.loganalysis.logalert.dto.AlertRuleResponse;
import com.evelin.loganalysis.logalert.dto.AlertRuleUpdateRequest;
import com.evelin.loganalysis.logalert.enums.AlertLevel;
import com.evelin.loganalysis.logalert.enums.RuleType;
import com.evelin.loganalysis.logalert.model.AlertRule;
import com.evelin.loganalysis.logalert.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 告警规则服务
 *
 * @author Evelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuleService {

    private final AlertRuleRepository alertRuleRepository;

    /**
     * 创建告警规则
     */
    @Transactional
    public AlertRuleResponse createRule(AlertRuleCreateRequest request) {
        if (alertRuleRepository.findByName(request.getName()).isPresent()) {
            throw new IllegalArgumentException("规则名称已存在: " + request.getName());
        }

        AlertRule rule = AlertRule.builder()
                .name(request.getName())
                .description(request.getDescription())
                .ruleType(request.getRuleType())
                .conditionExpression(request.getConditionExpression())
                .alertLevel(request.getAlertLevel())
                .alertTitle(request.getAlertTitle())
                .alertMessage(request.getAlertMessage())
                .notificationChannels(request.getNotificationChannels())
                .sourceIds(request.getSourceIds())
                .scheduleCron(request.getScheduleCron())
                .cooldownMinutes(request.getCooldownMinutes() != null ? request.getCooldownMinutes() : 10)
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .status("ACTIVE")
                .triggerCountToday(0)
                .config(request.getConfig())
                .projectId(request.getProjectId())
                .build();

        AlertRule saved = alertRuleRepository.save(rule);
        log.info("创建告警规则成功: {}", saved.getId());
        return toResponse(saved);
    }

    /**
     * 更新告警规则
     */
    @Transactional
    public AlertRuleResponse updateRule(UUID id, AlertRuleUpdateRequest request) {
        AlertRule rule = alertRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("规则不存在: " + id));

        if (request.getName() != null && !request.getName().equals(rule.getName())) {
            if (alertRuleRepository.findByName(request.getName()).isPresent()) {
                throw new IllegalArgumentException("规则名称已存在: " + request.getName());
            }
            rule.setName(request.getName());
        }

        if (request.getDescription() != null) {
            rule.setDescription(request.getDescription());
        }
        if (request.getRuleType() != null) {
            rule.setRuleType(request.getRuleType());
        }
        if (request.getConditionExpression() != null) {
            rule.setConditionExpression(request.getConditionExpression());
        }
        if (request.getAlertLevel() != null) {
            rule.setAlertLevel(request.getAlertLevel());
        }
        if (request.getAlertTitle() != null) {
            rule.setAlertTitle(request.getAlertTitle());
        }
        if (request.getAlertMessage() != null) {
            rule.setAlertMessage(request.getAlertMessage());
        }
        if (request.getNotificationChannels() != null) {
            rule.setNotificationChannels(request.getNotificationChannels());
        }
        if (request.getSourceIds() != null) {
            rule.setSourceIds(request.getSourceIds());
        }
        if (request.getScheduleCron() != null) {
            rule.setScheduleCron(request.getScheduleCron());
        }
        if (request.getCooldownMinutes() != null) {
            rule.setCooldownMinutes(request.getCooldownMinutes());
        }
        if (request.getEnabled() != null) {
            rule.setEnabled(request.getEnabled());
        }
        if (request.getConfig() != null) {
            rule.setConfig(request.getConfig());
        }
        if (request.getRemark() != null) {
            rule.setRemark(request.getRemark());
        }
        if (request.getProjectId() != null) {
            rule.setProjectId(request.getProjectId());
        }

        AlertRule saved = alertRuleRepository.save(rule);
        log.info("更新告警规则成功: {}", saved.getId());
        return toResponse(saved);
    }

    /**
     * 删除告警规则
     */
    @Transactional
    public void deleteRule(UUID id) {
        if (!alertRuleRepository.existsById(id)) {
            throw new IllegalArgumentException("规则不存在: " + id);
        }
        alertRuleRepository.deleteById(id);
        log.info("删除告警规则成功: {}", id);
    }

    /**
     * 获取规则详情
     */
    public AlertRuleResponse getRule(UUID id) {
        AlertRule rule = alertRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("规则不存在: " + id));
        return toResponse(rule);
    }

    /**
     * 获取规则实体
     */
    public AlertRule getRuleEntity(UUID id) {
        return alertRuleRepository.findById(id).orElse(null);
    }

    /**
     * 获取所有规则
     */
    public List<AlertRuleResponse> getAllRules() {
        return alertRuleRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 分页查询规则
     */
    public Page<AlertRuleResponse> getRules(Pageable pageable) {
        return alertRuleRepository.findAll(pageable)
                .map(this::toResponse);
    }

    /**
     * 根据项目ID分页查询规则
     */
    public Page<AlertRuleResponse> getRulesByProjectId(UUID projectId, Pageable pageable) {
        return alertRuleRepository.findByProjectId(projectId, pageable)
                .map(this::toResponse);
    }

    /**
     * 获取所有启用的规则
     */
    public List<AlertRule> getEnabledRules() {
        return alertRuleRepository.findByEnabledTrue();
    }

    /**
     * 根据规则类型获取规则
     */
    public List<AlertRuleResponse> getRulesByType(RuleType ruleType) {
        return alertRuleRepository.findByRuleType(ruleType).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 根据告警级别获取规则
     */
    public List<AlertRuleResponse> getRulesByLevel(AlertLevel alertLevel) {
        return alertRuleRepository.findByAlertLevel(alertLevel).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 启用/禁用规则
     */
    @Transactional
    public AlertRuleResponse toggleRule(UUID id) {
        AlertRule rule = alertRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("规则不存在: " + id));

        rule.setEnabled(!rule.getEnabled());
        AlertRule saved = alertRuleRepository.save(rule);
        log.info("切换告警规则状态: {}, enabled={}", id, saved.getEnabled());
        return toResponse(saved);
    }

    /**
     * 检查规则是否在冷却期内
     */
    public boolean isInCooldown(AlertRule rule) {
        if (rule.getLastTriggeredAt() == null) {
            return false;
        }
        LocalDateTime cutoffTime = rule.getLastTriggeredAt()
                .plusMinutes(rule.getCooldownMinutes());
        return LocalDateTime.now().isBefore(cutoffTime);
    }

    /**
     * 更新规则触发信息
     */
    @Transactional
    public void updateTriggerInfo(UUID ruleId) {
        AlertRule rule = alertRuleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("规则不存在: " + ruleId));

        rule.setLastTriggeredAt(LocalDateTime.now());
        rule.setTriggerCountToday(rule.getTriggerCountToday() + 1);

        // 重置每日计数（如果是新的一天）
        LocalDate lastTriggerDate = rule.getLastTriggeredAt().toLocalDate();
        LocalDate today = LocalDate.now();
        if (!lastTriggerDate.equals(today)) {
            rule.setTriggerCountToday(1);
        }

        alertRuleRepository.save(rule);
    }

    /**
     * 重置每日触发计数
     */
    @Transactional
    public void resetDailyTriggerCount() {
        List<AlertRule> rules = alertRuleRepository.findAll();
        for (AlertRule rule : rules) {
            rule.setTriggerCountToday(0);
            alertRuleRepository.save(rule);
        }
        log.info("重置所有规则的每日触发计数");
    }

    /**
     * 转换实体为响应DTO
     */
    private AlertRuleResponse toResponse(AlertRule rule) {
        return AlertRuleResponse.builder()
                .id(rule.getId())
                .name(rule.getName())
                .description(rule.getDescription())
                .ruleType(rule.getRuleType())
                .conditionExpression(rule.getConditionExpression())
                .alertLevel(rule.getAlertLevel())
                .alertTitle(rule.getAlertTitle())
                .alertMessage(rule.getAlertMessage())
                .notificationChannels(rule.getNotificationChannels())
                .sourceIds(rule.getSourceIds())
                .scheduleCron(rule.getScheduleCron())
                .cooldownMinutes(rule.getCooldownMinutes())
                .enabled(rule.getEnabled())
                .status(rule.getStatus())
                .triggerCountToday(rule.getTriggerCountToday())
                .lastTriggeredAt(rule.getLastTriggeredAt())
                .config(rule.getConfig())
                .remark(rule.getRemark())
                .projectId(rule.getProjectId())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
