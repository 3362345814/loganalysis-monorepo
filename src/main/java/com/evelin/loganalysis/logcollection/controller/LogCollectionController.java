package com.evelin.loganalysis.logcollection.controller;

import com.evelin.loganalysis.logcollection.collector.CollectorFactory;
import com.evelin.loganalysis.logcollection.collector.LogCollector;
import com.evelin.loganalysis.logcollection.dto.LogSourceCreateRequest;
import com.evelin.loganalysis.logcollection.dto.LogSourceResponse;
import com.evelin.loganalysis.logcollection.dto.LogSourceUpdateRequest;
import com.evelin.loganalysis.logcollection.dto.RawLogEventResponse;
import com.evelin.loganalysis.logcollection.model.CollectionState;
import com.evelin.loganalysis.logcollection.model.RawLogEvent;
import com.evelin.loganalysis.logcollection.model.entity.RawLogEventEntity;
import com.evelin.loganalysis.logcollection.service.LogSourceService;
import com.evelin.loganalysis.logcollection.service.RawLogEventService;
import com.evelin.loganalysis.logcommon.enums.CollectionStatus;
import com.evelin.loganalysis.logcommon.model.LogSource;
import com.evelin.loganalysis.logcommon.model.PageResult;
import com.evelin.loganalysis.logcommon.model.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

