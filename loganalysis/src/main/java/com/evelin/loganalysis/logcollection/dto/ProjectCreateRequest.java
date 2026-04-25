package com.evelin.loganalysis.logcollection.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ProjectCreateRequest {

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 100, message = "项目名称长度不能超过100")
    private String name;

    @Size(max = 500, message = "项目描述长度不能超过500")
    private String description;

    @Size(max = 100, message = "负责人名称长度不能超过100")
    private String owner;

    @Email(message = "邮箱格式不正确")
    @Size(max = 255, message = "邮箱长度不能超过255")
    private String email;

    @Size(max = 50, message = "采集类型长度不能超过50")
    private String collectionSourceType;

    @Size(max = 255, message = "SSH主机地址长度不能超过255")
    private String sshHost;

    @Min(value = 1, message = "SSH端口号必须大于0")
    @Max(value = 65535, message = "SSH端口号不能超过65535")
    private Integer sshPort;

    @Size(max = 100, message = "SSH用户名长度不能超过100")
    private String sshUsername;

    @Size(max = 255, message = "SSH密码长度不能超过255")
    private String sshPassword;

    private Boolean enabled;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
