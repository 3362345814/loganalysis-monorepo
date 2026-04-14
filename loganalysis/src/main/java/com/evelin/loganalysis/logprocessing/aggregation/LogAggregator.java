package com.evelin.loganalysis.logprocessing.aggregation;

import com.evelin.loganalysis.logcommon.utils.IdGenerator;
import com.evelin.loganalysis.logprocessing.config.ProcessingConfig;
import com.evelin.loganalysis.logprocessing.dto.AggregationResult;
import com.evelin.loganalysis.logprocessing.dto.ParsedLogEvent;
import com.evelin.loganalysis.logprocessing.entity.AggregationGroupEntity;
import com.evelin.loganalysis.logprocessing.service.AggregationGroupService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

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
    private final AggregationGroupService aggregationGroupService;

    /**
     * 当 message 退化为整行日志时，提取 " - " 之后的业务消息部分
     * 例如：... ERROR com.demo.ApiController - Invalid user id: -1
     */
    private static final Pattern STRUCTURED_LOG_MESSAGE_PATTERN = Pattern.compile(
            "^.*\\b(?:TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|CRITICAL)\\b\\s+[\\w.$]+\\s+-\\s+(.*)$"
    );

    /**
     * 日志级别优先级（数值越大级别越高）
     */
    private static final Map<String, Integer> LOG_LEVEL_PRIORITY = Map.of(
            "TRACE", 0,
            "DEBUG", 1,
            "INFO", 2,
            "WARN", 3,
            "WARNING", 3,
            "ERROR", 4,
            "FATAL", 5,
            "CRITICAL", 5
    );

    /**
     * 严重度优先级
     */
    private static final Map<String, Integer> SEVERITY_PRIORITY = Map.of(
            "INFO", 1,
            "WARNING", 2,
            "ERROR", 3,
            "CRITICAL", 4
    );

    /**
     * 活跃的聚合组缓存
     */
    private final Map<String, AggregationGroup> activeGroups = new ConcurrentHashMap<>();

    /**
     * 聚合关键路径锁，避免并发下重复创建同模板聚合组
     */
    private final Object aggregationLock = new Object();

    /**
     * 聚合日志事件（带日志源信息）
     *
     * @param event      日志事件
     * @param sourceId   日志源ID
     * @param sourceName 日志源名称
     * @param aggregationLevel 聚合级别配置（null表示聚合所有级别）
     * @return 聚合结果
     */
    public AggregationResult aggregate(ParsedLogEvent event, String sourceId, String sourceName, String aggregationLevel) {
        if (event == null) {
            return null;
        }

        // 检查是否满足聚合级别要求
        if (!shouldAggregate(event.getLogLevel(), aggregationLevel)) {
            return null;
        }

        return aggregateInternal(event, sourceId, sourceName);
    }

    /**
     * 聚合日志事件（不带聚合级别配置，默认聚合所有）
     *
     * @param event      日志事件
     * @param sourceId   日志源ID
     * @param sourceName 日志源名称
     * @return 聚合结果
     */
    public AggregationResult aggregate(ParsedLogEvent event, String sourceId, String sourceName) {
        return aggregate(event, sourceId, sourceName, null);
    }

    /**
     * 聚合日志事件（兼容旧版本调用）
     *
     * @param event 日志事件
     * @return 聚合结果
     */
    public AggregationResult aggregate(ParsedLogEvent event) {
        return aggregate(event, event != null ? event.getSourceId() : null, event != null ? event.getSourceName() : null, null);
    }

    /**
     * 判断是否应该聚合该日志
     *
     * @param logLevel          日志级别
     * @param aggregationLevel  聚合级别配置（null表示聚合所有级别）
     * @return 是否应该聚合
     */
    private boolean shouldAggregate(String logLevel, String aggregationLevel) {
        // 如果没有配置聚合级别，则聚合所有日志
        if (aggregationLevel == null || aggregationLevel.isEmpty()) {
            return true;
        }

        // 获取日志级别的优先级
        Integer eventLevelPriority = LOG_LEVEL_PRIORITY.getOrDefault(logLevel != null ? logLevel.toUpperCase() : "INFO", 2);

        // 获取聚合级别的优先级
        Integer aggLevelPriority = LOG_LEVEL_PRIORITY.getOrDefault(aggregationLevel.toUpperCase(), 3);

        // 只有当日志级别 >= 聚合级别时才进行聚合
        return eventLevelPriority >= aggLevelPriority;
    }

    /**
     * 内部聚合方法，实际执行聚合逻辑
     */
    private AggregationResult aggregateInternal(ParsedLogEvent event, String sourceId, String sourceName) {
        String scopedSourceId = normalizeSourceId(sourceId != null ? sourceId : event.getSourceId());

        synchronized (aggregationLock) {
            // 1) 使用模板指纹先做稳定命中：跨重启、跨超时都能回到同组
            String deterministicGroupId = generateGroupId(scopedSourceId, event.getMessage());
            AggregationGroup deterministicHit = activeGroups.get(deterministicGroupId);
            if (deterministicHit != null) {
                deterministicHit.addEvent(event);
                return persistAndBuildResult(deterministicHit, scopedSourceId, sourceName, false, 1.0);
            }

            // 2) 内存没有则回查历史（按指纹）
            AggregationGroup historicalByFingerprint = loadHistoricalGroupByGroupId(deterministicGroupId, scopedSourceId, event);
            if (historicalByFingerprint != null) {
                activeGroups.put(deterministicGroupId, historicalByFingerprint);
                double score = calculateSimilarity(event.getMessage(), historicalByFingerprint.getRepresentativeLog());
                return persistAndBuildResult(historicalByFingerprint, scopedSourceId, sourceName, false, score);
            }

            // 3) 兼容历史随机 groupId：按 sourceId + message 精确回查最近组
            AggregationGroup historicalByExactMessage = loadHistoricalGroupByExactMessage(scopedSourceId, event);
            if (historicalByExactMessage != null) {
                // 使用指纹 key 做缓存别名，避免每次都回表查历史随机 groupId
                activeGroups.put(deterministicGroupId, historicalByExactMessage);
                double score = calculateSimilarity(event.getMessage(), historicalByExactMessage.getRepresentativeLog());
                return persistAndBuildResult(historicalByExactMessage, scopedSourceId, sourceName, false, score);
            }

            // 4) 对同 source 的活跃组执行最佳相似度匹配
            BestMatch bestMatch = findBestMatchingGroup(event, scopedSourceId);
            if (bestMatch.group() != null) {
                AggregationGroup group = bestMatch.group();
                group.addEvent(event);
                return persistAndBuildResult(group, scopedSourceId, sourceName, false, bestMatch.similarityScore());
            }

            // 5) 都没命中，创建新分组（groupId 为稳定模板指纹）
            AggregationGroup newGroup = new AggregationGroup(deterministicGroupId, scopedSourceId, event.getMessage(), 0, null, event);
            activeGroups.put(deterministicGroupId, newGroup);
            return persistAndBuildResult(newGroup, scopedSourceId, sourceName, true, 1.0);
        }
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
     * 在所有活跃组中选择最相似且超过阈值的组（仅同 source）
     */
    private BestMatch findBestMatchingGroup(ParsedLogEvent event, String sourceId) {
        AggregationGroup bestGroup = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (AggregationGroup group : activeGroups.values()) {
            if (group == null) {
                continue;
            }
            if (!Objects.equals(group.getSourceId(), sourceId)) {
                continue;
            }

            double score = calculateSimilarity(event.getMessage(), group.getRepresentativeLog());
            if (score >= processingConfig.getSimilarityThreshold() && score > bestScore) {
                bestGroup = group;
                bestScore = score;
            }
        }

        if (bestGroup == null) {
            return new BestMatch(null, 0.0);
        }
        return new BestMatch(bestGroup, bestScore);
    }

    /**
     * 计算相似度
     */
    private double calculateSimilarity(String message1, String message2) {
        if (message1 == null || message2 == null) {
            return 0.0;
        }

        String normalizedMessage1 = normalizeMessage(message1);
        String normalizedMessage2 = normalizeMessage(message2);

        // 提取模板（将数字和特定字符串替换为占位符）
        String template1 = extractTemplate(normalizedMessage1);
        String template2 = extractTemplate(normalizedMessage2);

        if (template1.equals(template2)) {
            return 1.0;
        }

        // 简单的编辑距离相似度
        int distance = levenshteinDistance(template1, template2);
        int maxLength = Math.max(template1.length(), template2.length());
        if (maxLength == 0) {
            return 1.0;
        }

        return 1.0 - (double) distance / maxLength;
    }

    /**
     * 归一化消息，避免 parser 退化时把整行日志头参与相似度计算
     */
    private String normalizeMessage(String message) {
        if (message == null) {
            return "";
        }
        String trimmed = message.trim();
        java.util.regex.Matcher matcher = STRUCTURED_LOG_MESSAGE_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            return matcher.group(1).trim();
        }
        return trimmed;
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
     * 使用 source + template 的稳定指纹生成 groupId
     */
    private String generateGroupId(String sourceId, String message) {
        String template = extractTemplate(normalizeMessage(message));
        String fingerprint = sourceId + "|" + template;
        return "AGG-" + sha256Hex(fingerprint).substring(0, 16).toUpperCase(Locale.ROOT);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return IdGenerator.nextId();
        }
    }

    /**
     * 按 groupId 回查历史分组并补充当前事件
     */
    private AggregationGroup loadHistoricalGroupByGroupId(String groupId, String sourceId, ParsedLogEvent event) {
        if (aggregationGroupService == null) {
            return null;
        }
        try {
            Optional<AggregationGroupEntity> entityOpt = aggregationGroupService.findByGroupId(groupId);
            if (entityOpt.isEmpty()) {
                return null;
            }
            return AggregationGroup.fromEntity(entityOpt.get(), sourceId, event);
        } catch (Exception e) {
            log.warn("按 groupId 回查历史聚合组失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 兼容历史随机 groupId：按 sourceId + message 精确回查最近分组并补充当前事件
     */
    private AggregationGroup loadHistoricalGroupByExactMessage(String sourceId, ParsedLogEvent event) {
        if (aggregationGroupService == null) {
            return null;
        }
        try {
            Optional<AggregationGroupEntity> entityOpt =
                    aggregationGroupService.findLatestBySourceIdAndRepresentativeLog(sourceId, event.getMessage());
            if (entityOpt.isEmpty()) {
                return null;
            }
            return AggregationGroup.fromEntity(entityOpt.get(), sourceId, event);
        } catch (Exception e) {
            log.warn("按 source + message 回查历史聚合组失败: {}", e.getMessage());
            return null;
        }
    }

    private AggregationResult persistAndBuildResult(
            AggregationGroup group,
            String sourceId,
            String sourceName,
            boolean isNewGroup,
            double similarityScore) {
        LocalDateTime aggregatedAt = LocalDateTime.now();
        String severity = determineSeverity(group);

        AggregationResult persistencePayload = AggregationResult.builder()
                .groupId(group.getGroupId())
                .aggregationId(group.getId())
                .representativeLog(group.getRepresentativeLog())
                .eventCount(group.getEventCount())
                .severity(severity)
                .aggregatedAt(aggregatedAt)
                .build();

        try {
            if (aggregationGroupService != null) {
                aggregationGroupService.saveOrUpdate(persistencePayload, sourceId, sourceName);
            }
        } catch (Exception e) {
            log.warn("持久化聚合组失败: {}", e.getMessage());
        }

        return AggregationResult.builder()
                .groupId(group.getGroupId())
                .aggregationId(group.getId())
                .isNewGroup(isNewGroup)
                .representativeLog(group.getRepresentativeLog())
                .aggregationType("TEMPLATE")
                .eventCount(group.getEventCount())
                .similarityScore(similarityScore)
                .severity(severity)
                .aggregatedAt(aggregatedAt)
                .build();
    }

    private String normalizeSourceId(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            return "GLOBAL";
        }
        return sourceId;
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
        String maxSeverity = group.getHistoricalSeverity() != null ? group.getHistoricalSeverity() : "INFO";

        for (ParsedLogEvent event : group.getEvents()) {
            String severity = determineSeverity(event);
            int currentPriority = SEVERITY_PRIORITY.getOrDefault(maxSeverity, 1);
            int incomingPriority = SEVERITY_PRIORITY.getOrDefault(severity, 1);
            if (incomingPriority > currentPriority) {
                maxSeverity = severity;
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
        private final String sourceId;
        private final List<ParsedLogEvent> events = new ArrayList<>();
        private final int baseEventCount;
        private String representativeLog;
        private String historicalSeverity;
        private LocalDateTime firstEventTime;
        private LocalDateTime lastEventTime;

        public AggregationGroup(
                String groupId,
                String sourceId,
                String representativeLog,
                int baseEventCount,
                String historicalSeverity,
                ParsedLogEvent firstEvent) {
            this.id = IdGenerator.nextId();
            this.groupId = groupId;
            this.sourceId = sourceId;
            this.representativeLog = representativeLog != null ? representativeLog : firstEvent.getMessage();
            this.baseEventCount = Math.max(baseEventCount, 0);
            this.historicalSeverity = historicalSeverity;
            this.firstEventTime = firstEvent.getLogTime() != null ? firstEvent.getLogTime() : LocalDateTime.now();
            this.lastEventTime = this.firstEventTime;
            this.events.add(firstEvent);
        }

        public static AggregationGroup fromEntity(AggregationGroupEntity entity, String fallbackSourceId, ParsedLogEvent event) {
            String sourceId = entity.getSourceId() != null ? entity.getSourceId() : fallbackSourceId;
            String representative = entity.getRepresentativeLog() != null ? entity.getRepresentativeLog() : event.getMessage();
            int existingCount = entity.getEventCount() != null ? entity.getEventCount() : 0;
            return new AggregationGroup(
                    entity.getGroupId(),
                    sourceId,
                    representative,
                    existingCount,
                    entity.getSeverity(),
                    event
            );
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
            return baseEventCount + events.size();
        }
    }

    /**
     * 最佳匹配结果
     */
    private record BestMatch(AggregationGroup group, double similarityScore) {}
}