/**
 * 日志采集管理接口
 *
 * @author Evelin
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/collection")
@RequiredArgsConstructor
public class LogCollectionController {

    private final LogSourceService logSourceService;
    private final RawLogEventService rawLogEventService;
    private final CollectorFactory collectorFactory;

    // ==================== 日志源管理 ====================

    /**
     * 创建日志源
     */
    @PostMapping("/sources")
    public Result<LogSourceResponse> createSource(@RequestBody LogSourceCreateRequest request) {
        LogSourceResponse response = logSourceService.create(request);
        log.info("创建日志源: {} - {}", response.getId(), response.getName());
        return Result.success(response);
    }

    /**
     * 查询所有日志源
     */
    @GetMapping("/sources")
    public Result<List<LogSourceResponse>> listSources() {
        List<LogSourceResponse> sources = logSourceService.findAll();
        return Result.success(sources);
    }

    /**
     * 根据项目ID查询日志源
     */
    @GetMapping("/sources/project/{projectId}")
    public Result<List<LogSourceResponse>> listSourcesByProject(@PathVariable UUID projectId) {
        List<LogSourceResponse> sources = logSourceService.findByProjectId(projectId);
        return Result.success(sources);
    }

    /**
     * 根据ID查询日志源
     */
    @GetMapping("/sources/{sourceId}")
    public Result<LogSourceResponse> getSource(@PathVariable UUID sourceId) {
        Optional<LogSourceResponse> source = logSourceService.findById(sourceId);
        return source.map(Result::success)
                .orElseGet(() -> Result.error("日志源不存在: " + sourceId));
    }

    /**
     * 更新日志源
     */
    @PutMapping("/sources/{sourceId}")
    public Result<LogSourceResponse> updateSource(
            @PathVariable UUID sourceId,
            @RequestBody LogSourceUpdateRequest request) {
        Optional<LogSourceResponse> updated = logSourceService.update(sourceId, request);
        return updated.map(source -> {
            log.info("更新日志源: {} - {}", sourceId, source.getName());
            return Result.success(source);
        }).orElseGet(() -> Result.error("日志源不存在: " + sourceId));
    }

    /**
     * 删除日志源
     */
    @DeleteMapping("/sources/{sourceId}")
    public Result<Void> deleteSource(@PathVariable UUID sourceId) {
        boolean deleted = logSourceService.delete(sourceId);
        if (deleted) {
            log.info("删除日志源: {}", sourceId);
            return Result.success(null);
        }
        return Result.error("日志源不存在: " + sourceId);
    }

    /**
     * 启用/禁用日志源
     */
    @PatchMapping("/sources/{sourceId}/enabled")
    public Result<LogSourceResponse> toggleSourceEnabled(
            @PathVariable UUID sourceId,
            @RequestBody Map<String, Boolean> request) {
        Boolean enabled = request.get("enabled");
        if (enabled == null) {
            return Result.error("缺少 enabled 参数");
        }

        LogSourceUpdateRequest updateRequest = new LogSourceUpdateRequest();
        updateRequest.setEnabled(enabled);

        Optional<LogSourceResponse> updated = logSourceService.update(sourceId, updateRequest);
        return updated.map(source -> {
            log.info("{} 日志源: {}", enabled ? "启用" : "禁用", sourceId);
            return Result.success(source);
        }).orElseGet(() -> Result.error("日志源不存在: " + sourceId));
    }

    // ==================== 采集器管理 ====================

    /**
     * 启动采集器
     */
    @PostMapping("/collectors/{sourceId}/start")
    public Result<Map<String, Object>> startCollector(@PathVariable UUID sourceId) {
        // 1. 从数据库查询日志源
        Optional<LogSource> sourceOpt = logSourceService.getEntityById(sourceId);
        if (sourceOpt.isEmpty()) {
            return Result.error("日志源不存在: " + sourceId);
        }

        LogSource source = sourceOpt.get();

        // 2. 检查是否已启动
        if (collectorFactory.get(source) != null && collectorFactory.get(source).isRunning()) {
            return Result.error("采集器已在运行中");
        }

        // 3. 创建并启动采集器
        LogCollector collector = collectorFactory.create(source);
        collector.start();

        // 4. 更新数据库状态
        logSourceService.updateStatus(sourceId, CollectionStatus.RUNNING);

        // 5. 返回状态
        Map<String, Object> status = new HashMap<>();
        status.put("sourceId", sourceId);
        status.put("state", collector.getState().name());
        status.put("running", collector.isRunning());
        status.put("healthy", collector.isHealthy());
        status.put("collectedLines", collector.getCollectedLines());

        log.info("启动采集器: {} - {}", source.getName(), source.getPath());
        return Result.success(status);
    }

    /**
     * 停止采集器
     */
    @PostMapping("/collectors/{sourceId}/stop")
    public Result<Map<String, Object>> stopCollector(@PathVariable UUID sourceId) {
        // 1. 从数据库查询日志源
        Optional<LogSource> sourceOpt = logSourceService.getEntityById(sourceId);
        if (sourceOpt.isEmpty()) {
            return Result.error("日志源不存在: " + sourceId);
        }

        LogSource source = sourceOpt.get();

        // 2. 停止采集器
        LogCollector collector = collectorFactory.get(source);
        if (collector != null) {
            collector.stop();
            collectorFactory.remove(source);
            log.info("停止采集器: {} - {}", source.getName(), source.getPath());
        }

        // 3. 更新数据库状态
        logSourceService.updateStatus(sourceId, CollectionStatus.STOPPED);

        // 4. 返回状态
        Map<String, Object> status = new HashMap<>();
        status.put("sourceId", sourceId);
        status.put("state", CollectionState.STOPPED.name());
        status.put("running", false);

        return Result.success(status);
    }

    /**
     * 获取采集器状态
     */
    @GetMapping("/collectors/{sourceId}/status")
    public Result<Map<String, Object>> getCollectorStatus(@PathVariable UUID sourceId) {
        // 1. 从数据库查询日志源
        Optional<LogSource> sourceOpt = logSourceService.getEntityById(sourceId);
        if (sourceOpt.isEmpty()) {
            return Result.error("日志源不存在: " + sourceId);
        }

        LogSource source = sourceOpt.get();

        // 2. 获取采集器状态
        LogCollector collector = collectorFactory.get(source);

        Map<String, Object> status = new HashMap<>();
        status.put("sourceId", sourceId);
        status.put("sourceName", source.getName());
        status.put("path", source.getPath());

        if (collector != null) {
            status.put("state", collector.getState().name());
            status.put("running", collector.isRunning());
            status.put("healthy", collector.isHealthy());
            status.put("collectedLines", collector.getCollectedLines());
            status.put("dbStatus", source.getStatus().name());
        } else {
            status.put("state", CollectionState.STOPPED.name());
            status.put("running", false);
            status.put("dbStatus", source.getStatus().name());
        }

        return Result.success(status);
    }

    /**
     * 获取采集日志队列信息
     */
    @GetMapping("/collectors/{sourceId}/logs")
    public Result<Map<String, Object>> getLogs(@PathVariable UUID sourceId) {
        // 1. 从数据库查询日志源
        Optional<LogSource> sourceOpt = logSourceService.getEntityById(sourceId);
        if (sourceOpt.isEmpty()) {
            return Result.error("日志源不存在: " + sourceId);
        }

        LogSource source = sourceOpt.get();
        LogCollector collector = collectorFactory.get(source);

        Map<String, Object> result = new HashMap<>();
        result.put("sourceId", sourceId);
        result.put("sourceName", source.getName());

        if (collector != null) {
            BlockingQueue<RawLogEvent> queue = collector.getLogQueue();
            result.put("queueSize", queue.size());
            result.put("remainingCapacity", queue.remainingCapacity());
            result.put("running", collector.isRunning());
        } else {
            result.put("queueSize", 0);
            result.put("running", false);
        }

        return Result.success(result);
    }

    /**
     * 获取所有运行中的采集器状态
     */
    @GetMapping("/collectors/status")
    public Result<List<Map<String, Object>>> getAllCollectorsStatus() {
        List<LogSource> runningSources = logSourceService.findEntitiesByStatus(CollectionStatus.RUNNING);

        List<Map<String, Object>> statusList = runningSources.stream()
                .map(source -> {
                    LogCollector collector = collectorFactory.get(source);
                    Map<String, Object> status = new HashMap<>();
                    status.put("sourceId", source.getId());
                    status.put("sourceName", source.getName());
                    status.put("path", source.getPath());

                    if (collector != null) {
                        status.put("state", collector.getState().name());
                        status.put("running", collector.isRunning());
                        status.put("healthy", collector.isHealthy());
                        status.put("collectedLines", collector.getCollectedLines());
                    } else {
                        status.put("state", CollectionState.STOPPED.name());
                        status.put("running", false);
                    }

                    return status;
                })
                .toList();

        return Result.success(statusList);
    }

    // ==================== 原始日志查询 ====================

    /**
     * 查询指定日志源的原始日志
     *
     * @param sourceId 日志源ID
     * @param page    页码（从0开始）
     * @param size    每页大小
     * @return 分页的原始日志列表
     */
    @GetMapping("/logs/{sourceId}")
    public Result<PageResult<RawLogEventResponse>> getLogsBySource(
            @PathVariable UUID sourceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(required = false) String logLevel) {

        if (logSourceService.findById(sourceId).isEmpty()) {
            return Result.error("日志源不存在: " + sourceId);
        }

        Page<RawLogEventEntity> logPage;
        
        if (logLevel != null && !logLevel.isEmpty() && startTime != null && endTime != null) {
            logPage = rawLogEventService.findBySourceIdAndLogLevelAndTimeRange(sourceId, logLevel, startTime, endTime, page, size);
        } else if (logLevel != null && !logLevel.isEmpty()) {
            logPage = rawLogEventService.findBySourceIdAndLogLevel(sourceId, logLevel, page, size);
        } else if (startTime != null && endTime != null) {
            logPage = rawLogEventService.findBySourceIdAndTimeRange(sourceId, startTime, endTime, page, size);
        } else {
            logPage = rawLogEventService.findBySourceId(sourceId, page, size);
        }

        List<RawLogEventResponse> content = logPage.getContent().stream()
                .map(RawLogEventResponse::fromEntity)
                .collect(Collectors.toList());

        PageResult<RawLogEventResponse> result = PageResult.<RawLogEventResponse>builder()
                .content(content)
                .page(logPage.getNumber())
                .size(logPage.getSize())
                .total(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .first(logPage.isFirst())
                .last(logPage.isLast())
                .build();

        return Result.success(result);
    }

    /**
     * 查询所有原始日志（支持分页和过滤）
     *
     * @param page      页码（从0开始）
     * @param size      每页大小
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @param keyword   关键词搜索（可选）
     * @return 分页的原始日志列表
     */
    @GetMapping("/logs")
    public Result<PageResult<RawLogEventResponse>> getAllLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(required = false) String keyword) {

        Page<RawLogEventEntity> logPage;

        // 根据参数组合选择查询方式
        if (keyword != null && !keyword.isEmpty()) {
            // 模糊搜索
            logPage = rawLogEventService.findByContentContaining(keyword, page, size);
        } else if (startTime != null && endTime != null) {
            // 时间范围查询
            logPage = rawLogEventService.findByTimeRange(startTime, endTime, page, size);
        } else {
            // 查询所有（不带sourceId过滤）
            logPage = rawLogEventService.findAll(page, size);
        }

        List<RawLogEventResponse> content = logPage.getContent().stream()
                .map(RawLogEventResponse::fromEntity)
                .collect(Collectors.toList());

        PageResult<RawLogEventResponse> result = PageResult.<RawLogEventResponse>builder()
                .content(content)
                .page(logPage.getNumber())
                .size(logPage.getSize())
                .total(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .first(logPage.isFirst())
                .last(logPage.isLast())
                .build();

        return Result.success(result);
    }

    /**
     * 根据ID查询单条原始日志
     *
     * @param logId 日志ID
     * @return 原始日志详情
     */
    @GetMapping("/log/{logId}")
    public Result<RawLogEventResponse> getLogById(@PathVariable UUID logId) {
        Optional<RawLogEventEntity> logOpt = rawLogEventService.findById(logId);
        return logOpt.map(log -> Result.success(RawLogEventResponse.fromEntity(log)))
                .orElseGet(() -> Result.error("日志不存在: " + logId));
    }

    /**
     * 统计指定日志源的日志数量
     *
     * @param sourceId 日志源ID
     * @return 日志数量
     */
    @GetMapping("/logs/{sourceId}/count")
    public Result<Map<String, Object>> countLogsBySource(@PathVariable UUID sourceId) {
        long count = rawLogEventService.countBySourceId(sourceId);
        Map<String, Object> result = new HashMap<>();
        result.put("sourceId", sourceId);
        result.put("count", count);
        return Result.success(result);
    }

    /**
     * 清理指定时间之前的日志
     *
     * @param days 天数（清理多少天之前的日志）
     * @return 删除的日志数量
     */
    @DeleteMapping("/logs/cleanup")
    public Result<Map<String, Object>> cleanupOldLogs(@RequestParam(defaultValue = "7") int days) {
        long deletedCount = rawLogEventService.cleanupOldLogs(days);
        Map<String, Object> result = new HashMap<>();
        result.put("days", days);
        result.put("deletedCount", deletedCount);
        log.info("清理 {} 天前的原始日志: {} 条", days, deletedCount);
        return Result.success(result);
    }

    // ==================== 测试接口 ====================

    /**
     * 测试：向文件追加日志（用于测试采集功能）
     *
     * @param path    文件路径
     * @param content 日志内容
     * @return 结果
     */
    @PostMapping("/test/log")
    public Result<Map<String, Object>> addTestLog(
            @RequestParam("path") String path,
            @RequestParam("content") String content) {
        try {
            java.nio.file.Path filePath = java.nio.file.Paths.get(path);
            java.nio.file.Files.createDirectories(filePath.getParent());
            java.nio.file.Files.writeString(filePath, content + System.lineSeparator(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            
            Map<String, Object> result = new HashMap<>();
            result.put("path", path);
            result.put("content", content);
            result.put("success", true);
            
            log.info("测试日志已写入: {}", path);
            return Result.success(result);
        } catch (Exception e) {
            log.error("写入测试日志失败: {}", path, e);
            return Result.error("写入失败: " + e.getMessage());
        }
    }
}
