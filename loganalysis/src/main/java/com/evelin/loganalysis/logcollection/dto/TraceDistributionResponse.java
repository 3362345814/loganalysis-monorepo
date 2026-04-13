package com.evelin.loganalysis.logcollection.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 首页链路追踪分布响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TraceDistributionResponse {

    /**
     * 日期标签（yyyy-MM-dd）
     */
    private List<String> labels;

    /**
     * 最小值（秒）
     */
    private List<Double> min;

    /**
     * P25（秒）
     */
    private List<Double> p25;

    /**
     * P50（秒）
     */
    private List<Double> p50;

    /**
     * P75（秒）
     */
    private List<Double> p75;

    /**
     * 最大值（秒）
     */
    private List<Double> max;

    /**
     * 每天有效样本数（满足过滤条件的 trace 数）
     */
    private List<Long> sampleCount;
}
