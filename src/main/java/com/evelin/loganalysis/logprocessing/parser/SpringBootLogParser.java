package com.evelin.loganalysis.logprocessing.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring Boot 日志解析器
 * 支持格式: 2026-01-15 10:30:00.123 [INFO] [http-nio-8080-exec-1] [ClassName:45] Message
 *
 * @author Evelin
 */
@Slf4j
@Component
public class SpringBootLogParser implements ParseStrategy {

    /**
     * Spring Boot 默认日志格式正则
     * 支持: yyyy-MM-dd HH:mm:ss.SSS [LEVEL] [thread] [class:line] message
     */
    private static final Pattern PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})"  // 时间戳
            + "\\s+\\[(\\w+)\\]"                                          // 日志级别
            + "\\s+\\[([^\\]]+)\\]"                                        // 线程名
            + "\\s+\\[([^\\]]+)\\]?"                                       // 类名和行号(可选)
            + "\\s+(.*)$"                                                 // 消息
    );

    /**
     * 类名和行号提取正则
     */
    private static final Pattern CLASS_LINE_PATTERN = Pattern.compile(
            "([\\w\\.]+):(\\d+)"
    );

    private static final DateTimeFormatter FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public ParseResult parse(String content) {
        if (content == null || content.isEmpty()) {
            return ParseResult.builder()
                    .success(false)
                    .errorMessage("Empty content")
                    .build();
        }

        try {
            Matcher matcher = PATTERN.matcher(content);
            if (!matcher.matches()) {
                return parseFallback(content);
            }

            // 解析时间戳
            String timestampStr = matcher.group(1);
            LocalDateTime timestamp = LocalDateTime.parse(timestampStr, FORMATTER);

            // 解析日志级别
            String level = matcher.group(2);

            // 解析线程名
            String thread = matcher.group(3);

            // 解析类名和行号
            String classLine = matcher.group(4);
            String className = null;
            Integer lineNumber = null;
            if (classLine != null && !classLine.isEmpty()) {
                Matcher classMatcher = CLASS_LINE_PATTERN.matcher(classLine);
                if (classMatcher.find()) {
                    className = classMatcher.group(1);
                    lineNumber = Integer.parseInt(classMatcher.group(2));
                } else {
                    className = classLine;
                }
            }

            // 解析消息
            String message = matcher.group(5);

            // 检查是否有异常堆栈
            String stackTrace = null;
            String exceptionType = null;
            String exceptionMessage = null;

            // 如果消息以异常类型开头，尝试提取堆栈跟踪
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
                    .className(className)
                    .lineNumber(lineNumber)
                    .message(message)
                    .stackTrace(stackTrace)
                    .exceptionType(exceptionType)
                    .exceptionMessage(exceptionMessage)
                    .fields(parseFields(content))
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse Spring Boot log: {}", e.getMessage());
            return parseFallback(content);
        }
    }

    /**
     * 回退解析：无法匹配格式时使用
     */
    private ParseResult parseFallback(String content) {
        // 尝试简单解析：查找日志级别
        String level = detectLevel(content);
        
        return ParseResult.builder()
                .success(true)
                .timestamp(LocalDateTime.now())
                .level(level)
                .message(content)
                .fields(parseFields(content))
                .build();
    }

    /**
     * 检测日志级别
     */
    private String detectLevel(String content) {
        if (content.contains("[ERROR]") || content.contains("ERROR:")) {
            return "ERROR";
        } else if (content.contains("[WARN]") || content.contains("WARN:")) {
            return "WARN";
        } else if (content.contains("[DEBUG]") || content.contains("DEBUG:")) {
            return "DEBUG";
        } else if (content.contains("[TRACE]") || content.contains("TRACE:")) {
            return "TRACE";
        } else if (content.contains("[INFO]") || content.contains("INFO:")) {
            return "INFO";
        }
        return "INFO";
    }

    /**
     * 解析额外字段
     */
    private java.util.Map<String, Object> parseFields(String content) {
        java.util.Map<String, Object> fields = new java.util.HashMap<>();
        fields.put("rawLength", content.length());
        return fields;
    }

    @Override
    public String getFormatName() {
        return "SPRING_BOOT";
    }

    @Override
    public boolean supports(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        return PATTERN.matcher(content).matches();
    }
}
