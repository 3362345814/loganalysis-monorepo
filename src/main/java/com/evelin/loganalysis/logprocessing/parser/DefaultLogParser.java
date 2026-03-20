package com.evelin.loganalysis.logprocessing.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认日志解析器（通用文本格式）
 *
 * @author Evelin
 */
@Slf4j
@Component
public class DefaultLogParser implements ParseStrategy {

    // 尝试匹配各种常见日志格式
    private static final Pattern[] PATTERNS = {
            // 格式: 2026-01-15 10:30:00.123 INFO Message
            Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}[T\\s]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{3})?)\\s+(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\\s+(.*)$"),

            // 格式: [2026-01-15 10:30:00] [INFO] Message
            Pattern.compile("^\\[(\\d{4}-\\d{2}-\\d{2}[T\\s]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{3})?)\\]\\s+\\[(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\\]\\s+(.*)$"),

            // 格式: INFO 2026-01-15 10:30:00 Message
            Pattern.compile("^(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\\s+(\\d{4}-\\d{2}-\\d{2}[T\\s]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{3})?)\\s+(.*)$"),
    };

    @Override
    public ParseResult parse(String content, String customPattern) {
        if (content == null || content.isEmpty()) {
            return ParseResult.builder()
                    .success(false)
                    .errorMessage("Empty content")
                    .build();
        }

        // 尝试匹配各种模式
        for (Pattern pattern : PATTERNS) {
            Matcher matcher = pattern.matcher(content);
            if (matcher.matches()) {
                return parseMatched(matcher, pattern.pattern(), content);
            }
        }

        // 无法匹配任何模式，使用简单解析
        return parseSimple(content);
    }

    private ParseResult parseMatched(Matcher matcher, String pattern, String content) {
        try {
            String timestampStr, level, message;

            // 根据模式确定组的位置
            if (pattern.contains("INFO 2026")) {
                // 格式: INFO 2026-01-15...
                level = matcher.group(1);
                timestampStr = matcher.group(2);
                message = matcher.group(3);
            } else {
                // 其他格式: 2026-01-15 INFO Message
                timestampStr = matcher.group(1);
                level = matcher.group(2);
                message = matcher.group(3);
            }

            LocalDateTime timestamp = parseTimestamp(timestampStr);
            String normalizedLevel = normalizeLevel(level);

            return ParseResult.builder()
                    .success(true)
                    .timestamp(timestamp)
                    .level(normalizedLevel)
                    .message(message)
                    .fields(parseFields(content))
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse with pattern: {}", e.getMessage());
            return parseSimple(content);
        }
    }

    private ParseResult parseSimple(String content) {
        // 简单解析：尝试检测日志级别
        String level = detectLevel(content);

        return ParseResult.builder()
                .success(true)
                .timestamp(LocalDateTime.now())
                .level(level)
                .message(content)
                .fields(parseFields(content))
                .build();
    }

    private LocalDateTime parseTimestamp(String timestampStr) {
        try {
            // 尝试各种时间格式
            String[] formats = {
                    "yyyy-MM-dd HH:mm:ss.SSS",
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy-MM-dd'T'HH:mm:ss",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS"
            };

            for (String format : formats) {
                try {
                    return java.time.LocalDateTime.parse(
                            timestampStr.replace(' ', 'T'),
                            java.time.format.DateTimeFormatter.ofPattern(format)
                    );
                } catch (Exception e) {
                    // 继续尝试下一个格式
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse timestamp: {}", timestampStr);
        }
        return LocalDateTime.now();
    }

    private String normalizeLevel(String level) {
        if (level == null) return "INFO";
        return switch (level.toUpperCase()) {
            case "WARNING" -> "WARN";
            default -> level.toUpperCase();
        };
    }

    private String detectLevel(String content) {
        if (content.contains("ERROR") || content.contains("Exception") || content.contains("Error")) {
            return "ERROR";
        } else if (content.contains("WARN") || content.contains("Warning")) {
            return "WARN";
        } else if (content.contains("DEBUG")) {
            return "DEBUG";
        } else if (content.contains("TRACE")) {
            return "TRACE";
        }
        return "INFO";
    }

    private Map<String, Object> parseFields(String content) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("rawLength", content.length());
        fields.put("hasException", content.contains("Exception") || content.contains("Error"));
        return fields;
    }

    @Override
    public String getFormatName() {
        return "DEFAULT";
    }

    @Override
    public boolean supports(String content) {
        // 默认解析器支持所有内容
        return true;
    }
}
