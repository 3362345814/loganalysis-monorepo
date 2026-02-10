package com.evelin.loganalysis.logcollection.dto;

import lombok.Data;

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
     * 额外配置
     */
    private Map<String, Object> config;

    /**
     * 备注
     */
    private String remark;
}
