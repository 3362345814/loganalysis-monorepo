package com.evelin.loganalysis.logalert.dto;

import com.evelin.loganalysis.logalert.enums.AlertLevel;
import com.evelin.loganalysis.logalert.enums.RuleType;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 告警规则更新请求DTO
 *
 * @author Evelin
 */
@Data
public class AlertRuleUpdateRequest {

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
    private Map<String, Object> config;
    private String remark;
}
