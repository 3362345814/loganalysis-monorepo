package com.evelin.loganalysis.loganalysisai.analysis.repository;

import com.evelin.loganalysis.loganalysisai.analysis.entity.AnalysisResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 分析结果 Repository
 *
 * @author Evelin
 */
@Repository
public interface AnalysisResultRepository extends JpaRepository<AnalysisResultEntity, String> {
    
    /**
     * 根据聚合组ID查询分析结果
     */
    Optional<AnalysisResultEntity> findByAggregationId(String aggregationId);
    
    /**
     * 查询聚合组的最新分析结果
     */
    Optional<AnalysisResultEntity> findTopByAggregationIdOrderByCreatedAtDesc(String aggregationId);
    
    /**
     * 根据状态查询分析结果
     */
    List<AnalysisResultEntity> findByStatus(String status);
    
    /**
     * 根据聚合组ID删除分析结果
     */
    void deleteByAggregationId(String aggregationId);
}
