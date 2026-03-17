package com.evelin.loganalysis.logalert.dto;

import com.evelin.loganalysis.logalert.enums.AlertLevel;
import com.evelin.loganalysis.logalert.enums.RuleType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 告警规则响应DTO
 *
 * @author Evelin
 */
@Data
@Builder
public class AlertRuleResponse {

    private UUID id;
    private String name;
    private String description;
    private RuleType ruleType;
    private String conditionExpression;
    private AlertLevel alertLevel;
    private String alertTitle;
    private String alertMessage;
    private List<String> notificationChannels;
    private List<UUID> sourceIds;
    private String scheduleCron;
    private Integer cooldownMinutes;
    private Boolean enabled;
    private String status;
    private Integer triggerCountToday;
    private LocalDateTime lastTriggeredAt;
    private Map<String, Object> config;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
