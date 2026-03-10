package com.evelin.loganalysis.logcollection.dto;

import com.evelin.loganalysis.logcollection.model.entity.RawLogEventEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 原始日志事件响应DTO
 *
 * @author Evelin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawLogEventResponse {

    /**
     * 日志ID
     */
    private String id;

    /**
     * 事件ID
     */
    private String eventId;

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
     * 日志内容（rawContent的别名，供前端使用）
     */
    private String message;

    /**
     * 原始日志内容
     */
    private String rawContent;

    /**
     * 日志行号
     */
    private Long lineNumber;

    /**
     * 采集时间
     */
    private LocalDateTime collectionTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 从实体转换为响应DTO
     *
     * @param entity 实体
     * @return 响应DTO
     */
    public static RawLogEventResponse fromEntity(RawLogEventEntity entity) {
        if (entity == null) {
            return null;
        }
        return RawLogEventResponse.builder()
                .id(entity.getId() != null ? entity.getId().toString() : null)
                .eventId(entity.getEventId())
                .sourceId(entity.getSourceId() != null ? entity.getSourceId().toString() : null)
                .sourceName(entity.getSourceName())
                .filePath(entity.getFilePath())
                .message(entity.getRawContent())
                .rawContent(entity.getRawContent())
                .lineNumber(entity.getLineNumber())
                .collectionTime(entity.getCollectionTime())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
