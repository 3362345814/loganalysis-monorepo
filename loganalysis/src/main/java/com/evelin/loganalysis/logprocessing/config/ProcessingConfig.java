package com.evelin.loganalysis.logprocessing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 日志处理配置类
 *
 * @author Evelin
 */
@Configuration
@ConfigurationProperties(prefix = "log.processing")
public class ProcessingConfig {

    /**
     * 是否启用处理管道
     */
    private boolean enabled = true;

    /**
     * 批处理大小
     */
    private int batchSize = 100;

    /**
     * 批处理间隔（毫秒）
     */
    private long batchIntervalMs = 1000;

    /**
     * 上下文提取：前日志数量
     */
    private int contextBeforeLines = 10;

    /**
     * 上下文提取：后日志数量
     */
    private int contextAfterLines = 10;

    /**
     * 聚合组超时时间（分钟）
     */
    private int aggregationTimeoutMinutes = 60;

    /**
     * 相似度阈值
     */
    private double similarityThreshold = 0.85;

    /**
     * 是否启用敏感信息脱敏
     */
    private boolean desensitizationEnabled = true;

    /**
     * 是否启用事件识别
     */
    private boolean eventDetectionEnabled = true;

    /**
     * 是否启用日志聚合
     */
    private boolean aggregationEnabled = true;

    /**
     * 是否启用告警触发
     */
    private boolean alertEnabled = true;

    /**
     * 线程池核心线程数
     */
    private int corePoolSize = 4;

    /**
     * 线程池最大线程数
     */
    private int maxPoolSize = 8;

    /**
     * 线程池队列容量
     */
    private int queueCapacity = 100;

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getBatchIntervalMs() {
        return batchIntervalMs;
    }

    public void setBatchIntervalMs(long batchIntervalMs) {
        this.batchIntervalMs = batchIntervalMs;
    }

    public int getContextBeforeLines() {
        return contextBeforeLines;
    }

    public void setContextBeforeLines(int contextBeforeLines) {
        this.contextBeforeLines = contextBeforeLines;
    }

    public int getContextAfterLines() {
        return contextAfterLines;
    }

    public void setContextAfterLines(int contextAfterLines) {
        this.contextAfterLines = contextAfterLines;
    }

    public int getAggregationTimeoutMinutes() {
        return aggregationTimeoutMinutes;
    }

    public void setAggregationTimeoutMinutes(int aggregationTimeoutMinutes) {
        this.aggregationTimeoutMinutes = aggregationTimeoutMinutes;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public boolean isDesensitizationEnabled() {
        return desensitizationEnabled;
    }

    public void setDesensitizationEnabled(boolean desensitizationEnabled) {
        this.desensitizationEnabled = desensitizationEnabled;
    }

    public boolean isEventDetectionEnabled() {
        return eventDetectionEnabled;
    }

    public void setEventDetectionEnabled(boolean eventDetectionEnabled) {
        this.eventDetectionEnabled = eventDetectionEnabled;
    }

    public boolean isAggregationEnabled() {
        return aggregationEnabled;
    }

    public void setAggregationEnabled(boolean aggregationEnabled) {
        this.aggregationEnabled = aggregationEnabled;
    }

    public boolean isAlertEnabled() {
        return alertEnabled;
    }

    public void setAlertEnabled(boolean alertEnabled) {
        this.alertEnabled = alertEnabled;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }
}
