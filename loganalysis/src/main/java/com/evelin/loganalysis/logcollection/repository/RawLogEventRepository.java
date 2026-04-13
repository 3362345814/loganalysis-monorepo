package com.evelin.loganalysis.logcollection.repository;

import com.evelin.loganalysis.logcollection.model.entity.RawLogEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
    @Query("SELECT r FROM RawLogEventEntity r ORDER BY r.originalLogTime DESC NULLS LAST, r.collectionTime DESC")
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
     * 按 originalLogTime 降序排序，originalLogTime 相同时按 collectionTime 降序排序
     *
     * @param sourceId 日志源ID
     * @param pageable 分页参数
     * @return 原始日志事件分页
     */
    @Query("SELECT r FROM RawLogEventEntity r WHERE r.sourceId = :sourceId ORDER BY r.originalLogTime DESC NULLS LAST, r.collectionTime DESC")
    Page<RawLogEventEntity> findBySourceIdOrderByOriginalLogTimeDesc(UUID sourceId, Pageable pageable);

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
    @Query("SELECT r FROM RawLogEventEntity r WHERE r.rawContent LIKE %:content% ORDER BY r.originalLogTime DESC NULLS LAST, r.collectionTime DESC")
    Page<RawLogEventEntity> findByContentContaining(@Param("content") String content, Pageable pageable);

    /**
     * 根据日志源ID和内容模糊查询
     *
     * @param sourceId 日志源ID
     * @param content  模糊内容
     * @param pageable 分页参数
     * @return 原始日志事件分页
     */
    @Query("SELECT r FROM RawLogEventEntity r WHERE r.sourceId = :sourceId AND r.rawContent LIKE %:content% ORDER BY r.originalLogTime DESC NULLS LAST, r.collectionTime DESC")
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
     * 根据日志源ID和日志级别统计日志数量
     */
    long countBySourceIdAndLogLevel(UUID sourceId, String logLevel);

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

    /**
     * 根据聚合组ID查询日志（分页）
     *
     * @param aggregationGroupId 聚合组ID
     * @param pageable          分页参数
     * @return 原始日志事件分页
     */
    @Query("SELECT r FROM RawLogEventEntity r WHERE r.aggregationGroupId = :aggregationGroupId ORDER BY r.originalLogTime DESC NULLS LAST, r.collectionTime DESC")
    Page<RawLogEventEntity> findByAggregationGroupId(@Param("aggregationGroupId") String aggregationGroupId, Pageable pageable);

    /**
     * 根据聚合组ID查询所有日志
     *
     * @param aggregationGroupId 聚合组ID
     * @return 原始日志事件列表
     */
    List<RawLogEventEntity> findAllByAggregationGroupId(String aggregationGroupId);

    /**
     * 统计聚合组内的日志数量
     *
     * @param aggregationGroupId 聚合组ID
     * @return 数量
     */
    long countByAggregationGroupId(String aggregationGroupId);

    /**
     * 根据日志源ID删除所有日志
     *
     * @param sourceId 日志源ID
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM RawLogEventEntity r WHERE r.sourceId = :sourceId")
    int deleteBySourceId(@Param("sourceId") UUID sourceId);

    /**
     * 根据 traceId 查询日志（分页，按日志时间升序）
     *
     * @param traceId  链路追踪ID
     * @param pageable 分页参数
     * @return 原始日志事件分页
     */
    @Query("SELECT r FROM RawLogEventEntity r WHERE r.traceId = :traceId ORDER BY r.originalLogTime ASC NULLS LAST, r.collectionTime ASC")
    Page<RawLogEventEntity> findByTraceId(@Param("traceId") String traceId, Pageable pageable);

    /**
     * 根据 traceId 查询所有日志
     *
     * @param traceId 链路追踪ID
     * @return 原始日志事件列表
     */
    List<RawLogEventEntity> findAllByTraceIdOrderByOriginalLogTimeAsc(String traceId);

    /**
     * 统计指定 traceId 的日志数量
     *
     * @param traceId 链路追踪ID
     * @return 数量
     */
    long countByTraceId(String traceId);

    /**
     * 按 ID 升序查询，用于增量同步（避免时间戳时钟偏移问题）
     */
    @Query("SELECT e FROM RawLogEventEntity e WHERE e.id > :id ORDER BY e.id ASC")
    Page<RawLogEventEntity> findByIdGreaterThanOrderByIdAsc(@Param("id") UUID id, Pageable pageable);

    /**
     * 查询所有 ID 大于指定值的日志（按 ID 升序）
     */
    @Query("SELECT e FROM RawLogEventEntity e WHERE e.id > :id ORDER BY e.id ASC")
    Page<RawLogEventEntity> findAllByIdGreaterThanOrderByIdAsc(@Param("id") UUID id, Pageable pageable);

    /**
     * 查询所有日志（按 ID 升序），用于从头开始的同步
     */
    @Query("SELECT e FROM RawLogEventEntity e ORDER BY e.id ASC")
    Page<RawLogEventEntity> findAllOrderByIdAsc(Pageable pageable);

    /**
     * 获取当前最大的日志 ID
     */
    @Query("SELECT MAX(e.id) FROM RawLogEventEntity e")
    UUID findMaxId();

    /**
     * 按 sourceId 分组统计日志数量（包含 null）
     */
    @Query("SELECT r.sourceId, COUNT(r) FROM RawLogEventEntity r GROUP BY r.sourceId")
    List<Object[]> countGroupBySourceId();

    /**
     * sourceId 为空的日志数量
     */
    long countBySourceIdIsNull();

    /**
     * 按天统计链路耗时分位（P50/P95/P99）
     *
     * 先按 traceId 计算每条链路耗时（同一 trace 最早/最晚日志时间差，单位秒），
     * 再按天计算分位值。
     */
    @Query(value = """
            WITH trace_durations AS (
                SELECT
                    CAST(date_trunc('day', MIN(r.original_log_time)) AS date) AS day_date,
                    EXTRACT(EPOCH FROM (
                        MAX(r.original_log_time)
                        - MIN(r.original_log_time)
                    )) AS duration_sec,
                    COUNT(*) AS log_count
                FROM raw_log_events r
                LEFT JOIN log_sources s ON s.id = r.source_id
                WHERE r.trace_id IS NOT NULL
                  AND r.trace_id <> ''
                  AND r.original_log_time IS NOT NULL
                  AND r.original_log_time >= :startTime
                  AND r.original_log_time <= :endTime
                  AND s.project_id = :projectId
                GROUP BY r.trace_id
                HAVING COUNT(*) >= 2
            )
            SELECT
                day_date,
                percentile_cont(0.50) WITHIN GROUP (ORDER BY duration_sec) AS p50,
                percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_sec) AS p95,
                percentile_cont(0.99) WITHIN GROUP (ORDER BY duration_sec) AS p99,
                COUNT(*) AS sample_count
            FROM trace_durations
            WHERE duration_sec > 0 AND duration_sec <= :maxDurationSec
            GROUP BY day_date
            ORDER BY day_date
            """, nativeQuery = true)
    List<Object[]> findTraceDurationPercentilesByDayAndProject(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("projectId") UUID projectId,
            @Param("maxDurationSec") Double maxDurationSec
    );

    @Query(value = """
            WITH trace_durations AS (
                SELECT
                    CAST(date_trunc('day', MIN(r.original_log_time)) AS date) AS day_date,
                    EXTRACT(EPOCH FROM (
                        MAX(r.original_log_time)
                        - MIN(r.original_log_time)
                    )) AS duration_sec,
                    COUNT(*) AS log_count
                FROM raw_log_events r
                WHERE r.trace_id IS NOT NULL
                  AND r.trace_id <> ''
                  AND r.original_log_time IS NOT NULL
                  AND r.original_log_time >= :startTime
                  AND r.original_log_time <= :endTime
                GROUP BY r.trace_id
                HAVING COUNT(*) >= 2
            )
            SELECT
                day_date,
                percentile_cont(0.50) WITHIN GROUP (ORDER BY duration_sec) AS p50,
                percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_sec) AS p95,
                percentile_cont(0.99) WITHIN GROUP (ORDER BY duration_sec) AS p99,
                COUNT(*) AS sample_count
            FROM trace_durations
            WHERE duration_sec > 0 AND duration_sec <= :maxDurationSec
            GROUP BY day_date
            ORDER BY day_date
            """, nativeQuery = true)
    List<Object[]> findTraceDurationPercentilesByDay(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("maxDurationSec") Double maxDurationSec
    );

    @Query(value = """
            WITH trace_durations AS (
                SELECT
                    date_trunc('hour', MIN(r.original_log_time))
                    + (FLOOR(EXTRACT(MINUTE FROM MIN(r.original_log_time)) / 30) * INTERVAL '30 minutes') AS bucket_time,
                    EXTRACT(EPOCH FROM (
                        MAX(r.original_log_time)
                        - MIN(r.original_log_time)
                    )) AS duration_sec
                FROM raw_log_events r
                LEFT JOIN log_sources s ON s.id = r.source_id
                WHERE r.trace_id IS NOT NULL
                  AND r.trace_id <> ''
                  AND r.original_log_time IS NOT NULL
                  AND r.original_log_time >= :startTime
                  AND r.original_log_time <= :endTime
                  AND s.project_id = :projectId
                GROUP BY r.trace_id
                HAVING COUNT(*) >= 2
            )
            SELECT
                bucket_time,
                percentile_cont(0.50) WITHIN GROUP (ORDER BY duration_sec) AS p50,
                percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_sec) AS p95,
                percentile_cont(0.99) WITHIN GROUP (ORDER BY duration_sec) AS p99,
                COUNT(*) AS sample_count
            FROM trace_durations
            WHERE duration_sec > 0 AND duration_sec <= :maxDurationSec
            GROUP BY bucket_time
            ORDER BY bucket_time
            """, nativeQuery = true)
    List<Object[]> findTraceDurationPercentilesByHalfHourAndProject(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("projectId") UUID projectId,
            @Param("maxDurationSec") Double maxDurationSec
    );

    @Query(value = """
            WITH trace_durations AS (
                SELECT
                    date_trunc('hour', MIN(r.original_log_time))
                    + (FLOOR(EXTRACT(MINUTE FROM MIN(r.original_log_time)) / 30) * INTERVAL '30 minutes') AS bucket_time,
                    EXTRACT(EPOCH FROM (
                        MAX(r.original_log_time)
                        - MIN(r.original_log_time)
                    )) AS duration_sec
                FROM raw_log_events r
                WHERE r.trace_id IS NOT NULL
                  AND r.trace_id <> ''
                  AND r.original_log_time IS NOT NULL
                  AND r.original_log_time >= :startTime
                  AND r.original_log_time <= :endTime
                GROUP BY r.trace_id
                HAVING COUNT(*) >= 2
            )
            SELECT
                bucket_time,
                percentile_cont(0.50) WITHIN GROUP (ORDER BY duration_sec) AS p50,
                percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_sec) AS p95,
                percentile_cont(0.99) WITHIN GROUP (ORDER BY duration_sec) AS p99,
                COUNT(*) AS sample_count
            FROM trace_durations
            WHERE duration_sec > 0 AND duration_sec <= :maxDurationSec
            GROUP BY bucket_time
            ORDER BY bucket_time
            """, nativeQuery = true)
    List<Object[]> findTraceDurationPercentilesByHalfHour(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("maxDurationSec") Double maxDurationSec
    );
}
