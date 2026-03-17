package com.evelin.loganalysis.logalert.repository;

import com.evelin.loganalysis.logalert.enums.AlertLevel;
import com.evelin.loganalysis.logalert.enums.AlertStatus;
import com.evelin.loganalysis.logalert.model.AlertRecord;
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
 * 告警记录Repository
 *
 * @author Evelin
 */
@Repository
public interface AlertRecordRepository extends JpaRepository<AlertRecord, UUID> {

    /**
     * 根据告警编号查询
     */
    Optional<AlertRecord> findByAlertId(String alertId);

    /**
     * 根据规则ID查询告警记录
     */
    Page<AlertRecord> findByRuleId(UUID ruleId, Pageable pageable);

    /**
     * 根据聚合组ID查询告警记录
     */
    Page<AlertRecord> findByAggregationId(UUID aggregationId, Pageable pageable);

    /**
     * 根据状态查询告警记录
     */
    Page<AlertRecord> findByStatus(AlertStatus status, Pageable pageable);

    /**
     * 根据告警级别查询告警记录
     */
    Page<AlertRecord> findByAlertLevel(AlertLevel alertLevel, Pageable pageable);

    /**
     * 查询指定状态的告警记录列表
     */
    List<AlertRecord> findByStatus(AlertStatus status);

    /**
     * 查询待处理告警记录
     */
    @Query("SELECT ar FROM AlertRecord ar WHERE ar.status = 'PENDING' ORDER BY " +
           "CASE ar.alertLevel WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END, " +
           "ar.triggeredAt DESC")
    List<AlertRecord> findPendingAlerts();

    /**
     * 查询未解决的告警记录
     */
    @Query("SELECT ar FROM AlertRecord ar WHERE ar.status NOT IN ('RESOLVED', 'IGNORED') " +
           "ORDER BY ar.triggeredAt DESC")
    List<AlertRecord> findUnresolvedAlerts();

    /**
     * 统计各状态的告警数量
     */
    @Query("SELECT ar.status, COUNT(ar) FROM AlertRecord ar GROUP BY ar.status")
    List<Object[]> countByStatus();

    /**
     * 统计各告警级别的告警数量
     */
    @Query("SELECT ar.alertLevel, COUNT(ar) FROM AlertRecord ar GROUP BY ar.alertLevel")
    List<Object[]> countByAlertLevel();

    /**
     * 统计今日告警数量
     */
    @Query("SELECT COUNT(ar) FROM AlertRecord ar WHERE ar.triggeredAt >= :startOfDay")
    long countTodayAlerts(@Param("startOfDay") LocalDateTime startOfDay);

    /**
     * 统计指定时间范围内的告警数量
     */
    @Query("SELECT COUNT(ar) FROM AlertRecord ar WHERE ar.triggeredAt BETWEEN :startTime AND :endTime")
    long countByTimeRange(@Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime);

    /**
     * 查询今日各告警级别的告警数量
     */
    @Query("SELECT ar.alertLevel, COUNT(ar) FROM AlertRecord ar WHERE ar.triggeredAt >= :startOfDay " +
           "GROUP BY ar.alertLevel")
    List<Object[]> countTodayByAlertLevel(@Param("startOfDay") LocalDateTime startOfDay);

    /**
     * 查询已升级的告警
     */
    List<AlertRecord> findByEscalatedTrue();

    /**
     * 查询已分配的告警
     */
    List<AlertRecord> findByAssignedTo(UUID userId);

    /**
     * 分页查询所有告警记录
     */
    Page<AlertRecord> findAllByOrderByTriggeredAtDesc(Pageable pageable);

    /**
     * 复合条件查询
     */
    @Query(value = "SELECT * FROM alert_records ar WHERE " +
           "(COALESCE(:status, '') = '' OR ar.status = :status) AND " +
           "(COALESCE(:alertLevel, '') = '' OR ar.alert_level = :alertLevel) AND " +
           "(COALESCE(:ruleId, '') = '' OR ar.rule_id = CAST(:ruleId AS uuid)) AND " +
           "(CAST(:startTime AS timestamp) IS NULL OR ar.triggered_at >= :startTime) AND " +
           "(CAST(:endTime AS timestamp) IS NULL OR ar.triggered_at <= :endTime)",
           countQuery = "SELECT COUNT(*) FROM alert_records ar WHERE " +
           "(COALESCE(:status, '') = '' OR ar.status = :status) AND " +
           "(COALESCE(:alertLevel, '') = '' OR ar.alert_level = :alertLevel) AND " +
           "(COALESCE(:ruleId, '') = '' OR ar.rule_id = CAST(:ruleId AS uuid)) AND " +
           "(CAST(:startTime AS timestamp) IS NULL OR ar.triggered_at >= :startTime) AND " +
           "(CAST(:endTime AS timestamp) IS NULL OR ar.triggered_at <= :endTime)",
           nativeQuery = true)
    Page<AlertRecord> findByConditions(
            @Param("status") String status,
            @Param("alertLevel") String alertLevel,
            @Param("ruleId") String ruleId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);
}
