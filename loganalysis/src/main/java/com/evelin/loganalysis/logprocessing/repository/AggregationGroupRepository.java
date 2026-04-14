package com.evelin.loganalysis.logprocessing.repository;

import com.evelin.loganalysis.logprocessing.entity.AggregationGroupEntity;
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

/**
 * 聚合组 Repository
 *
 * @author Evelin
 */
@Repository
public interface AggregationGroupRepository extends JpaRepository<AggregationGroupEntity, String> {

    /**
     * 根据状态查询聚合组
     */
    List<AggregationGroupEntity> findByStatus(String status);

    /**
     * 分页查询聚合组
     */
    Page<AggregationGroupEntity> findByStatus(String status, Pageable pageable);

    /**
     * 根据日志源ID查询聚合组
     */
    List<AggregationGroupEntity> findBySourceId(String sourceId);

    /**
     * 根据严重程度查询聚合组
     */
    List<AggregationGroupEntity> findBySeverity(String severity);

    /**
     * 查询未分析的聚合组
     */
    List<AggregationGroupEntity> findByIsAnalyzedFalse();

    /**
     * 根据groupId查询
     */
    Optional<AggregationGroupEntity> findByGroupId(String groupId);

    /**
     * 按日志源 + 代表日志精确匹配，取最近一个分组
     */
    Optional<AggregationGroupEntity> findTopBySourceIdAndRepresentativeLogOrderByLastEventTimeDesc(
            String sourceId, String representativeLog);

    /**
     * 查询活跃的聚合组
     */
    @Query("SELECT a FROM AggregationGroupEntity a WHERE a.status = 'ACTIVE' ORDER BY a.lastEventTime DESC")
    List<AggregationGroupEntity> findActiveGroups();

    /**
     * 统计各严重程度的数量
     */
    @Query("SELECT a.severity, COUNT(a) FROM AggregationGroupEntity a WHERE a.status = 'ACTIVE' GROUP BY a.severity")
    List<Object[]> countBySeverity();

    /**
     * 清理超时的聚合组（更新状态为EXPIRED）
     */
    @Modifying
    @Query("UPDATE AggregationGroupEntity a SET a.status = 'EXPIRED' WHERE a.status = 'ACTIVE' AND a.lastEventTime < :cutoffTime")
    int expireOldGroups(LocalDateTime cutoffTime);

    /**
     * 删除指定日志源的聚合组
     */
    @Modifying
    @Query("DELETE FROM AggregationGroupEntity a WHERE a.sourceId = :sourceId")
    int deleteBySourceId(@Param("sourceId") String sourceId);

    /**
     * 批量删除指定日志源集合对应的聚合组
     */
    @Modifying
    @Query("DELETE FROM AggregationGroupEntity a WHERE a.sourceId IN :sourceIds")
    int deleteBySourceIdIn(@Param("sourceIds") List<String> sourceIds);

    /**
     * 查询聚合组中出现过的日志源ID（去重）
     */
    @Query("SELECT DISTINCT a.sourceId FROM AggregationGroupEntity a WHERE a.sourceId IS NOT NULL AND a.sourceId <> ''")
    List<String> findDistinctSourceIds();

    /**
     * 统计总数
     */
    long countByStatus(String status);
}
