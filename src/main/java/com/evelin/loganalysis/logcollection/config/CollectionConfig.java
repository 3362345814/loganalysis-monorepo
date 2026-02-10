package com.evelin.loganalysis.logcollection.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 日志采集配置类
 *
 * 从application.yml读取采集相关的配置
 *
 * @author Evelin
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.collection")
public class CollectionConfig {

    /**
     * 检查点保存间隔（行数）
     */
    private int checkpointInterval = 1000;

    /**
     * 检查点保存间隔（毫秒）
     */
    private long checkpointIntervalMs = 5000;

    /**
     * 文件读取缓冲区大小（字节）
     */
    private int readBufferSize = 8192;

    /**
     * 采集队列容量
     */
    private int queueCapacity = 10000;

    /**
     * 文件轮转检测间隔（毫秒）
     */
    private long fileRotateCheckIntervalMs = 1000;

    /**
     * 健康检查间隔（毫秒）
     */
    private long healthCheckIntervalMs = 30000;

    /**
     * 编码格式
     */
    private String defaultEncoding = "UTF-8";

    /**
     * 是否启用文件监听
     */
    private boolean enableFileWatcher = true;

    /**
     * Redis检查点key前缀
     */
    private String redisCheckpointPrefix = "log:checkpoint:";

    /**
     * Redis检查点过期时间（秒）
     */
    private long redisCheckpointTtlSeconds = 86400;
}
