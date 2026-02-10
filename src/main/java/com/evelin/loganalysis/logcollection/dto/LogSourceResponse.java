package com.evelin.loganalysis.logcollection.dto;

import lombok.Data;

import java.time.LocalDateTime;
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
     * 编码
     */
    private String encoding;

    /**
     * 是否启用
     */
    private Boolean enabled;

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
}
