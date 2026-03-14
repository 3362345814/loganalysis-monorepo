package com.evelin.loganalysis.logcollection.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 原始日志事件
 *
 * 采集器采集到的原始日志数据，包含采集过程中的元信息
 *
 * @author Evelin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawLogEvent {

    /**
     * 事件唯一ID
     */
    private String eventId;

    /**
     * 日志源ID
     */
    private UUID sourceId;

    /**
     * 日志源名称
     */
    private String sourceName;

    /**
     * 日志格式类型
     */
    private String logFormat;

    /**
     * 用户自定义的 log_format 字符串（用于 NGINX_ACCESS 等格式）
     * 例如: $remote_addr - $remote_user [$time_local] "$request" $status $body_bytes_sent
     */
    private String logFormatPattern;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 原始日志内容
     */
    private String rawContent;

    /**
     * 日志行号
     */
    private Long lineNumber;

    /**
     * 文件偏移量
     */
    private Long fileOffset;

    /**
     * 字节长度
     */
    private Integer byteLength;

    /**
     * 采集时间
     */
    private LocalDateTime collectionTime;
    private LocalDateTime originalLogTime;  // 日志原始生成时间（从日志内容中提取）
    private String logLevel;  // 日志级别（从解析结果中提取）

    /**
     * 文件inode（用于检测文件轮转）
     */
    private String fileInode;

    /**
     * 文件最后修改时间
     */
    private LocalDateTime fileMtime;

    /**
     * 脱敏后的内容
     */
    private String desensitizedContent;

    /**
     * 是否已脱敏
     */
    private Boolean masked;
    private String logType;  // 日志类型标识: 如 "error", "access" 等，从配置文件中读取

    /**
     * 解析后的字段（JSON格式）
     */
    @JsonProperty("parsedFields")
    private Map<String, Object> parsedFields;

    /**
     * 关联的聚合组ID
     */
    private String aggregationGroupId;

    /**
     * 创建工厂方法
     */
    public static RawLogEvent create(UUID sourceId, String sourceName, String filePath,
                                     String rawContent, Long lineNumber, Long fileOffset,
                                     Integer byteLength, String fileInode, LocalDateTime fileMtime,
                                     String logFormat) {
        return create(sourceId, sourceName, filePath, rawContent, lineNumber, fileOffset,
                byteLength, fileInode, fileMtime, logFormat, null);
    }

    /**
     * 创建工厂方法（支持 logFormatPattern）
     */
    public static RawLogEvent create(UUID sourceId, String sourceName, String filePath,
                                     String rawContent, Long lineNumber, Long fileOffset,
                                     Integer byteLength, String fileInode, LocalDateTime fileMtime,
                                     String logFormat, String logFormatPattern) {
        return RawLogEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sourceId(sourceId)
                .sourceName(sourceName)
                .filePath(filePath)
                .rawContent(rawContent)
                .lineNumber(lineNumber)
                .fileOffset(fileOffset)
                .byteLength(byteLength)
                .collectionTime(LocalDateTime.now())
                .fileInode(fileInode)
                .fileMtime(fileMtime)
                .logFormat(logFormat)
                .logFormatPattern(logFormatPattern)
                .build();
    }

    /**
     * 创建工厂方法（支持 logFormatPattern 和 logType）
     */
    public static RawLogEvent create(UUID sourceId, String sourceName, String filePath,
                                     String rawContent, Long lineNumber, Long fileOffset,
                                     Integer byteLength, String fileInode, LocalDateTime fileMtime,
                                     String logFormat, String logFormatPattern, String logType) {
        return RawLogEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sourceId(sourceId)
                .sourceName(sourceName)
                .filePath(filePath)
                .rawContent(rawContent)
                .lineNumber(lineNumber)
                .fileOffset(fileOffset)
                .byteLength(byteLength)
                .collectionTime(LocalDateTime.now())
                .fileInode(fileInode)
                .fileMtime(fileMtime)
                .logFormat(logFormat)
                .logFormatPattern(logFormatPattern)
                .logType(logType)
                .build();
    }
}
