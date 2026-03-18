package com.evelin.loganalysis.logprocessing.pipeline;

import com.evelin.loganalysis.logcollection.model.LogSource;
import com.evelin.loganalysis.logcollection.model.RawLogEvent;
import com.evelin.loganalysis.logcollection.service.LogSourceService;
import com.evelin.loganalysis.logprocessing.aggregation.LogAggregator;
import com.evelin.loganalysis.logprocessing.config.ProcessingConfig;
import com.evelin.loganalysis.logprocessing.context.ContextExtractor;
import com.evelin.loganalysis.logprocessing.desensitization.DesensitizationService;
import com.evelin.loganalysis.logprocessing.dto.*;
import com.evelin.loganalysis.logprocessing.event.EventDetector;
import com.evelin.loganalysis.logprocessing.parser.LogParser;
import com.evelin.loganalysis.logalert.service.AlertTriggerService;
import com.evelin.loganalysis.logprocessing.service.AutoAnalysisTrigger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 日志处理管道
 *
 * @author Evelin
 */
@Slf4j
@Component
public class LogProcessingPipeline {

    private final ProcessingConfig processingConfig;
    private final LogParser logParser;
    private final EventDetector eventDetector;
    private final ContextExtractor contextExtractor;
    private final LogAggregator logAggregator;
    private final DesensitizationService desensitizationService;
    private final Executor processingExecutor;
    private final AutoAnalysisTrigger autoAnalysisTrigger;
    private final LogSourceService logSourceService;
    private final AlertTriggerService alertTriggerService;

    public LogProcessingPipeline(
            ProcessingConfig processingConfig,
            LogParser logParser,
            EventDetector eventDetector,
            ContextExtractor contextExtractor,
            LogAggregator logAggregator,
            DesensitizationService desensitizationService,
            AutoAnalysisTrigger autoAnalysisTrigger,
            LogSourceService logSourceService,
            AlertTriggerService alertTriggerService,
            @Qualifier("processingExecutor") Executor processingExecutor) {
        this.processingConfig = processingConfig;
        this.logParser = logParser;
        this.eventDetector = eventDetector;
        this.contextExtractor = contextExtractor;
        this.logAggregator = logAggregator;
        this.desensitizationService = desensitizationService;
        this.autoAnalysisTrigger = autoAnalysisTrigger;
        this.logSourceService = logSourceService;
        this.alertTriggerService = alertTriggerService;
        this.processingExecutor = processingExecutor;
    }

    /**
     * 处理单条原始日志
     *
     * @param rawLogEvent 原始日志事件
     * @return 处理结果
     */
    public ProcessingResult process(RawLogEvent rawLogEvent) {
        long startTime = System.currentTimeMillis();

        try {
            // 步骤1: 解析日志
            ParsedLogEvent parsedEvent = logParser.parse(rawLogEvent);
            if (parsedEvent == null) {
                return ProcessingResult.builder()
                        .rawLogId(rawLogEvent.getEventId() != null ? rawLogEvent.getEventId() : null)
                        .success(false)
                        .stage("PARSED")
                        .errorMessage("Failed to parse log")
                        .timestamp(System.currentTimeMillis())
                        .build();
            }

            ProcessingResult result = ProcessingResult.builder()
                    .rawLogId(rawLogEvent.getEventId() != null ? rawLogEvent.getEventId() : null)
                    .success(true)
                    .stage("PARSED")
                    .parsedEvent(parsedEvent)
                    .timestamp(System.currentTimeMillis())
                    .build();

            // 步骤2: 敏感信息脱敏
            if (processingConfig.isDesensitizationEnabled()) {
                String originalMessage = parsedEvent.getMessage();
                String desensitizedMessage = desensitizationService.desensitizeMessage(originalMessage);
                parsedEvent.setMessage(desensitizedMessage);
                result.setStage("DESENSITIZED");
            }

            // 步骤3: 事件检测
            if (processingConfig.isEventDetectionEnabled()) {
                DetectionResult detectionResult = eventDetector.detect(parsedEvent);
                parsedEvent.setAnomaly(detectionResult.isAnomaly());
                parsedEvent.setAnomalyScore(detectionResult.getAnomalyScore());
                parsedEvent.setAnomalyReason(detectionResult.getAnomalyReason());
                result.setDetectionResult(detectionResult);
                result.setStage("DETECTED");

                // 步骤4: 上下文提取（仅对异常事件）
                if (detectionResult.isAnomaly() && processingConfig.getContextBeforeLines() > 0) {
                    ContextInfo contextInfo = contextExtractor.extract(parsedEvent, detectionResult);
                    result.setContextInfo(contextInfo);
                    result.setStage("CONTEXT_EXTRACTED");
                }
            }

            // 步骤5: 日志聚合
            if (processingConfig.isAggregationEnabled()) {
                // 获取日志源的聚合级别配置
                String aggregationLevel = getAggregationLevel(parsedEvent.getSourceId());

                // 从 parsedEvent 获取日志源信息，并传递聚合级别
                AggregationResult aggregationResult = logAggregator.aggregate(
                        parsedEvent,
                        parsedEvent.getSourceId(),
                        parsedEvent.getSourceName(),
                        aggregationLevel
                );
                result.setAggregationResult(aggregationResult);
                result.setStage("AGGREGATED");

                // 步骤6: 自动触发 AI 分析（仅对 ERROR 及以上级别的新聚合组）
                if (aggregationResult != null && aggregationResult.isNewGroup()) {
                    autoAnalysisTrigger.triggerAutoAnalysis(aggregationResult);
                }
            }

            // 步骤7: 告警检查
            if (processingConfig.isAlertEnabled()) {
                checkAlertTrigger(parsedEvent);
            }

            result.setProcessTimeMs(System.currentTimeMillis() - startTime);

            return result;

        } catch (Exception e) {
            log.error("Error processing log: {}", e.getMessage(), e);
            return ProcessingResult.builder()
                    .rawLogId(rawLogEvent.getEventId() != null ? rawLogEvent.getEventId() : null)
                    .success(false)
                    .stage("ERROR")
                    .errorMessage(e.getMessage())
                    .processTimeMs(System.currentTimeMillis() - startTime)
                    .timestamp(System.currentTimeMillis())
                    .build();
        }
    }

