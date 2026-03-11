package com.evelin.loganalysis.loganalysisai.analysis.service;

import com.evelin.loganalysis.loganalysisai.analysis.RootCauseAnalyzer;
import com.evelin.loganalysis.loganalysisai.analysis.dto.AnalysisResultDTO;
import com.evelin.loganalysis.loganalysisai.analysis.entity.AnalysisResultEntity;
import com.evelin.loganalysis.loganalysisai.analysis.repository.AnalysisResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 分析服务
 *
 * @author Evelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {
    
    private final RootCauseAnalyzer rootCauseAnalyzer;
    private final AnalysisResultRepository analysisResultRepository;
    
    /**
     * 获取严重程度优先级
     */
    private int getSeverityPriority(String severity) {
        if (severity == null) return 0;
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> 3;
            case "ERROR" -> 2;
            case "WARNING" -> 1;
            default -> 0;
        };
    }
    
    /**
     * 分析聚合组
     *
     * @param aggregationData 聚合组数据
     * @return 分析结果
     */
    @Transactional
    public AnalysisResultDTO analyze(Map<String, Object> aggregationData) {
        String aggregationId = String.valueOf(aggregationData.get("groupId"));
        String severity = String.valueOf(aggregationData.getOrDefault("severity", "INFO"));
        
        log.info("开始分析聚合组: {}, 严重程度: {}", aggregationId, severity);
        
        // 检查严重程度，只有 ERROR 及以上才能分析
        int severityPriority = getSeverityPriority(severity);
        if (severityPriority < 2) {
            log.warn("聚合组 {} 严重程度为 {}，低于 ERROR 阈值，跳过分析", aggregationId, severity);
            throw new IllegalArgumentException("只有 ERROR 及以上级别才能触发 AI 分析");
        }
        
        // 检查是否已有分析结果
        var existingResult = analysisResultRepository.findByAggregationId(aggregationId);
        if (existingResult.isPresent()) {
            AnalysisResultEntity entity = existingResult.get();
            if ("COMPLETED".equals(entity.getStatus())) {
                log.info("聚合组 {} 已有分析结果，直接返回", aggregationId);
                return toDTO(entity);
            }
        }
        
        // 执行分析
        AnalysisResultDTO result = rootCauseAnalyzer.analyze(aggregationData);
        
        // 保存结果
        AnalysisResultEntity entity = toEntity(result);
        analysisResultRepository.save(entity);
        
        log.info("聚合组 {} 分析完成，状态: {}", aggregationId, result.getStatus());
        
        return result;
    }
    
    /**
     * 获取分析结果
     *
     * @param aggregationId 聚合组ID
     * @return 分析结果
     */
    public AnalysisResultDTO getAnalysisResult(String aggregationId) {
        return analysisResultRepository.findByAggregationId(aggregationId)
                .map(this::toDTO)
                .orElse(null);
    }
    
    /**
     * 获取所有分析结果
     *
     * @return 分析结果列表
     */
    public List<AnalysisResultDTO> getAllAnalysisResults() {
        return analysisResultRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取最近的 N 条分析结果
     *
     * @param limit 数量限制
     * @return 分析结果列表
     */
    public List<AnalysisResultDTO> getRecentAnalysisResults(int limit) {
        return analysisResultRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(limit)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * 转换为 DTO
     */
    private AnalysisResultDTO toDTO(AnalysisResultEntity entity) {
        AnalysisResultDTO dto = new AnalysisResultDTO();
        dto.setId(entity.getId());
        dto.setAggregationId(entity.getAggregationId());
        dto.setAggregationName(entity.getAggregationName());
        dto.setRootCause(entity.getRootCause());
        dto.setRootCauseCategory(entity.getRootCauseCategory());
        dto.setAnalysisDetail(entity.getAnalysisDetail());
        dto.setConfidence(entity.getConfidence());
        dto.setImpactScope(entity.getImpactScope());
        dto.setImpactSeverity(entity.getImpactSeverity());
        dto.setModelName(entity.getModelName());
        dto.setRequestTokens(entity.getRequestTokens());
        dto.setResponseTokens(entity.getResponseTokens());
        dto.setProcessingTimeMs(entity.getProcessingTimeMs());
        dto.setStatus(entity.getStatus());
        dto.setStatusMessage(entity.getStatusMessage());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setCompletedAt(entity.getCompletedAt());
        return dto;
    }
    
    /**
     * 转换为实体
     */
    private AnalysisResultEntity toEntity(AnalysisResultDTO dto) {
        AnalysisResultEntity entity = new AnalysisResultEntity();
        entity.setId(dto.getId());
        entity.setAggregationId(dto.getAggregationId());
        entity.setAggregationName(dto.getAggregationName());
        entity.setRootCause(dto.getRootCause());
        entity.setRootCauseCategory(dto.getRootCauseCategory());
        entity.setAnalysisDetail(dto.getAnalysisDetail());
        entity.setConfidence(dto.getConfidence());
        entity.setImpactScope(dto.getImpactScope());
        entity.setImpactSeverity(dto.getImpactSeverity());
        entity.setModelName(dto.getModelName());
        entity.setRequestTokens(dto.getRequestTokens());
        entity.setResponseTokens(dto.getResponseTokens());
        entity.setProcessingTimeMs(dto.getProcessingTimeMs());
        entity.setStatus(dto.getStatus());
        entity.setStatusMessage(dto.getStatusMessage());
        entity.setCreatedAt(dto.getCreatedAt());
        entity.setCompletedAt(dto.getCompletedAt());
        return entity;
    }
}
