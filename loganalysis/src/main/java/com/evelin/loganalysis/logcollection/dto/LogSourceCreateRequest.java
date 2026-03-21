package com.evelin.loganalysis.logcollection.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * 日志源创建请求DTO
 *
 * @author Evelin
 */
@Data
public class LogSourceCreateRequest {
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
     * 日志路径列表（JSON格式）
     * SpringBoot: ["path/to/log/file.log"]
     * Nginx: ["path/to/access.log", "path/to/error.log"]
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
     * 密码
     */
    private String password;

    /**
     * 编码
     */
    private String encoding;

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
     * WARN: 聚合 WARN及以上级别
     * ERROR: 聚合 ERROR及以上级别
     */
    private String aggregationLevel;

    /**
     * 启用的脱敏规则ID列表
     */
    private List<String> enabledRuleIds;

    /**
     * 自定义脱敏规则
     */
    private List<CustomRule> customRules;

    /**
     * 额外配置
     */
    private Map<String, Object> config;

    /**
     * 备注
     */
    private String remark;

    /**
     * 所属项目ID
     */
    private UUID projectId;

    /**
     * 自定义脱敏规则
     */
    @Data
    public static class CustomRule {
        private String id;
        private String name;
        private String pattern;
        private String maskType;
        private String replacement;
    }
}
