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

    private Boolean enabled;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
