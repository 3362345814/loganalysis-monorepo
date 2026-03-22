package com.evelin.loganalysis.logcollection.controller;

import com.evelin.loganalysis.logcollection.dto.EsLogQueryRequest;
import com.evelin.loganalysis.logcollection.dto.EsLogSearchResponse;
import com.evelin.loganalysis.logcollection.service.LogElasticsearchService;
import com.evelin.loganalysis.logcommon.model.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ES 日志查询 Controller
 *
 * @author Evelin
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/collection/logs/es")
@RequiredArgsConstructor
public class LogElasticsearchController {

    private final LogElasticsearchService esService;

    /**
     * ES 高级搜索
     */
    @GetMapping("/search")
    public Result<EsLogSearchResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String regex,
            @RequestParam(required = false) UUID sourceId,
            @RequestParam(required = false) List<String> logLevels,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String aggregationGroupId,
            @RequestParam(required = false) String aggregationField,
            @RequestParam(required = false) String timeInterval,
            @RequestParam(required = false, defaultValue = "true") boolean highlight,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false, defaultValue = "originalLogTime") String sortField,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder
    ) {
        EsLogQueryRequest request = EsLogQueryRequest.builder()
                .keyword(keyword)
                .regex(regex)
                .sourceId(sourceId)
                .logLevels(logLevels)
                .startTime(startTime)
                .endTime(endTime)
                .filePath(filePath)
                .traceId(traceId)
                .aggregationGroupId(aggregationGroupId)
                .aggregationField(aggregationField)
                .timeInterval(timeInterval)
                .highlight(highlight)
                .page(page)
                .size(size)
                .sortField(sortField)
                .sortOrder(sortOrder)
                .build();

        EsLogSearchResponse response = esService.searchLogs(request);
        return Result.success(response);
    }

    /**
     * 获取聚合统计
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats(
            @RequestParam(required = false) UUID sourceId,
            @RequestParam(required = false) List<String> logLevels,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String aggregationField,
            @RequestParam(required = false) String timeInterval
    ) {
        EsLogQueryRequest request = EsLogQueryRequest.builder()
                .sourceId(sourceId)
                .logLevels(logLevels)
                .startTime(startTime)
                .endTime(endTime)
                .aggregationField(aggregationField)
                .timeInterval(timeInterval)
                .build();

        Map<String, Object> stats = esService.getAggregations(request);
        return Result.success(stats);
    }

    /**
     * 手动触发索引同步 (按日志源)
     */
    @PostMapping("/index/{sourceId}")
    public Result<Integer> syncBySourceId(
            @PathVariable UUID sourceId,
            @RequestParam(required = false, defaultValue = "500") Integer batchSize
    ) {
        int count = esService.syncLogsBySourceId(sourceId, batchSize);
        return Result.success(count);
    }

    /**
     * 手动触发全量同步
     */
    @PostMapping("/sync-all")
    public Result<Integer> syncAll(
            @RequestParam(required = false, defaultValue = "500") Integer batchSize
    ) {
        int count = esService.syncAllLogs(batchSize);
        return Result.success(count);
    }

    /**
     * 获取 ES 索引信息
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> getInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("indexedCount", esService.getIndexedCount());
        return Result.success(info);
    }

    /**
     * ES 健康检查
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> health = esService.healthCheck();
        return Result.success(health);
    }

    /**
     * 获取准确的文档总数（使用 count API）
     */
    @GetMapping("/count")
    public Result<Map<String, Object>> getCount(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String regex,
            @RequestParam(required = false) UUID sourceId,
            @RequestParam(required = false) List<String> logLevels,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String aggregationGroupId
    ) {
        EsLogQueryRequest request = EsLogQueryRequest.builder()
                .keyword(keyword)
                .regex(regex)
                .sourceId(sourceId)
                .logLevels(logLevels)
                .startTime(startTime)
                .endTime(endTime)
                .filePath(filePath)
                .traceId(traceId)
                .aggregationGroupId(aggregationGroupId)
                .build();

        long count = esService.countDocuments(request);
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        return Result.success(result);
    }
}
