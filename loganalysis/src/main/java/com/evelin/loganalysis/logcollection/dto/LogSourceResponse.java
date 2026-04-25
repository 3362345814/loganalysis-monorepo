package com.evelin.loganalysis.logcollection.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


/**
 * 日志源响应DTO
 *
 * @author Evelin
 */
@Data
public class LogSourceResponse {
    /**
     * 日志源ID
     */
    private UUID id;

    /**
     * 日志源名称
     */
    private String name;

    /**
     * 日志源描述
     */
    private String description;

    /**
     * 日志源类型
     */
    private String sourceType;

    /**
     * 是否沿用所属项目的采集连接配置
     */
    private Boolean useProjectConnectionConfig;

    /**
     * 日志格式
     */
    private String logFormat;

    /**
     * 用户自定义的 log_format 字符串（用于 NGINX_ACCESS 等格式）
     */
    private String logFormatPattern;

    /**
     * 自定义日志格式正则表达式
     */
    private String customPattern;

    /**
     * 额外配置（JSON格式）
     */
    private java.util.Map<String, Object> config;

    /**
     * 日志路径列表（JSON格式）
     */
    private List<String> paths;

    /**
     * 主机地址
     */
    private String host;

    /**
     * 端口
     */
    private Integer port;

    /**
     * 用户名
     */
    private String username;

    /**
     * 编码
     */
    private String encoding;

    /**
     * SSH 密码掩码（仅用于前端展示占位，不返回明文）
     */
    private String password;

    /**
     * 是否已配置 SSH 密码
     */
    private Boolean passwordConfigured;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 是否启用脱敏
     */
    private Boolean desensitizationEnabled;

    /**
     * 聚合级别配置：只有等于或高于此级别的日志才会被聚合
     */
    private String aggregationLevel;

    /**
     * 启用的脱敏规则ID列表
     */
    private List<String> enabledRuleIds;

    /**
     * 自定义脱敏规则
     */
    private List<LogSourceCreateRequest.CustomRule> customRules;

    /**
     * 采集状态
     */
    private String status;

    /**
     * 最后采集时间
     */
    private LocalDateTime lastCollectionTime;

    /**
     * 最后心跳时间
     */
    private LocalDateTime lastHeartbeatTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 备注
     */
    private String remark;

    /**
     * 所属项目ID
     */
    private UUID projectId;

    /**
     * 所属项目名称
     */
    private String projectName;
}
