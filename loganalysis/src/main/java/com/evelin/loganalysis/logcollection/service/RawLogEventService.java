package com.evelin.loganalysis.logcollection.service;

import com.evelin.loganalysis.logcollection.model.RawLogEvent;
import com.evelin.loganalysis.logcollection.model.entity.RawLogEventEntity;
import com.evelin.loganalysis.logcollection.repository.RawLogEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 原始日志事件服务层
 *
 * @author Evelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RawLogEventService {

    private final RawLogEventRepository rawLogEventRepository;

    /**
     * 保存原始日志事件
     *
     * @param event 原始日志事件
     * @return 保存后的实体
     */
    @Transactional
    public RawLogEventEntity save(RawLogEvent event) {
        RawLogEventEntity entity = RawLogEventEntity.from(event);
        RawLogEventEntity saved = rawLogEventRepository.save(entity);
        log.debug("保存原始日志事件: {}", saved.getEventId());
        return saved;
    }

    /**
     * 批量保存原始日志事件
     *
     * @param events 原始日志事件列表
     * @return 保存后的实体列表
     */
    @Transactional
    public List<RawLogEventEntity> saveAll(List<RawLogEvent> events) {
        List<RawLogEventEntity> entities = events.stream()
                .map(RawLogEventEntity::from)
                .collect(Collectors.toList());
        List<RawLogEventEntity> saved = rawLogEventRepository.saveAll(entities);
        log.info("批量保存原始日志事件: {} 条", saved.size());
        return saved;
    }

    /**
     * 根据ID查询
     *
     * @param id ID
     * @return 原始日志事件
     */
    public Optional<RawLogEventEntity> findById(UUID id) {
        return rawLogEventRepository.findById(id);
    }

    /**
     * 查询所有原始日志（分页）
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 原始日志事件分页
     */
    public Page<RawLogEventEntity> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return rawLogEventRepository.findAllOrderByCollectionTimeDesc(pageable);
    }

    /**
     * 根据事件ID查询
     *
     * @param eventId 事件ID
     * @return 原始日志事件
     */
    public Optional<RawLogEventEntity> findByEventId(String eventId) {
        return rawLogEventRepository.findByEventId(eventId);
    }

    /**
     * 根据日志源ID查询（分页）
     *
     * @param sourceId 日志源ID
     * @param page     页码（从0开始）
     * @param size     每页大小
     * @return 原始日志事件分页
     */
    public Page<RawLogEventEntity> findBySourceId(UUID sourceId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return rawLogEventRepository.findBySourceIdOrderByOriginalLogTimeDesc(sourceId, pageable);
    }

    /**
     * 根据日志源ID查询
     *
     * @param sourceId 日志源ID
     * @return 原始日志事件列表
     */
    public List<RawLogEventEntity> findBySourceId(UUID sourceId) {
        return rawLogEventRepository.findBySourceId(sourceId);
    }

    /**
     * 根据时间范围查询
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param page      页码（从0开始）
     * @param size      每页大小
     * @return 原始日志事件分页
     */
    public Page<RawLogEventEntity> findByTimeRange(LocalDateTime startTime, LocalDateTime endTime, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return rawLogEventRepository.findByCollectionTimeBetweenOrderByCollectionTimeDesc(startTime, endTime, pageable);
    }

    /**
     * 根据日志源ID和时间范围查询
     *
     * @param sourceId  日志源ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param page      页码（从0开始）
     * @param size      每页大小
     * @return 原始日志事件分页
     */
    public Page<RawLogEventEntity> findBySourceIdAndTimeRange(
            UUID sourceId, LocalDateTime startTime, LocalDateTime endTime, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return rawLogEventRepository.findBySourceIdAndCollectionTimeBetweenOrderByCollectionTimeDesc(
                sourceId, startTime, endTime, pageable);
    }

    public Page<RawLogEventEntity> findBySourceIdAndLogLevel(UUID sourceId, String logLevel, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return rawLogEventRepository.findBySourceIdAndLogLevelOrderByCollectionTimeDesc(sourceId, logLevel, pageable);
    }

    public Page<RawLogEventEntity> findBySourceIdAndLogLevelAndTimeRange(
            UUID sourceId, String logLevel, LocalDateTime startTime, LocalDateTime endTime, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return rawLogEventRepository.findBySourceIdAndLogLevelAndCollectionTimeBetweenOrderByCollectionTimeDesc(
                sourceId, logLevel, startTime, endTime, pageable);
    }

    /**
     * 根据内容模糊查询
     *
     * @param content 模糊内容
     * @param page    页码（从0开始）
     * @param size    每页大小
     * @return 原始日志事件分页
     */
    public Page<RawLogEventEntity> findByContentContaining(String content, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return rawLogEventRepository.findByContentContaining(content, pageable);
    }

    /**
     * 根据日志源ID和内容模糊查询
     *
     * @param sourceId 日志源ID
     * @param content  模糊内容
     * @param page     页码（从0开始）
     * @param size     每页大小
     * @return 原始日志事件分页
     */
    public Page<RawLogEventEntity> findBySourceIdAndContentContaining(
            UUID sourceId, String content, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return rawLogEventRepository.findBySourceIdAndContentContaining(sourceId, content, pageable);
    }

    /**
     * 统计指定日志源的日志数量
     *
     * @param sourceId 日志源ID
     * @return 数量
     */
    public long countBySourceId(UUID sourceId) {
        return rawLogEventRepository.countBySourceId(sourceId);
    }

    /**
     * 根据日志源ID和日志级别统计日志数量
     *
     * @param sourceId 日志源ID
     * @param logLevel 日志级别
     * @return 数量
     */
    public long countBySourceIdAndLogLevel(UUID sourceId, String logLevel) {
        return rawLogEventRepository.countBySourceIdAndLogLevel(sourceId, logLevel);
    }

    /**
     * 统计指定时间范围内的日志数量
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 数量
     */
    public long countByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return rawLogEventRepository.countByCollectionTimeBetween(startTime, endTime);
    }

    /**
     * 删除指定时间之前的日志（清理旧数据）
     *
     * @param beforeTime 时间阈值
     * @return 删除的记录数
     */
    @Transactional
    public long deleteByCollectionTimeBefore(LocalDateTime beforeTime) {
        long count = rawLogEventRepository.deleteByCollectionTimeBefore(beforeTime);
        log.info("清理 {} 之前的原始日志事件: {} 条", beforeTime, count);
        return count;
    }

    /**
     * 清理指定天数之前的日志
     *
     * @param days 天数
     * @return 删除的记录数
     */
    public long cleanupOldLogs(int days) {
        LocalDateTime beforeTime = LocalDateTime.now().minusDays(days);
        return deleteByCollectionTimeBefore(beforeTime);
    }

    /**
     * 根据聚合组ID查询日志（分页）
     *
     * @param aggregationGroupId 聚合组ID
     * @param page               页码（从0开始）
     * @param size               每页大小
     * @return 原始日志事件分页
     */
    public Page<RawLogEventEntity> findByAggregationGroupId(String aggregationGroupId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return rawLogEventRepository.findByAggregationGroupId(aggregationGroupId, pageable);
    }

    /**
     * 根据聚合组ID查询所有日志
     *
     * @param aggregationGroupId 聚合组ID
     * @return 原始日志事件列表
     */
    public List<RawLogEventEntity> findAllByAggregationGroupId(String aggregationGroupId) {
        return rawLogEventRepository.findAllByAggregationGroupId(aggregationGroupId);
    }

    /**
     * 统计聚合组内的日志数量
     *
     * @param aggregationGroupId 聚合组ID
     * @return 数量
     */
    public long countByAggregationGroupId(String aggregationGroupId) {
        return rawLogEventRepository.countByAggregationGroupId(aggregationGroupId);
    }

    /**
     * 根据 traceId 查询日志（分页）
     *
     * @param traceId 链路追踪ID
     * @param page    页码（从0开始）
     * @param size    每页大小
     * @return 原始日志事件分页
     */
    public Page<RawLogEventEntity> findByTraceId(String traceId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return rawLogEventRepository.findByTraceId(traceId, pageable);
    }

    /**
     * 根据 traceId 查询所有日志
     *
     * @param traceId 链路追踪ID
     * @return 原始日志事件列表
     */
    public List<RawLogEventEntity> findAllByTraceId(String traceId) {
        return rawLogEventRepository.findAllByTraceIdOrderByOriginalLogTimeAsc(traceId);
    }

    /**
     * 统计指定 traceId 的日志数量
     *
     * @param traceId 链路追踪ID
     * @return 数量
     */
    public long countByTraceId(String traceId) {
        return rawLogEventRepository.countByTraceId(traceId);
    }

    /**
     * 按 sourceId 分组统计日志数量（包含 null）
     */
    public Map<UUID, Long> countGroupBySourceId() {
        List<Object[]> rows = rawLogEventRepository.countGroupBySourceId();
        Map<UUID, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            UUID sourceId = row[0] instanceof UUID ? (UUID) row[0] : null;
            Long count = row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L;
            result.put(sourceId, count);
        }
        return result;
    }

    public long countWithNullSourceId() {
        return rawLogEventRepository.countBySourceIdIsNull();
    }
}
