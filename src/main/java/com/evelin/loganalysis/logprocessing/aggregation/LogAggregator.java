package com.evelin.loganalysis.logprocessing.aggregation;

import com.evelin.loganalysis.logcommon.utils.IdGenerator;
import com.evelin.loganalysis.logprocessing.config.ProcessingConfig;
import com.evelin.loganalysis.logprocessing.dto.AggregationResult;
import com.evelin.loganalysis.logprocessing.dto.ParsedLogEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 日志聚合器
 *
 * @author Evelin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogAggregator {

    private final ProcessingConfig processingConfig;

    /**
     * 活跃的聚合组缓存
     */
    private final Map<String, AggregationGroup> activeGroups = new ConcurrentHashMap<>();

    /**
     * 聚合日志事件
     *
     * @param event 日志事件
     * @return 聚合结果
     */
    public AggregationResult aggregate(ParsedLogEvent event) {
        if (event == null) {
            return null;
        }

        // 检查是否匹配现有聚合组
        for (String groupId : activeGroups.keySet()) {
            AggregationGroup group = activeGroups.get(groupId);
            if (group != null && isSimilarEnough(event, group)) {
                // 添加到现有组
                group.addEvent(event);
                return AggregationResult.builder()
                        .groupId(group.getGroupId())
                        .aggregationId(group.getId())
                        .isNewGroup(false)
                        .representativeLog(group.getRepresentativeLog())
                        .eventCount(group.getEventCount())
                        .similarityScore(calculateSimilarity(event.getMessage(), group.getRepresentativeLog()))
                        .severity(determineSeverity(group))
                        .aggregatedAt(LocalDateTime.now())
                        .build();
            }
        }

        // 创建新的聚合组
        String groupId = generateGroupId();
        AggregationGroup newGroup = new AggregationGroup(groupId, event);
        activeGroups.put(groupId, newGroup);

        return AggregationResult.builder()
                .groupId(groupId)
                .aggregationId(newGroup.getId())
                .isNewGroup(true)
                .representativeLog(event.getMessage())
                .aggregationType("TEMPLATE")
                .eventCount(1)
                .similarityScore(1.0)
                .severity(determineSeverity(event))
                .aggregatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 批量聚合
     */
    public List<AggregationResult> aggregateBatch(List<ParsedLogEvent> events) {
        return events.stream()
                .map(this::aggregate)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 判断是否足够相似
     */
    private boolean isSimilarEnough(ParsedLogEvent event, AggregationGroup group) {
        // 使用简单的模板匹配进行相似度计算
        double score = calculateSimilarity(event.getMessage(), group.getRepresentativeLog());
        return score >= processingConfig.getSimilarityThreshold();
    }

    /**
     * 计算相似度
     */
    private double calculateSimilarity(String message1, String message2) {
        if (message1 == null || message2 == null) {
            return 0.0;
        }

        // 提取模板（将数字和特定字符串替换为占位符）
        String template1 = extractTemplate(message1);
        String template2 = extractTemplate(message2);

        if (template1.equals(template2)) {
            return 1.0;
        }

        // 简单的编辑距离相似度
        int distance = levenshteinDistance(template1, template2);
        int maxLength = Math.max(template1.length(), template2.length());

        return 1.0 - (double) distance / maxLength;
    }

    /**
     * 提取日志模板
     */
    private String extractTemplate(String message) {
        if (message == null) {
            return "";
        }

        // 替换数字为占位符
        String template = message.replaceAll("\\d+", "<N>");
        // 替换常见路径
        template = template.replaceAll("[/\\\\][\\w/.]+", "<PATH>");
        // 替换IP地址
        template = template.replaceAll("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}", "<IP>");
        // 替换UUID
        template = template.replaceAll("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}", "<UUID>");

        return template;
    }

    /**
     * 计算编辑距离
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j], Math.min(dp[i][j - 1], dp[i - 1][j - 1]));
                }
            }
        }

        return dp[s1.length()][s2.length()];
    }

    /**
     * 生成组ID
     */
    private String generateGroupId() {
        return "AGG-" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + String.format("%03d", new Random().nextInt(1000));
    }

    /**
     * 确定严重程度
     */
    private String determineSeverity(ParsedLogEvent event) {
        String level = event.getLogLevel();
        if (level == null) {
            return "INFO";
        }
        return switch (level.toUpperCase()) {
            case "FATAL", "CRITICAL" -> "CRITICAL";
            case "ERROR" -> "ERROR";
            case "WARN", "WARNING" -> "WARNING";
            default -> "INFO";
        };
    }

    /**
     * 确定组的严重程度
     */
    private String determineSeverity(AggregationGroup group) {
        String maxSeverity = "INFO";
        for (ParsedLogEvent event : group.getEvents()) {
            String severity = determineSeverity(event);
            if ("CRITICAL".equals(severity)) {
                return "CRITICAL";
            } else if ("ERROR".equals(severity) && !"CRITICAL".equals(maxSeverity)) {
                maxSeverity = "ERROR";
            } else if ("WARNING".equals(severity) && "INFO".equals(maxSeverity)) {
                maxSeverity = "WARNING";
            }
        }
        return maxSeverity;
    }

    /**
     * 获取活跃组
     */
    public Map<String, AggregationGroup> getActiveGroups() {
        return new HashMap<>(activeGroups);
    }

    /**
     * 清理超时的聚合组
     */
    public void cleanupExpiredGroups() {
        int timeoutMinutes = processingConfig.getAggregationTimeoutMinutes();
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(timeoutMinutes);

        activeGroups.entrySet().removeIf(entry ->
                entry.getValue().getLastEventTime().isBefore(cutoffTime));
    }

    /**
     * 聚合组内部类
     */
    @Getter
    public static class AggregationGroup {
        private final String id;
        private final String groupId;
        private final List<ParsedLogEvent> events = new ArrayList<>();
        private String representativeLog;
        private LocalDateTime firstEventTime;
        private LocalDateTime lastEventTime;

        public AggregationGroup(String groupId, ParsedLogEvent firstEvent) {
            this.id = IdGenerator.nextId();
            this.groupId = groupId;
            this.representativeLog = firstEvent.getMessage();
            this.firstEventTime = firstEvent.getLogTime() != null ? firstEvent.getLogTime() : LocalDateTime.now();
            this.lastEventTime = this.firstEventTime;
            this.events.add(firstEvent);
        }

        public void addEvent(ParsedLogEvent event) {
            events.add(event);
            LocalDateTime eventTime = event.getLogTime() != null ? event.getLogTime() : LocalDateTime.now();
            if (eventTime.isBefore(firstEventTime)) {
                firstEventTime = eventTime;
            }
            if (eventTime.isAfter(lastEventTime)) {
                lastEventTime = eventTime;
            }
        }

        public int getEventCount() {
            return events.size();
        }
    }
}
