package com.evelin.loganalysis.logprocessing.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nginx Error Log 解析器
 * 固定格式解析错误日志
 * <p>
 * 格式: 2026/03/13 14:26:31 [error] 1234#1234: *5678 upstream timed out
 * 或:   2026/03/13 14:26:31 [notice] 1234#1234: signal process started
 *
 * @author Evelin
 */
@Slf4j
@Component
public class ErrorLogParser implements ParseStrategy {

    /**
     * 完整的错误日志正则
     * 包含: 时间、级别、进程ID、线程ID、连接ID、消息
     */
    private static final Pattern ERROR_PATTERN = Pattern.compile(
            "^(\\d{4}/\\d{2}/\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})\\s+"  // 1. time
                    + "\\[(\\w+)\\]\\s+"                                      // 2. level
                    + "(\\d+)#(\\d+):"                                       // 3. pid, 4. tid
                    + "(?:\\s+\\*(\\d+))?"                                   // 5. connection (optional)
                    + "\\s*(.*)$"                                           // 6. message
    );

    /**
     * 简化的错误日志正则（没有进程信息）
     */
    private static final Pattern SIMPLE_ERROR_PATTERN = Pattern.compile(
            "^(\\d{4}/\\d{2}/\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})\\s+"  // 1. time
                    + "\\[(\\w+)\\]\\s+"                                      // 2. level
                    + "(.*)$"                                                // 3. message
    );

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    @Override
    public ParseResult parse(String content) {

        if (content == null || content.isEmpty()) {
            log.warn("[ErrorLogParser] 日志内容为空");
            return ParseResult.builder()
                    .success(false)
                    .errorMessage("Empty content")
                    .build();
        }

        try {
            Matcher matcher = ERROR_PATTERN.matcher(content);
            if (matcher.matches()) {
                return buildParseResult(matcher, true);
            }

            Matcher simpleMatcher = SIMPLE_ERROR_PATTERN.matcher(content);
            if (simpleMatcher.matches()) {
                return buildParseResult(simpleMatcher, false);
            }

            log.warn("[ErrorLogParser] 无法匹配任何模式，内容: {}", content.substring(0, Math.min(100, content.length())));
        } catch (Exception e) {
            log.error("[ErrorLogParser] 解析异常: {}", e.getMessage(), e);
        }

        return parseFallback(content);
    }

    private ParseResult buildParseResult(Matcher matcher, boolean isFullFormat) {
        Map<String, Object> fields = new HashMap<>();

        String timeStr = matcher.group(1);
        String level = matcher.group(2);
        String message;
        Integer pid = null;
        Integer tid = null;
        Long connectionId = null;

        if (isFullFormat) {
            message = matcher.group(6);
            try {
                pid = Integer.parseInt(matcher.group(3));
                tid = Integer.parseInt(matcher.group(4));
                String connStr = matcher.group(5);
                if (connStr != null && !connStr.isEmpty()) {
                    connectionId = Long.parseLong(connStr);
                }
            } catch (NumberFormatException e) {
                log.debug("Failed to parse pid/tid: {}", e.getMessage());
            }
        } else {
            message = matcher.group(3);
        }

        // 解析时间
        try {
            LocalDateTime logTime = LocalDateTime.parse(timeStr, TIME_FORMATTER);
            fields.put("log_time", logTime);
        } catch (Exception e) {
            log.debug("Failed to parse time: {}", timeStr);
            fields.put("time_local", timeStr);
        }

        fields.put("level", level);
        fields.put("message", message);

        if (pid != null) fields.put("pid", pid);
        if (tid != null) fields.put("tid", tid);
        if (connectionId != null) fields.put("connection_id", connectionId);

        // 映射到标准字段
        String logLevel = mapNginxLevelToStandard(level);

        return ParseResult.builder()
                .success(true)
                .logType("nginx_error")
                .level(logLevel)
                .message(message)
                .fields(fields)
                .build();
    }

    /**
     * 将 Nginx 错误级别映射到标准日志级别
     */
    private String mapNginxLevelToStandard(String nginxLevel) {
        if (nginxLevel == null) return "INFO";

        return switch (nginxLevel.toLowerCase()) {
            case "debug" -> "DEBUG";
            case "info", "notice" -> "INFO";
            case "warn", "warning" -> "WARN";
            case "error", "crit", "alert", "emerg" -> "ERROR";
            default -> "INFO";
        };
    }

    @Override
    public boolean supports(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        // 检查是否匹配错误日志格式
        return ERROR_PATTERN.matcher(content).matches() ||
                SIMPLE_ERROR_PATTERN.matcher(content).matches();
    }

    @Override
    public String getFormatName() {
        return "NGINX_ERROR";
    }

    /**
     * Fallback: 无法解析时返回原始内容
     */
    private ParseResult parseFallback(String content) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("raw_content", content);

        return ParseResult.builder()
                .success(true)
                .logType("unknown")
                .fields(fields)
                .build();
    }
}
