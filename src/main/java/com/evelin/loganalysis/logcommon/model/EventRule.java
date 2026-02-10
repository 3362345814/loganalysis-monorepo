package com.evelin.loganalysis.logcommon.model;

import com.evelin.loganalysis.logcommon.enums.RuleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * 事件规则实体
 *
 * @author Evelin
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "event_rules", indexes = {
        @Index(name = "idx_event_rule_name", columnList = "name", unique = true),
        @Index(name = "idx_event_rule_enabled", columnList = "enabled")
})
public class EventRule extends BaseEntity {

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
     * 适用日志源类型
     */
    @Column(name = "source_type", length = 50)
    private String sourceType;

    /**
     * 规则类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 50)
    private RuleType ruleType;

    /**
     * 匹配模式
     */
    @Column(name = "pattern", nullable = false, columnDefinition = "TEXT")
    private String pattern;

    /**
     * 目标日志级别
     */
    @Column(name = "target_level", length = 20)
    private String targetLevel;

    /**
     * 优先级（数值越大优先级越高）
     */
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 0;

    /**
     * 额外配置（JSON格式）
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    private Map<String, Object> config;

    /**
     * 是否启用
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 备注
     */
    @Column(name = "remark", length = 500)
    private String remark;
}
