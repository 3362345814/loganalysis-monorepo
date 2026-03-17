package com.evelin.loganalysis.logalert.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 告警记录创建请求DTO
 *
 * @author Evelin
 */
@Data
public class AlertRecordCreateRequest {

    private UUID ruleId;
    private UUID aggregationId;
    private List<UUID> sourceIds;
    private String title;
    private String content;
    private String triggerCondition;
    private String triggerValue;
    private Integer triggerCount;
    private List<String> triggerSources;
    private UUID assignedTo;
    private String assignedToName;
}
