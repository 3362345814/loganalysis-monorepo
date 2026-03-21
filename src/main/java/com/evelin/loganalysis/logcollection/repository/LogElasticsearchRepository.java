package com.evelin.loganalysis.logcollection.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetMappingRequest;
import com.evelin.loganalysis.logcollection.dto.EsLogQueryRequest;
import com.evelin.loganalysis.logcollection.dto.EsLogSearchResponse;
import com.evelin.loganalysis.logcollection.model.LogIndexDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ES 日志 Repository
 *
 * @author Evelin
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class LogElasticsearchRepository {

    private final ElasticsearchClient esClient;

    private static final String INDEX_NAME = "loganalysis-logs";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    /**
     * 检查索引是否存在，不存在则创建
     */
    public void ensureIndexExists() throws IOException {
        boolean exists = esClient.indices().exists(ExistsRequest.of(e -> e.index(INDEX_NAME))).value();
        if (!exists) {
            esClient.indices().create(CreateIndexRequest.of(c -> c
                    .index(INDEX_NAME)
                    .mappings(m -> m
                            .properties("id", p -> p.keyword(k -> k))
                            .properties("dbId", p -> p.keyword(k -> k))
                            .properties("sourceId", p -> p.keyword(k -> k))
                            .properties("sourceName", p -> p.text(t -> t))
                            .properties("filePath", p -> p.text(t -> t))
                            .properties("rawContent", p -> p.text(t -> t.analyzer("standard")
                                    .fields("keyword", f -> f.keyword(kw -> kw.ignoreAbove(256)))))
                            .properties("desensitizedContent", p -> p.text(t -> t))
                            .properties("lineNumber", p -> p.long_(l -> l))
                            .properties("collectionTime", p -> p.date(d -> d.format("yyyy-MM-dd'T'HH:mm:ss||epoch_millis")))
                            .properties("originalLogTime", p -> p.date(d -> d.format("yyyy-MM-dd'T'HH:mm:ss||epoch_millis")))
                            .properties("logLevel", p -> p.keyword(k -> k))
                            .properties("logType", p -> p.keyword(k -> k))
                            .properties("traceId", p -> p.keyword(k -> k))
                            .properties("aggregationGroupId", p -> p.keyword(k -> k))
                            .properties("parsedFields", p -> p.object(o -> o.enabled(true)))
                    )
            ));
            log.info("Created ES index: {}", INDEX_NAME);
        }
    }

    /**
     * 索引单条日志文档
     */
    public void indexDocument(LogIndexDocument document) throws IOException {
        ensureIndexExists();
        IndexResponse response = esClient.index(IndexRequest.of(i -> i
                .index(INDEX_NAME)
                .id(document.getId())
                .document(document)
        ));
        log.debug("Indexed document: {}, result: {}", document.getId(), response.result());
    }

    /**
     * 批量索引日志文档
     */
    public BulkResponse bulkIndex(List<LogIndexDocument> documents) throws IOException {
        if (documents == null || documents.isEmpty()) {
            return null;
        }
        ensureIndexExists();

        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
        for (LogIndexDocument doc : documents) {
            bulkBuilder.operations(op -> op
                    .index(idx -> idx
                            .index(INDEX_NAME)
                            .id(doc.getId())
                            .document(doc)
                    )
            );
        }

        BulkResponse response = esClient.bulk(bulkBuilder.build());
        if (response.errors()) {
            log.error("Bulk indexing had errors");
            response.items().forEach(item -> {
                if (item.error() != null) {
                    log.error("Error indexing document {}: {}", item.id(), item.error().reason());
                }
            });
        } else {
            log.info("Bulk indexed {} documents", documents.size());
        }
        return response;
    }

    /**
     * 删除指定日志源的所有索引文档
     */
    public DeleteByQueryResponse deleteBySourceId(UUID sourceId) throws IOException {
        DeleteByQueryResponse response = esClient.deleteByQuery(DeleteByQueryRequest.of(d -> d
                .index(INDEX_NAME)
                .query(Query.of(q -> q.term(t -> t.field("sourceId").value(sourceId.toString()))))
        ));
        log.info("Deleted {} documents for sourceId: {}", response.deleted(), sourceId);
        return response;
    }

    /**
     * 删除指定索引
     */
    public void deleteIndex() throws IOException {
        boolean exists = esClient.indices().exists(ExistsRequest.of(e -> e.index(INDEX_NAME))).value();
        if (exists) {
            esClient.indices().delete(DeleteIndexRequest.of(d -> d.index(INDEX_NAME)));
            log.info("Deleted ES index: {}", INDEX_NAME);
        }
    }

    /**
     * 获取索引文档数量
     */
    public long getDocumentCount() throws IOException {
        CountResponse response = esClient.count(CountRequest.of(c -> c.index(INDEX_NAME)));
        return response.count();
    }

    /**
     * 获取索引映射
     */
    public Map<String, String> getIndexMapping() throws IOException {
        var response = esClient.indices().getMapping(GetMappingRequest.of(g -> g.index(INDEX_NAME)));
        Map<String, String> result = new LinkedHashMap<>();
        response.result().forEach((name, mapping) -> result.put(name, mapping.toString()));
        return result;
    }

    // ==================== 高级搜索方法 ====================

    /**
     * 高级搜索 - 支持全文搜索、正则、过滤、聚合
     */
    public EsLogSearchResponse advancedSearch(EsLogQueryRequest request) throws IOException {
        ensureIndexExists();

        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        // 1. 关键字全文搜索
        if (StringUtils.hasText(request.getKeyword())) {
            boolQuery.must(Query.of(q -> q
                    .match(m -> m
                            .field("rawContent")
                            .query(request.getKeyword())
                    )
            ));
        }

        // 2. 正则表达式匹配
        if (StringUtils.hasText(request.getRegex())) {
            boolQuery.must(Query.of(q -> q
                    .regexp(r -> r
                            .field("rawContent.keyword")
                            .value(request.getRegex())
                    )
            ));
        }

        // 3. 日志源过滤
        if (request.getSourceId() != null) {
            boolQuery.filter(Query.of(q -> q
                    .term(t -> t.field("sourceId").value(request.getSourceId().toString()))
            ));
        }

        // 4. 日志级别过滤
        if (request.getLogLevels() != null && !request.getLogLevels().isEmpty()) {
            List<FieldValue> levelValues = request.getLogLevels().stream()
                    .map(FieldValue::of)
                    .collect(Collectors.toList());
            boolQuery.filter(Query.of(q -> q
                    .terms(t -> t.field("logLevel").terms(terms -> terms.value(levelValues)))
            ));
        }

        // 5. 时间范围过滤 (基于原始日志时间)
        if (request.getStartTime() != null || request.getEndTime() != null) {
            boolQuery.filter(Query.of(q -> q
                    .range(r -> {
                        r.field("originalLogTime");
                        if (request.getStartTime() != null) {
                            r.gte(co.elastic.clients.json.JsonData.of(
                                    DATE_FORMATTER.format(request.getStartTime())));
                        }
                        if (request.getEndTime() != null) {
                            r.lte(co.elastic.clients.json.JsonData.of(
                                    DATE_FORMATTER.format(request.getEndTime())));
                        }
                        return r;
                    })
            ));
        }

        // 6. 文件路径模糊匹配
        if (StringUtils.hasText(request.getFilePath())) {
            boolQuery.filter(Query.of(q -> q
                    .match(m -> m.field("filePath").query(request.getFilePath()))
            ));
        }

        // 7. traceId 精确匹配
        if (StringUtils.hasText(request.getTraceId())) {
            boolQuery.filter(Query.of(q -> q
                    .term(t -> t.field("traceId").value(request.getTraceId()))
            ));
        }

        // 8. 聚合组过滤
        if (StringUtils.hasText(request.getAggregationGroupId())) {
            boolQuery.filter(Query.of(q -> q
                    .term(t -> t.field("aggregationGroupId").value(request.getAggregationGroupId()))
            ));
        }

        // 构建搜索请求
        SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                .index(INDEX_NAME)
                .query(Query.of(q -> q.bool(boolQuery.build())))
                .from(request.getPage() * request.getSize())
                .size(request.getSize());

        // 排序
        String sortField = StringUtils.hasText(request.getSortField()) ? request.getSortField() : "originalLogTime";
        SortOrder sortOrder = "asc".equalsIgnoreCase(request.getSortOrder()) ? SortOrder.Asc : SortOrder.Desc;

        // 打印查询参数日志
        log.info("=== ES 查询参数 ===");
        log.info("sourceId: {}, logLevels: {}, keyword: {}, regex: {}",
                request.getSourceId(), request.getLogLevels(), request.getKeyword(), request.getRegex());
        log.info("startTime: {}, endTime: {}, sortField: {}, sortOrder: {}",
                request.getStartTime(), request.getEndTime(), sortField, sortOrder);
        log.info("page: {}, size: {}", request.getPage(), request.getSize());
        log.info("aggregationField: {}, timeInterval: {}",
                request.getAggregationField(), request.getTimeInterval());
        log.info("filePath: {}, traceId: {}, aggregationGroupId: {}",
                request.getFilePath(), request.getTraceId(), request.getAggregationGroupId());

        // 打印构建的查询 JSON（用于调试）
        try {
            // 构建查询条件对象并打印
            Query query = Query.of(q -> q.bool(boolQuery.build()));
            // 直接使用 ES 的 JSON 序列化方式
            String queryJson = co.elastic.clients.json.JsonData.of(query.toString()).toString();
            log.info("=== ES 查询条件 ===\n{}", query);
        } catch (Exception e) {
            log.warn("无法将查询转换为 JSON: {}", e.getMessage());
        }

        searchBuilder.sort(s -> s.field(f -> f.field(sortField).order(sortOrder)));

        // 高亮
        if (request.isHighlight() && StringUtils.hasText(request.getKeyword())) {
            searchBuilder.highlight(h -> h
                    .fields("rawContent", HighlightField.of(hf -> hf
                            .preTags("<em>")
                            .postTags("</em>")
                            .fragmentSize(200)
                            .numberOfFragments(3)
                    ))
            );
        }

        // 聚合
        if (StringUtils.hasText(request.getAggregationField())) {
            String aggField = request.getAggregationField();
            if ("logLevel".equals(aggField) || "sourceName".equals(aggField) || "logType".equals(aggField)) {
                searchBuilder.aggregations("field_agg", Aggregation.of(a -> a
                        .terms(t -> t.field(aggField).size(20))
                ));
            }
        }

        // 时间直方图聚合
        if (StringUtils.hasText(request.getTimeInterval())) {
            String calendarInterval = switch (request.getTimeInterval().toLowerCase()) {
                case "minute" -> "minute";
                case "hour" -> "hour";
                case "day" -> "day";
                default -> "hour";
            };
            searchBuilder.aggregations("time_histogram", Aggregation.of(a -> a
                    .dateHistogram(dh -> dh
                            .field("originalLogTime")
                            .calendarInterval(co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval.valueOf(
                                    calendarInterval.substring(0, 1).toUpperCase() + calendarInterval.substring(1)))
                            .format("yyyy-MM-dd HH:mm")
                            .minDocCount(0)
                    )
            ));
        }

        // 执行搜索
        SearchResponse<LogIndexDocument> response = esClient.search(searchBuilder.build(), LogIndexDocument.class);

        // 打印搜索结果统计
        log.info("=== ES 查询结果 ===");
        log.info("总命中数: {}, 耗时: {}ms, 返回条数: {}",
                response.hits().total() != null ? response.hits().total().value() : 0,
                response.took(),
                response.hits().hits().size());

        // 打印前几条命中的 logLevel 分布
        if (response.hits().hits().size() > 0) {
            Map<String, Long> levelCount = new java.util.HashMap<>();
            for (Hit<LogIndexDocument> hit : response.hits().hits()) {
                LogIndexDocument doc = hit.source();
                if (doc != null && doc.getLogLevel() != null) {
                    levelCount.merge(doc.getLogLevel(), 1L, Long::sum);
                }
            }
            log.info("本次返回的 logLevel 分布: {}", levelCount);
        }

        // 构建响应
        List<EsLogSearchResponse.LogHit> hits = new ArrayList<>();
        for (Hit<LogIndexDocument> hit : response.hits().hits()) {
            LogIndexDocument doc = hit.source();
            if (doc != null) {
                Map<String, List<String>> highlight = new HashMap<>();
                if (hit.highlight() != null) {
                    hit.highlight().forEach(highlight::put);
                }

                EsLogSearchResponse.LogHit logHit = EsLogSearchResponse.LogHit.builder()
                        .id(doc.getId())
                        .dbId(doc.getDbId())
                        .sourceId(doc.getSourceId() != null ? doc.getSourceId().toString() : null)
                        .sourceName(doc.getSourceName())
                        .filePath(doc.getFilePath())
                        .rawContent(doc.getRawContent())
                        .desensitizedContent(doc.getDesensitizedContent())
                        .lineNumber(doc.getLineNumber())
                        .collectionTime(doc.getCollectionTime() != null ?
                                DATE_FORMATTER.format(doc.getCollectionTime()) : null)
                        .originalLogTime(doc.getOriginalLogTime() != null ?
                                DATE_FORMATTER.format(doc.getOriginalLogTime()) : null)
                        .logLevel(doc.getLogLevel())
                        .logType(doc.getLogType())
                        .traceId(doc.getTraceId())
                        .aggregationGroupId(doc.getAggregationGroupId())
                        .parsedFields(doc.getParsedFields())
                        .highlight(highlight)
                        .score(hit.score() != null ? hit.score().floatValue() : null)
                        .build();
                hits.add(logHit);
            }
        }

        // 聚合结果
        Map<String, Object> aggregations = new HashMap<>();
        if (response.aggregations() != null) {
            if (response.aggregations().containsKey("field_agg")) {
                var fieldAgg = response.aggregations().get("field_agg").sterms();
                List<EsLogSearchResponse.AggregationBucket> buckets = new ArrayList<>();
                for (StringTermsBucket bucket : fieldAgg.buckets().array()) {
                    buckets.add(EsLogSearchResponse.AggregationBucket.builder()
                            .key(bucket.key().stringValue())
                            .docCount(bucket.docCount())
                            .build());
                }
                aggregations.put("field_agg", buckets);
            }

            if (response.aggregations().containsKey("time_histogram")) {
                var timeAgg = response.aggregations().get("time_histogram").dateHistogram();
                List<EsLogSearchResponse.AggregationBucket> buckets = new ArrayList<>();
                for (DateHistogramBucket bucket : timeAgg.buckets().array()) {
                    buckets.add(EsLogSearchResponse.AggregationBucket.builder()
                            .key(bucket.keyAsString())
                            .docCount(bucket.docCount())
                            .build());
                }
                aggregations.put("time_histogram", buckets);
            }
        }

        long total = response.hits().total() != null ? response.hits().total().value() : 0;
        int totalPages = (int) Math.ceil((double) total / request.getSize());

        return EsLogSearchResponse.builder()
                .total(total)
                .page(request.getPage())
                .size(request.getSize())
                .totalPages(totalPages)
                .hits(hits)
                .aggregations(aggregations)
                .took(response.took())
                .build();
    }

    /**
     * 按日志级别聚合统计
     */
    public Map<String, Long> aggregateByLevel() throws IOException {
        ensureIndexExists();
        SearchResponse<Void> response = esClient.search(SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .size(0)
                .aggregations("levels", Aggregation.of(a -> a
                        .terms(t -> t.field("logLevel").size(10))
                ))
        ), Void.class);

        Map<String, Long> result = new LinkedHashMap<>();
        if (response.aggregations() != null && response.aggregations().containsKey("levels")) {
            var termsAgg = response.aggregations().get("levels").sterms();
            for (StringTermsBucket bucket : termsAgg.buckets().array()) {
                result.put(bucket.key().stringValue(), bucket.docCount());
            }
        }
        return result;
    }

    /**
     * 按日志源聚合统计
     */
    public Map<String, Long> aggregateBySource() throws IOException {
        ensureIndexExists();
        SearchResponse<Void> response = esClient.search(SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .size(0)
                .aggregations("sources", Aggregation.of(a -> a
                        .terms(t -> t.field("sourceName").size(20))
                ))
        ), Void.class);

        Map<String, Long> result = new LinkedHashMap<>();
        if (response.aggregations() != null && response.aggregations().containsKey("sources")) {
            var termsAgg = response.aggregations().get("sources").sterms();
            for (StringTermsBucket bucket : termsAgg.buckets().array()) {
                result.put(bucket.key().stringValue(), bucket.docCount());
            }
        }
        return result;
    }

    /**
     * 按时间直方图聚合
     */
    public List<EsLogSearchResponse.AggregationBucket> aggregateByTimeHistogram(String interval) throws IOException {
        ensureIndexExists();
        String calendarInterval = switch (interval.toLowerCase()) {
            case "minute" -> "minute";
            case "hour" -> "hour";
            case "day" -> "day";
            default -> "hour";
        };

        SearchResponse<Void> response = esClient.search(SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .size(0)
                .aggregations("time_histogram", Aggregation.of(a -> a
                        .dateHistogram(dh -> dh
                                .field("originalLogTime")
                                .calendarInterval(co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval.valueOf(
                                        calendarInterval.substring(0, 1).toUpperCase() + calendarInterval.substring(1)))
                                .format("yyyy-MM-dd HH:mm")
                                .minDocCount(0)
                        )
                ))
        ), Void.class);

        List<EsLogSearchResponse.AggregationBucket> result = new ArrayList<>();
        if (response.aggregations() != null && response.aggregations().containsKey("time_histogram")) {
            var dateAgg = response.aggregations().get("time_histogram").dateHistogram();
            for (DateHistogramBucket bucket : dateAgg.buckets().array()) {
                result.add(EsLogSearchResponse.AggregationBucket.builder()
                        .key(bucket.keyAsString())
                        .docCount(bucket.docCount())
                        .build());
            }
        }
        return result;
    }
}
