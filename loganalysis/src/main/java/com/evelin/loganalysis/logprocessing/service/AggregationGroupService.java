package com.evelin.loganalysis.logprocessing.service;

import com.evelin.loganalysis.loganalysisai.analysis.entity.AnalysisResultEntity;
import com.evelin.loganalysis.loganalysisai.analysis.repository.AnalysisResultRepository;
import com.evelin.loganalysis.logcollection.repository.LogSourceRepository;
import com.evelin.loganalysis.logcollection.repository.RawLogEventRepository;
import com.evelin.loganalysis.logprocessing.aggregation.LogTemplateUtils;
import com.evelin.loganalysis.logprocessing.dto.AggregationResult;
import com.evelin.loganalysis.logprocessing.entity.AggregationGroupEntity;
import com.evelin.loganalysis.logprocessing.repository.AggregationGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 聚合组服务
 *
 * @author Evelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AggregationGroupService {

    private final AggregationGroupRepository aggregationGroupRepository;
    private final LogSourceRepository logSourceRepository;
    private final RawLogEventRepository rawLogEventRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private static final Map<String, Integer> SEVERITY_PRIORITY = Map.of(
            "INFO", 1,
            "WARNING", 2,
            "ERROR", 3,
            "CRITICAL", 4
    );
    private static final int AGGREGATION_NAME_MAX_LENGTH = 200;
    private static final int ROOT_CAUSE_CATEGORY_MAX_LENGTH = 100;
    private static final int IMPACT_SCOPE_MAX_LENGTH = 500;
    private static final int IMPACT_SEVERITY_MAX_LENGTH = 20;
    private static final int STATUS_MAX_LENGTH = 20;
    private static final int STATUS_MESSAGE_MAX_LENGTH = 500;

    /**
     * 创建或更新聚合组
     */
    @Transactional
    public AggregationGroupEntity saveOrUpdate(AggregationResult aggregationResult, String sourceId, String sourceName) {
        // 检查是否已存在
        Optional<AggregationGroupEntity> existingGroup = aggregationGroupRepository.findByGroupId(aggregationResult.getGroupId());

        if (existingGroup.isPresent()) {
            // 更新现有聚合组
            AggregationGroupEntity group = existingGroup.get();
            Integer incomingCount = aggregationResult.getEventCount();
            if (incomingCount != null) {
                Integer currentCount = group.getEventCount() != null ? group.getEventCount() : 0;
                group.setEventCount(Math.max(currentCount, incomingCount));
            }

            LocalDateTime incomingTime = aggregationResult.getAggregatedAt();
            if (incomingTime != null) {
                LocalDateTime currentLast = group.getLastEventTime();
                if (currentLast == null || incomingTime.isAfter(currentLast)) {
                    group.setLastEventTime(incomingTime);
                }

                LocalDateTime currentFirst = group.getFirstEventTime();
                if (currentFirst == null || incomingTime.isBefore(currentFirst)) {
                    group.setFirstEventTime(incomingTime);
                }
            }

            group.setSeverity(mergeSeverity(group.getSeverity(), aggregationResult.getSeverity()));

            Double incomingScore = aggregationResult.getSimilarityScore();
            if (incomingScore != null) {
                Double currentScore = group.getSimilarityScore() != null ? group.getSimilarityScore() : 0.0;
                group.setSimilarityScore(Math.max(currentScore, incomingScore));
            }

            if ((group.getRepresentativeLog() == null || group.getRepresentativeLog().isBlank())
                    && aggregationResult.getRepresentativeLog() != null) {
                group.setRepresentativeLog(aggregationResult.getRepresentativeLog());
            }
//            log.debug("更新聚合组: {}, 事件数: {}", group.getGroupId(), group.getEventCount());
            return aggregationGroupRepository.save(group);
        } else {
            // 创建新聚合组
            AggregationGroupEntity group = new AggregationGroupEntity();
            group.setGroupId(aggregationResult.getGroupId());
            group.setName(aggregationResult.getGroupId());
            group.setRepresentativeLog(aggregationResult.getRepresentativeLog());
            group.setEventCount(aggregationResult.getEventCount());
            group.setSeverity(aggregationResult.getSeverity());
            group.setAggregationType(aggregationResult.getAggregationType());
            group.setSourceId(sourceId);
            group.setSourceName(sourceName);
            group.setFirstEventTime(aggregationResult.getAggregatedAt());
            group.setLastEventTime(aggregationResult.getAggregatedAt());
            group.setSimilarityScore(aggregationResult.getSimilarityScore());
            group.setStatus("ACTIVE");
            group.setIsAnalyzed(false);
//            log.info("创建新聚合组: {}, 事件数: {}", group.getGroupId(), group.getEventCount());
            return aggregationGroupRepository.save(group);
        }
    }

    /**
     * 根据ID查询聚合组
     */
    public Optional<AggregationGroupEntity> findById(String id) {
        return aggregationGroupRepository.findById(id);
    }

    /**
     * 根据groupId查询聚合组
     */
    public Optional<AggregationGroupEntity> findByGroupId(String groupId) {
        return aggregationGroupRepository.findByGroupId(groupId);
    }

    /**
     * 按 sourceId + 代表日志精确匹配最近聚合组
     */
    public Optional<AggregationGroupEntity> findLatestBySourceIdAndRepresentativeLog(String sourceId, String representativeLog) {
        if (sourceId == null || sourceId.isBlank() || representativeLog == null || representativeLog.isBlank()) {
            return Optional.empty();
        }
        return aggregationGroupRepository.findTopBySourceIdAndRepresentativeLogOrderByLastEventTimeDesc(sourceId, representativeLog);
    }

    /**
     * 在同一日志源的全部历史聚合组中查找最相似的分组。
     */
    public Optional<AggregationGroupEntity> findBestSimilarGroup(String sourceId, String message, double threshold) {
        if (sourceId == null || sourceId.isBlank() || message == null || message.isBlank()) {
            return Optional.empty();
        }

        AggregationGroupEntity bestGroup = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (AggregationGroupEntity group : aggregationGroupRepository.findBySourceId(sourceId)) {
            if (group.getRepresentativeLog() == null || group.getRepresentativeLog().isBlank()) {
                continue;
            }
            double score = LogTemplateUtils.calculateSimilarity(message, group.getRepresentativeLog());
            if (score >= threshold && score > bestScore) {
                bestGroup = group;
                bestScore = score;
            }
        }

        return Optional.ofNullable(bestGroup);
    }

    /**
     * 查询所有活跃聚合组
     */
    public List<AggregationGroupEntity> findActiveGroups() {
        return aggregationGroupRepository.findActiveGroups();
    }

    /**
     * 分页查询聚合组
     */
    public Page<AggregationGroupEntity> findByStatus(String status, Pageable pageable) {
        return aggregationGroupRepository.findByStatus(status, pageable);
    }

    /**
     * 查询所有聚合组（分页）
     */
    public Page<AggregationGroupEntity> findAll(Pageable pageable) {
        return aggregationGroupRepository.findAll(pageable);
    }

    /**
     * 按可选条件分页查询聚合组
     */
    public Page<AggregationGroupEntity> findByFilters(String analysisStatus, String severity, String sourceId, Pageable pageable) {
        return aggregationGroupRepository.findByFilters(analysisStatus, severity, sourceId, pageable);
    }

    /**
     * 根据日志源ID查询聚合组
     */
    public List<AggregationGroupEntity> findBySourceId(String sourceId) {
        return aggregationGroupRepository.findBySourceId(sourceId);
    }

    /**
     * 根据严重程度查询聚合组
     */
    public List<AggregationGroupEntity> findBySeverity(String severity) {
        return aggregationGroupRepository.findBySeverity(severity);
    }

    /**
     * 查询未分析的聚合组
     */
    public List<AggregationGroupEntity> findUnanalyzedGroups() {
        return aggregationGroupRepository.findByIsAnalyzedFalse();
    }

    /**
     * 标记为已分析
     */
    @Transactional
    public void markAsAnalyzed(String groupId) {
        aggregationGroupRepository.findByGroupId(groupId).ifPresent(group -> {
            group.setIsAnalyzed(true);
            group.setStatus("ANALYZED");
            aggregationGroupRepository.save(group);
        });
    }

    /**
     * 清理超时的聚合组
     */
    @Transactional
    public void cleanupExpiredGroups(int timeoutMinutes) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(timeoutMinutes);
        int updated = aggregationGroupRepository.expireOldGroups(cutoffTime);
        log.info("清理过期聚合组: {} 个", updated);
    }

    /**
     * 统计各严重程度数量
     */
    public Map<String, Long> countBySeverity() {
        List<Object[]> results = aggregationGroupRepository.countBySeverity();
        return results.stream()
                .collect(Collectors.toMap(
                        result -> (String) result[0],
                        result -> (Long) result[1]
                ));
    }

    /**
     * 获取统计摘要
     */
    public Map<String, Object> getSummary() {
        long total = aggregationGroupRepository.count();
        long active = aggregationGroupRepository.countByStatus("ACTIVE");
        long expired = aggregationGroupRepository.countByStatus("EXPIRED");
        long analyzed = aggregationGroupRepository.countByStatus("ANALYZED");

        Map<String, Long> severityCounts = countBySeverity();

        return Map.of(
                "total", total,
                "active", active,
                "expired", expired,
                "analyzed", analyzed,
                "severityCounts", severityCounts
        );
    }

    /**
     * 按代表日志相似度重新组合已有聚合组，并同步迁移组内日志与 AI 分析结果。
     */
    @Transactional
    public Map<String, Object> recombineSimilarGroups(double threshold) {
        List<AggregationGroupEntity> groups = aggregationGroupRepository.findAll().stream()
                .filter(group -> group.getGroupId() != null && !group.getGroupId().isBlank())
                .filter(group -> group.getRepresentativeLog() != null && !group.getRepresentativeLog().isBlank())
                .sorted(this::compareGroupsForMerge)
                .collect(Collectors.toCollection(ArrayList::new));

        Set<String> removedIds = new HashSet<>();
        int mergedGroups = 0;
        int migratedLogs = 0;
        int mergedAnalysisResults = 0;
        List<Map<String, Object>> mergeDetails = new ArrayList<>();

        for (int i = 0; i < groups.size(); i++) {
            AggregationGroupEntity target = groups.get(i);
            if (target.getId() == null || removedIds.contains(target.getId())) {
                continue;
            }

            for (int j = i + 1; j < groups.size(); j++) {
                AggregationGroupEntity source = groups.get(j);
                if (source.getId() == null || removedIds.contains(source.getId())) {
                    continue;
                }
                if (!sameSource(target, source)) {
                    continue;
                }

                double score = LogTemplateUtils.calculateSimilarity(
                        target.getRepresentativeLog(),
                        source.getRepresentativeLog());
                if (score < threshold) {
                    continue;
                }

                int movedLogCount = rawLogEventRepository.updateAggregationGroupId(source.getGroupId(), target.getGroupId());
                int movedAnalysisCount = mergeAnalysisResults(target, source);
                mergeGroupMetadata(target, source, score, movedLogCount);
                aggregationGroupRepository.save(target);
                aggregationGroupRepository.delete(source);

                removedIds.add(source.getId());
                mergedGroups++;
                migratedLogs += movedLogCount;
                mergedAnalysisResults += movedAnalysisCount;
                mergeDetails.add(Map.of(
                        "targetGroupId", target.getGroupId(),
                        "sourceGroupId", source.getGroupId(),
                        "similarityScore", score,
                        "migratedLogs", movedLogCount,
                        "mergedAnalysisResults", movedAnalysisCount
                ));
            }
        }

        return Map.of(
                "threshold", threshold,
                "scannedGroups", groups.size(),
                "mergedGroups", mergedGroups,
                "migratedLogs", migratedLogs,
                "mergedAnalysisResults", mergedAnalysisResults,
                "details", mergeDetails
        );
    }

    /**
     * 删除聚合组
     */
    @Transactional
    public void delete(String id) {
        aggregationGroupRepository.deleteById(id);
    }

    /**
     * 删除指定日志源关联的所有聚合组
     */
    @Transactional
    public int deleteBySourceId(String sourceId) {
        int deleted = aggregationGroupRepository.deleteBySourceId(sourceId);
        if (deleted > 0) {
            log.info("删除日志源 {} 关联聚合组: {} 个", sourceId, deleted);
        }
        return deleted;
    }

    /**
     * 删除指定日志源关联的所有聚合组（优先按 sourceId，兼容按 sourceName 清理历史异常数据）
     */
    @Transactional
    public int deleteBySourceIdOrSourceName(String sourceId, String sourceName) {
        int deleted = aggregationGroupRepository.deleteBySourceIdOrSourceName(sourceId, sourceName);
        if (deleted > 0) {
            log.info("删除日志源关联聚合组: sourceId={}, sourceName={}, deleted={}", sourceId, sourceName, deleted);
        }
        return deleted;
    }

    /**
     * 清理 sourceId 对应日志源已不存在的孤儿聚合组
     */
    @Transactional
    public int cleanupOrphanGroups() {
        List<String> sourceIds = aggregationGroupRepository.findDistinctSourceIds();
        if (sourceIds.isEmpty()) {
            return 0;
        }

        Set<String> existingSourceIds = new HashSet<>();
        List<String> orphanSourceIds = new ArrayList<>();

        for (String sourceId : sourceIds) {
            if (sourceId == null || sourceId.isBlank()) {
                continue;
            }

            if (existingSourceIds.contains(sourceId)) {
                continue;
            }

            boolean exists;
            try {
                exists = logSourceRepository.existsById(UUID.fromString(sourceId));
            } catch (IllegalArgumentException ex) {
                exists = false;
            }

            if (exists) {
                existingSourceIds.add(sourceId);
            } else {
                orphanSourceIds.add(sourceId);
            }
        }

        if (orphanSourceIds.isEmpty()) {
            return 0;
        }

        int deleted = aggregationGroupRepository.deleteBySourceIdIn(orphanSourceIds);
        log.warn("清理孤儿聚合组完成: 删除 {} 个，涉及已失联日志源 {}", deleted, orphanSourceIds);
        return deleted;
    }

    /**
     * 根据ID更新事件数
     */
    @Transactional
    public void updateEventCount(String groupId, int eventCount) {
        aggregationGroupRepository.findByGroupId(groupId).ifPresent(group -> {
            group.setEventCount(eventCount);
            group.setLastEventTime(LocalDateTime.now());
            aggregationGroupRepository.save(group);
        });
    }

    private String mergeSeverity(String currentSeverity, String incomingSeverity) {
        if (incomingSeverity == null || incomingSeverity.isBlank()) {
            return currentSeverity;
        }
        if (currentSeverity == null || currentSeverity.isBlank()) {
            return incomingSeverity;
        }

        int currentPriority = SEVERITY_PRIORITY.getOrDefault(currentSeverity.toUpperCase(), 1);
        int incomingPriority = SEVERITY_PRIORITY.getOrDefault(incomingSeverity.toUpperCase(), 1);
        return incomingPriority >= currentPriority ? incomingSeverity : currentSeverity;
    }

    private int compareGroupsForMerge(AggregationGroupEntity left, AggregationGroupEntity right) {
        int countCompare = Integer.compare(
                right.getEventCount() != null ? right.getEventCount() : 0,
                left.getEventCount() != null ? left.getEventCount() : 0);
        if (countCompare != 0) {
            return countCompare;
        }

        LocalDateTime leftTime = left.getLastEventTime();
        LocalDateTime rightTime = right.getLastEventTime();
        if (leftTime == null && rightTime == null) {
            return 0;
        }
        if (leftTime == null) {
            return 1;
        }
        if (rightTime == null) {
            return -1;
        }
        return rightTime.compareTo(leftTime);
    }

    private boolean sameSource(AggregationGroupEntity left, AggregationGroupEntity right) {
        String leftSource = left.getSourceId() != null && !left.getSourceId().isBlank() ? left.getSourceId() : "GLOBAL";
        String rightSource = right.getSourceId() != null && !right.getSourceId().isBlank() ? right.getSourceId() : "GLOBAL";
        return leftSource.equals(rightSource);
    }

    private void mergeGroupMetadata(
            AggregationGroupEntity target,
            AggregationGroupEntity source,
            double similarityScore,
            int migratedLogCount) {
        Integer targetCount = target.getEventCount() != null ? target.getEventCount() : 0;
        Integer sourceCount = source.getEventCount() != null ? source.getEventCount() : 0;
        long rawCount = rawLogEventRepository.countByAggregationGroupId(target.getGroupId());
        if (rawCount > 0) {
            target.setEventCount(Math.toIntExact(Math.min(rawCount, Integer.MAX_VALUE)));
        } else {
            target.setEventCount(targetCount + Math.max(sourceCount, migratedLogCount));
        }

        target.setSeverity(mergeSeverity(target.getSeverity(), source.getSeverity()));
        target.setFirstEventTime(earliest(target.getFirstEventTime(), source.getFirstEventTime()));
        target.setLastEventTime(latest(target.getLastEventTime(), source.getLastEventTime()));
        target.setSimilarityScore(Math.max(
                target.getSimilarityScore() != null ? target.getSimilarityScore() : 0.0,
                similarityScore));

        boolean hasAnalysis = !analysisResultRepository.findAllByAggregationId(target.getGroupId()).isEmpty();
        if (Boolean.TRUE.equals(target.getIsAnalyzed()) || Boolean.TRUE.equals(source.getIsAnalyzed()) || hasAnalysis) {
            target.setIsAnalyzed(true);
            target.setStatus("ANALYZED");
        }
    }

    private int mergeAnalysisResults(AggregationGroupEntity target, AggregationGroupEntity source) {
        List<AnalysisResultEntity> targetResults = new ArrayList<>(
                analysisResultRepository.findAllByAggregationId(target.getGroupId()));
        List<AnalysisResultEntity> sourceResults = new ArrayList<>(
                analysisResultRepository.findAllByAggregationId(source.getGroupId()));

        if (targetResults.isEmpty() && sourceResults.isEmpty()) {
            return 0;
        }

        List<AnalysisResultEntity> allResults = new ArrayList<>();
        allResults.addAll(targetResults);
        allResults.addAll(sourceResults);
        allResults.sort(this::compareAnalysisResultsForMerge);

        AnalysisResultEntity primary = allResults.get(0);
        primary.setAggregationId(target.getGroupId());
        primary.setAggregationName(limitLength(target.getName(), AGGREGATION_NAME_MAX_LENGTH));

        int mergedCount = 0;
        for (int i = 1; i < allResults.size(); i++) {
            AnalysisResultEntity duplicate = allResults.get(i);
            mergeAnalysisResult(primary, duplicate, duplicate.getAggregationId());
            analysisResultRepository.delete(duplicate);
            mergedCount++;
        }

        if (allResults.size() == 1 && sourceResults.contains(primary)) {
            mergedCount = 1;
        }

        analysisResultRepository.save(primary);
        return mergedCount;
    }

    private int compareAnalysisResultsForMerge(AnalysisResultEntity left, AnalysisResultEntity right) {
        boolean leftCompleted = "COMPLETED".equalsIgnoreCase(left.getStatus());
        boolean rightCompleted = "COMPLETED".equalsIgnoreCase(right.getStatus());
        if (leftCompleted != rightCompleted) {
            return leftCompleted ? -1 : 1;
        }

        LocalDateTime leftTime = left.getCompletedAt() != null ? left.getCompletedAt() : left.getCreatedAt();
        LocalDateTime rightTime = right.getCompletedAt() != null ? right.getCompletedAt() : right.getCreatedAt();
        if (leftTime == null && rightTime == null) {
            return 0;
        }
        if (leftTime == null) {
            return 1;
        }
        if (rightTime == null) {
            return -1;
        }
        return rightTime.compareTo(leftTime);
    }

    private void mergeAnalysisResult(
            AnalysisResultEntity target,
            AnalysisResultEntity source,
            String sourceGroupId) {
        target.setRootCause(mergeTextBlock(target.getRootCause(), source.getRootCause(), sourceGroupId, "根因分析"));
        target.setAnalysisDetail(mergeTextBlock(target.getAnalysisDetail(), source.getAnalysisDetail(), sourceGroupId, "分析详情"));
        target.setAnalysisDetail(mergeTextBlock(target.getAnalysisDetail(), source.getImpactScope(), sourceGroupId, "影响范围"));
        target.setAnalysisDetail(mergeTextBlock(target.getAnalysisDetail(), source.getStatusMessage(), sourceGroupId, "状态信息"));
        target.setImpactScope(limitLength(mergeShortField(target.getImpactScope(), source.getImpactScope()), IMPACT_SCOPE_MAX_LENGTH));
        target.setStatusMessage(limitLength(mergeStatusMessage(target.getStatusMessage(), source.getStatusMessage(), sourceGroupId), STATUS_MESSAGE_MAX_LENGTH));
        target.setRootCauseCategory(limitLength(
                mergeRootCauseCategory(target.getRootCauseCategory(), source.getRootCauseCategory()),
                ROOT_CAUSE_CATEGORY_MAX_LENGTH));
        target.setImpactSeverity(limitLength(
                mergeSeverity(target.getImpactSeverity(), source.getImpactSeverity()),
                IMPACT_SEVERITY_MAX_LENGTH));
        target.setConfidence(mergeConfidence(target.getConfidence(), source.getConfidence()));
        target.setRequestTokens(sum(target.getRequestTokens(), source.getRequestTokens()));
        target.setResponseTokens(sum(target.getResponseTokens(), source.getResponseTokens()));
        target.setProcessingTimeMs(max(target.getProcessingTimeMs(), source.getProcessingTimeMs()));
        target.setCompletedAt(latest(target.getCompletedAt(), source.getCompletedAt()));
        target.setStatus(limitLength(mergeAnalysisStatus(target.getStatus(), source.getStatus()), STATUS_MAX_LENGTH));
        target.setRawResponse(mergeTextBlock(target.getRawResponse(), source.getRawResponse(), sourceGroupId, "原始响应"));
    }

    private String mergeTextBlock(String current, String incoming, String sourceGroupId, String label) {
        if (incoming == null || incoming.isBlank()) {
            return current;
        }
        String trimmedIncoming = incoming.trim();
        String sourceBlock = "[" + sourceGroupId + " " + label + "]\n" + trimmedIncoming;
        if (current == null || current.isBlank()) {
            return sourceBlock;
        }
        if (current.contains(trimmedIncoming)) {
            return current;
        }
        return current.trim() + "\n\n" + sourceBlock;
    }

    private String mergeShortField(String current, String incoming) {
        if (incoming == null || incoming.isBlank()) {
            return current;
        }
        if (current == null || current.isBlank() || current.equalsIgnoreCase(incoming)) {
            return incoming;
        }
        return current + " / " + incoming;
    }

    /**
     * 问题分类只保留一个 tag，不拼接多个类别。
     */
    private String mergeRootCauseCategory(String current, String incoming) {
        if (current != null && !current.isBlank()) {
            return current;
        }
        return incoming;
    }

    private String mergeStatusMessage(String current, String incoming, String sourceGroupId) {
        if (incoming == null || incoming.isBlank()) {
            return current;
        }
        String incomingSummary = "[" + sourceGroupId + "] " + incoming.trim();
        if (current == null || current.isBlank()) {
            return incomingSummary;
        }
        if (current.contains(incoming.trim()) || current.contains(sourceGroupId)) {
            return current;
        }
        return current.trim() + " | " + incomingSummary;
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

    private String mergeAnalysisStatus(String current, String incoming) {
        if ("COMPLETED".equalsIgnoreCase(current) || "COMPLETED".equalsIgnoreCase(incoming)) {
            return "COMPLETED";
        }
        if (current == null || current.isBlank()) {
            return incoming;
        }
        return current;
    }

    private Double mergeConfidence(Double current, Double incoming) {
        if (current == null) {
            return incoming;
        }
        if (incoming == null) {
            return current;
        }
        return Math.max(current, incoming);
    }

    private Integer sum(Integer current, Integer incoming) {
        if (current == null) {
            return incoming;
        }
        if (incoming == null) {
            return current;
        }
        return current + incoming;
    }

    private Long sum(Long current, Long incoming) {
        if (current == null) {
            return incoming;
        }
        if (incoming == null) {
            return current;
        }
        return current + incoming;
    }

    private Long max(Long current, Long incoming) {
        if (current == null) {
            return incoming;
        }
        if (incoming == null) {
            return current;
        }
        return Math.max(current, incoming);
    }

    private LocalDateTime earliest(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }

    private LocalDateTime latest(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }
}
