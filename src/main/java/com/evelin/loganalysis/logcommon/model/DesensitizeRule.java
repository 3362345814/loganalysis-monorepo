package com.evelin.loganalysis.logcommon.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 脱敏规则实体
 *
 * @author Evelin
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "desensitize_rule", indexes = {
        @Index(name = "idx_desensitize_rule_name", columnList = "name", unique = true),
        @Index(name = "idx_desensitize_rule_enabled", columnList = "enabled")
})
public class DesensitizeRule extends BaseEntity {

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
     * 模式类型（REGEX、GLOB等）
     */
    @Column(name = "pattern_type", nullable = false, length = 50)
    private String patternType;

    /**
     * 匹配模式
     */
    @Column(name = "pattern", nullable = false, columnDefinition = "TEXT")
    private String pattern;

    /**
     * 替换内容
     */
    @Column(name = "replacement", length = 255)
    private String replacement;

    /**
     * 优先级（数值越大优先级越高）
     */
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 0;

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
