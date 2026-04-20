package com.evelin.loganalysis.logprocessing.service;

import com.evelin.loganalysis.logprocessing.dto.AggregationResult;
import com.evelin.loganalysis.logprocessing.entity.AggregationGroupEntity;
import com.evelin.loganalysis.logprocessing.repository.AggregationGroupRepository;
import com.evelin.loganalysis.logcollection.repository.LogSourceRepository;
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
    private static final Map<String, Integer> SEVERITY_PRIORITY = Map.of(
            "INFO", 1,
            "WARNING", 2,
            "ERROR", 3,
            "CRITICAL", 4
    );

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
}
