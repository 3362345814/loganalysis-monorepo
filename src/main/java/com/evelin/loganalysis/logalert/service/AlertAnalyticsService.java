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
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        AlertStatisticsResponse response = new AlertStatisticsResponse();

        // 总告警数
        response.setTotalAlerts(alertRecordRepository.count());

        // 待处理告警数
        response.setPendingAlerts((long) alertRecordRepository.findByStatus(AlertStatus.PENDING).size());

        // 已确认告警数
        response.setAcknowledgedAlerts((long) alertRecordRepository.findByStatus(AlertStatus.ACKNOWLEDGED).size());

        // 已解决告警数
        response.setResolvedAlerts((long) alertRecordRepository.findByStatus(AlertStatus.RESOLVED).size());

        // 今日告警数
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

        return response;
    }

    /**
     * 获取告警趋势数据
     *
     * @param days 天数
     */
    public List<AlertStatisticsResponse.AlertTrendPoint> getTrendData(int days) {
        List<AlertStatisticsResponse.AlertTrendPoint> trendData = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

            long count = alertRecordRepository.countByTimeRange(startOfDay, endOfDay);

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
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        List<AlertStatisticsResponse.AlertLevelCount> distribution = new ArrayList<>();

        for (Object[] row : alertRecordRepository.countTodayByAlertLevel(startOfDay)) {
            AlertLevel level = (AlertLevel) row[0];
            Long count = (Long) row[1];

            AlertStatisticsResponse.AlertLevelCount item = new AlertStatisticsResponse.AlertLevelCount();
            item.setLevel(level.name());
            item.setCount(count);
            distribution.add(item);
        }

        return distribution;
    }
}
