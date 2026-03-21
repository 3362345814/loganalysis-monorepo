package com.evelin.loganalysis.logprocessing.event;

import com.evelin.loganalysis.logprocessing.dto.DetectionResult;
import com.evelin.loganalysis.logprocessing.dto.ParsedLogEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 事件检测器
 *
 * @author Evelin
 */
@Slf4j
@Component
public class EventDetector {

    /**
     * 默认的事件规则
     */
    private final List<EventRule> defaultRules;

    public EventDetector() {
        this.defaultRules = buildDefaultRules();
    }

    /**
     * 检测日志事件
     *
     * @param parsedLogEvent 解析后的日志事件
     * @return 检测结果
     */
    public DetectionResult detect(ParsedLogEvent parsedLogEvent) {
        if (parsedLogEvent == null) {
            return DetectionResult.builder()
                    .anomaly(false)
                    .detectedAt(LocalDateTime.now())
                    .build();
        }

        DetectionResult result = DetectionResult.builder()
                .anomaly(false)
                .detectedAt(LocalDateTime.now())
                .build();

        // 按优先级排序规则
        List<EventRule> sortedRules = new ArrayList<>(defaultRules);
        sortedRules.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));

        // 遍历规则进行匹配
        for (EventRule rule : sortedRules) {
            if (!rule.isEnabled()) {
                continue;
            }

            if (matchesRule(parsedLogEvent, rule)) {
                result.setAnomaly(true);
                result.addMatchedRule(rule.getId(), rule.getName());
                
                // 设置事件级别
                if (result.getEventLevel() == null || 
                    getLevelPriority(rule.getEventLevel()) > getLevelPriority(result.getEventLevel())) {
                    result.setEventLevel(rule.getEventLevel());
                }

                // 设置事件分类
                if (result.getEventCategory() == null && rule.getEventCategory() != null) {
                    result.setEventCategory(rule.getEventCategory());
                }

                // 计算异常评分
                double score = calculateAnomalyScore(rule, parsedLogEvent);
                if (result.getAnomalyScore() == null || score > result.getAnomalyScore()) {
                    result.setAnomalyScore(score);
                }

                // 高优先级规则匹配后可以停止
                if (rule.getPriority() >= 100) {
                    break;
                }
            }
        }

        // 设置默认事件级别
        if (result.getEventLevel() == null) {
            result.setEventLevel(parsedLogEvent.getLogLevel());
        }

        // 根据日志级别自动识别异常
        if (!result.isAnomaly() && isHighLevelLog(parsedLogEvent.getLogLevel())) {
            result.setAnomaly(true);
            result.setAnomalyScore(0.8);
            result.setAnomalyReason("High level log detected");
            result.setEventLevel(parsedLogEvent.getLogLevel());
        }

        // 检查异常关键词
        if (!result.isAnomaly() && containsAnomalyKeyword(parsedLogEvent.getMessage())) {
            result.setAnomaly(true);
            result.setAnomalyScore(0.9);
            result.setAnomalyReason("Anomaly keyword detected in message");
        }

        return result;
    }

    /**
     * 批量检测
     *
     * @param parsedLogEvents 解析后的日志事件列表
     * @return 检测结果列表
     */
    public List<DetectionResult> detectBatch(List<ParsedLogEvent> parsedLogEvents) {
        return parsedLogEvents.stream()
                .map(this::detect)
                .toList();
    }

    /**
     * 检查规则是否匹配
     */
    private boolean matchesRule(ParsedLogEvent event, EventRule rule) {
        String message = event.getMessage();
        if (message == null) {
            return false;
        }

        String pattern = rule.getPattern();
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }

        return switch (rule.getRuleType().toUpperCase()) {
            case "KEYWORD" -> matchesKeyword(message, pattern, rule.isCaseSensitive(), rule.getMatchMode());
            case "REGEX" -> matchesRegex(message, pattern, rule.getPatternFlags());
            case "LEVEL" -> matchesLevel(event.getLogLevel(), pattern);
            default -> false;
        };
    }

    /**
     * 关键词匹配
     */
    private boolean matchesKeyword(String message, String pattern, boolean caseSensitive, String matchMode) {
        String target = caseSensitive ? message : message.toLowerCase();
        String pat = caseSensitive ? pattern : pattern.toLowerCase();

        return switch (matchMode != null ? matchMode.toUpperCase() : "CONTAINS") {
            case "EQUALS" -> target.equals(pat);
            case "CONTAINS" -> target.contains(pat);
            case "STARTS_WITH" -> target.startsWith(pat);
            case "ENDS_WITH" -> target.endsWith(pat);
            default -> target.contains(pat);
        };
    }

    /**
     * 正则匹配
     */
    private boolean matchesRegex(String message, String pattern, String flags) {
        try {
            int regexFlags = 0;
            if (flags != null) {
                if (flags.contains("i")) {
                    regexFlags |= Pattern.CASE_INSENSITIVE;
                }
            }
            Pattern p = Pattern.compile(pattern, regexFlags);
            return p.matcher(message).find();
        } catch (Exception e) {
            log.warn("Invalid regex pattern: {}", pattern, e);
            return false;
        }
    }

    /**
     * 级别匹配
     */
    private boolean matchesLevel(String logLevel, String targetLevel) {
        if (logLevel == null || targetLevel == null) {
            return false;
        }
        int logPriority = getLevelPriority(logLevel);
        int targetPriority = getLevelPriority(targetLevel);
        return logPriority >= targetPriority;
    }

    /**
     * 获取日志级别优先级
     */
    private int getLevelPriority(String level) {
        if (level == null) return 0;
        return switch (level.toUpperCase()) {
            case "TRACE" -> 0;
            case "DEBUG" -> 1;
            case "INFO" -> 2;
            case "WARN", "WARNING" -> 3;
            case "ERROR" -> 4;
            case "FATAL", "CRITICAL" -> 5;
            default -> 2;
        };
    }

    /**
     * 判断是否为高级别日志
     */
    private boolean isHighLevelLog(String logLevel) {
        if (logLevel == null) return false;
        int priority = getLevelPriority(logLevel);
        return priority >= 3; // WARN及以上
    }

    /**
     * 检查是否包含异常关键词
     */
    private boolean containsAnomalyKeyword(String message) {
        if (message == null) return false;
        String lowerMessage = message.toLowerCase();
        
        String[] keywords = {
            "exception", "error", "failed", "failure", "fatal",
            "outofmemory", "out of memory", "timeout", "deadlock",
            "nullpointer", "null pointer", "illegalargument", "illegal argument"
        };
        
        for (String keyword : keywords) {
            if (lowerMessage.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算异常评分
     */
    private double calculateAnomalyScore(EventRule rule, ParsedLogEvent event) {
        double baseScore = 0.5;
        
        // 根据规则优先级加分
        baseScore += rule.getPriority() / 200.0;
        
        // 根据日志级别调整
        String level = event.getLogLevel();
        if (level != null) {
            baseScore += getLevelPriority(level) * 0.1;
        }
        
        // 确保分数在0-1之间
        return Math.min(1.0, Math.max(0.0, baseScore));
    }

    /**
     * 构建默认规则
     */
    private List<EventRule> buildDefaultRules() {
        List<EventRule> rules = new ArrayList<>();
        
        // 致命错误规则
        rules.add(EventRule.builder()
                .id("rule-fatal")
                .name("致命错误")
                .ruleType("LEVEL")
                .pattern("FATAL")
                .eventLevel("FATAL")
                .eventCategory("CRITICAL_ERROR")
                .priority(100)
                .enabled(true)
                .build());
        
        // 错误规则
        rules.add(EventRule.builder()
                .id("rule-error")
                .name("错误日志")
                .ruleType("LEVEL")
                .pattern("ERROR")
                .eventLevel("ERROR")
                .eventCategory("ERROR")
                .priority(90)
                .enabled(true)
                .build());
        
        // 异常关键词规则
        rules.add(EventRule.builder()
                .id("rule-exception")
                .name("异常检测")
                .ruleType("KEYWORD")
                .pattern("Exception")
                .matchMode("CONTAINS")
                .eventLevel("ERROR")
                .eventCategory("EXCEPTION")
                .priority(80)
                .enabled(true)
                .build());
        
        // 空指针异常
        rules.add(EventRule.builder()
                .id("rule-npe")
                .name("空指针异常")
                .ruleType("KEYWORD")
                .pattern("NullPointerException")
                .eventLevel("ERROR")
                .eventCategory("EXCEPTION")
                .priority(95)
                .enabled(true)
                .build());
        
        return rules;
    }

    /**
     * 添加自定义规则
     */
    public void addRule(EventRule rule) {
        if (rule != null && rule.isEnabled()) {
            defaultRules.add(rule);
        }
    }

    /**
     * 获取所有规则
     */
    public List<EventRule> getRules() {
        return new ArrayList<>(defaultRules);
    }
}
