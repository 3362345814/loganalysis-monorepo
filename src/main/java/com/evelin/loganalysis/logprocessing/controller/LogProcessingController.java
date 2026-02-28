package com.evelin.loganalysis.logprocessing.controller;

import com.evelin.loganalysis.logcommon.model.Result;
import com.evelin.loganalysis.logprocessing.dto.ParsedLogEvent;
import com.evelin.loganalysis.logprocessing.service.LogProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 日志处理控制器
 *
 * @author Evelin
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/processing")
@RequiredArgsConstructor
public class LogProcessingController {

    private final LogProcessingService logProcessingService;

    /**
     * 测试日志解析
     *
     * @param content 日志内容
     * @param format 日志格式 (SPRING_BOOT/JSON/DEFAULT)
     * @return 解析结果
     */
    @GetMapping("/parse/test")
    public Result<ParsedLogEvent> testParse(
            @RequestParam("content") String content,
            @RequestParam(value = "format", required = false, defaultValue = "DEFAULT") String format) {
        
        log.info("Testing log parse, content length: {}, format: {}", content.length(), format);
        
        try {
            ParsedLogEvent result = logProcessingService.testParse(content, format);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Failed to parse test log", e);
            return Result.error("解析失败: " + e.getMessage());
        }
    }

    /**
     * 测试敏感信息脱敏
     *
     * @param content 待脱敏内容
     * @return 脱敏结果
     */
    @GetMapping("/desensitize/test")
    public Result<String> testDesensitize(@RequestParam("content") String content) {
        log.info("Testing desensitization, content length: {}", content.length());
        
        try {
            String result = logProcessingService.testDesensitize(content);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Failed to desensitize test content", e);
            return Result.error("脱敏失败: " + e.getMessage());
        }
    }

    /**
     * 获取处理管道状态
     *
     * @return 状态信息
     */
    @GetMapping("/status")
    public Result<Object> getStatus() {
        return Result.success(java.util.Map.of(
                "status", "RUNNING",
                "module", "logprocessing"
        ));
    }
}
