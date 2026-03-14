package com.evelin.loganalysis.logprocessing.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Log4j 日志解析器
 * 支持 Log4j 1.x 和 Log4j 2.x 格式
 *
 * Log4j 1.x 格式: 2026-01-15 10:30:02 ERROR [thread] class - message
 * Log4j 2.x 格式: 2026-01-15 10:30:02,123 ERROR [thread] class - message
 *
 * @author Evelin
 */
@Slf4j
@Component
public class Log4jLogParser implements ParseStrategy {

    private static final DateTimeFormatter FORMATTER_3_MS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final DateTimeFormatter FORMATTER_NO_MS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter FORMATTER_COMMA_MS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS");

    // Log4j 1.x 格式正则: yyyy-MM-dd HH:mm:ss [thread] LEVEL class - message
    // 实际格式: 2026-03-14 03:51:03 [http-nio-8080-exec-9] WARN  com.example.Controller - message
    private static final Pattern PATTERN_LOG4J1 = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})"  // 时间戳 (无毫秒)
                    + "\\s+"                                      // 时间戳后的空格
                    + "\\[([^\\]]+)\\]"                           // 线程名（带方括号）
                    + "\\s+"                                      // 线程名后的空格
                    + "(ERROR|WARN|INFO|DEBUG|TRACE|FATAL)"      // 日志级别
                    + "\\s+"                                      // 日志级别后的空格
                    + "([\\w\\.]+)"                              // 类名
                    + "\\s+-\\s+(.*)$"                           // 消息
    );

    // Log4j 1.x 带毫秒格式正则
    private static final Pattern PATTERN_LOG4J1_MS = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})"  // 时间戳 (3位毫秒)
                    + "\\s+"
                    + "\\[([^\\]]+)\\]"
                    + "\\s+"
                    + "(ERROR|WARN|INFO|DEBUG|TRACE|FATAL)"
                    + "\\s+"
                    + "([\\w\\.]+)"
                    + "\\s+-\\s+(.*)$"
    );

    // Log4j 2.x 格式正则: yyyy-MM-dd HH:mm:ss,SSS [thread] LEVEL class - message
    private static final Pattern PATTERN_LOG4J2 = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}[,\\.]\\d{3})"  // 时间戳 (带毫秒)
                    + "\\s+"
                    + "\\[([^\\]]+)\\]"
                    + "\\s+"
                    + "(ERROR|WARN|INFO|DEBUG|TRACE|FATAL)"
                    + "\\s+"
                    + "([\\w\\.]+)"
                    + "\\s+-\\s+(.*)$"
    );

    // Log4j 2.x 简洁格式: LEVEL [thread] class - message (无日期)
    private static final Pattern PATTERN_LOG4J2_SIMPLE = Pattern.compile(
            "^(ERROR|WARN|INFO|DEBUG|TRACE|FATAL)"
                    + "\\s+\\[([^\\]]+)\\]"
                    + "\\s+([\\w\\.]+)"
                    + "\\s+-\\s+(.*)$"
    );

    // 类名:行号 格式
    private static final Pattern CLASS_LINE_PATTERN = Pattern.compile(
            "([\\w\\.]+):(\\d+)"
    );

    @Override
    public ParseResult parse(String content) {
        if (content == null || content.isEmpty()) {
            return ParseResult.builder()
                    .success(false)
                    .errorMessage("Empty content")
                    .build();
        }

        try {
            String[] lines = content.split("\n");
            if (lines.length == 0) {
                return parseFallback(content);
            }

            String firstLine = lines[0].trim();

            // 尝试各种模式匹配
            ParseResult result = parseWithPattern(firstLine, content, PATTERN_LOG4J2, FORMATTER_COMMA_MS);
            if (result != null) {
                return attachStackTrace(result, lines);
            }

            result = parseWithPattern(firstLine, content, PATTERN_LOG4J1_MS, FORMATTER_3_MS);
            if (result != null) {
                return attachStackTrace(result, lines);
            }

            result = parseWithPattern(firstLine, content, PATTERN_LOG4J1, FORMATTER_NO_MS);
            if (result != null) {
                return attachStackTrace(result, lines);
            }

            result = parseSimplePattern(firstLine);
            if (result != null) {
                return attachStackTrace(result, lines);
            }

            return parseFallback(content);

        } catch (Exception e) {
            log.warn("Failed to parse Log4j log: {}", e.getMessage());
            return parseFallback(content);
        }
    }

    private ParseResult parseWithPattern(String firstLine, String content, Pattern pattern, DateTimeFormatter formatter) {
        Matcher matcher = pattern.matcher(firstLine);
        if (!matcher.matches()) {
            return null;
        }

        String timestampStr = matcher.group(1);
        LocalDateTime timestamp = parseTimestamp(timestampStr, formatter);

        String thread = matcher.group(2);
        String level = matcher.group(3);
        String classLine = matcher.group(4);
        String message = matcher.group(5);

        // 解析类名和行号
        String fullClassName = null;
        String simpleClassName = null;
        String packageName = null;
        Integer lineNumber = null;

        if (classLine != null && !classLine.isEmpty()) {
            Matcher classLineMatcher = CLASS_LINE_PATTERN.matcher(classLine);
            if (classLineMatcher.find()) {
                fullClassName = classLineMatcher.group(1);
                lineNumber = Integer.parseInt(classLineMatcher.group(2));
            } else {
                fullClassName = classLine;
            }

            if (fullClassName != null && fullClassName.contains(".")) {
                int lastDotIndex = fullClassName.lastIndexOf('.');
                simpleClassName = fullClassName.substring(lastDotIndex + 1);
                packageName = fullClassName.substring(0, lastDotIndex);
            } else {
                simpleClassName = fullClassName;
                packageName = "";
            }
        }

        // 提取异常信息
        String exceptionType = null;
        String exceptionMessage = null;
        if (message != null && message.contains(":")) {
            String potentialException = message.split(":")[0];
            if (potentialException != null &&
                    (potentialException.endsWith("Exception") ||
                            potentialException.endsWith("Error"))) {
                exceptionType = potentialException;
                exceptionMessage = message.substring(message.indexOf(":") + 1).trim();
            }
        }

        return ParseResult.builder()
                .success(true)
                .timestamp(timestamp)
                .level(level)
                .thread(thread)
                .className(fullClassName)
                .methodName(simpleClassName)
                .fileName(packageName)
                .lineNumber(lineNumber)
                .message(message)
                .exceptionType(exceptionType)
                .exceptionMessage(exceptionMessage)
                .fields(parseFields(firstLine))
                .build();
    }

    private ParseResult parseSimplePattern(String line) {
        Matcher matcher = PATTERN_LOG4J2_SIMPLE.matcher(line);
        if (!matcher.matches()) {
            return null;
        }

        String level = matcher.group(1);
        String thread = matcher.group(2);
        String classLine = matcher.group(3);
        String message = matcher.group(4);

        String fullClassName = null;
        String simpleClassName = null;
        String packageName = null;

        if (classLine != null && !classLine.isEmpty()) {
            Matcher classLineMatcher = CLASS_LINE_PATTERN.matcher(classLine);
            if (classLineMatcher.find()) {
                fullClassName = classLineMatcher.group(1);
            } else {
                fullClassName = classLine;
            }

            if (fullClassName != null && fullClassName.contains(".")) {
                int lastDotIndex = fullClassName.lastIndexOf('.');
                simpleClassName = fullClassName.lastIndexOf(".") > 0 ?
                        fullClassName.substring(fullClassName.lastIndexOf('.') + 1) : fullClassName;
                packageName = fullClassName.contains(".") ?
                        fullClassName.substring(0, fullClassName.lastIndexOf('.')) : "";
            } else {
                simpleClassName = fullClassName;
                packageName = "";
            }
        }

        return ParseResult.builder()
                .success(true)
                .timestamp(LocalDateTime.now())
                .level(level)
                .thread(thread)
                .className(fullClassName)
                .methodName(simpleClassName)
                .fileName(packageName)
                .message(message)
                .fields(parseFields(line))
                .build();
    }

    private LocalDateTime parseTimestamp(String timestampStr, DateTimeFormatter formatter) {
        try {
            return LocalDateTime.parse(timestampStr, formatter);
        } catch (Exception e) {
            try {
                String normalized = timestampStr.replace(',', '.');
                return LocalDateTime.parse(normalized, FORMATTER_COMMA_MS);
            } catch (Exception ex) {
                log.warn("Failed to parse timestamp: {}", timestampStr);
                return LocalDateTime.now();
            }
        }
    }

    private ParseResult attachStackTrace(ParseResult result, String[] lines) {
        if (lines.length > 1) {
            StringBuilder stackTraceBuilder = new StringBuilder();
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (!line.isEmpty()) {
                    stackTraceBuilder.append(line).append("\n");
                }
            }

            String stackTrace = stackTraceBuilder.length() > 0 ? stackTraceBuilder.toString().trim() : null;

            if (stackTrace != null) {
                result.setStackTrace(stackTrace);

                // 如果消息中没有异常信息，从堆栈中提取
                if (result.getExceptionType() == null) {
                    String[] stackLines = stackTrace.split("\n");
                    for (String line : stackLines) {
                        if (line.startsWith("java.") || line.startsWith("org.")) {
                            int colonIndex = line.indexOf(":");
                            if (colonIndex > 0) {
                                result.setExceptionType(line.substring(0, colonIndex));
                                if (colonIndex + 1 < line.length()) {
                                    result.setExceptionMessage(line.substring(colonIndex + 1).trim());
                                }
                                break;
                            } else {
                                result.setExceptionType(line.trim());
                                break;
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    private ParseResult parseFallback(String content) {
        String level = detectLevel(content);

        return ParseResult.builder()
                .success(true)
                .timestamp(LocalDateTime.now())
                .level(level)
                .message(content)
                .fields(parseFields(content))
                .build();
    }

    private String detectLevel(String content) {
        if (content.contains("ERROR") || content.contains("FATAL")) {
            return "ERROR";
        } else if (content.contains("WARN")) {
            return "WARN";
        } else if (content.contains("DEBUG")) {
            return "DEBUG";
        } else if (content.contains("TRACE")) {
            return "TRACE";
        } else if (content.contains("INFO")) {
            return "INFO";
        }
        return "INFO";
    }

    private java.util.Map<String, Object> parseFields(String content) {
        java.util.Map<String, Object> fields = new java.util.HashMap<>();
        fields.put("rawLength", content.length());
        fields.put("hasException", content.contains("Exception") || content.contains("Error"));
        return fields;
    }

    @Override
    public String getFormatName() {
        return "LOG4J";
    }

    @Override
    public boolean supports(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        String[] lines = content.split("\n");
        if (lines.length == 0) {
            return false;
        }

        String firstLine = lines[0].trim();

        return PATTERN_LOG4J2.matcher(firstLine).matches() ||
                PATTERN_LOG4J1_MS.matcher(firstLine).matches() ||
                PATTERN_LOG4J1.matcher(firstLine).matches() ||
                PATTERN_LOG4J2_SIMPLE.matcher(firstLine).matches();
    }
}
