package com.evelin.loganalysis.logprocessing.parser;

import com.evelin.loganalysis.logcollection.model.RawLogEvent;
import com.evelin.loganalysis.logcommon.utils.IdGenerator;
import com.evelin.loganalysis.logprocessing.dto.ParsedLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 日志解析器主类
 *
 * @author Evelin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogParser {

    private final JsonLogParser jsonLogParser;
    private final DefaultLogParser defaultLogParser;
    private final ErrorLogParser errorLogParser;
    private final NginxLogParser nginxLogParser;
    private final NginxJsonLogParser nginxJsonLogParser;
    private final AccessLogParser accessLogParser;
    private final Log4jLogParser log4jLogParser;

    // 用于存储每个日志源的 Pattern 配置（sourceId -> pattern）
    private final Map<String, String> patternConfigCache = new ConcurrentHashMap<>();


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

        // 处理自定义 Pattern 配置
        String pattern = handlePatternConfig(rawLogEvent);

        ParseStrategy strategy = selectStrategy(content, rawLogEvent.getLogFormat(), rawLogEvent.getLogType());

        // 解析日志
        ParseResult result = strategy.parse(content, pattern);

        log.info(result.toString());

        return buildParsedLogEvent(rawLogEvent, result);
    }

    /**
     * 处理 Pattern 配置
     * 如果 logFormatPattern 存在，则配置 Log4jLogParser 使用该 pattern
     */
    private String handlePatternConfig(RawLogEvent rawLogEvent) {
        String sourceId = rawLogEvent.getSourceId() != null ? rawLogEvent.getSourceId().toString() : null;
        String pattern = rawLogEvent.getLogFormatPattern();

        if (sourceId != null && pattern != null && !pattern.isEmpty()) {
            String cachedPattern = patternConfigCache.get(sourceId);
            if (!pattern.equals(cachedPattern)) {
                // Pattern 变更，需要重新配置
                log.debug("Configuring pattern for source {}: {}", sourceId, pattern);
                patternConfigCache.put(sourceId, pattern);
            }
        }
        return pattern;
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
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 选择解析策略
     *
     * @param content   日志内容
     * @param logFormat 日志格式（优先使用）
     * @return 解析策略
     */
    private ParseStrategy selectStrategy(String content, String logFormat, String logType) {
        // 如果指定了日志格式，直接使用对应的解析器
        if (logFormat != null && !logFormat.isEmpty()) {
            return getStrategyByFormat(content, logFormat, logType);
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

        // 其次尝试 Log4j 解析（支持自定义 Pattern）
        if (log4jLogParser.supports(content)) {
            return log4jLogParser;
        }

        // 使用默认解析器
        return defaultLogParser;
    }

    /**
     * 根据日志格式名称获取对应的解析器
     * 对于 NGINX，会根据 filePath 判断使用 AccessLogParser 还是 ErrorLogParser
     */
    private ParseStrategy getStrategyByFormat(String content, String logFormat, String logType) {
        try {
            String format = logFormat.toUpperCase();

            // NGINX 类型需要根据配置文件中的 logType 来确定是 error 还是 access
            if ("NGINX".equals(format)) {
                // 对于 access 日志，优先尝试 JSON 解析
                if ("access".equals(logType)) {
                    // 先尝试通用 JSON 解析器
                    if (jsonLogParser.supports(content)) {
                        return jsonLogParser;
                    }
                    // 再尝试 Nginx JSON 解析器
                    if (nginxJsonLogParser.supports(content)) {
                        return nginxJsonLogParser;
                    }
                    // JSON 解析失败，使用普通 access log 解析器
                    return accessLogParser;
                }
                // 对于 error 日志
                if ("error".equals(logType)) {
                    return errorLogParser;
                }
                // 默认使用 nginxLogParser
                log.warn("[LogParser] 未配置nginx日志类型，使用默认解析器");
                return nginxLogParser;
            }

            switch (format) {
                case "JSON":
                    return jsonLogParser;
                case "NGINX_JSON":
                    return nginxJsonLogParser;
                case "NGINX_ERROR":
                    return errorLogParser;
                case "NGINX_ACCESS":
                    return accessLogParser;
                case "LOG4J":
                case "LOG4J1":
                case "LOG4J2":
                case "SPRING_BOOT":
                case "CUSTOM":
                    // 这些格式都使用 Log4jLogParser，它支持自定义 Pattern
                    return log4jLogParser;
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

        // 从解析结果中提取 traceId
        String traceId = null;
        if (fields.containsKey("traceId")) {
            traceId = String.valueOf(fields.get("traceId"));
        }

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
                .traceId(traceId)
                .isAnomaly(false)
                .anomalyScore(null)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
