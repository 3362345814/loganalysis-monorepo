package com.evelin.loganalysis.logcommon.model;

import com.evelin.loganalysis.logcommon.enums.AlertLevel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

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
        @Index(name = "idx_alert_rule_enabled", columnList = "enabled")
})
public class AlertRule extends BaseEntity {

    /**
     * 规则名称
     */
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    /**
     * 规则描述
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 触发条件（表达式）
     */
    @Column(name = "condition", nullable = false, columnDefinition = "TEXT")
    private String condition;

    /**
     * 告警级别
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "alert_level", nullable = false, length = 20)
    private AlertLevel alertLevel;

    /**
     * 通知渠道（逗号分隔）
     */
    @Column(name = "notification_channels", length = 255)
    private String notificationChannels;

    /**
     * 是否启用
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 冷却时间（分钟）
     */
    @Column(name = "cooldown_minutes", nullable = false)
    @Builder.Default
    private Integer cooldownMinutes = 5;

    /**
     * 额外配置（JSON格式）
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    private Map<String, Object> config;

    /**
     * 备注
     */
    @Column(name = "remark", length = 500)
    private String remark;
}
