package com.evelin.loganalysis.logprocessing.parser;

import com.evelin.loganalysis.logcollection.model.RawLogEvent;
import com.evelin.loganalysis.logcommon.utils.IdGenerator;
import com.evelin.loganalysis.logprocessing.dto.ParsedLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志解析器主类
 *
 * @author Evelin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogParser {

    private final SpringBootLogParser springBootLogParser;
    private final JsonLogParser jsonLogParser;
    private final DefaultLogParser defaultLogParser;
    private final NielsLogParser nielsLogParser;
    private final ErrorLogParser errorLogParser;
    private final NginxLogParser nginxLogParser;
    private final NginxJsonLogParser nginxJsonLogParser;

    // #region agent log
    private static final Path AGENT_DEBUG_LOG_PATH = Path.of("/Users/cityseason/Documents/graduation_project/project/.cursor/debug-d4a73b.log");

    private static void agentLog(String runId, String hypothesisId, String location, String message, Map<String, Object> data) {
        try {
            long ts = System.currentTimeMillis();
            StringBuilder sb = new StringBuilder(256);
            sb.append('{')
              .append("\"sessionId\":\"d4a73b\",")
              .append("\"runId\":\"").append(escapeJson(runId)).append("\",")
              .append("\"hypothesisId\":\"").append(escapeJson(hypothesisId)).append("\",")
              .append("\"location\":\"").append(escapeJson(location)).append("\",")
              .append("\"message\":\"").append(escapeJson(message)).append("\",")
              .append("\"timestamp\":").append(ts).append(',')
              .append("\"data\":").append(toJsonObject(data))
              .append('}')
              .append('\n');
            Files.writeString(
                AGENT_DEBUG_LOG_PATH,
                sb.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String toJsonObject(Map<String, Object> data) {
        if (data == null || data.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : data.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("\"").append(escapeJson(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v.toString());
            } else {
                sb.append("\"").append(escapeJson(String.valueOf(v))).append("\"");
            }
        }
        sb.append('}');
        return sb.toString();
    }
    // #endregion

    /**
     * 解析单条原始日志
     *
     * @param rawLogEvent 原始日志事件
     * @return 解析后的日志事件
     */
    public ParsedLogEvent parse(RawLogEvent rawLogEvent) {
        if (rawLogEvent == null || rawLogEvent.getRawContent() == null) {
            return null;
        }

        String content = rawLogEvent.getRawContent();
        
        // 获取用户提供的 nginx log_format 字符串
        String logFormatPattern = rawLogEvent.getLogFormatPattern();

        // #region agent log
        Map<String, Object> agentData = new HashMap<>();
        agentData.put("eventId", rawLogEvent.getEventId());
        agentData.put("sourceId", rawLogEvent.getSourceId() != null ? rawLogEvent.getSourceId().toString() : null);
        agentData.put("logFormat", rawLogEvent.getLogFormat());
        agentData.put("patternNull", logFormatPattern == null);
        agentData.put("patternLen", logFormatPattern == null ? 0 : logFormatPattern.length());
        agentData.put("patternHead", logFormatPattern == null ? null : logFormatPattern.substring(0, Math.min(80, logFormatPattern.length())));
        agentData.put("patternTail", logFormatPattern == null ? null : logFormatPattern.substring(Math.max(0, logFormatPattern.length() - 80)));
        agentData.put("contentHead", content.substring(0, Math.min(120, content.length())));
        agentLog(
            "post-fix",
            "H1",
            "LogParser.java:parse:pattern_snapshot",
            "RawLogEvent logFormatPattern snapshot",
            agentData
        );
        // #endregion
        
        ParseStrategy strategy = selectStrategy(content, rawLogEvent.getLogFormat());
        
        // 如果是 NginxJsonLogParser，不需要额外配置，直接解析
        if (strategy instanceof NginxJsonLogParser) {
            log.debug("Using NginxJsonLogParser for JSON format");
        }

        // 解析日志
        ParseResult result = strategy.parse(content);

        return buildParsedLogEvent(rawLogEvent, result);
    }

    /**
     * 批量解析
     *
     * @param rawLogEvents 原始日志事件列表
     * @return 解析后的日志事件列表
     */
    public List<ParsedLogEvent> parseBatch(List<RawLogEvent> rawLogEvents) {
        return rawLogEvents.stream()
                .map(this::parse)
                .filter(e -> e != null)
                .toList();
    }

    /**
     * 选择解析策略
     *
     * @param content 日志内容
     * @param logFormat 日志格式（优先使用）
     * @return 解析策略
     */
    private ParseStrategy selectStrategy(String content, String logFormat) {
        // 如果指定了日志格式，直接使用对应的解析器
        if (logFormat != null && !logFormat.isEmpty()) {
            return getStrategyByFormat(logFormat);
        }

        // 如果没有指定格式，自动检测
        // 优先尝试 JSON 解析
        if (jsonLogParser.supports(content)) {
            return jsonLogParser;
        }

        // 其次尝试 Nginx JSON 解析
        if (nginxJsonLogParser.supports(content)) {
            return nginxJsonLogParser;
        }

        // 其次尝试 Nginx 解析
        if (nginxLogParser.supports(content)) {
            return nginxLogParser;
        }

        // 其次尝试 Spring Boot 解析
        if (springBootLogParser.supports(content)) {
            return springBootLogParser;
        }

        // 使用默认解析器
        return defaultLogParser;
    }

    /**
     * 根据日志格式名称获取对应的解析器
     * 对于 NGINX，会根据 logFormatPattern 判断使用 AccessLogParser 还是 ErrorLogParser
     */
    private ParseStrategy getStrategyByFormat(String logFormat) {
        try {
            switch (logFormat.toUpperCase()) {
                case "JSON":
                    return jsonLogParser;
                case "NGINX":
                case "NGINX_JSON":
                    // 使用 NginxJsonLogParser 解析 JSON 格式的 Nginx 日志
                    return nginxJsonLogParser;
                case "SPRING_BOOT":
                    return springBootLogParser;
                case "LOG4J":
                case "CUSTOM":
                case "PLAIN_TEXT":
                default:
                    return defaultLogParser;
            }
        } catch (Exception e) {
            log.warn("Unknown log format: {}, using default", logFormat);
            return defaultLogParser;
        }
    }

    /**
     * 构建解析后的日志事件
     *
     * @param rawLogEvent 原始日志事件
     * @param parseResult 解析结果
     * @return 解析后的日志事件
     */
    private ParsedLogEvent buildParsedLogEvent(RawLogEvent rawLogEvent, ParseResult parseResult) {
        Map<String, Object> fields = parseResult.getFields();
        if (fields == null) {
            fields = new HashMap<>();
        }
        fields.put("packageName", parseResult.getFileName());
        fields.put("simpleClassName", parseResult.getMethodName());

        return ParsedLogEvent.builder()
                .id(IdGenerator.nextId())
                .sourceId(rawLogEvent.getSourceId() != null ? rawLogEvent.getSourceId().toString() : null)
                .sourceName(rawLogEvent.getSourceName())
                .logTime(parseResult.getTimestamp() != null ? parseResult.getTimestamp() : LocalDateTime.now())
                .logLevel(parseResult.getLevel() != null ? parseResult.getLevel() : "INFO")
                .loggerName(parseResult.getLogger())
                .threadName(parseResult.getThread())
                .className(parseResult.getClassName())
                .methodName(parseResult.getMethodName())
                .fileName(parseResult.getFileName())
                .lineNumber(parseResult.getLineNumber())
                .message(parseResult.getMessage())
                .rawContent(rawLogEvent.getRawContent())
                .parsedFields(fields)
                .stackTrace(parseResult.getStackTrace())
                .exceptionType(parseResult.getExceptionType())
                .exceptionMessage(parseResult.getExceptionMessage())
                .isAnomaly(false)
                .anomalyScore(null)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
