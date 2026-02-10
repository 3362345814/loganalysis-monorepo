package com.evelin.loganalysis.logcollection.controller;

import com.evelin.loganalysis.logcollection.collector.LocalFileCollector;
import com.evelin.loganalysis.logcollection.collector.LocalFileCollectorFactory;
import com.evelin.loganalysis.logcollection.dto.LogSourceCreateRequest;
import com.evelin.loganalysis.logcollection.dto.LogSourceResponse;
import com.evelin.loganalysis.logcollection.dto.LogSourceUpdateRequest;
import com.evelin.loganalysis.logcollection.model.CollectionState;
import com.evelin.loganalysis.logcollection.model.RawLogEvent;
import com.evelin.loganalysis.logcollection.service.LogSourceService;
import com.evelin.loganalysis.logcommon.enums.CollectionStatus;
import com.evelin.loganalysis.logcommon.model.LogSource;
import com.evelin.loganalysis.logcommon.model.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;

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
    private final LocalFileCollectorFactory collectorFactory;

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
        LocalFileCollector collector = collectorFactory.create(source);
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
        LocalFileCollector collector = collectorFactory.get(source);
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
        LocalFileCollector collector = collectorFactory.get(source);

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
        LocalFileCollector collector = collectorFactory.get(source);

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
                    LocalFileCollector collector = collectorFactory.get(source);
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
}
