package com.evelin.loganalysis.logcollection.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ConnectionTestRequest {
    private String host;
    private Integer port;
    private String username;
    private String password;
    private String sourceType;
    private List<String> paths;
    private UUID projectId;
    private Boolean useProjectConnectionConfig;
}
