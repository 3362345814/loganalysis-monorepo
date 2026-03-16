package com.evelin.loganalysis.logprocessing.controller;

import com.evelin.loganalysis.logcollection.model.entity.RawLogEventEntity;
import com.evelin.loganalysis.logcollection.service.RawLogEventService;
import com.evelin.loganalysis.logcommon.model.PageResult;
import com.evelin.loganalysis.logcommon.model.Result;
import com.evelin.loganalysis.logprocessing.entity.AggregationGroupEntity;
import com.evelin.loganalysis.logprocessing.service.AggregationGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import java.util.Comparator;

/**
 * 聚合组管理接口
 *
 * @author Evelin
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/processing/aggregation")
@RequiredArgsConstructor
public class AggregationGroupController {

    private final AggregationGroupService aggregationGroupService;
    private final RawLogEventService rawLogEventService;

    /**
     * 获取聚合组统计摘要
     */
    @GetMapping("/summary")
    public Result<Map<String, Object>> getSummary() {
        try {
            Map<String, Object> summary = aggregationGroupService.getSummary();
            return Result.success(summary);
        } catch (Exception e) {
            log.error("获取聚合组统计摘要失败", e);
            return Result.error("获取统计摘要失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有活跃聚合组
     */
    @GetMapping("/active")
    public Result<List<AggregationGroupEntity>> getActiveGroups() {
        try {
            List<AggregationGroupEntity> groups = aggregationGroupService.findActiveGroups();
            return Result.success(groups);
        } catch (Exception e) {
            log.error("获取活跃聚合组失败", e);
            return Result.error("获取活跃聚合组失败: " + e.getMessage());
        }
    }

    /**
     * 分页查询聚合组
     *
     * @param page    页码（从0开始）
     * @param size    每页大小
     * @param status  状态过滤（可选）
     * @param severity 严重程度过滤（可选）
     * @return 分页的聚合组列表
     */
    @GetMapping
    public Result<PageResult<AggregationGroupEntity>> getAggregationGroups(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastEventTime"));
            Page<AggregationGroupEntity> groupPage;

            if (status != null && !status.isEmpty()) {
                groupPage = aggregationGroupService.findByStatus(status, pageable);
            } else {
                groupPage = aggregationGroupService.findAll(pageable);
            }

            // 如果有严重程度过滤，在内存中过滤
            if (severity != null && !severity.isEmpty() && groupPage.hasContent()) {
                List<AggregationGroupEntity> filtered = groupPage.getContent().stream()
                        .filter(g -> severity.equalsIgnoreCase(g.getSeverity()))
                        .toList();
                groupPage = new PageImplWrapper<>(filtered, pageable, filtered.size());
            }

            List<AggregationGroupEntity> content = groupPage.getContent();
            PageResult<AggregationGroupEntity> result = PageResult.<AggregationGroupEntity>builder()
                    .content(content)
                    .page(groupPage.getNumber())
                    .size(groupPage.getSize())
                    .total(groupPage.getTotalElements())
                    .totalPages(groupPage.getTotalPages())
                    .first(groupPage.isFirst())
                    .last(groupPage.isLast())
                    .build();

            return Result.success(result);
        } catch (Exception e) {
            log.error("获取聚合组列表失败", e);
            return Result.error("获取聚合组列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询聚合组
     */
    @GetMapping("/{id}")
    public Result<AggregationGroupEntity> getAggregationGroup(@PathVariable String id) {
        try {
            Optional<AggregationGroupEntity> group = aggregationGroupService.findById(id);
            return group.map(Result::success)
                    .orElseGet(() -> Result.error("聚合组不存在: " + id));
        } catch (Exception e) {
            log.error("获取聚合组详情失败", e);
            return Result.error("获取聚合组详情失败: " + e.getMessage());
        }
    }

    /**
     * 根据groupId查询聚合组
     */
    @GetMapping("/group/{groupId}")
    public Result<AggregationGroupEntity> getAggregationGroupByGroupId(@PathVariable String groupId) {
        try {
            Optional<AggregationGroupEntity> group = aggregationGroupService.findByGroupId(groupId);
            return group.map(Result::success)
                    .orElseGet(() -> Result.error("聚合组不存在: " + groupId));
        } catch (Exception e) {
            log.error("获取聚合组详情失败", e);
            return Result.error("获取聚合组详情失败: " + e.getMessage());
        }
    }

    /**
     * 根据聚合组groupId查询组内所有日志（分页）
     *
     * @param groupId 聚合组groupId
     * @param page    页码（从0开始）
     * @param size    每页大小
     * @return 分页的日志列表
     */
    @GetMapping("/group/{groupId}/logs")
    public Result<PageResult<RawLogEventEntity>> getLogsByGroupId(
            @PathVariable String groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Page<RawLogEventEntity> logPage = rawLogEventService.findByAggregationGroupId(groupId, page, size);
            
            PageResult<RawLogEventEntity> result = PageResult.<RawLogEventEntity>builder()
                    .content(logPage.getContent())
                    .page(logPage.getNumber())
                    .size(logPage.getSize())
                    .total(logPage.getTotalElements())
                    .totalPages(logPage.getTotalPages())
                    .first(logPage.isFirst())
                    .last(logPage.isLast())
                    .build();

            return Result.success(result);
        } catch (Exception e) {
            log.error("获取聚合组日志列表失败", e);
            return Result.error("获取聚合组日志列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据聚合组ID查询组内所有日志（分页）
     *
     * @param id   聚合组ID（数据库主键）
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 分页的日志列表
     */
    @GetMapping("/{id}/logs")
    public Result<PageResult<RawLogEventEntity>> getLogsByAggregationId(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Optional<AggregationGroupEntity> groupOpt = aggregationGroupService.findById(id);
            if (groupOpt.isEmpty()) {
                return Result.error("聚合组不存在: " + id);
            }

            String groupId = groupOpt.get().getGroupId();
            Page<RawLogEventEntity> logPage = rawLogEventService.findByAggregationGroupId(groupId, page, size);

            PageResult<RawLogEventEntity> result = PageResult.<RawLogEventEntity>builder()
                    .content(logPage.getContent())
                    .page(logPage.getNumber())
                    .size(logPage.getSize())
                    .total(logPage.getTotalElements())
                    .totalPages(logPage.getTotalPages())
                    .first(logPage.isFirst())
                    .last(logPage.isLast())
                    .build();

            return Result.success(result);
        } catch (Exception e) {
            log.error("获取聚合组日志列表失败", e);
            return Result.error("获取聚合组日志列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取聚合组日志上下文（用于AI分析）
     * 返回错误日志及其前后N条日志
     *
     * @param id           聚合组ID（数据库主键）
     * @param contextSize  前后上下文日志数量，默认10
     * @return 包含错误日志和上下文的数据
     */
    @GetMapping("/{id}/context")
    public Result<Map<String, Object>> getAggregationContext(
            @PathVariable String id,
            @RequestParam(defaultValue = "10") int contextSize) {
        try {
            Optional<AggregationGroupEntity> groupOpt = aggregationGroupService.findById(id);
            if (groupOpt.isEmpty()) {
                return Result.error("聚合组不存在: " + id);
            }

            AggregationGroupEntity group = groupOpt.get();
            String groupId = group.getGroupId();

            // 获取聚合组内的所有日志
            List<RawLogEventEntity> allLogs = rawLogEventService.findAllByAggregationGroupId(groupId);

            if (allLogs.isEmpty()) {
                return Result.error("聚合组内没有日志");
            }

            // 按时间排序（升序，最早的在前）
            allLogs.sort(Comparator.comparing(
                RawLogEventEntity::getOriginalLogTime,
                Comparator.nullsLast(Comparator.naturalOrder())
            ));

            // 构建返回数据
            Map<String, Object> contextData = new HashMap<>();

            // 基本信息
            contextData.put("groupId", group.getGroupId());
            contextData.put("name", group.getName());
            contextData.put("severity", group.getSeverity());
            contextData.put("eventCount", group.getEventCount());
            contextData.put("startTime", group.getFirstEventTime());
            contextData.put("endTime", group.getLastEventTime());
            contextData.put("representativeLog", group.getRepresentativeLog());

            // 相关日志（最多20条）
            List<Map<String, Object>> relatedLogs = new ArrayList<>();
            int maxLogs = Math.min(allLogs.size(), 20);
            for (int i = 0; i < maxLogs; i++) {
                RawLogEventEntity log = allLogs.get(i);
                Map<String, Object> logMap = new HashMap<>();
                logMap.put("id", log.getId());
                logMap.put("logTime", log.getOriginalLogTime());
                logMap.put("logLevel", log.getLogLevel());
                logMap.put("message", log.getRawContent());
                relatedLogs.add(logMap);
            }
            contextData.put("relatedLogs", relatedLogs);

            // 错误前后的上下文日志
            List<Map<String, Object>> contextBefore = new ArrayList<>();
            List<Map<String, Object>> contextAfter = new ArrayList<>();

            // 找到 ERROR/CRITICAL 日志的位置
            int errorIndex = -1;
            for (int i = 0; i < allLogs.size(); i++) {
                String level = allLogs.get(i).getLogLevel();
                if ("ERROR".equalsIgnoreCase(level) || "CRITICAL".equalsIgnoreCase(level)) {
                    errorIndex = i;
                    break;
                }
            }

            if (errorIndex >= 0) {
                // 获取错误前的日志
                int beforeStart = Math.max(0, errorIndex - contextSize);
                for (int i = beforeStart; i < errorIndex; i++) {
                    RawLogEventEntity log = allLogs.get(i);
                    Map<String, Object> logMap = new HashMap<>();
                    logMap.put("id", log.getId());
                    logMap.put("logTime", log.getOriginalLogTime());
                    logMap.put("logLevel", log.getLogLevel());
                    logMap.put("message", log.getRawContent());
                    contextBefore.add(logMap);
                }

                // 获取错误后的日志
                int afterEnd = Math.min(allLogs.size(), errorIndex + contextSize + 1);
                for (int i = errorIndex + 1; i < afterEnd; i++) {
                    RawLogEventEntity log = allLogs.get(i);
                    Map<String, Object> logMap = new HashMap<>();
                    logMap.put("id", log.getId());
                    logMap.put("logTime", log.getOriginalLogTime());
                    logMap.put("logLevel", log.getLogLevel());
                    logMap.put("message", log.getRawContent());
                    contextAfter.add(logMap);
                }
            }

            contextData.put("contextBefore", contextBefore);
            contextData.put("contextAfter", contextAfter);

            return Result.success(contextData);
        } catch (Exception e) {
            log.error("获取聚合组上下文失败", e);
            return Result.error("获取聚合组上下文失败: " + e.getMessage());
        }
    }

    /**
     * 根据日志源ID查询聚合组
     */
    @GetMapping("/source/{sourceId}")
    public Result<List<AggregationGroupEntity>> getAggregationGroupsBySource(@PathVariable String sourceId) {
        try {
            List<AggregationGroupEntity> groups = aggregationGroupService.findBySourceId(sourceId);
            return Result.success(groups);
        } catch (Exception e) {
            log.error("获取日志源的聚合组失败", e);
            return Result.error("获取日志源的聚合组失败: " + e.getMessage());
        }
    }

    /**
     * 查询未分析的聚合组
     */
    @GetMapping("/unanalyzed")
    public Result<List<AggregationGroupEntity>> getUnanalyzedGroups() {
        try {
            List<AggregationGroupEntity> groups = aggregationGroupService.findUnanalyzedGroups();
            return Result.success(groups);
        } catch (Exception e) {
            log.error("获取未分析聚合组失败", e);
            return Result.error("获取未分析聚合组失败: " + e.getMessage());
        }
    }

    /**
     * 删除聚合组
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteAggregationGroup(@PathVariable String id) {
        try {
            aggregationGroupService.delete(id);
            log.info("删除聚合组: {}", id);
            return Result.success(null);
        } catch (Exception e) {
            log.error("删除聚合组失败", e);
            return Result.error("删除聚合组失败: " + e.getMessage());
        }
    }

    /**
     * 清理过期聚合组
     */
    @PostMapping("/cleanup")
    public Result<Map<String, Object>> cleanupExpiredGroups(
            @RequestParam(defaultValue = "60") int timeoutMinutes) {
        try {
            aggregationGroupService.cleanupExpiredGroups(timeoutMinutes);
            Map<String, Object> result = Map.of(
                    "message", "清理完成",
                    "timeoutMinutes", timeoutMinutes
            );
            return Result.success(result);
        } catch (Exception e) {
            log.error("清理过期聚合组失败", e);
            return Result.error("清理失败: " + e.getMessage());
        }
    }

    // 简单的 Page 实现包装类
    private static class PageImplWrapper<T> extends org.springframework.data.domain.PageImpl<T> {
        public PageImplWrapper(List<T> content, Pageable pageable, long total) {
            super(content, pageable, total);
        }
    }
}
