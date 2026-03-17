package com.evelin.loganalysis.logcollection.collector;

import com.evelin.loganalysis.logcollection.config.CollectionConfig;
import com.evelin.loganalysis.logcollection.service.CheckpointManager;
import com.evelin.loganalysis.logcollection.service.RawLogEventService;
import com.evelin.loganalysis.logcollection.enums.LogSourceType;
import com.evelin.loganalysis.logcollection.model.LogSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 日志采集器工厂
 * <p>
 * 负责创建和管理各种类型的日志采集器实例
 *
 * @author Evelin
 */
@Slf4j
@Component
public class CollectorFactory {

    private final CheckpointManager checkpointManager;
    private final CollectionConfig collectionConfig;
    private final RawLogEventService rawLogEventService;
    private final RabbitTemplate rabbitTemplate;

    private final Map<String, LogCollector> collectorCache = new ConcurrentHashMap<>();

    public CollectorFactory(CheckpointManager checkpointManager,
                           CollectionConfig collectionConfig,
                           RawLogEventService rawLogEventService,
                           RabbitTemplate rabbitTemplate) {
        this.checkpointManager = checkpointManager;
        this.collectionConfig = collectionConfig;
        this.rawLogEventService = rawLogEventService;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 创建日志采集器
     *
     * @param logSource 日志源配置
     * @return 日志采集器
     */
    public LogCollector create(LogSource logSource) {
        String sourceId = logSource.getId().toString();

        if (collectorCache.containsKey(sourceId)) {
            log.warn("Collector already exists for source: {}", sourceId);
            return collectorCache.get(sourceId);
        }

        LogCollector collector = createCollector(logSource);
        collectorCache.put(sourceId, collector);

        log.info("Created {} collector: sourceId={}, name={}, path={}",
                logSource.getSourceType(), sourceId, logSource.getName(), logSource.getPath());

        return collector;
    }

    /**
     * 根据日志源类型创建对应的采集器
     */
    private LogCollector createCollector(LogSource logSource) {
        LogSourceType sourceType = logSource.getSourceType();

        switch (sourceType) {
            case LOCAL_FILE:
                return new LocalFileCollector(
                        logSource,
                        checkpointManager,
                        collectionConfig,
                        rawLogEventService,
                        rabbitTemplate
                );

            case SSH:
                return new SshRemoteCollector(
                        logSource,
                        checkpointManager,
                        collectionConfig,
                        rawLogEventService,
                        rabbitTemplate
                );

            default:
                throw new IllegalArgumentException(
                        "Unsupported source type: " + sourceType +
                                ". Supported types: LOCAL_FILE, SSH"
                );
        }
    }

    /**
     * 获取已创建的采集器
     *
     * @param sourceId 日志源ID
     * @return 采集器，如果不存在返回 null
     */
    public LogCollector get(String sourceId) {
        return collectorCache.get(sourceId);
    }

    /**
     * 获取已创建的采集器（通过日志源）
     *
     * @param logSource 日志源
     * @return 采集器，如果不存在返回 null
     */
    public LogCollector get(LogSource logSource) {
        return get(logSource.getId().toString());
    }

    /**
     * 移除并停止采集器
     *
     * @param sourceId 日志源ID
     * @return 被移除的采集器，如果不存在返回 null
     */
    public LogCollector remove(String sourceId) {
        LogCollector collector = collectorCache.remove(sourceId);

        if (collector != null) {
            if (collector.isRunning()) {
                collector.stop();
            }
            log.info("Removed collector: sourceId={}, type={}", sourceId, collector.getClass().getSimpleName());
        }

        return collector;
    }

    /**
     * 移除并停止采集器（通过日志源）
     *
     * @param logSource 日志源
     * @return 被移除的采集器，如果不存在返回 null
     */
    public LogCollector remove(LogSource logSource) {
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
    public Map<String, LogCollector> getAllCollectors() {
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
