package com.evelin.loganalysis.logprocessing.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nginx Access Log 解析器
 * 根据用户提供的 log_format 动态生成正则表达式进行解析
 *
 * @author Evelin
 */
@Slf4j
@Component
public class AccessLogParser implements ParseStrategy {

    private static final Map<String, String> VARIABLE_PATTERNS = new HashMap<>();

    static {
        // Nginx 常用变量
        VARIABLE_PATTERNS.put("remote_addr", "([\\d\\.]+)");
        VARIABLE_PATTERNS.put("remote_user", "(\\S+)");
        VARIABLE_PATTERNS.put("time_local", "([^\\]]+)");
        VARIABLE_PATTERNS.put("request", "\"(?:([A-Z]+)\\s+(\\S+)\\s+([^\"]+))\"|(\"-|\"[^\"]*\")");
        VARIABLE_PATTERNS.put("request_method", "([A-Z]+)");
        VARIABLE_PATTERNS.put("request_uri", "(\\S+)");
        VARIABLE_PATTERNS.put("http_version", "(HTTP/[\\d\\.]+)");
        VARIABLE_PATTERNS.put("status", "(\\d{3})");
        VARIABLE_PATTERNS.put("body_bytes_sent", "(\\d+)");
        VARIABLE_PATTERNS.put("http_referer", "(\"[^\"]*\"|-)");
        VARIABLE_PATTERNS.put("http_user_agent", "(\"[^\"]*\"|-)");
        VARIABLE_PATTERNS.put("http_x_forwarded_for", "(\"[^\"]*\"|-)");
        VARIABLE_PATTERNS.put("upstream_addr", "([\\d\\.:]+)");
        VARIABLE_PATTERNS.put("upstream_status", "(\\d{3}|-)");
        VARIABLE_PATTERNS.put("request_time", "([\\d\\.]+)");
        VARIABLE_PATTERNS.put("upstream_response_time", "([\\d\\.]+|-)");
        VARIABLE_PATTERNS.put("urt", "[\\d\\.]+|-");
    }

    private Pattern pattern;
    private List<String> fieldNames;
    private static final DateTimeFormatter TIME_LOCAL_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z").withLocale(Locale.US);

    /**
     * 配置 log_format
     *
     * @param logFormat nginx log_format 字符串
     *                  例如: $remote_addr - $remote_user [$time_local] "$request" $status $body_bytes_sent "$http_referer" "$http_user_agent"
     */
    public void configure(String logFormat) {
        if (logFormat == null || logFormat.isEmpty()) {
            throw new IllegalArgumentException("logFormat cannot be empty");
        }

        log.debug("Configuring AccessLogParser with format: {}", logFormat);

        this.fieldNames = new ArrayList<>();

        // 解析 log_format，提取变量名并构建正则
        String regex = parseLogFormat(logFormat);

        try {
            this.pattern = Pattern.compile(regex);
            log.debug("AccessLogParser regex: {}", regex);
        } catch (Exception e) {
            log.error("Failed to compile regex from log_format: {}", regex, e);
            throw new IllegalArgumentException("Invalid log_format: " + e.getMessage());
        }
    }

    /**
     * 解析 log_format 字符串，生成正则表达式
     */
    private String parseLogFormat(String logFormat) {
        StringBuilder regex = new StringBuilder("^");

        // 按字符遍历，提取变量和字面量
        int i = 0;
        while (i < logFormat.length()) {
            char c = logFormat.charAt(i);

            if (c == '$') {
                // 提取变量名
                int start = i + 1;
                int end = start;
                while (end < logFormat.length() && isVariableChar(logFormat.charAt(end))) {
                    end++;
                }
                String variableName = logFormat.substring(start, end);
                i = end;

                // 获取变量的正则模式
                String varPattern = VARIABLE_PATTERNS.get(variableName);
                if (varPattern != null) {
                    regex.append("(").append(varPattern).append(")");
                    fieldNames.add(variableName);
                } else {
                    // 未知变量，使用通用匹配
                    regex.append("(\\S+)");
                    fieldNames.add(variableName);
                    log.warn("Unknown variable: {}, using generic pattern", variableName);
                }
            } else if (c == ' ' || c == '\t') {
                // 空白字符，匹配一个或多个空白
                regex.append("\\s+");
                i++;
            } else if (c == '[' || c == ']') {
                // 方括号需要转义
                regex.append("\\").append(c);
                i++;
            } else if (c == '"') {
                // 双引号
                regex.append("\"");
                i++;
            } else if (c == '(' || c == ')' || c == '.' || c == '*' || c == '+' || c == '?' || c == '^' || c == '$' || c == '|') {
                // 正则特殊字符需要转义
                regex.append("\\").append(c);
                i++;
            } else {
                // 普通字符
                regex.append(c);
                i++;
            }
        }

        regex.append("$");
        return regex.toString();
    }

