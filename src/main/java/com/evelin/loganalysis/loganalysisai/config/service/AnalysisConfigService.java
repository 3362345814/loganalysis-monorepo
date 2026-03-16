package com.evelin.loganalysis.loganalysisai.config.service;

import com.evelin.loganalysis.loganalysisai.config.entity.AnalysisConfigEntity;
import com.evelin.loganalysis.loganalysisai.config.repository.AnalysisConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * AI分析配置 Service
 *
 * @author Evelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisConfigService {

    private final AnalysisConfigRepository analysisConfigRepository;

    private static final Long CONFIG_ID = 1L;

    /**
     * 获取配置，如果不存在则创建默认配置
     */
    @Transactional
    public AnalysisConfigEntity getConfig() {
        Optional<AnalysisConfigEntity> existing = analysisConfigRepository.findById(CONFIG_ID);
        if (existing.isPresent()) {
            return existing.get();
        }
        // 创建默认配置
        AnalysisConfigEntity config = new AnalysisConfigEntity();
        config.setId(CONFIG_ID);
        config.setContextSize(10);
        config.setAutoAnalysisSeverity("ERROR");
        config.setAutoAnalysisEnabled(true);
        return analysisConfigRepository.save(config);
    }

    /**
     * 更新配置
     */
    @Transactional
    public AnalysisConfigEntity updateConfig(AnalysisConfigEntity config) {
        config.setId(CONFIG_ID);
        
        // 验证上下文大小
        if (config.getContextSize() == null || config.getContextSize() < 10) {
            config.setContextSize(10);
        } else if (config.getContextSize() > 30) {
            config.setContextSize(30);
        }
        
        // 验证自动分析级别
        String severity = config.getAutoAnalysisSeverity();
        if (severity != null && !severity.equals("ERROR") && !severity.equals("WARNING")) {
            config.setAutoAnalysisSeverity("ERROR");
        }
        
        return analysisConfigRepository.save(config);
    }

    /**
     * 获取上下文大小
     */
    public Integer getContextSize() {
        return getConfig().getContextSize();
    }

    /**
     * 获取自动分析是否启用
     */
    public Boolean isAutoAnalysisEnabled() {
        return getConfig().getAutoAnalysisEnabled();
    }

    /**
     * 获取自动分析级别
     */
    public String getAutoAnalysisSeverity() {
        return getConfig().getAutoAnalysisSeverity();
    }
}
