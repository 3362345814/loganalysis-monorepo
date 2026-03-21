package com.evelin.loganalysis.logcollection.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * ES 日志搜索响应 DTO
 *
 * @author Evelin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EsLogSearchResponse {

    /**
     * 总记录数
     */
    private long total;

    /**
     * 当前页码
     */
    private int page;

    /**
     * 每页大小
     */
    private int size;

    /**
     * 总页数
     */
    private int totalPages;

    /**
     * 日志文档列表
     */
    private List<LogHit> hits;

    /**
     * 聚合结果
     */
    private Map<String, Object> aggregations;

    /**
     * 查询耗时 (毫秒)
     */
    private long took;

    /**
     * 日志命中
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogHit {

        /**
         * 文档ID
         */
        private String id;

        /**
         * 数据库ID
         */
        private String dbId;

        /**
         * 日志源ID
         */
        private String sourceId;

        /**
         * 日志源名称
         */
        private String sourceName;

        /**
         * 文件路径
         */
        private String filePath;

        /**
         * 原始日志内容
         */
        private String rawContent;

        /**
         * 脱敏内容
         */
        private String desensitizedContent;

        /**
         * 日志行号
         */
        private Long lineNumber;

        /**
         * 采集时间
         */
        private String collectionTime;

        /**
         * 日志原始时间
         */
        private String originalLogTime;

        /**
         * 日志级别
         */
        private String logLevel;

        /**
         * 日志类型
         */
        private String logType;

        /**
         * traceId
         */
        private String traceId;

        /**
         * 聚合组ID
         */
        private String aggregationGroupId;

        /**
         * 解析后的字段
         */
        private Map<String, Object> parsedFields;

        /**
         * 高亮内容
         */
        private Map<String, List<String>> highlight;

        /**
         * 相关性评分
         */
        private Float score;
    }

    /**
     * 聚合桶
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AggregationBucket {

        /**
         * 桶的 key
         */
        private String key;

        /**
         * 文档数量
         */
        private long docCount;

        /**
         * 子聚合
         */
        private Map<String, Object> subAggregations;
    }
}
