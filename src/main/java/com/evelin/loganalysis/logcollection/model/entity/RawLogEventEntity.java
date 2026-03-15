package com.evelin.loganalysis.logcollection.model.entity;

import com.evelin.loganalysis.logcommon.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 原始日志事件实体
 *
 * 存储采集器采集到的原始日志数据
 *
 * @author Evelin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "raw_log_events",
    indexes = {
        @Index(name = "idx_raw_event_id", columnList = "event_id"),
        @Index(name = "idx_raw_source_id", columnList = "source_id"),
        @Index(name = "idx_raw_collection_time", columnList = "collection_time"),
        @Index(name = "idx_raw_file_path", columnList = "file_path"),
        @Index(name = "idx_raw_log_level", columnList = "log_level"),
        @Index(name = "idx_raw_log_time", columnList = "log_time"),
        @Index(name = "idx_raw_aggregation_group_id", columnList = "aggregation_group_id")
    }
)
public class RawLogEventEntity extends BaseEntity {

    /**
     * 事件ID（业务唯一标识）
     */
    @Column(name = "event_id", nullable = false, length = 50)
    private String eventId;

    /**
     * 日志源ID
     */
    @Column(name = "source_id")
    private java.util.UUID sourceId;

    /**
     * 日志源名称
     */
    @Column(name = "source_name")
    private String sourceName;

    /**
     * 文件路径
     */
    @Column(name = "file_path", length = 500)
    private String filePath;

    /**
     * 原始日志内容
     */
    @Column(name = "raw_content", columnDefinition = "TEXT", nullable = false)
    private String rawContent;

    /**
     * 日志行号
     */
    @Column(name = "line_number")
    private Long lineNumber;

    /**
     * 文件偏移量
     */
    @Column(name = "file_offset")
    private Long fileOffset;

    /**
     * 字节长度
     */
    @Column(name = "byte_length")
    private Integer byteLength;

    /**
     * 采集时间
     */
    @Column(name = "collection_time", nullable = false)
    private LocalDateTime collectionTime;

    /**
     * 日志原始生成时间（从日志内容中提取）
     */
    @Column(name = "original_log_time")
    private LocalDateTime originalLogTime;

    /**
     * 文件inode（用于检测文件轮转）
     */
    @Column(name = "file_inode", length = 255)
    private String fileInode;

    /**
     * 日志类型标识（如 error, access），从配置文件中读取
     */
    @Column(name = "log_type", length = 50)
    private String logType;

    /**
     * 文件最后修改时间
     */
    @Column(name = "file_mtime")
    private LocalDateTime fileMtime;

    /**
     * 脱敏后的内容
     */
    @Column(name = "desensitized_content", columnDefinition = "TEXT")
    private String desensitizedContent;

    /**
     * 是否已脱敏
     */
    @Column(name = "masked")
    private Boolean masked;

    /**
     * 日志级别（从解析字段中提取，方便查询）
     */
    @Column(name = "log_level", length = 20)
    private String logLevel;

    /**
     * 日志时间（从解析字段中提取，方便查询）
     */
    @Column(name = "log_time")
    private LocalDateTime logTime;

    /**
     * 解析后的字段（JSON格式）
     * 包含: logTime, logLevel, threadName, loggerName, className, message, 
     *       exceptionType, exceptionMessage, stackTrace, traceId 等
     */
    @JsonProperty("parsedFields")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_fields", columnDefinition = "jsonb")
    private Map<String, Object> parsedFields;

    /**
     * 关联的聚合组ID
     */
    @Column(name = "aggregation_group_id", length = 50)
    private String aggregationGroupId;

    /**
     * 从 RawLogEvent DTO 创建实体
     *
     * @param dto 原始日志事件DTO
     * @return 实体
     */
    public static RawLogEventEntity from(com.evelin.loganalysis.logcollection.model.RawLogEvent dto) {
        Map<String, Object> parsedFields = dto.getParsedFields();
        
        // 优先使用 dto 中直接设置的 logLevel（从 ParsedLogEvent.getLogLevel() 获取）
        String logLevel = dto.getLogLevel();
        
        // 如果没有，则从 parsedFields 中提取
        if (logLevel == null && parsedFields != null) {
            Object level = parsedFields.get("logLevel");
            if (level != null) {
                logLevel = level.toString();
            }
        }
        
        // 同样处理 logTime
        LocalDateTime logTime = null;
        if (parsedFields != null) {
            Object time = parsedFields.get("logTime");
            if (time instanceof String) {
                try {
                    logTime = LocalDateTime.parse((String) time);
                } catch (Exception e) {
                }
            } else if (time instanceof LocalDateTime) {
                logTime = (LocalDateTime) time;
            }
        }
        
        return RawLogEventEntity.builder()
                .eventId(dto.getEventId())
                .sourceId(dto.getSourceId())
                .sourceName(dto.getSourceName())
                .filePath(dto.getFilePath())
                .rawContent(dto.getRawContent())
                .lineNumber(dto.getLineNumber())
                .fileOffset(dto.getFileOffset())
                .byteLength(dto.getByteLength())
                .collectionTime(dto.getCollectionTime())
                .originalLogTime(dto.getOriginalLogTime())
                .fileInode(dto.getFileInode())
                .fileMtime(dto.getFileMtime())
                .desensitizedContent(dto.getDesensitizedContent())
                .masked(dto.getMasked())
                .logType(dto.getLogType())
                .logLevel(logLevel)
                .logTime(logTime)
                .parsedFields(parsedFields)
                .aggregationGroupId(dto.getAggregationGroupId())
                .build();
    }
}
