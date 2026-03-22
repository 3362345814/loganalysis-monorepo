package com.evelin.loganalysis.logcollection.collector;

import com.evelin.loganalysis.logcollection.collector.strategy.LocalFileReadingStrategy;
import com.evelin.loganalysis.logcollection.collector.strategy.SshFileReadingStrategy;
import com.evelin.loganalysis.logcollection.config.CollectionConfig;
import com.evelin.loganalysis.logcollection.enums.LogSourceType;
import com.evelin.loganalysis.logcollection.model.LogSource;
import com.evelin.loganalysis.logcollection.service.CheckpointManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 日志采集器工厂
 * <p>
 * 使用 Map 注册机制替代 switch，消除条件分支，新增采集类型只需注册无需修改此文件。
 *
 * @author Evelin
 */
@Slf4j
@Component
public class CollectorFactory {

    private final CheckpointManager checkpointManager;
    private final CollectionConfig collectionConfig;
    private final RabbitTemplate rabbitTemplate;

    private final Map<String, LogCollector> collectorCache = new ConcurrentHashMap<>();

    /**
     * 采集器创建器函数式接口
     */
    @FunctionalInterface
    public interface CollectorCreator {
        LogCollector create(LogSource logSource,
                           CheckpointManager checkpointManager,
                           CollectionConfig config,
                           RabbitTemplate rabbitTemplate,
                           AtomicBoolean running,
                           AtomicBoolean paused,
                           AtomicLong collectedLines);
    }

    /**
     * 策略注册表：LogSourceType -> CollectorCreator
     */
    private final Map<LogSourceType, CollectorCreator> strategyRegistry = new ConcurrentHashMap<>();

    public CollectorFactory(CheckpointManager checkpointManager,
                           CollectionConfig collectionConfig,
                           RabbitTemplate rabbitTemplate) {
        this.checkpointManager = checkpointManager;
        this.collectionConfig = collectionConfig;
        this.rabbitTemplate = rabbitTemplate;

        // 注册所有采集策略
        registerStrategies();
    }

    /**
     * 注册所有支持的采集策略
     * <p>
     * 新增采集类型只需在此方法中添加一行注册，无需修改 create 方法。
     */
    private void registerStrategies() {
        strategyRegistry.put(LogSourceType.LOCAL_FILE, LocalFileReadingStrategy::new);
        strategyRegistry.put(LogSourceType.SSH, SshFileReadingStrategy::new);
        log.info("Registered {} collector strategies: {}",
                strategyRegistry.size(), strategyRegistry.keySet());
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
     * 根据日志源类型从注册表中获取对应的采集器
     */
    private LogCollector createCollector(LogSource logSource) {
        LogSourceType sourceType = logSource.getSourceType();
        CollectorCreator creator = strategyRegistry.get(sourceType);

        if (creator == null) {
            throw new IllegalArgumentException(
                    "Unsupported source type: " + sourceType +
                            ". Supported types: " + strategyRegistry.keySet() +
                            ". Did you forget to register the strategy?"
            );
        }

        return creator.create(
                logSource,
                checkpointManager,
                collectionConfig,
                rabbitTemplate,
                new AtomicBoolean(false),
                new AtomicBoolean(false),
                new AtomicLong(0)
        );
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
