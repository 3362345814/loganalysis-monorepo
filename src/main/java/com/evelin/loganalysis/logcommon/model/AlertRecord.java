package com.evelin.loganalysis.logcommon.model;

import com.evelin.loganalysis.logcommon.enums.AlertLevel;
import com.evelin.loganalysis.logcommon.enums.AlertStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
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
@Table(name = "alert_record", indexes = {
        @Index(name = "idx_alert_record_alert_id", columnList = "alert_id", unique = true),
        @Index(name = "idx_alert_record_rule_id", columnList = "rule_id"),
        @Index(name = "idx_alert_record_status", columnList = "status"),
        @Index(name = "idx_alert_record_triggered_at", columnList = "triggered_at")
})
public class AlertRecord extends BaseEntity {

    /**
     * 告警规则ID
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    private AlertRule ruleId;

    /**
     * 告警ID（业务ID）
     */
    @Column(name = "alert_id", nullable = false, unique = true, length = 50)
    private String alertId;

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
     * 状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AlertStatus status = AlertStatus.PENDING;

    /**
     * 元数据（JSON格式）
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    /**
     * 触发时间
     */
    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;

    /**
     * 解决时间
     */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /**
     * 触发人ID
     */
    @Column(name = "triggered_by")
    private UUID triggeredBy;

    /**
     * 解决人ID
     */
    @Column(name = "resolved_by")
    private UUID resolvedBy;

    /**
     * 解决备注
     */
    @Column(name = "resolve_remark", length = 500)
    private String resolveRemark;
}
