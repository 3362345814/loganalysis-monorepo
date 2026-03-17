package com.evelin.loganalysis.logalert.dto;

import com.evelin.loganalysis.logalert.enums.AlertLevel;
import com.evelin.loganalysis.logalert.enums.RuleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 告警规则创建请求DTO
 *
 * @author Evelin
 */
@Data
public class AlertRuleCreateRequest {

    /**
     * 规则名称
     */
    @NotBlank(message = "规则名称不能为空")
    private String name;

    /**
     * 规则描述
     */
    private String description;

    /**
     * 规则类型
     */
    @NotNull(message = "规则类型不能为空")
    private RuleType ruleType;

    /**
     * 触发条件表达式
     */
    @NotBlank(message = "触发条件不能为空")
    private String conditionExpression;

    /**
     * 告警级别
     */
    @NotNull(message = "告警级别不能为空")
    private AlertLevel alertLevel;

    /**
     * 告警标题
     */
    private String alertTitle;

    /**
     * 告警消息模板
     */
    private String alertMessage;

    /**
     * 通知渠道列表
     */
    private List<String> notificationChannels;

    /**
     * 关联的日志源ID列表
     */
    private List<UUID> sourceIds;

    /**
     * Cron调度表达式
     */
    private String scheduleCron;

    /**
     * 冷却时间（分钟）
     */
    private Integer cooldownMinutes;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 额外配置
     */
    private Map<String, Object> config;

    /**
     * 备注
     */
    private String remark;
}
