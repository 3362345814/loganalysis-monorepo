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
import java.util.List;
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
     * 异步批量保存原始日志事件
     *
     * @param events 原始日志事件列表
     */
    @Async
    @Transactional
    public void saveAllAsync(List<RawLogEvent> events) {
        saveAll(events);
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
        return rawLogEventRepository.findBySourceIdOrderByCollectionTimeDesc(sourceId, pageable);
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
}
