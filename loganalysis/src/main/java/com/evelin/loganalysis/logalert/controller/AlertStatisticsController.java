package com.evelin.loganalysis.logalert.controller;

import com.evelin.loganalysis.logalert.dto.AlertStatisticsResponse;
import com.evelin.loganalysis.logalert.service.AlertAnalyticsService;
import com.evelin.loganalysis.logcommon.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 告警统计控制器
 *
 * @author Evelin
 */
@RestController
@RequestMapping("/api/v1/alert/statistics")
@RequiredArgsConstructor
public class AlertStatisticsController {

    private final AlertAnalyticsService alertAnalyticsService;

    /**
     * 获取告警统计信息
     */
    @GetMapping
    public Result<AlertStatisticsResponse> getStatistics(
            @RequestParam(required = false) UUID projectId) {
        AlertStatisticsResponse response = alertAnalyticsService.getStatisticsByProjectId(projectId);
        return Result.success(response);
    }

    /**
     * 获取告警趋势数据
     */
    @GetMapping("/trend")
    public Result<List<AlertStatisticsResponse.AlertTrendPoint>> getTrend(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) UUID projectId) {
        List<AlertStatisticsResponse.AlertTrendPoint> trendData = alertAnalyticsService.getTrendData(days, projectId);
        return Result.success(trendData);
    }

    /**
     * 获取今日告警级别分布
     */
    @GetMapping("/level-distribution")
    public Result<List<AlertStatisticsResponse.AlertLevelCount>> getLevelDistribution(
            @RequestParam(required = false) UUID projectId) {
        List<AlertStatisticsResponse.AlertLevelCount> distribution = alertAnalyticsService.getTodayLevelDistribution(projectId);
        return Result.success(distribution);
    }
}
