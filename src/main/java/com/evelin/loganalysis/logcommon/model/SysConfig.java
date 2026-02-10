package com.evelin.loganalysis.logcommon.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

/**
 * 系统配置实体
 *
 * @author Evelin
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "system_configs", indexes = {
        @Index(name = "idx_sys_config_key", columnList = "config_key", unique = true)
})
public class SysConfig extends BaseEntity {

    /**
     * 配置键
     */
    @Column(name = "config_key", nullable = false, unique = true, length = 100)
    private String configKey;

    /**
     * 配置值
     */
    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    /**
     * 配置类型
     */
    @Column(name = "config_type", length = 50)
    private String configType;

    /**
     * 配置描述
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 分类
     */
    @Column(name = "category", length = 50)
    private String category;

    /**
     * 是否可编辑
     */
    @Column(name = "editable", nullable = false)
    @Builder.Default
    private Boolean editable = true;
}
