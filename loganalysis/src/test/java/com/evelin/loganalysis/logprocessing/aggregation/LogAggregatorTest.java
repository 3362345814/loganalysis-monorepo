package com.evelin.loganalysis.logprocessing.aggregation;

import com.evelin.loganalysis.logprocessing.config.ProcessingConfig;
import com.evelin.loganalysis.logprocessing.dto.AggregationResult;
import com.evelin.loganalysis.logprocessing.dto.ParsedLogEvent;
import com.evelin.loganalysis.logprocessing.entity.AggregationGroupEntity;
import com.evelin.loganalysis.logprocessing.service.AggregationGroupService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class LogAggregatorTest {

    @Test
    void shouldNotMergeDifferentBusinessMessagesWhenThresholdIsLow() {
        InMemoryAggregationGroupService fakeService = new InMemoryAggregationGroupService();
        LogAggregator aggregator = createAggregator(0.80, fakeService);

        ParsedLogEvent invalidUser = event(
                "2026-04-13 17:17:49 [http-nio-8080-exec-2] traceId=c475013a52e34f5a ERROR com.example.nginxlog.ApiController - Invalid user id: -1",
                "ERROR"
        );
        ParsedLogEvent deletingUser = event(
                "2026-04-14 03:04:13 [http-nio-8080-exec-7] traceId=d72bae7901d94ebf WARN com.example.nginxlog.ApiController - Deleting user with id: 1",
                "WARN"
        );

        AggregationResult r1 = aggregator.aggregate(invalidUser, "source-1", "api");
        AggregationResult r2 = aggregator.aggregate(deletingUser, "source-1", "api");

        assertNotNull(r1);
        assertNotNull(r2);
        assertTrue(r1.isNewGroup());
        assertTrue(r2.isNewGroup());
        assertNotEquals(r1.getGroupId(), r2.getGroupId());
    }

    @Test
    void shouldMergeSameBusinessMessageWithDifferentHeaderValues() {
        InMemoryAggregationGroupService fakeService = new InMemoryAggregationGroupService();
        LogAggregator aggregator = createAggregator(0.85, fakeService);

        ParsedLogEvent first = event(
                "2026-04-13 17:17:39 [http-nio-8080-exec-8] traceId=da3b46d651084041 ERROR com.example.nginxlog.ApiController - Invalid user id: -1",
                "ERROR"
        );
        ParsedLogEvent second = event(
                "2026-04-14 03:04:13 [http-nio-8080-exec-10] traceId=9b25c76c6b42417c ERROR com.example.nginxlog.ApiController - Invalid user id: -1",
                "ERROR"
        );

        AggregationResult r1 = aggregator.aggregate(first, "source-1", "api");
        AggregationResult r2 = aggregator.aggregate(second, "source-1", "api");

        assertNotNull(r1);
        assertNotNull(r2);
        assertTrue(r1.isNewGroup());
        assertFalse(r2.isNewGroup());
        assertEquals(r1.getGroupId(), r2.getGroupId());
    }

    @Test
    void shouldKeepSameGroupAcrossAggregatorRestartByTemplateFingerprint() {
        InMemoryAggregationGroupService fakeService = new InMemoryAggregationGroupService();

        LogAggregator aggregator1 = createAggregator(0.85, fakeService);
        AggregationResult first = aggregator1.aggregate(
                event("Invalid user id: -1", "ERROR"),
                "source-1",
                "api"
        );

        // 模拟服务重启：新建一个聚合器实例（内存 activeGroups 清空）
        LogAggregator aggregator2 = createAggregator(0.85, fakeService);
        AggregationResult second = aggregator2.aggregate(
                event("Invalid user id: -2", "ERROR"),
                "source-1",
                "api"
        );

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first.getGroupId(), second.getGroupId());
        assertFalse(second.isNewGroup());
        assertEquals(2, second.getEventCount());
    }

    @Test
    void shouldMergeBusinessMessagesWithDifferentOrderAndPaymentIds() {
        InMemoryAggregationGroupService fakeService = new InMemoryAggregationGroupService();
        LogAggregator aggregator = createAggregator(0.85, fakeService);

        AggregationResult first = aggregator.aggregate(
                event("payment rejected paymentNo=PM78544841D2 orderNo=OBD22478171 reason=OUTCOME_FAILED", "ERROR"),
                "source-1",
                "payment"
        );
        AggregationResult second = aggregator.aggregate(
                event("payment rejected paymentNo=PMCF28527656 orderNo=OE2F3D2DCB6 reason=OUTCOME_FAILED", "ERROR"),
                "source-1",
                "payment"
        );

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first.getGroupId(), second.getGroupId());
        assertFalse(second.isNewGroup());
        assertEquals(2, second.getEventCount());
    }

    private LogAggregator createAggregator(double threshold, AggregationGroupService groupService) {
        ProcessingConfig config = new ProcessingConfig();
        config.setSimilarityThreshold(threshold);
        return new LogAggregator(config, groupService);
    }

    private ParsedLogEvent event(String message, String level) {
        return ParsedLogEvent.builder()
                .message(message)
                .logLevel(level)
                .logTime(LocalDateTime.now())
                .build();
    }

    /**
     * 轻量内存版聚合组服务，避免依赖真实数据库
     */
    static class InMemoryAggregationGroupService extends AggregationGroupService {
        private final Map<String, AggregationGroupEntity> groups = new ConcurrentHashMap<>();

        InMemoryAggregationGroupService() {
            super(null, null, null, null);
        }

        @Override
        public AggregationGroupEntity saveOrUpdate(AggregationResult aggregationResult, String sourceId, String sourceName) {
            AggregationGroupEntity entity = groups.get(aggregationResult.getGroupId());
            if (entity == null) {
                entity = new AggregationGroupEntity();
                entity.setId(aggregationResult.getAggregationId());
                entity.setGroupId(aggregationResult.getGroupId());
                entity.setSourceId(sourceId);
                entity.setSourceName(sourceName);
                entity.setRepresentativeLog(aggregationResult.getRepresentativeLog());
                entity.setFirstEventTime(aggregationResult.getAggregatedAt());
            }

            entity.setEventCount(aggregationResult.getEventCount());
            entity.setLastEventTime(aggregationResult.getAggregatedAt());
            entity.setSeverity(aggregationResult.getSeverity());
            entity.setSimilarityScore(aggregationResult.getSimilarityScore());
            entity.setStatus("ACTIVE");
            groups.put(entity.getGroupId(), entity);
            return entity;
        }

        @Override
        public Optional<AggregationGroupEntity> findByGroupId(String groupId) {
            return Optional.ofNullable(groups.get(groupId));
        }

        @Override
        public Optional<AggregationGroupEntity> findLatestBySourceIdAndRepresentativeLog(String sourceId, String representativeLog) {
            return groups.values().stream()
                    .filter(g -> sourceId.equals(g.getSourceId()) && representativeLog.equals(g.getRepresentativeLog()))
                    .max((a, b) -> {
                        LocalDateTime ta = a.getLastEventTime();
                        LocalDateTime tb = b.getLastEventTime();
                        if (ta == null && tb == null) {
                            return 0;
                        }
                        if (ta == null) {
                            return -1;
                        }
                        if (tb == null) {
                            return 1;
                        }
                        return ta.compareTo(tb);
                    });
        }

        @Override
        public Optional<AggregationGroupEntity> findBestSimilarGroup(String sourceId, String message, double threshold) {
            return groups.values().stream()
                    .filter(g -> sourceId.equals(g.getSourceId()))
                    .filter(g -> LogTemplateUtils.calculateSimilarity(message, g.getRepresentativeLog()) >= threshold)
                    .findFirst();
        }
    }
}
