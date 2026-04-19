package com.evelin.loganalysis.logcollection.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class Log4jExtractTestResponse {
    private boolean matched;
    private String message;
    private String logTime;
    private String logLevel;
    private String threadName;
    private String loggerName;
    private String content;
    private String traceId;
    private String traceFieldName;
    private String stackTrace;
    private Map<String, Object> parsedFields;
}
