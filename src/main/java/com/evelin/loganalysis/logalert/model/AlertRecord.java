package com.evelin.loganalysis.logalert.model;

import com.evelin.loganalysis.logalert.enums.AlertLevel;
import com.evelin.loganalysis.logalert.enums.AlertStatus;
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
 * 告警记录实体
 *
 * @author Evelin
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "alert_records", indexes = {
        @Index(name = "idx_alert_record_alert_id", columnList = "alert_id", unique = true),
        @Index(name = "idx_alert_record_rule_id", columnList = "rule_id"),
        @Index(name = "idx_alert_record_status", columnList = "status"),
        @Index(name = "idx_alert_record_triggered_at", columnList = "triggered_at"),
        @Index(name = "idx_alert_record_alert_level", columnList = "alert_level"),
        @Index(name = "idx_alert_record_priority", columnList = "priority")
})
public class AlertRecord extends BaseEntity {

    /**
     * 告警编号（如：ALT-20240316-001）
     */
    @Column(name = "alert_id", nullable = false, unique = true, length = 100)
    private String alertId;

    /**
     * 关联的告警规则ID
     */
    @Column(name = "rule_id")
    private UUID ruleId;

    /**
     * 关联的聚合组ID
     */
    @Column(name = "aggregation_id")
    private UUID aggregationId;

    /**
     * 关联的日志源ID列表
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_ids", columnDefinition = "jsonb")
    private List<UUID> sourceIds;

    /**
     * 告警级别
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "alert_level", nullable = false, length = 20)
    private AlertLevel alertLevel;

    /**
     * 告警标题
     */
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    /**
     * 告警内容
     */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /**
     * 触发条件描述
     */
    @Column(name = "trigger_condition", columnDefinition = "TEXT")
    private String triggerCondition;

    /**
     * 触发时的值
     */
    @Column(name = "trigger_value", length = 500)
    private String triggerValue;

    /**
     * 触发次数
     */
    @Column(name = "trigger_count", nullable = false)
    @Builder.Default
    private Integer triggerCount = 0;

    /**
     * 触发来源列表
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_sources", columnDefinition = "jsonb")
    private List<String> triggerSources;

    /**
     * 告警状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AlertStatus status = AlertStatus.PENDING;

    /**
     * 优先级：CRITICAL, HIGH, NORMAL, LOW
     */
    @Column(name = "priority", length = 20)
    @Builder.Default
    private String priority = "NORMAL";

    /**
     * 触发时间
     */
    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;

    /**
     * 确认时间
     */
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    /**
     * 确认人ID
     */
    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    /**
     * 解决时间
     */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /**
     * 解决人ID
     */
    @Column(name = "resolved_by")
    private UUID resolvedBy;

    /**
     * 分配给的用户ID
     */
    @Column(name = "assigned_to")
    private UUID assignedTo;

    /**
     * 分配给的用户名称
     */
    @Column(name = "assigned_to_name", length = 100)
    private String assignedToName;

    /**
     * 解决备注
     */
    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    /**
     * 是否已升级
     */
    @Column(name = "escalated", nullable = false)
    @Builder.Default
    private Boolean escalated = false;

    /**
     * 升级级别
     */
    @Column(name = "escalation_level", nullable = false)
    @Builder.Default
    private Integer escalationLevel = 0;

    /**
     * 额外元数据
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
