package com.evelin.loganalysis.logprocessing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聚合结果DTO
 *
 * @author Evelin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregationResult {

    /**
     * 聚合组ID
     */
    private String groupId;

    /**
     * 聚合组UUID
     */
    private String aggregationId;

    /**
     * 是否为新建聚合组
     */
    private boolean isNewGroup;

    /**
     * 代表日志
     */
    private String representativeLog;

    /**
     * 聚合类型: SEMANTIC/TEMPLATE/KEYWORD
     */
    private String aggregationType;

    /**
     * 组内日志数量
     */
    private int eventCount;

    /**
     * 相似度分数
     */
    private Double similarityScore;

    /**
     * 严重程度
     */
    private String severity;

    /**
     * 聚合时间
     */
    private LocalDateTime aggregatedAt;
}
