package com.evelin.loganalysis.logprocessing.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 聚合组实体
 *
 * @author Evelin
 */
@Data
@Entity
@Table(name = "aggregation_group")
public class AggregationGroupEntity {

    @Id
    @Column(name = "id", length = 50)
    private String id;

    /**
     * 聚合组编号（展示用）
     */
    @Column(name = "group_id", length = 50)
    private String groupId;

    /**
     * 聚合组名称
     */
    @Column(name = "name", length = 200)
    private String name;

    /**
     * 代表性日志（模板）
     */
    @Column(name = "representative_log", columnDefinition = "TEXT")
    private String representativeLog;

    /**
     * 日志数量
     */
    @Column(name = "event_count")
    private Integer eventCount;

    /**
     * 严重程度：INFO / WARNING / ERROR / CRITICAL
     */
    @Column(name = "severity", length = 20)
    private String severity;

    /**
     * 聚合类型：TEMPLATE / TIME / SOURCE
     */
    @Column(name = "aggregation_type", length = 20)
    private String aggregationType;

    /**
     * 日志源ID
     */
    @Column(name = "source_id", length = 50)
    private String sourceId;

    /**
     * 日志源名称
     */
    @Column(name = "source_name", length = 100)
    private String sourceName;

    /**
     * 首次发生时间
     */
    @Column(name = "first_event_time")
    private LocalDateTime firstEventTime;

    /**
     * 最后发生时间
     */
    @Column(name = "last_event_time")
    private LocalDateTime lastEventTime;

    /**
     * 相似度分数
     */
    @Column(name = "similarity_score")
    private Double similarityScore;

    /**
     * 状态：ACTIVE / EXPIRED / ANALYZED
     */
    @Column(name = "status", length = 20)
    private String status;

    /**
     * 是否已分析
     */
    @Column(name = "is_analyzed")
    private Boolean isAnalyzed;

    /**
     * 备注
     */
    @Column(name = "remark", length = 500)
    private String remark;

    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "ACTIVE";
        }
        if (isAnalyzed == null) {
            isAnalyzed = false;
        }
        if (eventCount == null) {
            eventCount = 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
