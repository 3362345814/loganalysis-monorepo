package com.evelin.loganalysis.logprocessing.service;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
            group.setEventCount(aggregationResult.getEventCount());
            group.setLastEventTime(aggregationResult.getAggregatedAt());
            group.setSeverity(aggregationResult.getSeverity());
            group.setSimilarityScore(aggregationResult.getSimilarityScore());
            log.debug("更新聚合组: {}, 事件数: {}", group.getGroupId(), group.getEventCount());
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
            log.info("创建新聚合组: {}, 事件数: {}", group.getGroupId(), group.getEventCount());
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
}
