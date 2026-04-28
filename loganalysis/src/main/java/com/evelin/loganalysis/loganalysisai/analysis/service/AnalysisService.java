package com.evelin.loganalysis.loganalysisai.analysis.service;

import com.evelin.loganalysis.loganalysisai.analysis.RootCauseAnalyzer;
import com.evelin.loganalysis.loganalysisai.config.service.AnalysisConfigService;
import com.evelin.loganalysis.loganalysisai.analysis.dto.AnalysisResultDTO;
import com.evelin.loganalysis.loganalysisai.analysis.entity.AnalysisResultEntity;
import com.evelin.loganalysis.loganalysisai.analysis.repository.AnalysisResultRepository;
import com.evelin.loganalysis.logcollection.model.entity.RawLogEventEntity;
import com.evelin.loganalysis.logcollection.service.RawLogEventService;
import com.evelin.loganalysis.logcommon.utils.IdGenerator;
import com.evelin.loganalysis.logprocessing.entity.AggregationGroupEntity;
import com.evelin.loganalysis.logprocessing.service.AggregationGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AI 分析服务
 *
 * @author Evelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {
    
    private final RootCauseAnalyzer rootCauseAnalyzer;
    private final AnalysisResultRepository analysisResultRepository;
    private final AggregationGroupService aggregationGroupService;
    private final RawLogEventService rawLogEventService;
    private final AnalysisConfigService analysisConfigService;

    private static final int MAX_REPRESENTATIVE_LOG_CHARS = 1_500;
    private static final int MAX_LOG_MESSAGE_CHARS = 800;
    private static final int MAX_STACK_TRACE_CHARS = 8_000;
    private static final int MAX_RELATED_LOGS = 120;
    private static final int MAX_RELATED_TOTAL_CHARS = 16_000;
    private static final int MAX_CONTEXT_TOTAL_CHARS = 8_000;
    private static final int MAX_TRACE_CHAIN_LOGS = 200;
    private static final int MAX_TRACE_CHAIN_TOTAL_CHARS = 24_000;
    
    /**
     * 获取严重程度优先级
     */
    private int getSeverityPriority(String severity) {
        if (severity == null) return 0;
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> 3;
            case "ERROR" -> 2;
            case "WARNING" -> 1;
            default -> 0;
        };
    }
    
    /**
     * 分析聚合组
     *
     * @param aggregationData 聚合组数据
     * @return 分析结果
     */
    public AnalysisResultDTO analyze(Map<String, Object> aggregationData) {
        return analyze(aggregationData, false);
    }

    /**
     * 分析聚合组
     *
     * @param aggregationData 聚合组数据
     * @param isManualTrigger 是否为手动触发（手动触发不做级别限制）
     * @return 分析结果
     */
    public AnalysisResultDTO analyze(Map<String, Object> aggregationData, boolean isManualTrigger) {
        Map<String, Object> payload = buildAnalysisPayload(aggregationData);
        String aggregationId = String.valueOf(payload.get("groupId"));
        String severity = String.valueOf(payload.getOrDefault("severity", "INFO"));

        log.info("开始分析聚合组: {}, 严重程度: {}, 触发类型: {}",
                aggregationId, severity, isManualTrigger ? "手动" : "自动");

        String analysisId = startAnalysis(payload, isManualTrigger);
        if (analysisId == null) {
            return getAnalysisResult(aggregationId);
        }

        return finishStartedAnalysis(payload, analysisId);
    }

    /**
     * 创建 PROCESSING 记录作为分析任务锁。
     *
     * @return 分析记录 ID；如果已有 COMPLETED 结果则返回 null
     */
    @Transactional
    public String startAnalysis(Map<String, Object> aggregationData, boolean isManualTrigger) {
        String aggregationId = resolveGroupId(aggregationData);
        if (aggregationId == null || aggregationId.isBlank()) {
            throw new IllegalArgumentException("缺少有效的 groupId，无法触发分析");
        }
        String severity = String.valueOf(aggregationData.getOrDefault("severity", "INFO"));

        // 自动触发时才检查严重程度，手动触发不限制级别
        if (!isManualTrigger) {
            int severityPriority = getSeverityPriority(severity);
            if (severityPriority < 2) {
                log.warn("聚合组 {} 严重程度为 {}，低于 ERROR 阈值，跳过自动分析", aggregationId, severity);
                throw new IllegalArgumentException("只有 ERROR 及以上级别才能触发 AI 分析");
            }
        }
        
        AnalysisResultEntity entity = deduplicateByAggregationId(aggregationId);
        if (entity != null) {
            if ("COMPLETED".equals(entity.getStatus())) {
                log.info("聚合组 {} 已有分析结果，跳过重复分析", aggregationId);
                return null;
            }
            if (isRunningStatus(entity.getStatus())) {
                throw new IllegalStateException("聚合组正在分析中，请勿重复触发");
            }

            entity.setStatus("PROCESSING");
            entity.setStatusMessage("分析任务已提交，正在分析");
            entity.setCreatedAt(LocalDateTime.now());
            entity.setCompletedAt(null);
            entity.setProcessingTimeMs(null);
            analysisResultRepository.save(entity);
            return entity.getId();
        }

        AnalysisResultEntity processing = new AnalysisResultEntity();
        processing.setId(IdGenerator.nextId());
        processing.setAggregationId(aggregationId);
        processing.setAggregationName(String.valueOf(aggregationData.getOrDefault("name", aggregationId)));
        processing.setStatus("PROCESSING");
        processing.setStatusMessage("分析任务已提交，正在分析");
        processing.setCreatedAt(LocalDateTime.now());
        analysisResultRepository.save(processing);
        return processing.getId();
    }

    /**
     * 执行已开始的分析任务，并回写结果。
     */
    public AnalysisResultDTO finishStartedAnalysis(Map<String, Object> aggregationData, String analysisId) {
        Map<String, Object> payload = isPayloadReady(aggregationData)
                ? aggregationData
                : buildAnalysisPayload(aggregationData);
        String aggregationId = String.valueOf(payload.get("groupId"));
        try {
        // 执行分析
        AnalysisResultDTO result = rootCauseAnalyzer.analyze(payload);

        // 保存结果
            result.setId(analysisId);
            saveAnalysisResult(result);
            if (isAnalyzedStatus(result.getStatus())) {
                aggregationGroupService.markAsAnalyzed(aggregationId);
            }

        log.info("聚合组 {} 分析完成，状态: {}", aggregationId, result.getStatus());
        return result;
        } catch (Exception e) {
            markAnalysisFailed(analysisId, aggregationId, e.getMessage());
            throw e;
        }
    }
    
    /**
     * 获取分析结果
     *
     * @param aggregationId 聚合组ID
     * @return 分析结果
     */
    public AnalysisResultDTO getAnalysisResult(String aggregationId) {
        return analysisResultRepository.findTopByAggregationIdOrderByCreatedAtDesc(aggregationId)
                .map(this::toDTO)
                .orElse(null);
    }

    @Transactional
    protected void saveAnalysisResult(AnalysisResultDTO result) {
        AnalysisResultEntity entity = toEntity(result);
        analysisResultRepository.save(entity);
    }

    @Transactional
    protected void markAnalysisFailed(String analysisId, String aggregationId, String message) {
        AnalysisResultEntity entity = analysisResultRepository.findById(analysisId)
                .orElseGet(() -> {
                    AnalysisResultEntity fallback = new AnalysisResultEntity();
                    fallback.setId(analysisId);
                    fallback.setAggregationId(aggregationId);
                    fallback.setCreatedAt(LocalDateTime.now());
                    return fallback;
                });
        entity.setStatus("FAILED");
        entity.setStatusMessage(limitLength(message != null ? message : "分析执行失败", 500));
        entity.setCompletedAt(LocalDateTime.now());
        analysisResultRepository.save(entity);
        aggregationGroupService.markAsAnalyzed(aggregationId);
    }

    private boolean isRunningStatus(String status) {
        return "PROCESSING".equalsIgnoreCase(status) || "PENDING".equalsIgnoreCase(status);
    }

    private boolean isAnalyzedStatus(String status) {
        if (status == null) {
            return false;
        }
        return "COMPLETED".equalsIgnoreCase(status)
                || "FAILED".equalsIgnoreCase(status)
                || "PARSE_ERROR".equalsIgnoreCase(status);
    }

    /**
     * 同一聚合组只保留一条分析记录。
     * 优先保留已分析记录，其次保留最新记录；其余记录会删除。
     */
    @Transactional
    protected AnalysisResultEntity deduplicateByAggregationId(String aggregationId) {
        List<AnalysisResultEntity> results = new ArrayList<>(analysisResultRepository.findAllByAggregationId(aggregationId));
        if (results.isEmpty()) {
            return null;
        }

        results.sort(Comparator.comparing(this::isAnalyzedStatusForSort).reversed()
                .thenComparing(AnalysisResultEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        AnalysisResultEntity keep = results.get(0);

        for (int i = 1; i < results.size(); i++) {
            AnalysisResultEntity duplicated = results.get(i);
            if (duplicated.getId() != null && !duplicated.getId().equals(keep.getId())) {
                analysisResultRepository.delete(duplicated);
            }
        }

        return keep;
    }

    private boolean isAnalyzedStatusForSort(AnalysisResultEntity entity) {
        return isAnalyzedStatus(entity.getStatus());
    }

    private boolean isPayloadReady(Map<String, Object> payload) {
        return payload != null && Boolean.TRUE.equals(payload.get("_payloadReady"));
    }

    private String resolveGroupId(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        String groupId = toText(payload.get("groupId"));
        if (groupId != null) {
            return groupId;
        }
        String aggregationId = toText(payload.get("aggregationId"));
        if (aggregationId == null) {
            return null;
        }
        return aggregationGroupService.findById(aggregationId)
                .map(AggregationGroupEntity::getGroupId)
                .orElse(null);
    }

    private Map<String, Object> buildAnalysisPayload(Map<String, Object> incoming) {
        Map<String, Object> source = incoming != null ? incoming : Collections.emptyMap();
        String groupId = resolveGroupId(source);
        if (groupId == null || groupId.isBlank()) {
            throw new IllegalArgumentException("缺少有效的 groupId，无法构建分析上下文");
        }

        Optional<AggregationGroupEntity> groupOpt = aggregationGroupService.findByGroupId(groupId);
        if (groupOpt.isEmpty()) {
            throw new IllegalArgumentException("聚合组不存在: " + groupId);
        }
        AggregationGroupEntity group = groupOpt.get();

        List<RawLogEventEntity> allLogs = rawLogEventService.findAllByAggregationGroupId(groupId);
        allLogs.sort(Comparator
                .comparing(RawLogEventEntity::getOriginalLogTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(RawLogEventEntity::getCollectionTime, Comparator.nullsLast(Comparator.naturalOrder())));

        int contextSize = safeContextSize();
        int pivotIndex = findPivotIndex(allLogs);
        List<Map<String, Object>> relatedLogs = toLogMaps(allLogs, 0, allLogs.size(), MAX_RELATED_LOGS, MAX_RELATED_TOTAL_CHARS);
        List<Map<String, Object>> contextBefore = pivotIndex >= 0
                ? toLogMaps(allLogs, Math.max(0, pivotIndex - contextSize), pivotIndex, contextSize, MAX_CONTEXT_TOTAL_CHARS)
                : List.of();
        List<Map<String, Object>> contextAfter = pivotIndex >= 0
                ? toLogMaps(allLogs, pivotIndex + 1, Math.min(allLogs.size(), pivotIndex + 1 + contextSize), contextSize, MAX_CONTEXT_TOTAL_CHARS)
                : List.of();

        String traceId = resolvePrimaryTraceId(allLogs, pivotIndex);
        List<Map<String, Object>> traceChainLogs = buildTraceChainLogs(traceId);
        String stackTrace = limitLength(resolveStackTrace(allLogs, pivotIndex), MAX_STACK_TRACE_CHARS);

        Map<String, Object> payload = new HashMap<>();
        payload.putAll(source);
        payload.put("groupId", group.getGroupId());
        payload.put("aggregationId", group.getId());
        payload.put("name", group.getName());
        payload.put("severity", group.getSeverity() != null ? group.getSeverity() : source.getOrDefault("severity", "INFO"));
        payload.put("eventCount", group.getEventCount() != null ? group.getEventCount() : allLogs.size());
        payload.put("startTime", group.getFirstEventTime());
        payload.put("endTime", group.getLastEventTime());
        payload.put("representativeLog", limitLength(group.getRepresentativeLog(), MAX_REPRESENTATIVE_LOG_CHARS));
        payload.put("relatedLogs", relatedLogs);
        payload.put("contextBefore", contextBefore);
        payload.put("contextAfter", contextAfter);
        payload.put("traceId", traceId != null ? traceId : "");
        payload.put("traceChainLogs", traceChainLogs);
        payload.put("stackTrace", stackTrace != null ? stackTrace : "无");
        payload.put("_payloadReady", true);
        return payload;
    }

    private int safeContextSize() {
        Integer configured = analysisConfigService.getContextSize();
        if (configured == null) {
            return 10;
        }
        return Math.max(1, Math.min(configured, 100));
    }

    private int findPivotIndex(List<RawLogEventEntity> logs) {
        if (logs == null || logs.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < logs.size(); i++) {
            String level = toText(logs.get(i).getLogLevel());
            if ("CRITICAL".equalsIgnoreCase(level) || "ERROR".equalsIgnoreCase(level)) {
                return i;
            }
        }
        return 0;
    }

    private List<Map<String, Object>> toLogMaps(List<RawLogEventEntity> logs,
                                                int startInclusive,
                                                int endExclusive,
                                                int maxEntries,
                                                int maxTotalChars) {
        if (logs == null || logs.isEmpty() || startInclusive >= endExclusive || maxEntries <= 0 || maxTotalChars <= 0) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        int totalChars = 0;
        int actualEnd = Math.min(endExclusive, logs.size());
        for (int i = Math.max(0, startInclusive); i < actualEnd; i++) {
            if (result.size() >= maxEntries || totalChars >= maxTotalChars) {
                break;
            }
            RawLogEventEntity log = logs.get(i);
            String message = limitLength(log.getRawContent(), MAX_LOG_MESSAGE_CHARS);
            if (message == null || message.isBlank()) {
                message = "";
            }
            int nextChars = totalChars + message.length();
            if (!result.isEmpty() && nextChars > maxTotalChars) {
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", log.getId());
            item.put("logTime", log.getOriginalLogTime() != null ? log.getOriginalLogTime() : log.getCollectionTime());
            item.put("logLevel", log.getLogLevel());
            item.put("traceId", log.getTraceId());
            item.put("message", message);
            result.add(item);
            totalChars = nextChars;
        }
        return result;
    }

    private String resolvePrimaryTraceId(List<RawLogEventEntity> logs, int pivotIndex) {
        if (logs == null || logs.isEmpty()) {
            return null;
        }
        if (pivotIndex >= 0 && pivotIndex < logs.size()) {
            String pivotTrace = toText(logs.get(pivotIndex).getTraceId());
            if (pivotTrace != null) {
                return pivotTrace;
            }
        }
        for (RawLogEventEntity log : logs) {
            String traceId = toText(log.getTraceId());
            if (traceId != null) {
                return traceId;
            }
        }
        return null;
    }

    private List<Map<String, Object>> buildTraceChainLogs(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return List.of();
        }
        List<RawLogEventEntity> traceLogs = rawLogEventService.findAllByTraceId(traceId);
        traceLogs.sort(Comparator
                .comparing(RawLogEventEntity::getOriginalLogTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(RawLogEventEntity::getCollectionTime, Comparator.nullsLast(Comparator.naturalOrder())));
        return toLogMaps(traceLogs, 0, traceLogs.size(), MAX_TRACE_CHAIN_LOGS, MAX_TRACE_CHAIN_TOTAL_CHARS);
    }

    private String resolveStackTrace(List<RawLogEventEntity> logs, int pivotIndex) {
        if (logs == null || logs.isEmpty()) {
            return null;
        }
        if (pivotIndex >= 0 && pivotIndex < logs.size()) {
            String stack = extractStackTrace(logs.get(pivotIndex));
            if (stack != null) {
                return stack;
            }
        }
        for (RawLogEventEntity log : logs) {
            String stack = extractStackTrace(log);
            if (stack != null) {
                return stack;
            }
        }
        return null;
    }

    private String extractStackTrace(RawLogEventEntity log) {
        if (log == null || log.getParsedFields() == null) {
            return null;
        }
        Map<String, Object> fields = log.getParsedFields();
        Object value = fields.get("stackTrace");
        if (value == null) {
            value = fields.get("stack_trace");
        }
        if (value == null) {
            value = fields.get("throwable");
        }
        if (value == null) {
            return null;
        }
        String text = Objects.toString(value, "").trim();
        return text.isEmpty() ? null : text;
    }

    private String toText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String limitLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= 3) {
            return value.substring(0, maxLength);
        }
        return value.substring(0, maxLength - 3) + "...";
    }
    
    /**
     * 获取所有分析结果
     *
     * @return 分析结果列表
     */
    public List<AnalysisResultDTO> getAllAnalysisResults() {
        return analysisResultRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取最近的 N 条分析结果
     *
     * @param limit 数量限制
     * @return 分析结果列表
     */
    public List<AnalysisResultDTO> getRecentAnalysisResults(int limit) {
        return analysisResultRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(limit)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * 转换为 DTO
     */
    private AnalysisResultDTO toDTO(AnalysisResultEntity entity) {
        AnalysisResultDTO dto = new AnalysisResultDTO();
        dto.setId(entity.getId());
        dto.setAggregationId(entity.getAggregationId());
        dto.setAggregationName(entity.getAggregationName());
        dto.setRootCause(entity.getRootCause());
        dto.setRootCauseCategory(entity.getRootCauseCategory());
        dto.setAnalysisDetail(entity.getAnalysisDetail());
        dto.setConfidence(entity.getConfidence());
        dto.setImpactScope(entity.getImpactScope());
        dto.setImpactSeverity(entity.getImpactSeverity());
        dto.setModelName(entity.getModelName());
        dto.setRequestTokens(entity.getRequestTokens());
        dto.setResponseTokens(entity.getResponseTokens());
        dto.setProcessingTimeMs(entity.getProcessingTimeMs());
        dto.setStatus(entity.getStatus());
        dto.setStatusMessage(entity.getStatusMessage());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setCompletedAt(entity.getCompletedAt());
        return dto;
    }
    
    /**
     * 转换为实体
     */
    private AnalysisResultEntity toEntity(AnalysisResultDTO dto) {
        AnalysisResultEntity entity = new AnalysisResultEntity();
        entity.setId(dto.getId());
        entity.setAggregationId(dto.getAggregationId());
        entity.setAggregationName(dto.getAggregationName());
        entity.setRootCause(dto.getRootCause());
        entity.setRootCauseCategory(dto.getRootCauseCategory());
        entity.setAnalysisDetail(dto.getAnalysisDetail());
        entity.setConfidence(dto.getConfidence());
        entity.setImpactScope(dto.getImpactScope());
        entity.setImpactSeverity(dto.getImpactSeverity());
        entity.setModelName(dto.getModelName());
        entity.setRequestTokens(dto.getRequestTokens());
        entity.setResponseTokens(dto.getResponseTokens());
        entity.setProcessingTimeMs(dto.getProcessingTimeMs());
        entity.setStatus(dto.getStatus());
        entity.setStatusMessage(dto.getStatusMessage());
        entity.setCreatedAt(dto.getCreatedAt());
        entity.setCompletedAt(dto.getCompletedAt());
        return entity;
    }
}
