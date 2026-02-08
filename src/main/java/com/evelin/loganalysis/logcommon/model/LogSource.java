package com.evelin.loganalysis.logcommon.model;

import com.evelin.loganalysis.logcommon.enums.CollectionStatus;
import com.evelin.loganalysis.logcommon.enums.LogSourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

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
@Table(name = "log_source")
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
     * 日志路径（文件路径、URL等）
     */
    @Column(name = "path", nullable = false, length = 500)
    private String path;

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
    private com.evelin.loganalysis.logcommon.enums.CollectionStatus status =
            com.evelin.loganalysis.logcommon.enums.CollectionStatus.STOPPED;

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
}
