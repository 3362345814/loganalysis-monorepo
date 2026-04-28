package com.evelin.loganalysis.loganalysisai.analysis.repository;

import com.evelin.loganalysis.loganalysisai.analysis.entity.AnalysisResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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
     * 查询聚合组的所有分析结果
     */
    List<AnalysisResultEntity> findAllByAggregationId(String aggregationId);
    
    /**
     * 查询聚合组的最新分析结果
     */
    Optional<AnalysisResultEntity> findTopByAggregationIdOrderByCreatedAtDesc(String aggregationId);
    
    /**
     * 根据状态查询分析结果
     */
    List<AnalysisResultEntity> findByStatus(String status);

    /**
     * 查询指定聚合组集合中已有分析结果的聚合组ID
     */
    @Query("SELECT DISTINCT ar.aggregationId FROM AnalysisResultEntity ar WHERE ar.aggregationId IN :aggregationIds")
    List<String> findDistinctAggregationIdsIn(@Param("aggregationIds") Collection<String> aggregationIds);
    
    /**
     * 根据聚合组ID删除分析结果
     */
    void deleteByAggregationId(String aggregationId);
}
