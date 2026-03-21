package com.evelin.loganalysis.logcollection.model;

import com.evelin.loganalysis.logcollection.enums.LogFormat;
import com.evelin.loganalysis.logcollection.enums.LogSourceType;
import com.evelin.loganalysis.logcommon.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 日志源实体
 *
 * @author Evelin
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "log_sources")
public class LogSource extends BaseEntity {

    /**
     * 日志源名称
     */
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    /**
     * 日志源描述
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 日志源类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private LogSourceType sourceType;

    /**
     * 日志格式
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "log_format", length = 50)
    private LogFormat logFormat;

    /**
     * 用户自定义的 log_format 字符串（用于 NGINX_ACCESS 等格式）
     * 例如: $remote_addr - $remote_user [$time_local] "$request" $status $body_bytes_sent
     */
    @Column(name = "log_format_pattern", length = 1000)
    private String logFormatPattern;

    /**
     * 自定义日志格式正则表达式（当 logFormat 为 CUSTOM 时使用）
     */
    @Column(name = "custom_pattern", length = 500)
    private String customPattern;

    /**
     * 日志路径（JSON格式存储，支持多种日志类型）
     * 
     * 结构设计（未来可扩展添加新的日志类型）：
     * 
     * SpringBoot日志 - 单个路径：
     * {
     *   "type": "SPRING_BOOT",
     *   "paths": ["/var/log/myapp/application.log"]
     * }
     * 
     * Nginx日志 - 两个路径（access和error）+ 日志格式：
     * {
     *   "type": "NGINX",
     *   "paths": ["/var/log/nginx/access.log", "/var/log/nginx/error.log"],
     *   "logFormatPattern": "$remote_addr - $remote_user [$time_local] \"$request\" $status"
     * }
     * 
     * Generic/其他日志类型：
     * {
     *   "type": "LOCAL_FILE",
     *   "paths": ["/var/log/app.log"]
     * }
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "paths", columnDefinition = "jsonb")
    private Map<String, Object> paths;

    /**
     * 主机地址（远程采集时使用）
     */
    @Column(name = "host", length = 255)
    private String host;

    /**
     * 端口
     */
    @Column(name = "port")
    private Integer port;

    /**
     * 用户名
     */
    @Column(name = "username", length = 100)
    private String username;

    /**
     * 密码
     */
    @Column(name = "password", length = 255)
    private String password;

    /**
     * 编码
     */
    @Column(name = "encoding", length = 20)
    @Builder.Default
    private String encoding = "UTF-8";

    /**
     * 是否启用
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 采集状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private com.evelin.loganalysis.logcollection.enums.CollectionStatus status =
            com.evelin.loganalysis.logcollection.enums.CollectionStatus.STOPPED;

    /**
     * 额外配置（JSON格式）
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    private Map<String, Object> config;

    /**
     * 最后采集时间
     */
    @Column(name = "last_collection_time")
    private LocalDateTime lastCollectionTime;

    /**
     * 最后心跳时间
     */
    @Column(name = "last_heartbeat_time")
    private LocalDateTime lastHeartbeatTime;

    /**
     * 备注
     */
    @Column(name = "remark", length = 500)
    private String remark;

    /**
     * 所属项目ID
     */
    @Column(name = "project_id")
    private UUID projectId;

    /**
     * 是否启用脱敏
     */
    @Column(name = "desensitization_enabled", nullable = false)
    @Builder.Default
    private Boolean desensitizationEnabled = false;

    /**
     * 聚合级别配置：只有等于或高于此级别的日志才会被聚合
     * WARN: 聚合 WARN及以上级别（ WARN, ERROR, FATAL）
     * ERROR: 聚合 ERROR及以上级别（ ERROR, FATAL）
     * 不配置则聚合所有级别
     */
    @Column(name = "aggregation_level", length = 20)
    private String aggregationLevel;

    /**
     * 启用的脱敏规则ID列表
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "enabled_rule_ids", columnDefinition = "jsonb")
    private List<String> enabledRuleIds;

    /**
     * 自定义脱敏规则（JSON格式）
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_rules", columnDefinition = "jsonb")
    private List<CustomDesensitizeRule> customRules;

    /**
     * 自定义脱敏规则
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomDesensitizeRule {
        /**
         * 规则ID
         */
        private String id;

        /**
         * 规则名称
         */
        private String name;

        /**
         * 正则表达式
         */
        private String pattern;

        /**
         * 脱敏类型：FULL/PARTIAL/HASH
         */
        private String maskType;

        /**
         * 替换内容
         */
        private String replacement;
    }

    @SuppressWarnings("unchecked")
    public String getPath() {
        if (this.paths == null) {
            return null;
        }
        Object pathsObj = this.paths.get("paths");
        if (pathsObj instanceof List && ((List<?>) pathsObj).size() > 0) {
            return (String) ((List<?>) pathsObj).get(0);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public String getFilePattern() {
        if (this.paths == null) {
            return null;
        }
        Object pathsObj = this.paths.get("paths");
        if (pathsObj instanceof List && ((List<?>) pathsObj).size() > 1) {
            List<String> pathList = (List<String>) pathsObj;
            return String.join(",", pathList.subList(1, pathList.size()));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public List<String> getPathsList() {
        if (this.paths == null) {
            return null;
        }
        Object pathsObj = this.paths.get("paths");
        if (pathsObj instanceof List) {
            return (List<String>) pathsObj;
        }
        return null;
    }
}
