package com.evelin.loganalysis.logcollection.service;

import com.evelin.loganalysis.logcollection.config.RabbitMQConfig;
import com.evelin.loganalysis.logcollection.dto.LogDesensitizationMessage;
import com.evelin.loganalysis.logcollection.model.RawLogEvent;
import com.evelin.loganalysis.logprocessing.desensitization.DesensitizationService;
import com.evelin.loganalysis.logprocessing.dto.ParsedLogEvent;
import com.evelin.loganalysis.logprocessing.parser.LogParser;
import com.evelin.loganalysis.logprocessing.pipeline.LogProcessingPipeline;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 脱敏消费者服务
 * 从 RabbitMQ 消费原始日志，进行脱敏处理后保存到数据库
 *
 * @author Evelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DesensitizationConsumerService {

    private final DesensitizationService desensitizationService;
    private final RawLogEventService rawLogEventService;
    private final LogParser logParser;
    private final LogProcessingPipeline logProcessingPipeline;

    /**
     * 批量处理大小
     */
    private static final int BATCH_SIZE = 100;

    /**
     * 批量缓冲区
     */
    private final List<RawLogEvent> batchBuffer = new ArrayList<>();

    /**
     * 监听脱敏队列，处理消息
     * 注意：不抛出异常，避免消息重复投递
     */
    @RabbitListener(queues = RabbitMQConfig.LOG_DESENSITIZE_QUEUE)
    public void handleLogMessage(LogDesensitizationMessage message) {

        try {
            // 构建原始日志事件
            RawLogEvent event = buildRawLogEvent(message);

            // 根据配置决定是否脱敏
            if (isDesensitizationEnabled(message)) {
                // 执行脱敏
                String desensitizedContent = desensitize(message);
                event.setDesensitizedContent(desensitizedContent);
                event.setMasked(true);
            } else {
                // 不脱敏，直接使用原始内容
                event.setDesensitizedContent(event.getRawContent());
                event.setMasked(false);
            }

            // 添加到批处理缓冲区
            synchronized (batchBuffer) {
                batchBuffer.add(event);

                // 达到批量大小时刷新
                if (batchBuffer.size() >= BATCH_SIZE) {
                    flushBuffer();
                }
            }

        } catch (Exception e) {
            log.error("Failed to process log message: {}, error: {}", message.getMessageId(), e.getMessage(), e);
            // 不抛出异常，消息会被自动确认，避免重复消费
        }
    }

    /**
     * 定时刷新缓冲区（每5秒检查一次）
     */
    @Scheduled(fixedDelay = 5000)
    public void scheduledFlush() {
        synchronized (batchBuffer) {
            if (!batchBuffer.isEmpty()) {
                log.info("Scheduled flush: {} logs in buffer", batchBuffer.size());
                flushBuffer();
            }
        }
    }

    /**
     * 判断是否启用脱敏
     */
    private boolean isDesensitizationEnabled(LogDesensitizationMessage message) {
        if (message.getDesensitizationConfig() == null) {
            return false;
        }
        return Boolean.TRUE.equals(message.getDesensitizationConfig().getEnabled());
    }

    /**
     * 执行脱敏处理
     */
    private String desensitize(LogDesensitizationMessage message) {
        String content = message.getRawContent();
        String result = content;

        // 获取脱敏配置
        var config = message.getDesensitizationConfig();

        // 处理自定义规则（优先）
        if (config.getCustomRules() != null && !config.getCustomRules().isEmpty()) {
            for (var customRule : config.getCustomRules()) {
                result = applyCustomRule(result, customRule);
            }
        }

        // 处理预设规则
        if (config.getEnabledRuleIds() != null && !config.getEnabledRuleIds().isEmpty()) {
            // 默认规则处理（简化版，实际可以根据规则ID选择）
            result = desensitizationService.desensitize(result);
        } else if (config.getCustomRules() == null || config.getCustomRules().isEmpty()) {
            // 如果没有自定义规则，使用默认规则
            result = desensitizationService.desensitize(result);
        }

        return result;
    }

    /**
     * 应用自定义规则
     */
    private String applyCustomRule(String text, LogDesensitizationMessage.DesensitizationConfig.CustomRule rule) {
        try {
            return switch (rule.getMaskType().toUpperCase()) {
                case "FULL" -> text.replaceAll(rule.getPattern(), rule.getReplacement());
                case "PARTIAL" -> applyPartialMask(text, rule.getPattern(), rule.getReplacement());
                case "HASH" -> applyHashMask(text, rule.getPattern());
                default -> text;
            };
        } catch (Exception e) {
            log.warn("Failed to apply custom rule: {}", rule.getId(), e);
            return text;
        }
    }

    /**
     * 应用部分脱敏
     */
    private String applyPartialMask(String text, String pattern, String replacement) {
        try {
            return text.replaceAll(pattern, replacement);
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * 应用哈希脱敏
     */
    private String applyHashMask(String text, String pattern) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher matcher = p.matcher(text);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String hash = "HASH_" + String.valueOf(matcher.group().hashCode()).substring(0, 8);
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(hash));
            }
            matcher.appendTail(sb);
            return sb.toString();
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * 构建原始日志事件
     */
    private RawLogEvent buildRawLogEvent(LogDesensitizationMessage message) {
        return RawLogEvent.create(
                message.getSourceId(),
                message.getSourceName(),
                message.getFilePath(),
                message.getRawContent(),
                message.getLineNumber(),
                message.getOffset(),
                message.getRawContent().getBytes().length,
                null, // inode
                message.getCollectionTime(),
                message.getLogFormat(),
                message.getLogFormatPattern()
        );
    }

    /**
     * 刷新缓冲区（批量保存到数据库）
     */
    private void flushBuffer() {
        if (batchBuffer.isEmpty()) {
            return;
        }

        try {
            // 创建副本以避免并发问题
            List<RawLogEvent> toSave = new ArrayList<>(batchBuffer);
            batchBuffer.clear();

            // 处理每条日志：解析、脱敏、事件检测、聚合、自动分析
            for (RawLogEvent event : toSave) {
                try {
                    // 调用完整的处理管道
                    var processingResult = logProcessingPipeline.process(event);

                    if (processingResult.isSuccess() && processingResult.getParsedEvent() != null) {
                        // 解析成功，提取字段
                        ParsedLogEvent parsed = processingResult.getParsedEvent();
                        event.setParsedFields(convertToMap(parsed));

                        // 如果有聚合结果，更新聚合组ID
                        if (processingResult.getAggregationResult() != null) {
                            event.setAggregationGroupId(processingResult.getAggregationResult().getGroupId());
                        }

//                        log.debug("Log processed successfully, groupId: {}",
//                                processingResult.getAggregationResult() != null ?
//                                processingResult.getAggregationResult().getGroupId() : "N/A");
                    }
                } catch (Exception e) {
                    log.warn("Failed to process log event: {}", event.getEventId(), e);
                }
            }

            // 保存到数据库
            rawLogEventService.saveAll(toSave);
            log.info("Saved {} log events to database", toSave.size());

        } catch (Exception e) {
            log.error("Failed to flush batch to database, retrying...", e);
        }
    }

    /**
     * 将 ParsedLogEvent 转换为 Map
     */
    private Map<String, Object> convertToMap(ParsedLogEvent parsed) {
        Map<String, Object> map = new HashMap<>();

        if (parsed.getLogTime() != null) {
            map.put("logTime", parsed.getLogTime().toString());
        }
        if (parsed.getLogLevel() != null) {
            map.put("logLevel", parsed.getLogLevel());
        }
        if (parsed.getThreadName() != null) {
            map.put("threadName", parsed.getThreadName());
        }
        if (parsed.getLoggerName() != null) {
            map.put("loggerName", parsed.getLoggerName());
        }
        if (parsed.getClassName() != null) {
            map.put("className", parsed.getClassName());
        }
        if (parsed.getMethodName() != null) {
            map.put("methodName", parsed.getMethodName());
        }
        if (parsed.getMessage() != null) {
            map.put("message", parsed.getMessage());
        }
        if (parsed.getExceptionType() != null) {
            map.put("exceptionType", parsed.getExceptionType());
        }
        if (parsed.getExceptionMessage() != null) {
            map.put("exceptionMessage", parsed.getExceptionMessage());
        }
        if (parsed.getStackTrace() != null) {
            map.put("stackTrace", parsed.getStackTrace());
        }
        if (parsed.getTraceId() != null) {
            map.put("traceId", parsed.getTraceId());
        }
        if (parsed.getCategory() != null) {
            map.put("category", parsed.getCategory());
        }
        if (parsed.getTags() != null && !parsed.getTags().isEmpty()) {
            map.put("tags", parsed.getTags());
        }

        if (parsed.getParsedFields() != null && !parsed.getParsedFields().isEmpty()) {
            for (Map.Entry<String, Object> entry : parsed.getParsedFields().entrySet()) {
                map.put(entry.getKey(), entry.getValue());
            }
        }
        return map;
    }

    /**
     * 应用关闭时刷新缓冲区
     */
    @PreDestroy
    public void onShutdown() {
        log.info("Flushing remaining desensitized logs before shutdown...");
        synchronized (batchBuffer) {
            flushBuffer();
        }
    }
}
