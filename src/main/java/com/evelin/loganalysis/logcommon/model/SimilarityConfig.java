package com.evelin.loganalysis.logcommon.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * 相似度配置实体
 *
 * @author Evelin
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "similarity_configs")
public class SimilarityConfig extends BaseEntity {

    /**
     * 配置名称
     */
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    /**
     * 描述
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 相似度阈值
     */
    @Column(name = "threshold", nullable = false)
    private Float threshold;

    /**
     * 算法类型
     */
    @Column(name = "algorithm", nullable = false, length = 50)
    private String algorithm;

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
}
