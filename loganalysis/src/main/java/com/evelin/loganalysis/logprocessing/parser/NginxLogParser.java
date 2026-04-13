package com.evelin.loganalysis.logprocessing.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class NginxLogParser implements ParseStrategy {

    private static final DateTimeFormatter NGINX_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");

    private static final DateTimeFormatter NGINX_ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static final DateTimeFormatter NGINX_DATELESS_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter NGINX_ERROR_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    private static final Pattern NGINX_ACCESS_PATTERN = Pattern.compile(
            "^(\\S+)\\s+"                           // 1. client_ip
                    + "(\\S+)\\s+"                          // 2. remote_user
                    + "\\[([^\\]]+)\\]\\s+"                 // 3. time_local
                    + "\"(\\S+)\\s+(\\S+)\\s+(\\S+)\"\\s+"   // 4. request_method, 5. request_uri, 6. protocol
                    + "(\\d+)\\s+"                          // 7. status
                    + "(\\d+)\\s+"                          // 8. body_bytes_sent
                    + "\"([^\"]*)\"\\s+"                    // 9. http_referer
                    + "\"([^\"]*)\""                        // 10. http_user_agent
    );

    private static final Pattern NGINX_ACCESS_EXTENDED_PATTERN = Pattern.compile(
            "^(\\S+)\\s+"                           // 1. client_ip
                    + "(\\S+)\\s+"                          // 2. remote_user
                    + "\\[([^\\]]+)\\]\\s+"                 // 3. time_local
                    + "\"(\\S+)\\s+(\\S+)\\s+(\\S+)\"\\s+"   // 4. request_method, 5. request_uri, 6. protocol
                    + "(\\d+)\\s+"                          // 7. status
                    + "(\\d+)\\s+"                          // 8. body_bytes_sent
                    + "\"([^\"]*)\"\\s+"                    // 9. http_referer
                    + "\"([^\"]*)\"\\s+"                    // 10. http_user_agent
                    + "(\\S+)\\s+"                          // 11. upstream_addr (if present)
                    + "([\\d.]+)"                           // 12. upstream_response_time
    );

    private static final Pattern NGINX_ACCESS_SIMPLE_PATTERN = Pattern.compile(
            "^(.+?)\\s+"                            // ip or client
                    + "\\[([^\\]]+)\\]\\s+"                 // time
                    + "\"(.*?)\"\\s+"                       // request
                    + "(\\d+)"                              // status
    );

    private static final Pattern NGINX_ERROR_PATTERN = Pattern.compile(
            "^(\\d{4}/\\d{2}/\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})\\s+"  // 1. timestamp
                    + "\\[(\\w+)\\]\\s+"                    // 2. log_level (error, warn, crit, etc.)
                    + "(?:\\d+#\\d+:\\s*)?"                 // optional: pid#tid:
                    + "(?:\\*\\d+:\\s*)?"                   // optional: *connectionId:
                    + "(.*)$"                               // 3. error message
    );

    private static final Pattern NGINX_ERROR_SIMPLE_PATTERN = Pattern.compile(
            "^(\\d{4}/\\d{2}/\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})\\s+"  // 1. timestamp
                    + "\\[(\\w+)\\]\\s+"                    // 2. log_level
                    + "(.*)$"                               // 3. error message
    );

    @Override
    public ParseResult parse(String content, String customPattern) {
        if (content == null || content.isEmpty()) {
            return ParseResult.builder()
                    .success(false)
                    .errorMessage("Empty content")
                    .build();
        }

        try {
            if (isErrorLog(content)) {
                return parseErrorLog(content);
            } else {
                return parseAccessLog(content);
            }
        } catch (Exception e) {
            log.warn("Failed to parse nginx log: {}", e.getMessage());
            return parseFallback(content);
        }
    }

    private boolean isErrorLog(String content) {
        String trimmed = content.trim();
        if (trimmed.contains("[error]") || trimmed.contains("[warn]") ||
                trimmed.contains("[crit]") || trimmed.contains("[alert]") ||
                trimmed.contains("[emerg]") || trimmed.contains("[notice]")) {
            return true;
        }
        if (trimmed.matches("^\\d{4}/\\d{2}/\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\s+\\[\\w+\\]")) {
            return true;
        }
        return false;
    }

    private ParseResult parseAccessLog(String content) {
        Matcher matcher = NGINX_ACCESS_PATTERN.matcher(content);
        if (matcher.matches()) {
            return parseAccessWithMatcher(matcher);
        }

        Matcher extendedMatcher = NGINX_ACCESS_EXTENDED_PATTERN.matcher(content);
        if (extendedMatcher.matches()) {
            return parseAccessExtendedWithMatcher(extendedMatcher);
        }

        Matcher simpleMatcher = NGINX_ACCESS_SIMPLE_PATTERN.matcher(content);
        if (simpleMatcher.matches()) {
            return parseAccessSimpleWithMatcher(simpleMatcher);
        }

        return parseFallback(content);
    }

    private ParseResult parseErrorLog(String content) {
        Matcher matcher = NGINX_ERROR_PATTERN.matcher(content);
        if (matcher.matches()) {
            return parseErrorWithMatcher(matcher);
        }

        Matcher simpleMatcher = NGINX_ERROR_SIMPLE_PATTERN.matcher(content);
        if (simpleMatcher.matches()) {
            return parseErrorSimpleWithMatcher(simpleMatcher);
        }

        return parseFallback(content);
    }

    private ParseResult parseAccessWithMatcher(Matcher matcher) {
        Map<String, Object> fields = new HashMap<>();

        String clientIp = matcher.group(1);
        String remoteUser = matcher.group(2);
        String timeLocalStr = matcher.group(3);
        String requestMethod = matcher.group(4);
        String requestUri = matcher.group(5);
        String protocol = matcher.group(6);
        String status = matcher.group(7);
        String bodyBytesSent = matcher.group(8);
        String httpReferer = matcher.group(9);
        String httpUserAgent = matcher.group(10);

        LocalDateTime timestamp = parseTimestamp(timeLocalStr);
        String level = calculateAccessLevel(status);

        String message = buildAccessMessage(requestMethod, requestUri, status, clientIp);

        fields.put("logType", "access");
        fields.put("clientIp", clientIp);
        fields.put("remoteUser", remoteUser);
        fields.put("requestMethod", requestMethod);
        fields.put("requestUri", requestUri);
        fields.put("protocol", protocol);
        fields.put("status", Integer.parseInt(status));
        fields.put("bodyBytesSent", Long.parseLong(bodyBytesSent));
        fields.put("httpReferer", httpReferer);
        fields.put("httpUserAgent", httpUserAgent);

        return ParseResult.builder()
                .success(true)
                .timestamp(timestamp)
                .level(level)
                .message(message)
                .fields(fields)
                .build();
    }

    private ParseResult parseAccessExtendedWithMatcher(Matcher matcher) {
        ParseResult result = parseAccessWithMatcher(matcher);

        String upstreamAddr = matcher.group(11);
        String upstreamResponseTime = matcher.group(12);

        result.getFields().put("logType", "access");
        result.getFields().put("upstreamAddr", upstreamAddr);
        result.getFields().put("upstreamResponseTime", parseUpstreamTime(upstreamResponseTime));

        if (upstreamResponseTime != null && !upstreamResponseTime.isEmpty()) {
            try {
                double responseTime = Double.parseDouble(upstreamResponseTime);
                result.getFields().put("responseTime", responseTime);
            } catch (NumberFormatException e) {
                result.getFields().put("upstreamResponseTime", upstreamResponseTime);
            }
        }

        return result;
    }

    private ParseResult parseAccessSimpleWithMatcher(Matcher matcher) {
        Map<String, Object> fields = new HashMap<>();

        String client = matcher.group(1);
        String timeLocalStr = matcher.group(2);
        String request = matcher.group(3);
        String status = matcher.group(4);

        LocalDateTime timestamp = parseTimestamp(timeLocalStr);
        String level = calculateAccessLevel(status);

        String requestMethod = "";
        String requestUri = request;
        if (request.contains(" ")) {
            String[] parts = request.split("\\s+");
            if (parts.length >= 2) {
                requestMethod = parts[0];
                requestUri = parts[1];
            }
        }

        String message = buildAccessMessage(requestMethod, requestUri, status, client);

        fields.put("logType", "access");
        fields.put("clientIp", client);
        fields.put("requestMethod", requestMethod);
        fields.put("requestUri", requestUri);
        fields.put("status", Integer.parseInt(status));

        return ParseResult.builder()
                .success(true)
                .timestamp(timestamp)
                .level(level)
                .message(message)
                .fields(fields)
                .build();
    }

    private ParseResult parseErrorWithMatcher(Matcher matcher) {
        Map<String, Object> fields = new HashMap<>();

        String timeStr = matcher.group(1);
        String levelStr = matcher.group(2);
        String errorMessage = matcher.group(3);

        LocalDateTime timestamp = parseErrorTimestamp(timeStr);
        String level = convertErrorLevel(levelStr);

        fields.put("logType", "error");
        fields.put("rawLevel", levelStr);
        fields.put("errorMessage", errorMessage);

        return ParseResult.builder()
                .success(true)
                .timestamp(timestamp)
                .level(level)
                .message(errorMessage)
                .fields(fields)
                .build();
    }

    private ParseResult parseErrorSimpleWithMatcher(Matcher matcher) {
        Map<String, Object> fields = new HashMap<>();

        String timeStr = matcher.group(1);
        String levelStr = matcher.group(2);
        String errorMessage = matcher.group(3);

        LocalDateTime timestamp = parseErrorTimestamp(timeStr);
        String level = convertErrorLevel(levelStr);

        fields.put("logType", "error");
        fields.put("rawLevel", levelStr);
        fields.put("errorMessage", errorMessage);

        return ParseResult.builder()
                .success(true)
                .timestamp(timestamp)
                .level(level)
                .message(errorMessage)
                .fields(fields)
                .build();
    }

    private LocalDateTime parseTimestamp(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return UtcTimestampParser.nowUtc();
        }

        return UtcTimestampParser.parseUtc(timeStr, NGINX_FORMATTER, NGINX_ISO_FORMATTER, NGINX_DATELESS_FORMATTER);
    }

    private LocalDateTime parseErrorTimestamp(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return UtcTimestampParser.nowUtc();
        }

        return UtcTimestampParser.parseUtc(timeStr, NGINX_ERROR_DATE_FORMATTER);
    }

    private Double parseUpstreamTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty() || timeStr.equals("-")) {
            return null;
        }
        try {
            return Double.parseDouble(timeStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String calculateAccessLevel(String status) {
        try {
            int statusCode = Integer.parseInt(status);
            if (statusCode >= 500) {
                return "ERROR";
            } else if (statusCode >= 400) {
                return "WARN";
            } else if (statusCode >= 300) {
                return "INFO";
            }
            return "INFO";
        } catch (NumberFormatException e) {
            return "INFO";
        }
    }

    private String convertErrorLevel(String rawLevel) {
        if (rawLevel == null) {
            return "ERROR";
        }
        return switch (rawLevel.toLowerCase()) {
            case "debug" -> "DEBUG";
            case "info", "notice" -> "INFO";
            case "warn", "warning" -> "WARN";
            case "error" -> "ERROR";
            case "crit", "critical" -> "ERROR";
            case "alert" -> "ERROR";
            case "emerg", "emergency" -> "ERROR";
            default -> "ERROR";
        };
    }

    private String buildAccessMessage(String method, String uri, String status, String ip) {
        return String.format("%s %s -> %s (from %s)",
                method, uri, status, ip);
    }

    private ParseResult parseFallback(String content) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("rawContent", content);

        return ParseResult.builder()
                .success(true)
                .timestamp(UtcTimestampParser.nowUtc())
                .level("INFO")
                .message(content)
                .fields(fields)
                .build();
    }

    @Override
    public String getFormatName() {
        return "NGINX";
    }

    @Override
    public boolean supports(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        String trimmed = content.trim();

        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return false;
        }

        if (isErrorLog(trimmed)) {
            return true;
        }

        if (trimmed.contains("[") && trimmed.contains("]\"")) {
            if (trimmed.matches("^\\S+\\s+\\S+\\s+\\[.+\\]\\s+\".+\"\\s+\\d+.*")) {
                return true;
            }
        }

        return false;
    }
}
