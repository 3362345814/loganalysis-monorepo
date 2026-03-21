package com.evelin.loganalysis.logalert.model;

import com.evelin.loganalysis.logalert.enums.AlertLevel;
import com.evelin.loganalysis.logalert.enums.RuleType;
import com.evelin.loganalysis.logcommon.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 告警规则实体
 *
 * @author Evelin
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "alert_rules", indexes = {
        @Index(name = "idx_alert_rule_name", columnList = "name", unique = true),
        @Index(name = "idx_alert_rule_enabled", columnList = "enabled"),
        @Index(name = "idx_alert_rule_rule_type", columnList = "rule_type"),
        @Index(name = "idx_alert_rule_status", columnList = "status"),
        @Index(name = "idx_alert_rule_alert_level", columnList = "alert_level")
})
public class AlertRule extends BaseEntity {

    /**
     * 规则名称
     */
    @Column(name = "name", nullable = false, unique = true, length = 255)
    private String name;

    /**
     * 规则描述
     */
    @Column(name = "description", length = 1000)
    private String description;

    /**
     * 规则类型：KEYWORD, REGEX, LEVEL, THRESHOLD, COMBINATION
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 50)
    private RuleType ruleType;

    /**
     * 触发条件表达式
     */
    @Column(name = "condition_expression", nullable = false, columnDefinition = "TEXT")
    private String conditionExpression;

    /**
     * 告警级别
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "alert_level", nullable = false, length = 20)
    private AlertLevel alertLevel;

    /**
     * 告警标题
     */
    @Column(name = "alert_title", length = 255)
    private String alertTitle;

    /**
     * 告警消息模板
     */
    @Column(name = "alert_message", columnDefinition = "TEXT")
    private String alertMessage;

    /**
     * 通知渠道列表
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification_channels", columnDefinition = "jsonb")
    private List<String> notificationChannels;

    /**
     * 关联的日志源ID列表
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_ids", columnDefinition = "jsonb")
    private List<UUID> sourceIds;

    /**
     * Cron调度表达式（用于定时检查）
     */
    @Column(name = "schedule_cron", length = 100)
    private String scheduleCron;

    /**
     * 冷却时间（分钟），防止重复告警
     */
    @Column(name = "cooldown_minutes", nullable = false)
    @Builder.Default
    private Integer cooldownMinutes = 10;

    /**
     * 是否启用
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 规则状态：ACTIVE, INACTIVE, ERROR
     */
    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    /**
     * 今日触发次数
     */
    @Column(name = "trigger_count_today", nullable = false)
    @Builder.Default
    private Integer triggerCountToday = 0;

    /**
     * 上次触发时间
     */
    @Column(name = "last_triggered_at")
    private LocalDateTime lastTriggeredAt;

    /**
     * 额外配置（JSON格式）
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    private Map<String, Object> config;

    /**
     * 所属项目ID
     */
    @Column(name = "project_id")
    private UUID projectId;
}
