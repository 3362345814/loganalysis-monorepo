package com.evelin.loganalysis.logprocessing.parser;

import com.evelin.loganalysis.logprocessing.parser.context.ParseContext;
import com.evelin.loganalysis.logprocessing.parser.token.LiteralToken;
import com.evelin.loganalysis.logprocessing.parser.token.PatternTokenizer;
import com.evelin.loganalysis.logprocessing.parser.token.Token;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自定义日志格式解析器
 * 支持用户通过 Pattern 字符串（如 "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"）
 * 自定义日志解析规则
 * <p>
 * 同时保留原有的固定格式解析（向后兼容）：
 * - Log4j 1.x 格式: 2026-01-15 10:30:02 ERROR [thread] class - message
 * - Log4j 2.x 格式: 2026-01-15 10:30:02,123 ERROR [thread] class - message
 *
 * @author Evelin
 */
@Slf4j
@Component
public class Log4jLogParser implements ParseStrategy {

    // ========== 原有固定格式正则（向后兼容 - 仅用于格式检测）==========

    private static final DateTimeFormatter FORMATTER_3_MS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final DateTimeFormatter FORMATTER_NO_MS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter FORMATTER_COMMA_MS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS");

    // Log4j 1.x 格式正则
    private static final Pattern PATTERN_LOG4J1 = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})"
                    + "\\s+"
                    + "\\[([^\\]]+)\\]"
                    + "\\s+"
                    + "(ERROR|WARN|INFO|DEBUG|TRACE|FATAL)"
                    + "\\s+"
                    + "([\\w\\.]+)"
                    + "\\s+-\\s+(.*)$"
    );

    private static final Pattern PATTERN_LOG4J1_MS = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})"
                    + "\\s+"
                    + "\\[([^\\]]+)\\]"
                    + "\\s+"
                    + "(ERROR|WARN|INFO|DEBUG|TRACE|FATAL)"
                    + "\\s+"
                    + "([\\w\\.]+)"
                    + "\\s+-\\s+(.*)$"
    );

    private static final Pattern PATTERN_LOG4J2 = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}[,\\.]\\d{3})"
                    + "\\s+"
                    + "\\[([^\\]]+)\\]"
                    + "\\s+"
                    + "(ERROR|WARN|INFO|DEBUG|TRACE|FATAL)"
                    + "\\s+"
                    + "([\\w\\.]+)"
                    + "\\s+-\\s+(.*)$"
    );

    private static final Pattern PATTERN_LOG4J2_SIMPLE = Pattern.compile(
            "^(ERROR|WARN|INFO|DEBUG|TRACE|FATAL)"
                    + "\\s+\\[([^\\]]+)\\]"
                    + "\\s+([\\w\\.]+)"
                    + "\\s+-\\s+(.*)$"
    );

    private static final Pattern CLASS_LINE_PATTERN = Pattern.compile(
            "([\\w\\.]+):(\\d+)"
    );

    // ========== 自定义 Pattern 解析相关（Delimiter 方式）==========

    private List<Token> customTokens;
    private String customPatternStr;

    // 默认 Pattern（用于 SPRING_BOOT 和 LOG4J 格式）
    public static final String DEFAULT_PATTERN = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n";

    // ========== ParseStrategy 接口实现 ==========

    @Override
    public ParseResult parse(String content, String customPattern) {
        if (content == null || content.isEmpty()) {
            return ParseResult.builder()
                    .success(false)
                    .errorMessage("Empty content")
                    .build();
        }

        configure(customPattern);

        try {
            String[] lines = content.split("\n");
            if (lines.length == 0) {
                return parseFallback(content);
            }

            String firstLine = lines[0].trim();

            // 如果配置了自定义 Pattern，优先使用
            if (customTokens != null && !customTokens.isEmpty()) {
                ParseResult result = parseWithCustomPattern(firstLine, content);
                if (result != null && result.isSuccess()) {
                    return attachStackTrace(result, lines);
                }
            }

            // 尝试固定格式解析（向后兼容）
            ParseResult result = parseWithFixedPattern(firstLine, content, PATTERN_LOG4J2, FORMATTER_COMMA_MS);
            if (result != null) {
                return attachStackTrace(result, lines);
            }

            result = parseWithFixedPattern(firstLine, content, PATTERN_LOG4J1_MS, FORMATTER_3_MS);
            if (result != null) {
                return attachStackTrace(result, lines);
            }

            result = parseWithFixedPattern(firstLine, content, PATTERN_LOG4J1, FORMATTER_NO_MS);
            if (result != null) {
                return attachStackTrace(result, lines);
            }

            result = parseSimplePattern(firstLine);
            if (result != null) {
                return attachStackTrace(result, lines);
            }

            return parseFallback(content);

        } catch (Exception e) {
            log.warn("Failed to parse log: {}", e.getMessage());
            return parseFallback(content);
        }
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


        // 尝试固定格式
        return PATTERN_LOG4J2.matcher(firstLine).matches() ||
                PATTERN_LOG4J1_MS.matcher(firstLine).matches() ||
                PATTERN_LOG4J1.matcher(firstLine).matches() ||
                PATTERN_LOG4J2_SIMPLE.matcher(firstLine).matches();
    }

    // ========== 自定义 Pattern 配置方法 ==========

    /**
     * 配置自定义日志格式 Pattern
     *
     * @param pattern Log4j/Logback 格式字符串
     *                例如: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
     */
    public void configure(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            pattern = DEFAULT_PATTERN;
        }

        if (Objects.equals(customPatternStr, pattern)) {
            return;
        }
        this.customPatternStr = pattern;

        PatternTokenizer tokenizer = new PatternTokenizer();
        this.customTokens = tokenizer.tokenize(pattern);

        // 调试输出
        debugPrintTokens();
    }

    /**
     * 配置自定义日志格式 Pattern（带默认格式）
     *
     * @param pattern       Log4j/Logback 格式字符串
     * @param defaultFormat 默认格式类型（SPRING_BOOT 或 LOG4J）
     */
    public void configure(String pattern, String defaultFormat) {
        if (pattern == null || pattern.isEmpty()) {
            if ("SPRING_BOOT".equalsIgnoreCase(defaultFormat)) {
                pattern = DEFAULT_PATTERN;
            } else {
                pattern = "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n";
            }
        }
        configure(pattern);
    }

    /**
     * 使用自定义 Pattern 解析日志（Delimiter 方式 - 不使用正则）
     */
    private ParseResult parseWithCustomPattern(String firstLine, String content) {
        if (customTokens == null || customTokens.isEmpty()) {
            return null;
        }

        try {
            ParseContext ctx = new ParseContext(firstLine);
            ParseResult result = ParseResult.builder()
                    .success(true)
                    .fields(new HashMap<>())
                    .build();

            // 遍历所有 token，依次解析
            for (Token token : customTokens) {
                if (token instanceof LiteralToken) {
                    // 跳过 literal，直接匹配
                    String literal = ((LiteralToken) token).getText();
                    if (!ctx.skipLiteral(literal)) {
                        log.debug("Failed to match literal: {}", literal);
                        return null;
                    }
                    continue;
                }

                // 调用 token 的 parse 方法
                token.parse(ctx, result);
            }

            // 设置时间戳（如果没有从 token 中设置）
            if (result.getTimestamp() == null) {
                result.setTimestamp(UtcTimestampParser.nowUtc());
            }

            return result;

        } catch (Exception e) {
            log.debug("Failed to parse with custom pattern: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 调试输出：打印所有 Token 及其 delimiter
     */
    private void debugPrintTokens() {
        if (log.isDebugEnabled() && customTokens != null) {
            log.debug("=== Pattern Tokens ===");
            for (Token token : customTokens) {
                String type = token.getClass().getSimpleName();
                String endDelim = token.getEndDelimiter();
                String leadDelim = token.getLeadingDelimiter();
                log.debug("{}: endDelimiter={}, leadingDelimiter={}", type, endDelim, leadDelim);
            }
            log.debug("======================");
        }
    }

    // ========== 原有固定格式解析（向后兼容）==========

    private ParseResult parseWithFixedPattern(String firstLine, String content, Pattern pattern, DateTimeFormatter formatter) {
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
                .timestamp(UtcTimestampParser.nowUtc())
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
            return UtcTimestampParser.parseUtc(timestampStr, formatter);
        } catch (Exception e) {
            try {
                String normalized = timestampStr.replace(',', '.');
                return UtcTimestampParser.parseUtc(normalized, FORMATTER_COMMA_MS);
            } catch (Exception ex) {
                log.warn("Failed to parse timestamp: {}", timestampStr);
                return UtcTimestampParser.nowUtc();
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
                .timestamp(UtcTimestampParser.nowUtc())
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

    private Map<String, Object> parseFields(String content) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("rawLength", content.length());
        fields.put("hasException", content.contains("Exception") || content.contains("Error"));
        return fields;
    }
}
