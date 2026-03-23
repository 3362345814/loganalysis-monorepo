package com.evelin.loganalysis.loganalysisai.config.dto;

import lombok.Data;

/**
 * LLM 配置 DTO
 *
 * @author Evelin
 */
@Data
public class LlmConfigDTO {
    
    private String id;
    
    /**
     * 配置名称
     */
    private String name;
    
    /**
     * API Key (仅在创建/更新时使用，查询时不返回)
     */
    private String apiKey;
    
    /**
     * 使用的模型
     */
    private String model;
    
    /**
     * 最大 Token 数
     */
    private Integer maxTokens;
    
    /**
     * 温度参数
     */
    private Double temperature;
    
    /**
     * 请求超时时间（秒）
     */
    private Integer timeout;
    
    /**
     * API 端点
     */
    private String endpoint;
    
    /**
     * 是否启用
     */
    private Boolean enabled;
    
    /**
     * 是否为默认配置
     */
    private Boolean isDefault;
    
    /**
     * 备注
     */
    private String remark;

    /**
     * API Key 脱敏显示（仅在查询时返回，前端用于判断是否有保存的 Key）
     * 格式: "******" + 原始 Key 后4位
     */
    private String maskedApiKey;
}
