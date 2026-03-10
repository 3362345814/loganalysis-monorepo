package com.evelin.loganalysis.logcollection.service;

import com.evelin.loganalysis.logcollection.collector.LocalFileCollector;
import com.evelin.loganalysis.logcollection.collector.LocalFileCollectorFactory;
import com.evelin.loganalysis.logcommon.enums.CollectionStatus;
import com.evelin.loganalysis.logcommon.model.LogSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 采集器启动恢复器
 * 应用启动时自动恢复之前处于 Running 状态的采集器
 *
 * @author Evelin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectorRestoreRunner implements ApplicationRunner {

    private final LogSourceService logSourceService;
    private final LocalFileCollectorFactory collectorFactory;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("Starting collector restore process...");

        // 查询所有状态为 RUNNING 的日志源
        List<LogSource> runningSources = logSourceService.findEntitiesByStatus(CollectionStatus.RUNNING);

        if (runningSources.isEmpty()) {
            log.info("No running collectors to restore");
            return;
        }

        log.info("Found {} running collectors to restore", runningSources.size());

        // 自动启动每个采集器
        for (LogSource source : runningSources) {
            try {
                // 检查是否已是启用状态
                if (!source.getEnabled()) {
                    log.info("Skipping disabled source: {} - {}", source.getId(), source.getName());
                    continue;
                }

                // 检查是否已经是 LOCAL_FILE 类型
                if (source.getSourceType() != com.evelin.loganalysis.logcommon.enums.LogSourceType.LOCAL_FILE) {
                    log.info("Skipping non-LOCAL_FILE source: {} - {}", source.getId(), source.getName());
                    continue;
                }

                // 检查是否已存在运行中的采集器
                if (collectorFactory.get(source) != null && collectorFactory.get(source).isRunning()) {
                    log.info("Collector already running: {} - {}", source.getId(), source.getName());
                    continue;
                }

                // 创建并启动采集器
                LocalFileCollector collector = collectorFactory.create(source);
                collector.start();

                log.info("Successfully restored collector: {} - {}", source.getId(), source.getName());

            } catch (Exception e) {
                log.error("Failed to restore collector: {} - {}", source.getId(), source.getName(), e);
            }
        }

        log.info("Collector restore process completed");
    }
}
