package com.evelin.loganalysis.logcollection.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 项目响应DTO
 *
 * @author Evelin
 */
@Data
public class ProjectResponse {

    private UUID id;
    private String name;
    private String code;
    private String description;
    private String owner;
    private String email;
    private String collectionSourceType;
    private String sshHost;
    private Integer sshPort;
    private String sshUsername;
    private String sshPassword;
    private Boolean sshPasswordConfigured;
    private Boolean enabled;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
