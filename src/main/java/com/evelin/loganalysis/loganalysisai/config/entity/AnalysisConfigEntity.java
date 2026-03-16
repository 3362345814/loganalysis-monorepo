package com.evelin.loganalysis.loganalysisai.config.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * AI分析配置实体
 *
 * @author Evelin
 */
@Data
@Entity
@Table(name = "analysis_config")
public class AnalysisConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 上下文行数 (10-30)
     */
    @Column(name = "context_size")
    private Integer contextSize = 10;

    /**
     * 自动分析级别 (ERROR, WARNING, null=关闭)
     */
    @Column(name = "auto_analysis_severity", length = 20)
    private String autoAnalysisSeverity = "ERROR";

    /**
     * 是否启用自动分析
     */
    @Column(name = "auto_analysis_enabled")
    private Boolean autoAnalysisEnabled = true;
}