    /**
     * 批量处理原始日志
     *
     * @param rawLogEvents 原始日志事件列表
     * @return 处理结果列表
     */
    public List<ProcessingResult> processBatch(List<RawLogEvent> rawLogEvents) {
        return rawLogEvents.parallelStream()
                .map(this::process)
                .toList();
    }

    /**
     * 异步处理单条日志
     *
     * @param rawLogEvent 原始日志事件
     * @return CompletableFuture
     */
    public CompletableFuture<ProcessingResult> processAsync(RawLogEvent rawLogEvent) {
        return CompletableFuture.supplyAsync(() -> process(rawLogEvent), processingExecutor);
    }

    /**
     * 定时清理过期的聚合组
     */
    @Scheduled(fixedRate = 60000) // 每分钟执行一次
    public void cleanupExpiredGroups() {
        if (processingConfig.isAggregationEnabled()) {
            logAggregator.cleanupExpiredGroups();
        }
    }

    /**
     * 定时清理上下文缓存
     */
    @Scheduled(fixedRate = 300000) // 每5分钟执行一次
    public void cleanupContextCache() {
        contextExtractor.clearCache();
    }

    /**
     * 获取日志源的聚合级别配置
     *
     * @param sourceId 日志源ID
     * @return 聚合级别配置（null表示聚合所有级别）
     */
    private String getAggregationLevel(String sourceId) {
        if (sourceId == null) {
            return null;
        }
        try {
            Optional<LogSource> sourceOpt =
                    logSourceService.getEntityById(UUID.fromString(sourceId));
            if (sourceOpt.isPresent()) {
                return sourceOpt.get().getAggregationLevel();
            }
        } catch (Exception e) {
            log.warn("获取日志源聚合级别配置失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 检查并触发告警
     *
     * @param parsedEvent 解析后的日志事件
     */
    private void checkAlertTrigger(ParsedLogEvent parsedEvent) {
        try {
            String logMessage = parsedEvent.getMessage();
            String logLevel = parsedEvent.getLogLevel();
            String sourceId = parsedEvent.getSourceId();
            String sourceName = parsedEvent.getSourceName();

            // 调用告警触发服务检查规则
            alertTriggerService.checkAndTrigger(logMessage, logLevel, sourceId, sourceName);

            // 如果检测到异常事件，也触发告警（基于异常检测结果）
            if (parsedEvent.isAnomaly()) {
                String anomalyMessage = String.format("[异常检测] %s (置信度: %.2f)",
                        parsedEvent.getAnomalyReason() != null ? parsedEvent.getAnomalyReason() : "检测到异常",
                        parsedEvent.getAnomalyScore());
                alertTriggerService.checkAndTrigger(anomalyMessage, logLevel, sourceId, sourceName);
            }
        } catch (Exception e) {
            log.error("执行告警检查失败: {}", e.getMessage(), e);
        }
    }
}
