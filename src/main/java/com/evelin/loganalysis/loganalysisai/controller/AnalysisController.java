package com.evelin.loganalysis.loganalysisai.controller;

import com.evelin.loganalysis.loganalysisai.analysis.dto.AnalysisResultDTO;
import com.evelin.loganalysis.loganalysisai.analysis.service.AnalysisService;
import com.evelin.loganalysis.logcommon.model.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    
    /**
     * 触发分析（手动触发，不限制级别）
     */
    @PostMapping
    public Result<AnalysisResultDTO> triggerAnalysis(@RequestBody Map<String, Object> aggregationData) {
        try {
            log.info("收到手动分析请求: {}", aggregationData.get("groupId"));
            // 手动触发，设置为 true，不限制日志级别
            AnalysisResultDTO result = analysisService.analyze(aggregationData, true);
            return Result.success(result);
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
