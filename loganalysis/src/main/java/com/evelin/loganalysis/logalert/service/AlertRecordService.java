package com.evelin.loganalysis.logalert.service;

import com.evelin.loganalysis.logalert.dto.AlertRecordCreateRequest;
import com.evelin.loganalysis.logalert.dto.AlertRecordResponse;
import com.evelin.loganalysis.logalert.enums.AlertLevel;
import com.evelin.loganalysis.logalert.enums.AlertStatus;
import com.evelin.loganalysis.logalert.model.AlertRecord;
import com.evelin.loganalysis.logalert.model.AlertRule;
import com.evelin.loganalysis.logalert.repository.AlertRecordRepository;
import com.evelin.loganalysis.logcollection.repository.ProjectRepository;
import com.evelin.loganalysis.logcommon.model.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 告警记录服务
 *
 * @author Evelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRecordService {

    private final AlertRecordRepository alertRecordRepository;
    private final AlertRuleService alertRuleService;
    private final ProjectRepository projectRepository;

    private static final String ALERT_ID_PREFIX = "ALT";
    private static final DateTimeFormatter ALERT_ID_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 创建告警记录
     */
    @Transactional
    public AlertRecordResponse createAlert(AlertRecordCreateRequest request, AlertRule rule) {
        String alertId = generateAlertId();

        AlertRecord alert = AlertRecord.builder()
                .alertId(alertId)
                .ruleId(request.getRuleId())
                .aggregationId(request.getAggregationId())
                .sourceIds(request.getSourceIds())
                .alertLevel(rule.getAlertLevel())
                .title(request.getTitle() != null ? request.getTitle() : rule.getAlertTitle())
                .content(request.getContent() != null ? request.getContent() : rule.getAlertMessage())
                .triggerCondition(request.getTriggerCondition())
                .triggerValue(request.getTriggerValue())
                .triggerCount(request.getTriggerCount() != null ? request.getTriggerCount() : 1)
                .triggerSources(request.getTriggerSources())
                .status(AlertStatus.PENDING)
                .priority(rule.getAlertLevel().name())
                .triggeredAt(LocalDateTime.now())
                .assignedTo(request.getAssignedTo())
                .assignedToName(request.getAssignedToName())
                .escalated(false)
                .escalationLevel(0)
                .build();

        AlertRecord saved = alertRecordRepository.save(alert);

        // 更新规则的触发信息
        if (rule != null) {
            alertRuleService.updateTriggerInfo(rule.getId());
        }

        log.info("创建告警记录成功: {}", saved.getAlertId());
        return toResponse(saved);
    }

    /**
     * 创建告警记录（从告警规则）
     */
    @Transactional
    public AlertRecordResponse createAlertFromRule(AlertRule rule, String title, String content,
                                                    String triggerCondition, String triggerValue,
                                                    Integer triggerCount, List<String> triggerSources) {
        return createAlertFromRule(rule, title, content, triggerCondition, triggerValue,
                triggerCount, triggerSources, null, null);
    }

    @Transactional
    public AlertRecordResponse createAlertFromRule(AlertRule rule, String title, String content,
                                                    String triggerCondition, String triggerValue,
                                                    Integer triggerCount, List<String> triggerSources,
                                                    UUID aggregationId, List<UUID> sourceIds) {
        AlertRecordCreateRequest request = new AlertRecordCreateRequest();
        request.setRuleId(rule.getId());
        request.setAggregationId(aggregationId);
        request.setSourceIds(sourceIds);
        request.setTitle(title);
        request.setContent(content);
        request.setTriggerCondition(triggerCondition);
        request.setTriggerValue(triggerValue);
        request.setTriggerCount(triggerCount);
        request.setTriggerSources(triggerSources);

        return createAlert(request, rule);
    }

    /**
     * 确认告警
     */
    @Transactional
    public AlertRecordResponse acknowledgeAlert(UUID id, UUID userId, String userName) {
        AlertRecord alert = alertRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("告警不存在: " + id));

        if (alert.getStatus() != AlertStatus.PENDING) {
            throw new IllegalStateException("只有待处理状态的告警才能确认");
        }

        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedAt(LocalDateTime.now());
        alert.setAcknowledgedBy(userId);

        AlertRecord saved = alertRecordRepository.save(alert);
        log.info("确认告警: {}", saved.getAlertId());
        return toResponse(saved);
    }

    /**
     * 解决告警
     */
    @Transactional
    public AlertRecordResponse resolveAlert(UUID id, UUID userId, String userName, String resolutionNote) {
        AlertRecord alert = alertRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("告警不存在: " + id));

        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedAt(LocalDateTime.now());
        alert.setResolvedBy(userId);
        alert.setResolutionNote(resolutionNote);

        AlertRecord saved = alertRecordRepository.save(alert);
        log.info("解决告警: {}", saved.getAlertId());
        return toResponse(saved);
    }

    /**
     * 分配告警
     */
    @Transactional
    public AlertRecordResponse assignAlert(UUID id, UUID userId, String userName) {
        AlertRecord alert = alertRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("告警不存在: " + id));

        alert.setAssignedTo(userId);
        alert.setAssignedToName(userName);

        AlertRecord saved = alertRecordRepository.save(alert);
        log.info("分配告警: {}, 分配给: {}", saved.getAlertId(), userName);
        return toResponse(saved);
    }

    /**
     * 升级告警
     */
    @Transactional
    public AlertRecordResponse escalateAlert(UUID id) {
        AlertRecord alert = alertRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("告警不存在: " + id));

        alert.setEscalated(true);
        alert.setEscalationLevel(alert.getEscalationLevel() + 1);

        AlertRecord saved = alertRecordRepository.save(alert);
        log.info("升级告警: {}, 升级级别: {}", saved.getAlertId(), saved.getEscalationLevel());
        return toResponse(saved);
    }

    /**
     * 获取告警详情
     */
    public AlertRecordResponse getAlert(UUID id) {
        AlertRecord alert = alertRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("告警不存在: " + id));
        return toResponse(alert);
    }

    /**
     * 根据告警编号获取告警
     */
    public AlertRecordResponse getAlertByAlertId(String alertId) {
        AlertRecord alert = alertRecordRepository.findByAlertId(alertId)
                .orElseThrow(() -> new IllegalArgumentException("告警不存在: " + alertId));
        return toResponse(alert);
    }

    /**
     * 获取所有告警（分页）
     */
    public Page<AlertRecordResponse> getAlerts(Pageable pageable) {
        return alertRecordRepository.findAllByOrderByTriggeredAtDesc(pageable)
                .map(this::toResponse);
    }

    /**
     * 根据项目ID获取告警（分页）
     */
    public Page<AlertRecordResponse> getAlertsByProjectId(UUID projectId, Pageable pageable) {
        return alertRecordRepository.findByProjectId(projectId, null, null, null, null, pageable)
                .map(this::toResponse);
    }

    /**
     * 获取待处理告警
     */
    public List<AlertRecordResponse> getPendingAlerts() {
        return alertRecordRepository.findPendingAlerts().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 获取未解决告警
     */
    public List<AlertRecordResponse> getUnresolvedAlerts() {
        return alertRecordRepository.findUnresolvedAlerts().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 根据状态查询告警
     */
    public Page<AlertRecordResponse> getAlertsByStatus(AlertStatus status, Pageable pageable) {
        return alertRecordRepository.findByStatus(status, pageable)
                .map(this::toResponse);
    }

    /**
     * 根据告警级别查询告警
     */
    public Page<AlertRecordResponse> getAlertsByLevel(AlertLevel level, Pageable pageable) {
        return alertRecordRepository.findByAlertLevel(level, pageable)
                .map(this::toResponse);
    }

    /**
     * 复合条件查询
     */
    public Page<AlertRecordResponse> queryAlerts(String status, String level,
                                                  String ruleId, LocalDateTime startTime,
                                                  LocalDateTime endTime, Pageable pageable) {
        return alertRecordRepository.findByConditions(status, level, ruleId, startTime, endTime, pageable)
                .map(this::toResponse);
    }

    /**
     * 根据项目ID复合条件查询告警
     */
    public Page<AlertRecordResponse> queryAlertsByProjectId(UUID projectId, String status, String level,
                                                            LocalDateTime startTime, LocalDateTime endTime,
                                                            Pageable pageable) {
        return alertRecordRepository.findByProjectId(projectId, status, level, startTime, endTime, pageable)
                .map(this::toResponse);
    }

    /**
     * 生成告警编号
     */
    private String generateAlertId() {
        String dateStr = LocalDate.now().format(ALERT_ID_DATE_FORMAT);

        // 获取今日告警数量
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long todayCount = alertRecordRepository.countByTimeRange(startOfDay, LocalDateTime.now());

        return String.format("%s-%s-%04d", ALERT_ID_PREFIX, dateStr, todayCount + 1);
    }

    /**
     * 转换实体为响应DTO
     */
    private AlertRecordResponse toResponse(AlertRecord alert) {
        AlertRecordResponse.AlertRecordResponseBuilder builder = AlertRecordResponse.builder()
                .id(alert.getId())
                .alertId(alert.getAlertId())
                .ruleId(alert.getRuleId())
                .aggregationId(alert.getAggregationId())
                .sourceIds(alert.getSourceIds())
                .alertLevel(alert.getAlertLevel())
                .title(alert.getTitle())
                .content(alert.getContent())
                .triggerCondition(alert.getTriggerCondition())
                .triggerValue(alert.getTriggerValue())
                .triggerCount(alert.getTriggerCount())
                .triggerSources(alert.getTriggerSources())
                .status(alert.getStatus())
                .priority(alert.getPriority())
                .triggeredAt(alert.getTriggeredAt())
                .acknowledgedAt(alert.getAcknowledgedAt())
                .acknowledgedBy(alert.getAcknowledgedBy())
                .resolvedAt(alert.getResolvedAt())
                .resolvedBy(alert.getResolvedBy())
                .assignedTo(alert.getAssignedTo())
                .assignedToName(alert.getAssignedToName())
                .resolutionNote(alert.getResolutionNote())
                .escalated(alert.getEscalated())
                .escalationLevel(alert.getEscalationLevel())
                .metadata(alert.getMetadata())
                .createdAt(alert.getCreatedAt());

        // 获取关联规则的项目信息
        if (alert.getRuleId() != null) {
            try {
                AlertRule rule = alertRuleService.getRuleEntity(alert.getRuleId());
                if (rule != null && rule.getProjectId() != null) {
                    builder.projectId(rule.getProjectId());
                    projectRepository.findById(rule.getProjectId())
                            .ifPresent(project -> builder.projectName(project.getName()));
                }
            } catch (Exception e) {
                log.debug("获取告警项目信息失败: {}", e.getMessage());
            }
        }

        return builder.build();
    }
}
