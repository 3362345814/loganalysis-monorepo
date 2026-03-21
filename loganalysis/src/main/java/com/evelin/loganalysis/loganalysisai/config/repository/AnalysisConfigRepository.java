package com.evelin.loganalysis.loganalysisai.config.repository;

import com.evelin.loganalysis.loganalysisai.config.entity.AnalysisConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * AI分析配置 Repository
 *
 * @author Evelin
 */
@Repository
public interface AnalysisConfigRepository extends JpaRepository<AnalysisConfigEntity, Long> {

    /**
     * 获取唯一的配置（系统只有一条配置记录）
     */
    default AnalysisConfigEntity getConfig() {
        return findAll().stream().findFirst().orElse(null);
    }
}
