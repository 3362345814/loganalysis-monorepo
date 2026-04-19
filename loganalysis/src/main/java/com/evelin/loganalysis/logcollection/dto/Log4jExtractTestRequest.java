package com.evelin.loganalysis.logcollection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class Log4jExtractTestRequest {

    @NotBlank(message = "日志格式不能为空")
    @Size(max = 500, message = "日志格式长度不能超过500")
    private String pattern;

    @NotBlank(message = "示例日志不能为空")
    @Size(max = 10000, message = "示例日志长度不能超过10000")
    private String sampleLog;

    @Size(max = 100, message = "追踪字段名长度不能超过100")
    private String traceFieldName;
}
