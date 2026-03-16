package com.evelin.loganalysis.loganalysisai.config.controller;

import com.evelin.loganalysis.loganalysisai.config.entity.AnalysisConfigEntity;
import com.evelin.loganalysis.loganalysisai.config.service.AnalysisConfigService;
import com.evelin.loganalysis.logcommon.model.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * AI分析配置 Controller
 *
 * @author Evelin
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/analysis-config")
@RequiredArgsConstructor
public class AnalysisConfigController {

    private final AnalysisConfigService analysisConfigService;

    /**
     * 获取配置
     */
    @GetMapping
    public Result<AnalysisConfigEntity> getConfig() {
        AnalysisConfigEntity config = analysisConfigService.getConfig();
        return Result.success(config);
    }

    /**
     * 更新配置
     */
    @PutMapping
    public Result<AnalysisConfigEntity> updateConfig(@RequestBody AnalysisConfigEntity config) {
        log.info("更新分析配置: contextSize={}, autoAnalysisSeverity={}, autoAnalysisEnabled={}",
                config.getContextSize(), config.getAutoAnalysisSeverity(), config.getAutoAnalysisEnabled());
        AnalysisConfigEntity updated = analysisConfigService.updateConfig(config);
        return Result.success(updated);
    }
}
