package com.evelin.loganalysis.logcollection.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;


/**
 * 日志源更新请求DTO
 *
 * @author Evelin
 */
@Data
public class LogSourceUpdateRequest {
    /**
     * 日志源名称
     */
    private String name;

    /**
     * 日志源描述
     */
    private String description;

    /**
     * 日志路径
     */
    private String path;

    /**
     * 主机地址
     */
    private String host;

    /**
     * 端口
     */
    private Integer port;

    /**
     * 日志格式
     */
    private String logFormat;

    /**
     * 自定义日志格式正则表达式
     */
    private String customPattern;

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
     * 启用的脱敏规则ID列表
     */
    private List<String> enabledRuleIds;

    /**
     * 自定义脱敏规则
     */
    private List<LogSourceCreateRequest.CustomRule> customRules;

    /**
     * 额外配置
     */
    private Map<String, Object> config;

    /**
     * 备注
     */
    private String remark;
}
