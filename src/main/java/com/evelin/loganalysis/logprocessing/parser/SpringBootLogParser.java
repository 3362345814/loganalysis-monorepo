package com.evelin.loganalysis.logprocessing.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring Boot 日志解析器
 * 支持格式: 2026-01-15 10:30:00.123 [INFO] [thread] [c.e.a.service.ClassName:45] Message
 * 或: 2026-01-15 10:30:00.123 [INFO] [thread] [c.e.a.service.ClassName] Message
 *
 * @author Evelin
 */
@Slf4j
@Component
public class SpringBootLogParser implements ParseStrategy {

    private static final DateTimeFormatter FORMATTER_3_MS = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    private static final DateTimeFormatter FORMATTER_6_MS = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    private static final Pattern PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+)"  
            + "\\s+\\[([^\\]]+)\\]"                                        
            + "\\s+([A-Z]+)"                                             
            + "\\s+([^\\s]+)"                                             
            + "\\s+-\\s+(.*)$"                                             
    );

    private static final Pattern CLASS_LINE_PATTERN = Pattern.compile(
            "([\\w\\.]+):(\\d+)"
    );

    private static final Pattern CLASS_ONLY_PATTERN = Pattern.compile(
            "([\\w\\.]+)$"
    );

    @Override
    public ParseResult parse(String content) {
        if (content == null || content.isEmpty()) {
            return ParseResult.builder()
                    .success(false)
                    .errorMessage("Empty content")
                    .build();
        }

        try {
            // 首先处理第一行，提取基本信息
            String[] lines = content.split("\n");
            if (lines.length == 0) {
                return parseFallback(content);
            }

            String firstLine = lines[0].trim();
            Matcher matcher = PATTERN.matcher(firstLine);
            if (!matcher.matches()) {
                return parseFallback(content);
            }

            String timestampStr = matcher.group(1);
            LocalDateTime timestamp = parseTimestamp(timestampStr);

            String thread = matcher.group(2);

            String level = matcher.group(3);

            String classLine = matcher.group(4);
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

            String message = matcher.group(5);

            // 处理堆栈信息（如果有）
            StringBuilder stackTraceBuilder = new StringBuilder();
            String exceptionType = null;
            String exceptionMessage = null;

            if (lines.length > 1) {
                for (int i = 1; i < lines.length; i++) {
                    String line = lines[i].trim();
                    if (!line.isEmpty()) {
                        stackTraceBuilder.append(line).append("\n");
                    }
                }
            }

            String stackTrace = stackTraceBuilder.length() > 0 ? stackTraceBuilder.toString().trim() : null;

            // 提取异常类型和消息
            if (message != null && message.contains(":")) {
                String potentialException = message.split(":")[0];
                if (potentialException != null && 
                    (potentialException.endsWith("Exception") || 
                     potentialException.endsWith("Error"))) {
                    exceptionType = potentialException;
                    exceptionMessage = message.substring(message.indexOf(":") + 1).trim();
                }
            }

            // 如果消息中没有异常信息，但堆栈中有，尝试从堆栈中提取
            if (exceptionType == null && stackTrace != null) {
                String[] stackLines = stackTrace.split("\n");
                for (String line : stackLines) {
                    if (line.startsWith("java.")) {
                        int colonIndex = line.indexOf(":");
                        if (colonIndex > 0) {
                            exceptionType = line.substring(0, colonIndex);
                            if (colonIndex + 1 < line.length()) {
                                exceptionMessage = line.substring(colonIndex + 1).trim();
                            }
                            break;
                        }
                    }
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

    private LocalDateTime parseTimestamp(String timestampStr) {
        try {
            return LocalDateTime.parse(timestampStr, FORMATTER_3_MS);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(timestampStr, FORMATTER_6_MS);
            } catch (Exception ex) {
                log.warn("Failed to parse timestamp: {}", timestampStr);
                return LocalDateTime.now();
            }
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
        fields.put("hasException", content.contains("Exception") || content.contains("Error"));
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
        // 只检查第一行是否匹配
        String[] lines = content.split("\n");
        if (lines.length == 0) {
            return false;
        }
        String firstLine = lines[0].trim();
        return PATTERN.matcher(firstLine).matches();
    }
}
