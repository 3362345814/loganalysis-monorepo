package com.evelin.loganalysis.loganalysisai.controller;

import com.evelin.loganalysis.loganalysisai.analysis.dto.AnalysisResultDTO;
import com.evelin.loganalysis.loganalysisai.analysis.service.AnalysisService;
import com.evelin.loganalysis.logcommon.model.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * AI 分析 Controller
 *
 * @author Evelin
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
public class AnalysisController {
    
    private final AnalysisService analysisService;
    @Qualifier("llmExecutor")
    private final Executor llmExecutor;
    
    /**
     * 触发分析（手动触发，不限制级别）
     */
    @PostMapping
    public Result<Void> triggerAnalysis(@RequestBody Map<String, Object> aggregationData) {
        try {
            Map<String, Object> payload = new HashMap<>(aggregationData);
            String groupId = String.valueOf(payload.get("groupId"));
            String analysisId = analysisService.startAnalysis(payload, true);
            if (analysisId == null) {
                return Result.success("该聚合组已有分析结果，无需重复分析");
            }
            log.info("收到手动分析请求，已异步提交: {}", groupId);

            llmExecutor.execute(() -> {
                try {
                    analysisService.finishStartedAnalysis(payload, analysisId);
                    log.info("手动分析任务执行完成: {}", groupId);
                } catch (Exception ex) {
                    log.error("手动分析任务执行失败: {}, 错误: {}", groupId, ex.getMessage(), ex);
                }
            });

            return Result.success("分析任务已提交，正在后台执行");
        } catch (IllegalStateException e) {
            log.warn("分析任务重复提交: {}", e.getMessage());
            return Result.failed(409, e.getMessage());
        } catch (Exception e) {
            log.error("分析失败: {}", e.getMessage(), e);
            return Result.failed(500, "分析失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取分析结果
     */
    @GetMapping("/{aggregationId}")
    public Result<AnalysisResultDTO> getAnalysisResult(@PathVariable String aggregationId) {
        AnalysisResultDTO result = analysisService.getAnalysisResult(aggregationId);
        if (result == null) {
            return Result.failed(404, "未找到分析结果");
        }
        return Result.success(result);
    }
    
    /**
     * 获取所有分析结果
     */
    @GetMapping
    public Result<List<AnalysisResultDTO>> getAllAnalysisResults() {
        List<AnalysisResultDTO> results = analysisService.getRecentAnalysisResults(20);
        return Result.success(results);
    }
    
    /**
     * 获取最近的 N 条分析结果
     */
    @GetMapping("/recent")
    public Result<List<AnalysisResultDTO>> getRecentAnalysisResults(
            @RequestParam(defaultValue = "10") int limit) {
        List<AnalysisResultDTO> results = analysisService.getRecentAnalysisResults(limit);
        return Result.success(results);
    }
}
