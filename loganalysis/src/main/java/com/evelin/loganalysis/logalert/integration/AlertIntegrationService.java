package com.evelin.loganalysis.logalert.integration;

import com.evelin.loganalysis.logalert.service.AlertTriggerService;
import com.evelin.loganalysis.logcollection.model.RawLogEvent;
import com.evelin.loganalysis.logprocessing.dto.ParsedLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 告警集成服务 - 将告警触发集成到日志处理流程中
 *
 * @author Evelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertIntegrationService {

    private final AlertTriggerService alertTriggerService;

    /**
     * 在日志处理后触发告警检查
     *
     * @param event       原始日志事件
     * @param parsedEvent 解析后的日志事件（可选）
     */
    public void triggerAlertCheck(RawLogEvent event, ParsedLogEvent parsedEvent) {
        try {
            String logMessage = event.getRawContent();
            String logLevel = event.getLogLevel();
            String sourceId = event.getSourceId() != null ? event.getSourceId().toString() : null;
            String sourceName = event.getSourceName();

            // 调用告警触发引擎检查是否触发告警
            alertTriggerService.checkAndTrigger(logMessage, logLevel, sourceId, sourceName);
        } catch (Exception e) {
            log.error("告警检查失败: {}", event.getEventId(), e);
        }
    }

    /**
     * 批量触发告警检查（用于阈值类规则）
     *
     * @param events 原始日志事件列表
     */
    public void triggerBatchAlertCheck(List<RawLogEvent> events) {
        try {
            List<AlertTriggerService.LogEntry> logEntries = events.stream()
                    .map(e -> new AlertTriggerService.LogEntry(
                            e.getRawContent(),
                            e.getLogLevel() != null ? e.getLogLevel() : "INFO",
                            e.getSourceName(),
                            e.getCollectionTime() != null ? e.getCollectionTime().toEpochSecond(java.time.ZoneOffset.UTC) : System.currentTimeMillis()
                    ))
                    .toList();

            // 批量检查阈值规则
            alertTriggerService.checkBatchAndTrigger(logEntries, 5);
        } catch (Exception e) {
            log.error("批量告警检查失败", e);
        }
    }

    /**
     * AI分析结果异常时触发告警
     *
     * @param aggregationId 聚合组ID
     * @param analysisResult 分析结果
     */
    public void triggerAnalysisAlert(String aggregationId, String analysisResult) {
        try {
            // 如果分析结果显示有异常，触发告警
            // 这里需要根据实际情况实现
            log.info("AI分析完成，聚合组: {}, 结果: {}", aggregationId, analysisResult);
        } catch (Exception e) {
            log.error("AI分析告警触发失败: {}", aggregationId, e);
        }
    }
}
