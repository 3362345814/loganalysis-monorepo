package com.evelin.loganalysis.logalert.service;

import com.evelin.loganalysis.logalert.enums.RuleType;
import com.evelin.loganalysis.logalert.model.AlertRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 告警触发引擎
 *
 * @author Evelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertTriggerService {

    private final AlertRuleService alertRuleService;
    private final AlertRecordService alertRecordService;
    private final NotificationService notificationService;

    /**
     * 检查日志是否触发告警规则
     *
     * @param logMessage 日志消息
     * @param logLevel   日志级别
     * @param sourceId   日志源ID
     * @param sourceName 日志源名称
     * @param logId     日志ID（可选）
     * @param traceId   链路追踪ID（可选）
     */
    public void checkAndTrigger(String logMessage, String logLevel, String sourceId, String sourceName, String logId, String traceId) {
        List<AlertRule> enabledRules = alertRuleService.getEnabledRules();

        for (AlertRule rule : enabledRules) {
            // 检查是否在冷却期内
            if (alertRuleService.isInCooldown(rule)) {
                continue;
            }

            // 检查规则是否适用于该日志源
            if (rule.getSourceIds() != null && !rule.getSourceIds().isEmpty()) {
                // 如果规则指定了日志源，但当前日志源不在列表中，跳过
                // 这里需要根据实际情况实现
            }

            // 根据规则类型进行匹配
            boolean matched = false;
            switch (rule.getRuleType()) {
                case KEYWORD -> matched = matchKeyword(rule.getConditionExpression(), logMessage);
                case REGEX -> matched = matchRegex(rule.getConditionExpression(), logMessage);
                case LEVEL -> matched = matchLevel(rule.getConditionExpression(), logLevel);
                default -> log.warn("不支持的规则类型: {}", rule.getRuleType());
            }

            if (matched) {
                triggerAlert(rule, logMessage, logLevel, sourceId, sourceName, logId, traceId);
            }
        }
    }

    /**
     * 检查日志是否触发告警规则（无logId和traceId版本）
     */
    public void checkAndTrigger(String logMessage, String logLevel, String sourceId, String sourceName) {
        checkAndTrigger(logMessage, logLevel, sourceId, sourceName, null, null);
    }

    /**
     * 关键词匹配（大小写不敏感）
     */
    private boolean matchKeyword(String keyword, String logMessage) {
        if (keyword == null || logMessage == null) {
            return false;
        }
        return logMessage.toLowerCase().contains(keyword.toLowerCase());
    }

    /**
     * 正则表达式匹配
     */
    private boolean matchRegex(String regex, String logMessage) {
        if (regex == null || logMessage == null) {
            return false;
        }
        try {
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(logMessage);
            return matcher.find();
        } catch (Exception e) {
            log.error("正则表达式匹配失败: {}", regex, e);
            return false;
        }
    }

    /**
     * 日志级别匹配
     */
    private boolean matchLevel(String condition, String logLevel) {
        if (condition == null || logLevel == null) {
            return false;
        }
        // condition 格式: "ERROR" 或 ">=ERROR" 或 "ERROR,WARN"
        String[] parts = condition.split(",");
        for (String level : parts) {
            level = level.trim().toUpperCase();
            if (level.equals(logLevel.toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 触发告警
     */
    private void triggerAlert(AlertRule rule, String logMessage, String logLevel,
                              String sourceId, String sourceName, String logId, String traceId) {
        try {
            String title = rule.getAlertTitle();
            String content = rule.getAlertMessage();
            String triggerCondition = String.format("%s: %s", rule.getRuleType(), rule.getConditionExpression());
            String triggerValue = logMessage;

            // 如果标题或内容为空，使用默认值
            if (title == null || title.isEmpty()) {
                title = String.format("%s - %s 告警", rule.getName(), rule.getAlertLevel());
            }
            if (content == null || content.isEmpty()) {
                content = String.format("触发条件: %s\n日志级别: %s\n日志内容: %s\n来源: %s",
                        triggerCondition, logLevel, logMessage, sourceName);
            }

            // 创建告警记录
            // 将 sourceId 字符串转换为 UUID
            List<UUID> sourceIdList = null;
            if (sourceId != null && !sourceId.isEmpty()) {
                try {
                    sourceIdList = List.of(UUID.fromString(sourceId));
                } catch (IllegalArgumentException e) {
                    log.warn("无效的sourceId格式: {}", sourceId);
                }
            }
            alertRecordService.createAlertFromRule(
                    rule,
                    title,
                    content,
                    triggerCondition,
                    triggerValue,
                    1,
                    List.of(sourceName),
                    null,  // aggregationId - 暂时不传
                    sourceIdList,
                    logId,
                    traceId
            );

            // 发送通知
            notificationService.sendNotifications(rule, title, content);

            log.info("触发告警: {} - {}", rule.getName(), title);
        } catch (Exception e) {
            log.error("触发告警失败: {}", rule.getName(), e);
        }
    }

    /**
     * 批量检查日志（用于阈值类规则）
     *
     * @param logs       日志列表
     * @param timeWindow 时间窗口（分钟）
     */
    public void checkBatchAndTrigger(List<LogEntry> logs, int timeWindow) {
        // 获取阈值类规则
        List<AlertRule> thresholdRules = alertRuleService.getEnabledRules().stream()
                .filter(r -> r.getRuleType() == RuleType.THRESHOLD)
                .toList();

        for (AlertRule rule : thresholdRules) {
            // 解析阈值条件，如 "error_count > 100"
            Map<String, Object> parsed = parseThresholdCondition(rule.getConditionExpression());
            if (parsed == null) {
                continue;
            }

            String targetLevel = (String) parsed.get("level");
            int threshold = (int) parsed.get("threshold");
            String operator = (String) parsed.get("operator");

            // 统计符合条件的日志数量
            long errorCount = logs.stream()
                    .filter(l -> l.level().equalsIgnoreCase(targetLevel))
                    .count();

            // 检查是否满足阈值条件
            boolean triggered = switch (operator) {
                case ">" -> errorCount > threshold;
                case ">=" -> errorCount >= threshold;
                case "==" -> errorCount == threshold;
                case "<" -> errorCount < threshold;
                case "<=" -> errorCount <= threshold;
                default -> false;
            };

            if (triggered) {
                String title = String.format("%s - 阈值告警", rule.getName());
                String content = String.format("在最近 %d 分钟内，%s 级别日志数量为 %d，超过了阈值 %d",
                        timeWindow, targetLevel, errorCount, threshold);

                alertRecordService.createAlertFromRule(
                        rule,
                        title,
                        content,
                        rule.getConditionExpression(),
                        String.valueOf(errorCount),
                        (int) errorCount,
                        logs.stream().map(LogEntry::source).distinct().toList()
                );

                notificationService.sendNotifications(rule, title, content);
            }
        }
    }

    /**
     * 解析阈值条件
     * 格式: "error_count > 100 in 5 minutes" 或 "ERROR > 100"
     */
    private Map<String, Object> parseThresholdCondition(String condition) {
        try {
            // 简化解析：假设格式为 "LEVEL > threshold"
            String[] parts = condition.split(">");
            if (parts.length != 2) {
                return null;
            }

            String level = parts[0].trim();
            int threshold = Integer.parseInt(parts[1].trim());

            return Map.of(
                    "level", level,
                    "threshold", threshold,
                    "operator", ">"
            );
        } catch (Exception e) {
            log.error("解析阈值条件失败: {}", condition, e);
            return null;
        }
    }

    /**
     * 日志条目
     */
    public record LogEntry(String message, String level, String source, long timestamp) {
    }
}
