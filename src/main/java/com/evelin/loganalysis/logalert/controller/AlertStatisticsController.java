package com.evelin.loganalysis.logalert.controller;

import com.evelin.loganalysis.logalert.dto.AlertStatisticsResponse;
import com.evelin.loganalysis.logalert.service.AlertAnalyticsService;
import com.evelin.loganalysis.logcommon.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public Result<AlertStatisticsResponse> getStatistics() {
        AlertStatisticsResponse response = alertAnalyticsService.getStatistics();
        return Result.success(response);
    }

    /**
     * 获取告警趋势数据
     */
    @GetMapping("/trend")
    public Result<List<AlertStatisticsResponse.AlertTrendPoint>> getTrend(
            @RequestParam(defaultValue = "7") int days) {
        List<AlertStatisticsResponse.AlertTrendPoint> trendData = alertAnalyticsService.getTrendData(days);
        return Result.success(trendData);
    }

    /**
     * 获取今日告警级别分布
     */
    @GetMapping("/level-distribution")
    public Result<List<AlertStatisticsResponse.AlertLevelCount>> getLevelDistribution() {
        List<AlertStatisticsResponse.AlertLevelCount> distribution = alertAnalyticsService.getTodayLevelDistribution();
        return Result.success(distribution);
    }
}
