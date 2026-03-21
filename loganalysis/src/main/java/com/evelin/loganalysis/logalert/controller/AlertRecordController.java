package com.evelin.loganalysis.logalert.controller;

import com.evelin.loganalysis.logalert.dto.AlertRecordResponse;
import com.evelin.loganalysis.logalert.service.AlertRecordService;
import com.evelin.loganalysis.logalert.enums.AlertLevel;
import com.evelin.loganalysis.logalert.enums.AlertStatus;
import com.evelin.loganalysis.logcommon.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 告警记录控制器
 *
 * @author Evelin
 */
@RestController
@RequestMapping("/api/v1/alert/records")
@RequiredArgsConstructor
public class AlertRecordController {

    private final AlertRecordService alertRecordService;

    /**
     * 获取告警详情
     */
    @GetMapping("/{id}")
    public Result<AlertRecordResponse> getAlert(@PathVariable UUID id) {
        AlertRecordResponse response = alertRecordService.getAlert(id);
        return Result.success(response);
    }

    /**
     * 根据告警编号获取告警
     */
    @GetMapping("/no/{alertId}")
    public Result<AlertRecordResponse> getAlertByAlertId(@PathVariable String alertId) {
        AlertRecordResponse response = alertRecordService.getAlertByAlertId(alertId);
        return Result.success(response);
    }

    /**
     * 获取所有告警（分页）
     */
    @GetMapping
    public Result<Page<AlertRecordResponse>> getAlerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "triggeredAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) UUID projectId) {
        Pageable pageable = PageRequest.of(page, size,
                sortDir.equalsIgnoreCase("asc") ?
                        org.springframework.data.domain.Sort.by(sortBy).ascending() :
                        org.springframework.data.domain.Sort.by(sortBy).descending());
        Page<AlertRecordResponse> response;
        if (projectId != null) {
            response = alertRecordService.getAlertsByProjectId(projectId, pageable);
        } else {
            response = alertRecordService.getAlerts(pageable);
        }
        return Result.success(response);
    }

    /**
     * 获取待处理告警
     */
    @GetMapping("/pending")
    public Result<List<AlertRecordResponse>> getPendingAlerts() {
        List<AlertRecordResponse> response = alertRecordService.getPendingAlerts();
        return Result.success(response);
    }

    /**
     * 获取未解决告警
     */
    @GetMapping("/unresolved")
    public Result<List<AlertRecordResponse>> getUnresolvedAlerts() {
        List<AlertRecordResponse> response = alertRecordService.getUnresolvedAlerts();
        return Result.success(response);
    }

    /**
     * 根据状态查询告警
     */
    @GetMapping("/status/{status}")
    public Result<Page<AlertRecordResponse>> getAlertsByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AlertRecordResponse> response = alertRecordService.getAlertsByStatus(
                AlertStatus.valueOf(status.toUpperCase()), pageable);
        return Result.success(response);
    }

    /**
     * 根据告警级别查询告警
     */
    @GetMapping("/level/{level}")
    public Result<Page<AlertRecordResponse>> getAlertsByLevel(
            @PathVariable String level,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AlertRecordResponse> response = alertRecordService.getAlertsByLevel(
                AlertLevel.valueOf(level.toUpperCase()), pageable);
        return Result.success(response);
    }

    /**
     * 复合条件查询
     */
    @GetMapping("/query")
    public Result<Page<AlertRecordResponse>> queryAlerts(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) UUID ruleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);

        String statusStr = status != null ? status.toUpperCase() : null;
        String levelStr = level != null ? level.toUpperCase() : null;

        Page<AlertRecordResponse> response;
        if (projectId != null) {
            String ruleIdStr = ruleId != null ? ruleId.toString() : null;
            response = alertRecordService.queryAlertsByProjectId(projectId, statusStr, levelStr, startTime, endTime, pageable);
        } else {
            String ruleIdStr = ruleId != null ? ruleId.toString() : null;
            response = alertRecordService.queryAlerts(statusStr, levelStr, ruleIdStr, startTime, endTime, pageable);
        }
        return Result.success(response);
    }

    /**
     * 确认告警
     */
    @PutMapping("/{id}/acknowledge")
    public Result<AlertRecordResponse> acknowledgeAlert(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false, defaultValue = "System") String userName) {
        AlertRecordResponse response = alertRecordService.acknowledgeAlert(id, userId, userName);
        return Result.success(response);
    }

    /**
     * 解决告警
     */
    @PutMapping("/{id}/resolve")
    public Result<AlertRecordResponse> resolveAlert(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false, defaultValue = "System") String userName,
            @RequestParam(required = false) String resolutionNote) {
        AlertRecordResponse response = alertRecordService.resolveAlert(id, userId, userName, resolutionNote);
        return Result.success(response);
    }

    /**
     * 分配告警
     */
    @PutMapping("/{id}/assign")
    public Result<AlertRecordResponse> assignAlert(
            @PathVariable UUID id,
            @RequestParam UUID userId,
            @RequestParam String userName) {
        AlertRecordResponse response = alertRecordService.assignAlert(id, userId, userName);
        return Result.success(response);
    }

    /**
     * 升级告警
     */
    @PutMapping("/{id}/escalate")
    public Result<AlertRecordResponse> escalateAlert(@PathVariable UUID id) {
        AlertRecordResponse response = alertRecordService.escalateAlert(id);
        return Result.success(response);
    }
}
