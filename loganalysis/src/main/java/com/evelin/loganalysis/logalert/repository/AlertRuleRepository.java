package com.evelin.loganalysis.logalert.repository;

import com.evelin.loganalysis.logalert.enums.AlertLevel;
import com.evelin.loganalysis.logalert.enums.RuleType;
import com.evelin.loganalysis.logalert.model.AlertRule;
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
 * 告警规则Repository
 *
 * @author Evelin
 */
@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {

    /**
     * 根据名称查询规则
     */
    Optional<AlertRule> findByName(String name);

    /**
     * 查询所有启用的规则
     */
    List<AlertRule> findByEnabledTrue();

    /**
     * 查询指定类型的规则
     */
    List<AlertRule> findByRuleType(RuleType ruleType);

    /**
     * 查询指定告警级别的规则
     */
    List<AlertRule> findByAlertLevel(AlertLevel alertLevel);

    /**
     * 分页查询启用的规则
     */
    Page<AlertRule> findByEnabledTrue(Pageable pageable);

    /**
     * 根据状态查询规则
     */
    List<AlertRule> findByStatus(String status);

    /**
     * 查询今日触发了指定次数的规则
     */
    @Query("SELECT r FROM AlertRule r WHERE r.enabled = true AND r.triggerCountToday >= :minCount")
    List<AlertRule> findByTriggerCountTodayGreaterThan(@Param("minCount") Integer minCount);

    /**
     * 查询需要冷却的规则（在冷却时间内不触发）
     */
    @Query("SELECT r FROM AlertRule r WHERE r.enabled = true AND r.lastTriggeredAt IS NOT NULL " +
           "AND r.lastTriggeredAt > :cutoffTime")
    List<AlertRule> findRulesInCooldown(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * 统计启用的规则数量
     */
    long countByEnabledTrue();

    /**
     * 根据项目ID查询规则
     */
    Page<AlertRule> findByProjectId(UUID projectId, Pageable pageable);

    /**
     * 根据项目ID查询所有规则
     */
    List<AlertRule> findByProjectId(UUID projectId);
}
