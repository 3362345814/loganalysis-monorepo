package com.evelin.loganalysis.logcollection.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import com.evelin.loganalysis.logcollection.dto.EsLogQueryRequest;
import com.evelin.loganalysis.logcollection.dto.EsLogSearchResponse;
import com.evelin.loganalysis.logcollection.model.LogIndexDocument;
import com.evelin.loganalysis.logcollection.model.entity.RawLogEventEntity;
import com.evelin.loganalysis.logcollection.repository.LogElasticsearchRepository;
import com.evelin.loganalysis.logcollection.repository.RawLogEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ES 日志服务层
 *
 * @author Evelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogElasticsearchService {

    private final LogElasticsearchRepository esRepository;
    private final RawLogEventRepository rawLogEventRepository;
    private final ElasticsearchClient esClient;

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
