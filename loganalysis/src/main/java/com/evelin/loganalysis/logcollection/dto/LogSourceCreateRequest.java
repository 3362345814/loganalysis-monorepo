package com.evelin.loganalysis.logcollection.dto;

import jakarta.validation.constraints.*;
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
    @NotBlank(message = "名称不能为空")
    @Size(max = 100, message = "名称长度不能超过100")
    private String name;

    @Size(max = 500, message = "描述长度不能超过500")
    private String description;

    @NotBlank(message = "日志源类型不能为空")
    private String sourceType;

    private String logFormat;

    @Size(max = 500, message = "日志格式模式长度不能超过500")
    private String logFormatPattern;

    @Size(max = 1000, message = "自定义正则表达式长度不能超过1000")
    private String customPattern;

    @NotEmpty(message = "日志路径不能为空")
    private List<String> paths;

    @Size(max = 255, message = "主机地址长度不能超过255")
    private String host;

    @Min(value = 1, message = "端口号必须大于0")
    @Max(value = 65535, message = "端口号不能超过65535")
    private Integer port;

    @Size(max = 100, message = "用户名长度不能超过100")
    private String username;

    @Size(max = 255, message = "密码长度不能超过255")
    private String password;

    @Size(max = 50, message = "编码长度不能超过50")
    private String encoding;

    private Boolean enabled;

    private Boolean desensitizationEnabled;

    @Size(max = 20, message = "聚合级别长度不能超过20")
    private String aggregationLevel;

    private List<String> enabledRuleIds;

    private List<CustomRule> customRules;

    private Map<String, Object> config;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;

    @NotNull(message = "所属项目ID不能为空")
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
