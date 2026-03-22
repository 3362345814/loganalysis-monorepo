package com.evelin.loganalysis.logcollection.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import com.evelin.loganalysis.logcollection.config.EsSyncConfig;
import com.evelin.loganalysis.logcollection.dto.EsLogQueryRequest;
import com.evelin.loganalysis.logcollection.dto.EsLogSearchResponse;
import com.evelin.loganalysis.logcollection.model.LogIndexDocument;
import com.evelin.loganalysis.logcollection.model.entity.RawLogEventEntity;
import com.evelin.loganalysis.logcollection.repository.LogElasticsearchRepository;
import com.evelin.loganalysis.logcollection.repository.RawLogEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * ES 日志服务层
 *
 * @author Evelin
 */
@Slf4j
@Service
public class LogElasticsearchService {

    private final LogElasticsearchRepository esRepository;
    private final RawLogEventRepository rawLogEventRepository;
    private final ElasticsearchClient esClient;
    private final EsSyncConfig syncConfig;

    /**
     * 记录上次同步完成的最后一个日志 ID，用于增量同步
     * UUID 类型，初始为 null（从头开始同步）
     */
    private final AtomicReference<UUID> lastSyncedId = new AtomicReference<>(null);

    /**
     * 防止定时任务并发执行
     */
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);

    /**
     * 上次同步时间戳
     */
    private final AtomicReference<Long> lastSyncTime = new AtomicReference<>(null);

    public LogElasticsearchService(LogElasticsearchRepository esRepository,
                                  RawLogEventRepository rawLogEventRepository,
                                  ElasticsearchClient esClient,
                                  EsSyncConfig syncConfig) {
        this.esRepository = esRepository;
        this.rawLogEventRepository = rawLogEventRepository;
        this.esClient = esClient;
        this.syncConfig = syncConfig;
    }

    /**
     * 定时增量同步：每次只同步上次同步点之后新增的日志
     * 通过记录 lastSyncedId 实现增量，避免重复同步
     * 使用游标分页（id > lastId），避免新插入数据导致跳页或重复
     */
    @Scheduled(fixedDelayString = "${elasticsearch.sync.interval-ms:300000}")
    public void scheduledIncrementalSync() {
        if (!syncConfig.isEnabled()) {
            return;
        }

        if (!syncInProgress.compareAndSet(false, true)) {
            log.debug("ES incremental sync already in progress, skipping this run");
            return;
        }

        long startTime = System.currentTimeMillis();
        UUID lastId = lastSyncedId.get();

        try {
            int totalIndexed = 0;
            int batchSize = syncConfig.getBatchSize();
            int maxPerRun = syncConfig.getMaxBatchPerRun();
            UUID currentLastId = lastId;
            boolean firstRun = (lastId == null);

            // 首次运行（应用重启后内存状态丢失）：
            // 找到 DB 中最大 ID，从头开始同步，完成后记录 maxId 作为游标
            if (firstRun) {
                UUID maxId = rawLogEventRepository.findMaxId();
                if (maxId == null) {
                    log.info("ES scheduled sync: no logs in DB yet, skipping");
                    lastSyncedId.set(UUID.fromString("00000000-0000-0000-0000-000000000000"));
                    lastSyncTime.set(System.currentTimeMillis());
                    syncInProgress.set(false);
                    return;
                }
                log.info("ES scheduled sync: first run, DB maxId={}, syncing all from beginning", maxId);
            }

            while (totalIndexed < maxPerRun) {
                Page<RawLogEventEntity> entityPage;
                if (firstRun) {
                    // 首次运行：从头按 ID 升序遍历全表
                    entityPage = rawLogEventRepository.findAllOrderByIdAsc(PageRequest.of(0, batchSize));
                } else {
                    // 增量：id > currentLastId
                    entityPage = rawLogEventRepository.findAllByIdGreaterThanOrderByIdAsc(
                            currentLastId, PageRequest.of(0, batchSize));
                }

                List<RawLogEventEntity> entities = entityPage.getContent();
                if (entities.isEmpty()) {
                    break;
                }

                int indexed = bulkIndexLogs(entities);
                if (indexed == 0) {
                    log.warn("Bulk index returned 0, breaking sync loop");
                    break;
                }

                totalIndexed += indexed;
                currentLastId = entities.get(entities.size() - 1).getId();

                if (!entityPage.hasNext()) {
                    break;
                }
            }

            // 更新同步游标：首次运行全表完成后记录 maxId，增量运行记录 lastId
            if (firstRun) {
                lastSyncedId.set(currentLastId);
                log.info("ES first-run full sync completed, switch to incremental mode, lastId={}", currentLastId);
            } else if (totalIndexed > 0) {
                lastSyncedId.set(currentLastId);
            }
            lastSyncTime.set(System.currentTimeMillis());

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("ES scheduled incremental sync completed: indexed={}, lastId={}, elapsed={}ms, firstRun={}",
                    totalIndexed, currentLastId, elapsed, firstRun);

        } catch (Exception e) {
            log.error("ES scheduled incremental sync failed", e);
        } finally {
            syncInProgress.set(false);
        }
    }

    /**
     * 索引单条日志
     */
    public void indexLog(RawLogEventEntity entity) {
        try {
            LogIndexDocument document = LogIndexDocument.fromEntity(entity);
            esRepository.indexDocument(document);
            log.debug("Indexed log: {}", entity.getEventId());
        } catch (IOException e) {
            log.error("Failed to index log: {}", entity.getEventId(), e);
        }
    }

    /**
     * 异步索引单条日志
     */
    @Async
    public void indexLogAsync(RawLogEventEntity entity) {
        indexLog(entity);
    }

    /**
     * 批量索引日志
     */
    public int bulkIndexLogs(List<RawLogEventEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        try {
            List<LogIndexDocument> documents = entities.stream()
                    .map(LogIndexDocument::fromEntity)
                    .collect(Collectors.toList());
            esRepository.bulkIndex(documents);
            log.info("Bulk indexed {} logs", documents.size());
            return documents.size();
        } catch (IOException e) {
            log.error("Failed to bulk index logs", e);
            return 0;
        }
    }

    /**
     * 异步批量索引日志
     */
    @Async
    public void bulkIndexLogsAsync(List<RawLogEventEntity> entities) {
        bulkIndexLogs(entities);
    }

    /**
     * 高级搜索日志
     */
    public EsLogSearchResponse searchLogs(EsLogQueryRequest request) {
        try {
            return esRepository.advancedSearch(request);
        } catch (IOException e) {
            log.error("Failed to search logs", e);
            return EsLogSearchResponse.builder()
                    .total(0)
                    .page(request.getPage())
                    .size(request.getSize())
                    .hits(List.of())
                    .aggregations(new HashMap<>())
                    .took(0)
                    .build();
        }
    }

    /**
     * 获取聚合统计
     */
    public Map<String, Object> getAggregations(EsLogQueryRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 按日志级别聚合
            if (request.getAggregationField() == null || "logLevel".equals(request.getAggregationField())) {
                result.put("byLevel", esRepository.aggregateByLevel());
            }
            // 按日志源聚合
            if ("sourceName".equals(request.getAggregationField()) || request.getAggregationField() == null) {
                result.put("bySource", esRepository.aggregateBySource());
            }
            // 按时间直方图聚合
            if (request.getTimeInterval() != null) {
                result.put("byTime", esRepository.aggregateByTimeHistogram(request.getTimeInterval()));
            }
        } catch (IOException e) {
            log.error("Failed to get aggregations", e);
        }
        return result;
    }

    /**
     * 按日志源ID同步到 ES
     */
    public int syncLogsBySourceId(UUID sourceId, int batchSize) {
        int totalIndexed = 0;
        int page = 0;
        boolean hasMore = true;

        while (hasMore) {
            Page<RawLogEventEntity> entityPage = rawLogEventRepository
                    .findBySourceIdOrderByOriginalLogTimeDesc(sourceId, PageRequest.of(page, batchSize));

            List<RawLogEventEntity> entities = entityPage.getContent();
            if (entities.isEmpty()) {
                hasMore = false;
            } else {
                int indexed = bulkIndexLogs(entities);
                totalIndexed += indexed;
                hasMore = entityPage.hasNext();
                page++;

                if (indexed == 0) {
                    break;
                }
            }
        }

        log.info("Synced {} logs for source: {}", totalIndexed, sourceId);
        return totalIndexed;
    }

    /**
     * 同步所有日志到 ES
     */
    public int syncAllLogs(int batchSize) {
        int totalIndexed = 0;
        int page = 0;
        boolean hasMore = true;

        while (hasMore) {
            Page<RawLogEventEntity> entityPage = rawLogEventRepository
                    .findAllOrderByCollectionTimeDesc(PageRequest.of(page, batchSize));

            List<RawLogEventEntity> entities = entityPage.getContent();
            if (entities.isEmpty()) {
                hasMore = false;
            } else {
                int indexed = bulkIndexLogs(entities);
                totalIndexed += indexed;
                hasMore = entityPage.hasNext();
                page++;

                if (indexed == 0) {
                    break;
                }
            }
        }

        log.info("Synced {} total logs to ES", totalIndexed);
        return totalIndexed;
    }

    /**
     * 删除指定日志源的 ES 索引
     */
    public long deleteBySourceId(UUID sourceId) {
        try {
            var response = esRepository.deleteBySourceId(sourceId);
            return response.deleted();
        } catch (IOException e) {
            log.error("Failed to delete logs for source: {}", sourceId, e);
            return 0;
        }
    }

    /**
     * 获取 ES 索引文档数量
     */
    public long getIndexedCount() {
        try {
            return esRepository.getDocumentCount();
        } catch (IOException e) {
            log.error("Failed to get document count", e);
            return -1;
        }
    }

    /**
     * ES 健康检查
     */
    public Map<String, Object> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        try {
            HealthResponse response = esClient.cluster().health();
            health.put("status", response.status().jsonValue());
            health.put("clusterName", response.clusterName());
            health.put("numberOfNodes", response.numberOfNodes());
            health.put("activeShards", response.activeShards());
            health.put("activePrimaryShards", response.activePrimaryShards());
            health.put("relocatingShards", response.relocatingShards());
            health.put("initializingShards", response.initializingShards());
            health.put("unassignedShards", response.unassignedShards());
            health.put("delayedUnassignedShards", response.delayedUnassignedShards());
            health.put("indexedCount", esRepository.getDocumentCount());
            health.put("available", true);
        } catch (Exception e) {
            log.error("ES health check failed", e);
            health.put("available", false);
            health.put("error", e.getMessage());
        }
        return health;
    }
}
