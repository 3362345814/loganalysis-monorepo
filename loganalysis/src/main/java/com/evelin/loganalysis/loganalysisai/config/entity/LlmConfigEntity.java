package com.evelin.loganalysis.loganalysisai.config.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * LLM 配置实体
 *
 * @author Evelin
 */
@Data
@Entity
@Table(name = "llm_config")
public class LlmConfigEntity {
    
    @Id
    @Column(name = "id", length = 50)
    private String id;
    
    /**
     * 配置名称
     */
    @Column(name = "name", length = 100, nullable = false)
    private String name;
    
    /**
     * API Key
     */
    @Column(name = "api_key", length = 500)
    private String apiKey;
    
    /**
     * 使用的模型
     */
    @Column(name = "model", length = 100)
    private String model;
    
    /**
     * 最大 Token 数
     */
    @Column(name = "max_tokens")
    private Integer maxTokens;
    
    /**
     * 温度参数
     */
    @Column(name = "temperature")
    private Double temperature;
    
    /**
     * 请求超时时间（秒）
     */
    @Column(name = "timeout")
    private Integer timeout;
    
    /**
     * API 端点（可选，用于代理或自定义）
     */
    @Column(name = "endpoint", length = 500)
    private String endpoint;
    
    /**
     * 是否启用
     */
    @Column(name = "enabled")
    private Boolean enabled;
    
    /**
     * 是否为默认配置
     */
    @Column(name = "is_default")
    private Boolean isDefault;
    
    /**
     * 备注
     */
    @Column(name = "remark", length = 500)
    private String remark;
    
    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (enabled == null) {
            enabled = true;
        }
        if (isDefault == null) {
            isDefault = false;
        }
    }
    
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
