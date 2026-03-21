package com.evelin.loganalysis.logprocessing.service;

import com.evelin.loganalysis.logcollection.model.RawLogEvent;
import com.evelin.loganalysis.logprocessing.dto.*;
import com.evelin.loganalysis.logprocessing.pipeline.LogProcessingPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 日志处理服务
 *
 * @author Evelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogProcessingService {

    private final LogProcessingPipeline pipeline;
    private final com.evelin.loganalysis.logprocessing.desensitization.DesensitizationService desensitizationService;

    /**
     * 处理单条原始日志
     *
     * @param rawLogEvent 原始日志事件
     * @return 处理结果
     */
    public ProcessingResult process(RawLogEvent rawLogEvent) {
        return pipeline.process(rawLogEvent);
    }

    /**
     * 批量处理原始日志
     *
     * @param rawLogEvents 原始日志事件列表
     * @return 处理结果列表
     */
    public List<ProcessingResult> processBatch(List<RawLogEvent> rawLogEvents) {
        return pipeline.processBatch(rawLogEvents);
    }

    /**
     * 异步处理单条日志
     *
     * @param rawLogEvent 原始日志事件
     * @return CompletableFuture
     */
    public CompletableFuture<ProcessingResult> processAsync(RawLogEvent rawLogEvent) {
        return pipeline.processAsync(rawLogEvent);
    }

    /**
     * 测试解析日志
     *
     * @param content 日志内容
     * @param format 日志格式
     * @return 解析结果
     */
    public ParsedLogEvent testParse(String content, String format) {
        RawLogEvent rawLogEvent = RawLogEvent.builder()
                .rawContent(content)
                .sourceName("test")
                .build();
        
        return pipeline.process(rawLogEvent).getParsedEvent();
    }

    /**
     * 测试脱敏
     *
     * @param content 内容
     * @return 脱敏后的内容
     */
    public String testDesensitize(String content) {
        return desensitizationService.desensitize(content);
    }
}
