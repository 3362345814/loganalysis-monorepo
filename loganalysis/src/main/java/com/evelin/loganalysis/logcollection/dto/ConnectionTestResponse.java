package com.evelin.loganalysis.logcollection.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ConnectionTestResponse {
    private boolean success;
    private String message;
    private String host;
    private Integer port;
    private String username;
    private String sourceType;
    private List<String> paths;
    private Map<String, Boolean> pathResults;
    private Integer existingCount;
    private Integer missingCount;
    private List<String> missingPaths;
}
