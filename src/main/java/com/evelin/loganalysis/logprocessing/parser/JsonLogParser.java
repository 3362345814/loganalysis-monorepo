package com.evelin.loganalysis.logprocessing.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * JSON 日志解析器
 *
 * @author Evelin
 */
@Slf4j
@Component
public class JsonLogParser implements ParseStrategy {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 常见的时间字段名
    private static final String[] TIMESTAMP_FIELDS = {
            "timestamp", "time", "datetime", "@timestamp", "ts", "date", "logTime"
    };

    // 常见的日志级别字段名
    private static final String[] LEVEL_FIELDS = {
            "level", "severity", "loglevel", "log_level", "lvl"
    };

    // 常见的消息字段名
    private static final String[] MESSAGE_FIELDS = {
            "message", "msg", "text", "content", "log"
    };

    // 常见的异常字段名
    private static final String[] EXCEPTION_FIELDS = {
            "exception", "error", "stackTrace", "stack_trace", "throwable"
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
            JsonNode rootNode = objectMapper.readTree(content);

            // 解析时间戳
            LocalDateTime timestamp = parseTimestamp(rootNode);

            // 解析日志级别
            String level = parseLevel(rootNode);

            // 解析消息
            String message = parseMessage(rootNode);

            // 解析异常信息
            String stackTrace = parseStackTrace(rootNode);
            String exceptionType = parseExceptionType(rootNode);
            String exceptionMessage = parseExceptionMessage(rootNode);

            // 提取所有字段
            Map<String, Object> fields = extractFields(rootNode);

            return ParseResult.builder()
                    .success(true)
                    .timestamp(timestamp)
                    .level(level)
                    .message(message)
                    .stackTrace(stackTrace)
                    .exceptionType(exceptionType)
                    .exceptionMessage(exceptionMessage)
                    .fields(fields)
                    .build();

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON log: {}", e.getMessage());
            return parseFallback(content);
        } catch (Exception e) {
            log.error("Unexpected error parsing JSON log: {}", e.getMessage());
            return parseFallback(content);
        }
    }

    private LocalDateTime parseTimestamp(JsonNode node) {
        for (String field : TIMESTAMP_FIELDS) {
            JsonNode timestampNode = node.get(field);
            if (timestampNode != null) {
                String value = timestampNode.asText();
                try {
                    // 尝试 ISO 格式
                    return LocalDateTime.parse(value);
                } catch (Exception e) {
                    try {
                        // 尝试毫秒时间戳
                        long millis = Long.parseLong(value);
                        return LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(millis),
                                java.time.ZoneId.systemDefault()
                        );
                    } catch (Exception e2) {
                        // 继续尝试其他格式
                    }
                }
            }
        }
        return LocalDateTime.now();
    }

    private String parseLevel(JsonNode node) {
        for (String field : LEVEL_FIELDS) {
            JsonNode levelNode = node.get(field);
            if (levelNode != null) {
                return levelNode.asText().toUpperCase();
            }
        }
        return "INFO";
    }

    private String parseMessage(JsonNode node) {
        for (String field : MESSAGE_FIELDS) {
            JsonNode messageNode = node.get(field);
            if (messageNode != null) {
                return messageNode.asText();
            }
        }
        return node.toString();
    }

    private String parseStackTrace(JsonNode node) {
        for (String field : EXCEPTION_FIELDS) {
            JsonNode stackTraceNode = node.get(field);
            if (stackTraceNode != null && stackTraceNode.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode element : stackTraceNode) {
                    sb.append(element.asText()).append("\n");
                }
                return sb.toString();
            } else if (stackTraceNode != null && stackTraceNode.isTextual()) {
                return stackTraceNode.asText();
            }
        }
        return null;
    }

    private String parseExceptionType(JsonNode node) {
        JsonNode exceptionNode = node.get("exceptionType");
        if (exceptionNode != null) {
            return exceptionNode.asText();
        }
        return null;
    }

    private String parseExceptionMessage(JsonNode node) {
        JsonNode exceptionNode = node.get("exceptionMessage");
        if (exceptionNode != null) {
            return exceptionNode.asText();
        }
        return null;
    }

    private Map<String, Object> extractFields(JsonNode node) {
        Map<String, Object> fields = new HashMap<>();
        node.fields().forEachRemaining(entry -> {
            if (!isStandardField(entry.getKey())) {
                fields.put(entry.getKey(), entry.getValue().toString());
            }
        });
        return fields;
    }

    private boolean isStandardField(String key) {
        for (String field : TIMESTAMP_FIELDS) {
            if (field.equalsIgnoreCase(key)) return true;
        }
        for (String field : LEVEL_FIELDS) {
            if (field.equalsIgnoreCase(key)) return true;
        }
        for (String field : MESSAGE_FIELDS) {
            if (field.equalsIgnoreCase(key)) return true;
        }
        return false;
    }

    private ParseResult parseFallback(String content) {
        return ParseResult.builder()
                .success(true)
                .timestamp(LocalDateTime.now())
                .level("INFO")
                .message(content)
                .fields(new HashMap<>())
                .build();
    }

    @Override
    public String getFormatName() {
        return "JSON";
    }

    @Override
    public boolean supports(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        String trimmed = content.trim();
        return trimmed.startsWith("{") && trimmed.endsWith("}");
    }
}
