package com.evelin.loganalysis.logcollection.collector;

import com.evelin.loganalysis.logcollection.config.CollectionConfig;
import com.evelin.loganalysis.logcollection.service.CheckpointManager;
import com.evelin.loganalysis.logcollection.service.RawLogEventService;
import com.evelin.loganalysis.logcommon.enums.LogSourceType;
import com.evelin.loganalysis.logcommon.model.LogSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地文件采集器工厂
 * <p>
 * 负责创建和管理 LocalFileCollector 实例
 *
 * @author Evelin
 */
@Slf4j
@Component
public class LocalFileCollectorFactory {

    private final CheckpointManager checkpointManager;
    private final CollectionConfig collectionConfig;
    private final RawLogEventService rawLogEventService;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 采集器缓存（用于管理已创建的采集器）
     */
    private final Map<String, LocalFileCollector> collectorCache = new ConcurrentHashMap<>();

    public LocalFileCollectorFactory(CheckpointManager checkpointManager,
                                     CollectionConfig collectionConfig,
                                     RawLogEventService rawLogEventService,
                                     RabbitTemplate rabbitTemplate) {
        this.checkpointManager = checkpointManager;
        this.collectionConfig = collectionConfig;
        this.rawLogEventService = rawLogEventService;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 创建本地文件采集器
     *
     * @param logSource 日志源配置
     * @return 本地文件采集器
     */
    public LocalFileCollector create(LogSource logSource) {
        // 验证日志源类型
        if (logSource.getSourceType() != LogSourceType.LOCAL_FILE) {
            throw new IllegalArgumentException(
                    "Invalid source type: " + logSource.getSourceType() +
                            ", expected LOCAL_FILE");
        }

        String sourceId = logSource.getId().toString();

        // 检查是否已存在
        if (collectorCache.containsKey(sourceId)) {
            log.warn("Collector already exists for source: {}", sourceId);
            return collectorCache.get(sourceId);
        }

        // 创建采集器（通过 RabbitMQ 发送到脱敏队列）
        LocalFileCollector collector = new LocalFileCollector(
                logSource,
                checkpointManager,
                collectionConfig,
                rawLogEventService,
                rabbitTemplate
        );

        // 缓存
        collectorCache.put(sourceId, collector);

        log.info("Created LocalFileCollector: sourceId={}, name={}, path={}",
                sourceId, logSource.getName(), logSource.getPath());

        return collector;
    }

    /**
     * 获取已创建的采集器
     *
     * @param sourceId 日志源ID
     * @return 采集器，如果不存在返回 null
     */
    public LocalFileCollector get(String sourceId) {
        return collectorCache.get(sourceId);
    }

    /**
     * 获取已创建的采集器（通过日志源）
     *
     * @param logSource 日志源
     * @return 采集器，如果不存在返回 null
     */
    public LocalFileCollector get(LogSource logSource) {
        return get(logSource.getId().toString());
    }

    /**
     * 移除并停止采集器
     *
     * @param sourceId 日志源ID
     * @return 被移除的采集器，如果不存在返回 null
     */
    public LocalFileCollector remove(String sourceId) {
        LocalFileCollector collector = collectorCache.remove(sourceId);

        if (collector != null) {
            if (collector.isRunning()) {
                collector.stop();
            }
            log.info("Removed LocalFileCollector: sourceId={}", sourceId);
        }

        return collector;
    }

    /**
     * 移除并停止采集器（通过日志源）
     *
     * @param logSource 日志源
     * @return 被移除的采集器，如果不存在返回 null
     */
    public LocalFileCollector remove(LogSource logSource) {
        return remove(logSource.getId().toString());
    }

    /**
     * 停止并移除所有采集器
     */
    public void shutdownAll() {
        log.info("Shutting down all collectors...");

        collectorCache.forEach((sourceId, collector) -> {
            try {
                if (collector.isRunning()) {
                    collector.stop();
                }
            } catch (Exception e) {
                log.error("Error stopping collector: sourceId={}", sourceId, e);
            }
        });

        collectorCache.clear();
        log.info("All collectors shut down");
    }

    /**
     * 获取所有已创建的采集器
     *
     * @return 采集器映射
     */
    public Map<String, LocalFileCollector> getAllCollectors() {
        return new ConcurrentHashMap<>(collectorCache);
    }

    /**
     * 获取采集器数量
     *
     * @return 采集器数量
     */
    public int getCollectorCount() {
        return collectorCache.size();
    }
}
