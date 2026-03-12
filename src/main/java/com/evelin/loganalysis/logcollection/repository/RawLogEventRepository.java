package com.evelin.loganalysis.logcollection.repository;

import com.evelin.loganalysis.logcollection.model.entity.RawLogEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 原始日志事件数据访问接口
 *
 * @author Evelin
 */
@Repository
public interface RawLogEventRepository extends JpaRepository<RawLogEventEntity, UUID> {

    /**
     * 查询所有原始日志（分页，按采集时间倒序）
     *
     * @param pageable 分页参数
     * @return 原始日志事件分页
     */
    @Query("SELECT r FROM RawLogEventEntity r ORDER BY r.collectionTime DESC")
    Page<RawLogEventEntity> findAllOrderByCollectionTimeDesc(Pageable pageable);

    /**
     * 根据事件ID查找
     *
     * @param eventId 事件ID
     * @return 原始日志事件
     */
    Optional<RawLogEventEntity> findByEventId(String eventId);

    /**
     * 根据日志源ID查询原始日志（分页）
     *
     * @param sourceId 日志源ID
     * @param pageable 分页参数
     * @return 原始日志事件分页
     */
    Page<RawLogEventEntity> findBySourceIdOrderByCollectionTimeDesc(UUID sourceId, Pageable pageable);

    /**
     * 根据日志源ID查询原始日志
     *
     * @param sourceId 日志源ID
     * @return 原始日志事件列表
     */
    List<RawLogEventEntity> findBySourceId(UUID sourceId);

    /**
     * 根据文件路径查询
     *
     * @param filePath 文件路径
     * @return 原始日志事件列表
     */
    List<RawLogEventEntity> findByFilePath(String filePath);

    /**
     * 根据时间范围查询
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param pageable  分页参数
     * @return 原始日志事件分页
     */
    Page<RawLogEventEntity> findByCollectionTimeBetweenOrderByCollectionTimeDesc(
            LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * 根据日志源ID和时间范围查询
     *
     * @param sourceId   日志源ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param pageable  分页参数
     * @return 原始日志事件分页
     */
    Page<RawLogEventEntity> findBySourceIdAndCollectionTimeBetweenOrderByCollectionTimeDesc(
            UUID sourceId, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * 根据日志源ID和日志级别查询
     */
    Page<RawLogEventEntity> findBySourceIdAndLogLevelOrderByCollectionTimeDesc(
            UUID sourceId, String logLevel, Pageable pageable);

    /**
     * 根据日志源ID、日志级别和时间范围查询
     */
    Page<RawLogEventEntity> findBySourceIdAndLogLevelAndCollectionTimeBetweenOrderByCollectionTimeDesc(
            UUID sourceId, String logLevel, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * 根据内容模糊查询
     *
     * @param content   模糊内容
     * @param pageable  分页参数
     * @return 原始日志事件分页
     */
    @Query("SELECT r FROM RawLogEventEntity r WHERE r.rawContent LIKE %:content% ORDER BY r.collectionTime DESC")
    Page<RawLogEventEntity> findByContentContaining(@Param("content") String content, Pageable pageable);

    /**
     * 根据日志源ID和内容模糊查询
     *
     * @param sourceId 日志源ID
     * @param content  模糊内容
     * @param pageable 分页参数
     * @return 原始日志事件分页
     */
    @Query("SELECT r FROM RawLogEventEntity r WHERE r.sourceId = :sourceId AND r.rawContent LIKE %:content% ORDER BY r.collectionTime DESC")
    Page<RawLogEventEntity> findBySourceIdAndContentContaining(
            @Param("sourceId") UUID sourceId, @Param("content") String content, Pageable pageable);

    /**
     * 统计指定日志源的日志数量
     *
     * @param sourceId 日志源ID
     * @return 数量
     */
    long countBySourceId(UUID sourceId);

    /**
     * 统计指定时间范围内的日志数量
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 数量
     */
    long countByCollectionTimeBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 删除指定时间之前的日志（清理旧数据）
     *
     * @param beforeTime 时间阈值
     * @return 删除的记录数
     */
    long deleteByCollectionTimeBefore(LocalDateTime beforeTime);

    /**
     * 查询指定时间之前的日志（用于归档）
     *
     * @param beforeTime 时间阈值
     * @return 日志列表
     */
    List<RawLogEventEntity> findByCollectionTimeBefore(LocalDateTime beforeTime);
}
