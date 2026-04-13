package com.evelin.loganalysis.logcollection.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import com.evelin.loganalysis.logcollection.config.EsSyncConfig;
import com.evelin.loganalysis.logcollection.dto.EsLogQueryRequest;
import com.evelin.loganalysis.logcollection.dto.EsLogSearchResponse;
import com.evelin.loganalysis.logcollection.dto.TraceDistributionResponse;
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
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
    private final StringRedisTemplate stringRedisTemplate;

    private static final String ES_SYNC_CURSOR_KEY = "loganalysis:es:sync:last-synced-id";
    private static final double TRACE_MAX_DURATION_SEC = 3600D;

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
                                  EsSyncConfig syncConfig,
                                  StringRedisTemplate stringRedisTemplate) {
        this.esRepository = esRepository;
        this.rawLogEventRepository = rawLogEventRepository;
        this.esClient = esClient;
        this.syncConfig = syncConfig;
        this.stringRedisTemplate = stringRedisTemplate;
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
        if (lastId == null) {
            UUID persistedCursor = loadCursorFromRedis();
            if (persistedCursor != null) {
                lastSyncedId.set(persistedCursor);
                lastId = persistedCursor;
                log.info("ES sync loaded persisted cursor: {}", persistedCursor);
            }
        }

        try {
            int totalIndexed = 0;
            int batchSize = syncConfig.getBatchSize();
            int maxPerRun = syncConfig.getMaxBatchPerRun();
            UUID currentLastId = lastId;
            boolean firstRun = (lastId == null);

            // 首次运行（没有持久化游标）：
            // 将游标初始化到当前 DB 最大 ID，避免重启后全量回扫造成日志洪峰。
            if (firstRun) {
                UUID maxId = rawLogEventRepository.findMaxId();
                if (maxId == null) {
                    log.info("ES scheduled sync: no logs in DB yet, skipping");
                    lastSyncedId.set(UUID.fromString("00000000-0000-0000-0000-000000000000"));
                    persistCursorToRedis(lastSyncedId.get());
                    lastSyncTime.set(System.currentTimeMillis());
                    syncInProgress.set(false);
                    return;
                }
                lastSyncedId.set(maxId);
                persistCursorToRedis(maxId);
                lastSyncTime.set(System.currentTimeMillis());
                log.info("ES scheduled sync bootstrap cursor to latest DB id={}, skip historical backfill", maxId);
                return;
            }

            while (totalIndexed < maxPerRun) {
                // 增量：id > currentLastId
                Page<RawLogEventEntity> entityPage = rawLogEventRepository.findAllByIdGreaterThanOrderByIdAsc(
                        currentLastId, PageRequest.of(0, batchSize));
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
                persistCursorToRedis(currentLastId);
                log.info("ES first-run full sync completed, switch to incremental mode, lastId={}", currentLastId);
            } else if (totalIndexed > 0) {
                lastSyncedId.set(currentLastId);
                persistCursorToRedis(currentLastId);
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

    private UUID loadCursorFromRedis() {
        try {
            String value = stringRedisTemplate.opsForValue().get(ES_SYNC_CURSOR_KEY);
            if (value == null || value.isBlank()) {
                return null;
            }
            return UUID.fromString(value);
        } catch (Exception e) {
            log.warn("Failed to load ES sync cursor from Redis: {}", e.getMessage());
            return null;
        }
    }

    private void persistCursorToRedis(UUID cursor) {
        if (cursor == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(ES_SYNC_CURSOR_KEY, cursor.toString());
        } catch (Exception e) {
            log.warn("Failed to persist ES sync cursor to Redis: {}", e.getMessage());
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
     * 使用 count API 获取准确的文档总数
     */
    public long countDocuments(EsLogQueryRequest request) {
        try {
            return esRepository.countDocuments(request);
        } catch (IOException e) {
            log.error("Failed to count documents", e);
            return 0;
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

    /**
     * 获取链路追踪耗时分布（P50/P95/P99）
     */
    public TraceDistributionResponse getTraceDistribution(UUID projectId, Integer days, String interval) {
        boolean halfHourInterval = "HALF_HOUR".equalsIgnoreCase(interval) || "30M".equalsIgnoreCase(interval);

        if (halfHourInterval) {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withSecond(0).withNano(0);
            int minute = now.getMinute();
            LocalDateTime endBucket = now.withMinute(minute < 30 ? 0 : 30);
            LocalDateTime startBucket = endBucket.minusMinutes(47L * 30);
            LocalDateTime startTime = startBucket;
            LocalDateTime endTime = LocalDateTime.now(ZoneOffset.UTC);

            Map<LocalDateTime, Quantiles> quantilesByBucket = new HashMap<>();
            Map<LocalDateTime, Long> sampleCountByBucket = new HashMap<>();
            try {
                List<Object[]> rows = projectId == null
                        ? rawLogEventRepository.findTraceDurationPercentilesByHalfHour(startTime, endTime, TRACE_MAX_DURATION_SEC)
                        : rawLogEventRepository.findTraceDurationPercentilesByHalfHourAndProject(startTime, endTime, projectId, TRACE_MAX_DURATION_SEC);
                for (Object[] row : rows) {
                    LocalDateTime bucket = toLocalDateTime(row[0]);
                    if (bucket == null) {
                        continue;
                    }
                    Quantiles q = new Quantiles(toDouble(row[1]), toDouble(row[2]), toDouble(row[3]));
                    quantilesByBucket.put(bucket, q);
                    sampleCountByBucket.put(bucket, toLong(row.length > 4 ? row[4] : null));
                }
            } catch (Exception e) {
                log.error("Failed to load half-hour trace distribution", e);
            }

            List<String> labels = new java.util.ArrayList<>(48);
            List<Double> p50 = new java.util.ArrayList<>(48);
            List<Double> p95 = new java.util.ArrayList<>(48);
            List<Double> p99 = new java.util.ArrayList<>(48);
            List<Long> sampleCount = new java.util.ArrayList<>(48);

            for (int i = 0; i < 48; i++) {
                LocalDateTime bucket = startBucket.plusMinutes(i * 30L);
                labels.add(bucket.toString());
                Quantiles q = quantilesByBucket.get(bucket);
                p50.add(q == null ? null : q.p50());
                p95.add(q == null ? null : q.p95());
                p99.add(q == null ? null : q.p99());
                sampleCount.add(sampleCountByBucket.getOrDefault(bucket, 0L));
            }

            return TraceDistributionResponse.builder()
                    .labels(labels)
                    .p50(p50)
                    .p95(p95)
                    .p99(p99)
                    .sampleCount(sampleCount)
                    .build();
        }

        int safeDays = days == null ? 30 : Math.max(1, Math.min(days, 60));
        LocalDate endDate = LocalDate.now(ZoneOffset.UTC);
        LocalDate startDate = endDate.minusDays(safeDays - 1L);
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = LocalDateTime.now(ZoneOffset.UTC);

        Map<LocalDate, Quantiles> quantilesByDay = new HashMap<>();
        Map<LocalDate, Long> sampleCountByDay = new HashMap<>();
        try {
            List<Object[]> rows = projectId == null
                    ? rawLogEventRepository.findTraceDurationPercentilesByDay(startTime, endTime, TRACE_MAX_DURATION_SEC)
                    : rawLogEventRepository.findTraceDurationPercentilesByDayAndProject(startTime, endTime, projectId, TRACE_MAX_DURATION_SEC);
            for (Object[] row : rows) {
                LocalDate day = toLocalDate(row[0]);
                if (day == null) {
                    continue;
                }
                Quantiles q = new Quantiles(toDouble(row[1]), toDouble(row[2]), toDouble(row[3]));
                quantilesByDay.put(day, q);
                sampleCountByDay.put(day, toLong(row.length > 4 ? row[4] : null));
            }
        } catch (Exception e) {
            log.error("Failed to load day trace distribution", e);
        }

        List<String> labels = new java.util.ArrayList<>(safeDays);
        List<Double> p50 = new java.util.ArrayList<>(safeDays);
        List<Double> p95 = new java.util.ArrayList<>(safeDays);
        List<Double> p99 = new java.util.ArrayList<>(safeDays);
        List<Long> sampleCount = new java.util.ArrayList<>(safeDays);

        for (int i = 0; i < safeDays; i++) {
            LocalDate day = startDate.plusDays(i);
            labels.add(day.toString());
            Quantiles q = quantilesByDay.get(day);
            p50.add(q == null ? null : q.p50());
            p95.add(q == null ? null : q.p95());
            p99.add(q == null ? null : q.p99());
            sampleCount.add(sampleCountByDay.getOrDefault(day, 0L));
        }

        return TraceDistributionResponse.builder()
                .labels(labels)
                .p50(p50)
                .p95(p95)
                .p99(p99)
                .sampleCount(sampleCount)
                .build();
    }

    private static LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (Exception ignore) {
            return null;
        }
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.withSecond(0).withNano(0);
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().withSecond(0).withNano(0);
        }
        if (value instanceof LocalDate localDate) {
            return localDate.atStartOfDay();
        }
        try {
            return LocalDateTime.parse(String.valueOf(value)).withSecond(0).withNano(0);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal.doubleValue();
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignore) {
            return null;
        }
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignore) {
            return 0L;
        }
    }

    private record Quantiles(Double p50, Double p95, Double p99) {
    }
}
