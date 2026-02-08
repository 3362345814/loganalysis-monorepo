package com.evelin.loganalysis.logcommon.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聚合成员实体
 *
 * @author Evelin
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "aggregation_member")
public class AggregationMember extends BaseEntity {

    /**
     * 聚合组ID
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aggregation_id", nullable = false)
    private LogAggregation aggregationId;

    /**
     * 日志事件ID
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private LogEvent eventId;

    /**
     * 加入时间
     */
    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;
}
