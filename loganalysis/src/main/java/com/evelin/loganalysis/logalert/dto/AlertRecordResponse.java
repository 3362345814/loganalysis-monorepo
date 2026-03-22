package com.evelin.loganalysis.logalert.dto;

import com.evelin.loganalysis.logalert.enums.AlertLevel;
import com.evelin.loganalysis.logalert.enums.AlertStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 告警记录响应DTO
 *
 * @author Evelin
 */
@Data
@Builder
public class AlertRecordResponse {

    private UUID id;
    private String alertId;
    private UUID ruleId;
    private String ruleName;
    private UUID aggregationId;
    private String logId;
    private String traceId;
    private List<UUID> sourceIds;
    private AlertLevel alertLevel;
    private String title;
    private String content;
    private String triggerCondition;
    private String triggerValue;
    private Integer triggerCount;
    private List<String> triggerSources;
    private AlertStatus status;
    private String priority;
    private LocalDateTime triggeredAt;
    private LocalDateTime acknowledgedAt;
    private UUID acknowledgedBy;
    private String acknowledgedByName;
    private LocalDateTime resolvedAt;
    private UUID resolvedBy;
    private String resolvedByName;
    private UUID assignedTo;
    private String assignedToName;
    private String resolutionNote;
    private Boolean escalated;
    private Integer escalationLevel;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;

    /**
     * 所属项目ID
     */
    private UUID projectId;

    /**
     * 所属项目名称
     */
    private String projectName;
}
