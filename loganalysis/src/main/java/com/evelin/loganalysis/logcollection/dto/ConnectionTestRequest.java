package com.evelin.loganalysis.logcollection.dto;

import lombok.Data;

import java.util.List;

@Data
public class ConnectionTestRequest {
    private String host;
    private Integer port;
    private String username;
    private String password;
    private String sourceType;
    private List<String> paths;
}
