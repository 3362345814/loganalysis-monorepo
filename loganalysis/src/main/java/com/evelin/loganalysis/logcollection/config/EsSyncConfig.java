package com.evelin.loganalysis.logcollection.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch 定时同步配置
 *
 * @author Evelin
 */
@Data
@Component
@ConfigurationProperties(prefix = "elasticsearch.sync")
public class EsSyncConfig {

    /**
     * 是否启用定时同步
     */
    private boolean enabled = true;

    /**
     * 同步间隔（毫秒），默认 5 分钟
     */
    private long intervalMs = 300000;

    /**
     * 每批同步数量
     */
    private int batchSize = 500;

    /**
     * 单次同步最大数量（防止一次同步过多）
     */
    private int maxBatchPerRun = 5000;
}
