package com.evelin.loganalysis.logalert.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 告警统计响应DTO
 *
 * @author Evelin
 */
@Data
public class AlertStatisticsResponse {

    private Long totalAlerts;
    private Long pendingAlerts;
    private Long acknowledgedAlerts;
    private Long resolvedAlerts;
    private Long criticalAlerts;
    private Long highAlerts;
    private Long mediumAlerts;
    private Long lowAlerts;
    private Long todayAlerts;
    private List<AlertLevelCount> levelDistribution;
    private List<AlertTrendPoint> trendData;

    @Data
    public static class AlertLevelCount {
        private String level;
        private Long count;
    }

    @Data
    public static class AlertTrendPoint {
        private String date;
        private Long count;
    }
}
