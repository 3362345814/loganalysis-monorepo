package com.evelin.loganalysis.logalert.service;

import com.evelin.loganalysis.logalert.dto.AlertStatisticsResponse;
import com.evelin.loganalysis.logalert.enums.AlertLevel;
import com.evelin.loganalysis.logalert.enums.AlertStatus;
import com.evelin.loganalysis.logalert.repository.AlertRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 告警统计服务
 *
 * @author Evelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertAnalyticsService {

    private final AlertRecordRepository alertRecordRepository;

    /**
     * 获取告警统计信息
     */
    public AlertStatisticsResponse getStatistics() {
        return getStatisticsByProjectId(null);
    }

    /**
     * 根据项目ID获取告警统计信息
     */
    public AlertStatisticsResponse getStatisticsByProjectId(UUID projectId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        AlertStatisticsResponse response = new AlertStatisticsResponse();

        if (projectId != null) {
            // 按项目统计
            response.setTotalAlerts(alertRecordRepository.countByProjectId(projectId));
            response.setPendingAlerts(alertRecordRepository.countPendingByProjectId(projectId));
            response.setTodayAlerts(alertRecordRepository.countTodayByProjectId(projectId, startOfDay));

            // 按级别统计
            Map<AlertLevel, Long> levelCounts = new HashMap<>();
            for (Object[] row : alertRecordRepository.countByAlertLevelByProjectId(projectId)) {
                String levelStr = (String) row[0];
                AlertLevel level = AlertLevel.valueOf(levelStr);
                Long count = (Long) row[1];
                levelCounts.put(level, count);
            }
            response.setCriticalAlerts(levelCounts.getOrDefault(AlertLevel.CRITICAL, 0L));
            response.setHighAlerts(levelCounts.getOrDefault(AlertLevel.HIGH, 0L));
            response.setMediumAlerts(levelCounts.getOrDefault(AlertLevel.MEDIUM, 0L));
            response.setLowAlerts(levelCounts.getOrDefault(AlertLevel.LOW, 0L));

            // 级别分布
            List<AlertStatisticsResponse.AlertLevelCount> levelDistribution = new ArrayList<>();
            levelCounts.forEach((level, count) -> {
                AlertStatisticsResponse.AlertLevelCount item = new AlertStatisticsResponse.AlertLevelCount();
                item.setLevel(level.name());
                item.setCount(count);
                levelDistribution.add(item);
            });
            response.setLevelDistribution(levelDistribution);
        } else {
            // 全局统计
            response.setTotalAlerts(alertRecordRepository.count());
            response.setPendingAlerts((long) alertRecordRepository.findByStatus(AlertStatus.PENDING).size());
            response.setAcknowledgedAlerts((long) alertRecordRepository.findByStatus(AlertStatus.ACKNOWLEDGED).size());
            response.setResolvedAlerts((long) alertRecordRepository.findByStatus(AlertStatus.RESOLVED).size());
            response.setTodayAlerts(alertRecordRepository.countTodayAlerts(startOfDay));

            // 按级别统计
            Map<AlertLevel, Long> levelCounts = new HashMap<>();
            for (Object[] row : alertRecordRepository.countByAlertLevel()) {
                AlertLevel level = (AlertLevel) row[0];
                Long count = (Long) row[1];
                levelCounts.put(level, count);
            }
            response.setCriticalAlerts(levelCounts.getOrDefault(AlertLevel.CRITICAL, 0L));
            response.setHighAlerts(levelCounts.getOrDefault(AlertLevel.HIGH, 0L));
            response.setMediumAlerts(levelCounts.getOrDefault(AlertLevel.MEDIUM, 0L));
            response.setLowAlerts(levelCounts.getOrDefault(AlertLevel.LOW, 0L));

            // 级别分布
            List<AlertStatisticsResponse.AlertLevelCount> levelDistribution = new ArrayList<>();
            levelCounts.forEach((level, count) -> {
                AlertStatisticsResponse.AlertLevelCount item = new AlertStatisticsResponse.AlertLevelCount();
                item.setLevel(level.name());
                item.setCount(count);
                levelDistribution.add(item);
            });
            response.setLevelDistribution(levelDistribution);
        }

        return response;
    }

    /**
     * 获取告警趋势数据
     *
     * @param days 天数
     */
    public List<AlertStatisticsResponse.AlertTrendPoint> getTrendData(int days) {
        return getTrendData(days, null);
    }

    /**
     * 根据项目ID获取告警趋势数据
     */
    public List<AlertStatisticsResponse.AlertTrendPoint> getTrendData(int days, UUID projectId) {
        List<AlertStatisticsResponse.AlertTrendPoint> trendData = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

            long count;
            if (projectId != null) {
                count = alertRecordRepository.countByTimeRangeByProjectId(projectId, startOfDay, endOfDay);
            } else {
                count = alertRecordRepository.countByTimeRange(startOfDay, endOfDay);
            }

            AlertStatisticsResponse.AlertTrendPoint point = new AlertStatisticsResponse.AlertTrendPoint();
            point.setDate(date.format(formatter));
            point.setCount(count);
            trendData.add(point);
        }

        return trendData;
    }

    /**
     * 获取今日告警级别分布
     */
    public List<AlertStatisticsResponse.AlertLevelCount> getTodayLevelDistribution() {
        return getTodayLevelDistribution(null);
    }

    /**
     * 根据项目ID获取今日告警级别分布
     */
    public List<AlertStatisticsResponse.AlertLevelCount> getTodayLevelDistribution(UUID projectId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        List<AlertStatisticsResponse.AlertLevelCount> distribution = new ArrayList<>();

        List<Object[]> rows;
        if (projectId != null) {
            rows = alertRecordRepository.countTodayByAlertLevelByProjectId(projectId, startOfDay);
        } else {
            rows = alertRecordRepository.countTodayByAlertLevel(startOfDay);
        }

        for (Object[] row : rows) {
            AlertLevel level;
            if (row[0] instanceof String) {
                level = AlertLevel.valueOf((String) row[0]);
            } else {
                level = (AlertLevel) row[0];
            }
            Long count = (Long) row[1];

            AlertStatisticsResponse.AlertLevelCount item = new AlertStatisticsResponse.AlertLevelCount();
            item.setLevel(level.name());
            item.setCount(count);
            distribution.add(item);
        }

        return distribution;
    }
}
