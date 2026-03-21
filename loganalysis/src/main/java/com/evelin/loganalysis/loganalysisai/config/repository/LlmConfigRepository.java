package com.evelin.loganalysis.loganalysisai.config.repository;

import com.evelin.loganalysis.loganalysisai.config.entity.LlmConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * LLM 配置 Repository
 *
 * @author Evelin
 */
@Repository
public interface LlmConfigRepository extends JpaRepository<LlmConfigEntity, String> {
    
    /**
     * 获取默认配置
     */
    Optional<LlmConfigEntity> findByIsDefaultTrue();
    
    /**
     * 获取启用的配置
     */
    List<LlmConfigEntity> findByEnabledTrue();
    
    /**
     * 清除所有默认配置
     */
    @Modifying
    @Query("UPDATE LlmConfigEntity SET isDefault = false WHERE isDefault = true")
    void clearDefaultConfigs();
}
