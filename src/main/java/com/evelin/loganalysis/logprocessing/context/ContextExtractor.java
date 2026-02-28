package com.evelin.loganalysis.logprocessing.context;

import com.evelin.loganalysis.logprocessing.config.ProcessingConfig;
import com.evelin.loganalysis.logprocessing.dto.ContextInfo;
import com.evelin.loganalysis.logprocessing.dto.DetectionResult;
import com.evelin.loganalysis.logprocessing.dto.ParsedLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 上下文提取引擎
 *
 * @author Evelin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextExtractor {

    private final ProcessingConfig processingConfig;

    /**
     * 内存缓存：按日志源ID存储最近的日志
     */
    private final Map<String, List<ParsedLogEvent>> recentLogsCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 提取异常事件的上下文信息
     *
     * @param event 异常事件
     * @param detectionResult 检测结果
     * @return 上下文信息
     */
    public ContextInfo extract(ParsedLogEvent event, DetectionResult detectionResult) {
        if (event == null || !detectionResult.isAnomaly()) {
            return null;
        }

        String sourceId = event.getSourceId();
        if (sourceId == null) {
            sourceId = "default";
        }

        List<ParsedLogEvent> recentLogs = recentLogsCache.getOrDefault(sourceId, new ArrayList<>());
        
        int beforeLines = processingConfig.getContextBeforeLines();
        int afterLines = processingConfig.getContextAfterLines();

        // 找到当前事件在列表中的位置
        int eventIndex = findEventIndex(recentLogs, event);
        
        // 提取前后日志
        List<ParsedLogEvent> beforeLogs = new ArrayList<>();
        List<ParsedLogEvent> afterLogs = new ArrayList<>();

        if (eventIndex >= 0) {
            // 提取前日志
            int start = Math.max(0, eventIndex - beforeLines);
            for (int i = start; i < eventIndex; i++) {
                beforeLogs.add(recentLogs.get(i));
            }

            // 提取后日志
            int end = Math.min(recentLogs.size(), eventIndex + afterLines + 1);
            for (int i = eventIndex + 1; i < end; i++) {
                afterLogs.add(recentLogs.get(i));
            }
        }

        // 提取堆栈跟踪信息
        String stackTrace = extractStackTrace(event);

        // 解析堆栈跟踪
        Map<String, Object> parsedStackTrace = parseStackTrace(stackTrace);

        // 提取链路追踪ID
        String traceId = extractTraceId(event, beforeLogs, afterLogs);

        // 更新缓存
        addToCache(sourceId, event);

        return ContextInfo.builder()
                .eventId(event.getId())
                .event(event)
                .beforeLogs(beforeLogs)
                .afterLogs(afterLogs)
                .stackTrace(stackTrace)
                .parsedStackTrace(parsedStackTrace)
                .traceId(traceId)
                .relatedLogCount(beforeLogs.size() + afterLogs.size())
                .extractedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 提取堆栈跟踪信息
     */
    private String extractStackTrace(ParsedLogEvent event) {
        // 首先检查事件本身的堆栈跟踪
        if (event.getStackTrace() != null && !event.getStackTrace().isEmpty()) {
            return event.getStackTrace();
        }

        // 检查消息中是否包含堆栈跟踪
        String message = event.getMessage();
        if (message != null && message.contains("at ")) {
            // 简单提取堆栈跟踪
            int startIndex = message.indexOf("at ");
            if (startIndex >= 0) {
                return message.substring(startIndex);
            }
        }

        return null;
    }

    /**
     * 解析堆栈跟踪
     */
    private Map<String, Object> parseStackTrace(String stackTrace) {
        if (stackTrace == null || stackTrace.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> result = new HashMap<>();
        List<String> frames = new ArrayList<>();
        
        String[] lines = stackTrace.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("at ")) {
                frames.add(line.substring(3));
            }
        }

        result.put("frames", frames);
        result.put("frameCount", frames.size());
        
        // 提取异常类型和消息
        if (!frames.isEmpty()) {
            String firstFrame = frames.get(0);
            if (firstFrame.contains("(")) {
                String classMethod = firstFrame.split("\\(")[0];
                result.put("exceptionClass", classMethod);
            }
        }

        return result;
    }

    /**
     * 提取链路追踪ID
     */
    private String extractTraceId(ParsedLogEvent event, List<ParsedLogEvent> beforeLogs, List<ParsedLogEvent> afterLogs) {
        // 首先检查当前事件
        if (event.getTraceId() != null) {
            return event.getTraceId();
        }

        // 检查消息中是否包含traceId
        String message = event.getMessage();
        if (message != null) {
            String traceId = extractTraceIdFromText(message);
            if (traceId != null) {
                return traceId;
            }
        }

        // 前后日志中查找
        for (ParsedLogEvent log : beforeLogs) {
            String traceId = extractTraceIdFromText(log.getMessage());
            if (traceId != null) {
                return traceId;
            }
        }

        for (ParsedLogEvent log : afterLogs) {
            String traceId = extractTraceIdFromText(log.getMessage());
            if (traceId != null) {
                return traceId;
            }
        }

        return null;
    }

    /**
     * 从文本中提取traceId
     */
    private String extractTraceIdFromText(String text) {
        if (text == null) return null;

        // 常见格式: traceId=xxx, trace_id=xxx, X-B3-TraceId=xxx
        String[] patterns = {
            "traceId[=:]\\s*([a-zA-Z0-9-]+)",
            "trace_id[=:]\\s*([a-zA-Z0-9-]+)",
            "X-B3-TraceId[=:]\\s*([a-zA-Z0-9-]+)",
            "requestId[=:]\\s*([a-zA-Z0-9-]+)",
            "request_id[=:]\\s*([a-zA-Z0-9-]+)"
        };

        for (String pattern : patterns) {
            try {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher m = p.matcher(text);
                if (m.find()) {
                    return m.group(1);
                }
            } catch (Exception e) {
                log.debug("Failed to extract traceId with pattern: {}", pattern);
            }
        }

        return null;
    }

    /**
     * 查找事件在列表中的索引
     */
    private int findEventIndex(List<ParsedLogEvent> logs, ParsedLogEvent target) {
        for (int i = logs.size() - 1; i >= 0; i--) {
            if (logs.get(i).getId() != null && logs.get(i).getId().equals(target.getId())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 添加到缓存
     */
    private void addToCache(String sourceId, ParsedLogEvent event) {
        List<ParsedLogEvent> logs = recentLogsCache.computeIfAbsent(sourceId, k -> new ArrayList<>());
        
        // 只保留最近1000条
        synchronized (logs) {
            logs.add(event);
            if (logs.size() > 1000) {
                logs.subList(0, logs.size() - 1000).clear();
            }
        }
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        recentLogsCache.clear();
    }

    /**
     * 清除指定日志源的缓存
     */
    public void clearCache(String sourceId) {
        recentLogsCache.remove(sourceId);
    }
}