    private boolean isVariableChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    @Override
    public ParseResult parse(String content, String customPattern) {
        if (content == null || content.isEmpty()) {
            return ParseResult.builder()
                    .success(false)
                    .errorMessage("Empty content")
                    .build();
        }

        // 如果未配置 log_format，使用默认配置
        if (pattern == null) {
            configure(getDefaultLogFormat());
        }

        try {
            Matcher matcher = pattern.matcher(content);

            if (matcher.matches()) {
                log.debug("Pattern matched successfully, groups: {}", matcher.groupCount());
                return buildParseResult(matcher);
            } else {
                log.debug("Regex: {}", pattern.pattern());
            }
        } catch (Exception e) {
            log.warn("Failed to parse access log: {}", e.getMessage());
        }

        return parseFallback(content);
    }

    private ParseResult buildParseResult(Matcher matcher) {
        Map<String, Object> fields = new HashMap<>();

        int groupCount = matcher.groupCount();
        for (int i = 1; i <= groupCount && i <= fieldNames.size(); i++) {
            String fieldName = fieldNames.get(i - 1);
            String value = matcher.group(i);

            // 特殊处理 request 字段
            if ("request".equals(fieldName) && value != null) {
                // request 包含 method, uri, version，需要进一步解析
                parseRequestField(fields, value);
            } else if (value != null && !value.isEmpty()) {
                // 尝试转换为数字
                Object parsedValue = parseValue(fieldName, value);
                fields.put(fieldName, parsedValue);
            }
        }

        LocalDateTime timestamp = extractTimestamp(fields);

        return ParseResult.builder()
                .success(true)
                .logType("nginx_access")
                .timestamp(timestamp)
                .fields(fields)
                .build();
    }

    private LocalDateTime extractTimestamp(Map<String, Object> fields) {
        Object timeLocal = fields.get("time_local");
        if (timeLocal != null) {
            return UtcTimestampParser.parseUtc(String.valueOf(timeLocal), TIME_LOCAL_FORMATTER);
        }
        Object timestamp = fields.get("timestamp");
        if (timestamp != null) {
            return UtcTimestampParser.parseUtc(String.valueOf(timestamp), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        return UtcTimestampParser.nowUtc();
    }

    private void parseRequestField(Map<String, Object> fields, String request) {
        // request 格式: "METHOD URI HTTP/X.X"
        Pattern requestPattern = Pattern.compile("([A-Z]+)\\s+(\\S+)\\s+(HTTP/[\\d\\.]+)");
        Matcher requestMatcher = requestPattern.matcher(request);
        if (requestMatcher.matches()) {
            fields.put("request_method", requestMatcher.group(1));
            fields.put("request_uri", requestMatcher.group(2));
            fields.put("http_version", requestMatcher.group(3));
        }
    }

    private Object parseValue(String fieldName, String value) {
        // 数字类型字段
        if (fieldName.contains("status") || fieldName.contains("bytes") || fieldName.contains("time")) {
            try {
                if (fieldName.contains("status")) {
                    return Integer.parseInt(value);
                }
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                // 不是数字，返回原值
            }
        }
        return value;
    }

    @Override
    public boolean supports(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        if (pattern == null) {
            configure(getDefaultLogFormat());
        }

        return pattern.matcher(content).matches();
    }

    @Override
    public String getFormatName() {
        return "NGINX_ACCESS";
    }

    /**
     * 获取默认的 log_format（标准 Nginx combined 格式）
     */
    public static String getDefaultLogFormat() {
        return "$remote_addr - $remote_user [$time_local] \"$request\" $status $body_bytes_sent \"$http_referer\" \"$http_user_agent\"";
    }

    /**
     * 获取支持的变量列表
     */
    public static Set<String> getSupportedVariables() {
        return VARIABLE_PATTERNS.keySet();
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
