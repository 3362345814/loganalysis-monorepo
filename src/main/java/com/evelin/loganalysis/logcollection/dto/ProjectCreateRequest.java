package com.evelin.loganalysis.logcollection.dto;

import lombok.Data;

/**
 * 项目创建请求DTO
 *
 * @author Evelin
 */
@Data
public class ProjectCreateRequest {

    /**
     * 项目名称
     */
    private String name;

    /**
     * 项目代码（用于关联日志中的项目标识）
     */
    private String code;

    /**
     * 项目描述
     */
    private String description;

    /**
     * 项目负责人
     */
    private String owner;

    /**
     * 联系人邮箱
     */
    private String email;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 备注
     */
    private String remark;
}
