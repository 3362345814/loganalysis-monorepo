package com.evelin.loganalysis.logprocessing.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class NginxJsonLogParser implements ParseStrategy {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 常见的时间字段名
    private static final String[] TIME_FIELDS = {
            "time_local", "timestamp", "@timestamp", "time", "datetime", "date"
    };

    @Override
    public ParseResult parse(String content, String customPattern) {
        if (content == null || content.isEmpty()) {
            return ParseResult.builder()
                    .success(false)
                    .errorMessage("Empty content")
                    .build();
        }

        try {
            // 尝试解析为 JSON
            JsonNode rootNode = objectMapper.readTree(content);
            Map<String, Object> jsonMap = objectMapper.convertValue(rootNode, Map.class);

            if (jsonMap != null && !jsonMap.isEmpty()) {
                return buildParseResult(jsonMap);
            }
        } catch (JsonProcessingException e) {
            log.debug("Not JSON format: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to parse nginx JSON log: {}", e.getMessage());
        }

        return parseFallback(content);
    }

    private ParseResult buildParseResult(Map<String, Object> jsonMap) {
        Map<String, Object> fields = new HashMap<>();

        for (Map.Entry<String, Object> entry : jsonMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            fields.put(key, convertValue(value));
        }

        // 尝试解析时间
        for (String timeField : TIME_FIELDS) {
            if (fields.containsKey(timeField)) {
                LocalDateTime logTime = parseTimeField(timeField, fields.get(timeField));
                if (logTime != null) {
                    fields.put("log_time", logTime);
                    break;
                }
            }
        }

        // 根据 HTTP 状态码计算日志级别
        String level = calculateAccessLevel(getStatusCode(jsonMap));

        return ParseResult.builder()
                .success(true)
                .logType("nginx_access_json")
                .level(level)
                .fields(fields)
                .build();
    }

    private int getStatusCode(Map<String, Object> jsonMap) {
        // 常见的状态码字段名
        String[] statusFields = {"status", "status_code", "http_status", "httpStatus", "sc_status"};
        for (String field : statusFields) {
            Object value = jsonMap.get(field);
            if (value != null) {
                try {
                    return Integer.parseInt(value.toString());
                } catch (NumberFormatException e) {
                    // 继续尝试其他字段
                }
            }
        }
        return 200; // 默认返回成功
    }

    private String calculateAccessLevel(int statusCode) {
        if (statusCode >= 500) {
            return "ERROR";
        } else if (statusCode >= 400) {
            return "WARN";
        }
        return "INFO";
    }

    private LocalDateTime parseTimeField(String timeField, Object value) {
        if (value == null) return null;

        String timeStr = value.toString().trim();
        if (timeStr.isEmpty()) return null;

        // 尝试多种格式
        DateTimeFormatter[] formatters = {
                // Nginx 标准格式: 13/Mar/2026:14:59:16 +0000
                DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss X").withLocale(java.util.Locale.US),
                // 另一种: 13/Mar/2026:14:59:16 +00:00
                DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss XXX").withLocale(java.util.Locale.US),
                // ISO 格式
                DateTimeFormatter.ISO_OFFSET_DATE_TIME,
                // 简单格式
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        };

        for (DateTimeFormatter formatter : formatters) {
            try {
                ZonedDateTime zdt = ZonedDateTime.parse(timeStr, formatter);
                return zdt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            } catch (DateTimeParseException e) {
                // 尝试下一个
            }
        }

        log.debug("Failed to parse time field '{}': {}", timeField, timeStr);
        return null;
    }

    private Object convertValue(Object value) {
        if (value == null) {
            return null;
        }
        // 如果是字符串，去除引号
        if (value instanceof String) {
            String str = (String) value;
            // 去除首尾引号
            if (str.startsWith("\"") && str.endsWith("\"") && str.length() >= 2) {
                return str.substring(1, str.length() - 1);
            }
            return str;
        }
        if (value instanceof Double) {
            Double d = (Double) value;
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return d.longValue();
            }
            return d;
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Boolean) {
            return value;
        }
        return value.toString();
    }

    @Override
    public String getFormatName() {
        return "nginx_json";
    }

    @Override
    public boolean supports(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        String trimmed = content.trim();
        return trimmed.startsWith("{") && trimmed.endsWith("}");
    }

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
