package com.evelin.loganalysis.logprocessing.parser;

import com.evelin.loganalysis.logcollection.model.RawLogEvent;
import com.evelin.loganalysis.logprocessing.dto.ParsedLogEvent;
import com.evelin.loganalysis.logcommon.utils.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

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
        ParseStrategy strategy = selectStrategy(content);

        log.debug("Using parser: {} for content: {}", strategy.getFormatName(), 
                content.substring(0, Math.min(50, content.length())));

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
     * @return 解析策略
     */
    private ParseStrategy selectStrategy(String content) {
        // 优先尝试 JSON 解析
        if (jsonLogParser.supports(content)) {
            return jsonLogParser;
        }

        // 其次尝试 Spring Boot 解析
        if (springBootLogParser.supports(content)) {
            return springBootLogParser;
        }

        // 使用默认解析器
        return defaultLogParser;
    }

    /**
     * 构建解析后的日志事件
     *
     * @param rawLogEvent 原始日志事件
     * @param parseResult 解析结果
     * @return 解析后的日志事件
     */
    private ParsedLogEvent buildParsedLogEvent(RawLogEvent rawLogEvent, ParseResult parseResult) {
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
                .parsedFields(parseResult.getFields())
                .stackTrace(parseResult.getStackTrace())
                .exceptionType(parseResult.getExceptionType())
                .exceptionMessage(parseResult.getExceptionMessage())
                .isAnomaly(false)
                .anomalyScore(null)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
