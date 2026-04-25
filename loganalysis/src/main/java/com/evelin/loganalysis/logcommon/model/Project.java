package com.evelin.loganalysis.logcommon.model;

import com.evelin.loganalysis.logcollection.enums.LogSourceType;
import jakarta.persistence.*;
import lombok.*;

/**
 * 项目实体 - 用于管理多个日志源的分组
 *
 * @author Evelin
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "projects", indexes = {
        @Index(name = "idx_project_name", columnList = "name", unique = true),
        @Index(name = "idx_project_code", columnList = "code", unique = true)
})
public class Project extends BaseEntity {

    /**
     * 项目名称
     */
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    /**
     * 项目代码（用于关联日志中的项目标识）
     */
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    /**
     * 项目描述
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 项目负责人
     */
    @Column(name = "owner", length = 100)
    private String owner;

    /**
     * 联系人邮箱
     */
    @Column(name = "email", length = 100)
    private String email;

    /**
     * 项目默认采集类型（可选）
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "collection_source_type", length = 50)
    private LogSourceType collectionSourceType;

    /**
     * 项目默认 SSH 主机地址
     */
    @Column(name = "ssh_host", length = 255)
    private String sshHost;

    /**
     * 项目默认 SSH 端口
     */
    @Column(name = "ssh_port")
    private Integer sshPort;

    /**
     * 项目默认 SSH 用户名
     */
    @Column(name = "ssh_username", length = 100)
    private String sshUsername;

    /**
     * 项目默认 SSH 密码
     */
    @Column(name = "ssh_password", length = 255)
    private String sshPassword;

    /**
     * 是否启用
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;
}
