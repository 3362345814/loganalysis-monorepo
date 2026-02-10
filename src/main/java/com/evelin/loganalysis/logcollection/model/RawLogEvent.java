package com.evelin.loganalysis.logcollection.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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

    /**
     * 文件inode（用于检测文件轮转）
     */
    private String fileInode;

    /**
     * 文件最后修改时间
     */
    private LocalDateTime fileMtime;

    /**
     * 创建工厂方法
     */
    public static RawLogEvent create(UUID sourceId, String sourceName, String filePath,
                                     String rawContent, Long lineNumber, Long fileOffset,
                                     Integer byteLength, String fileInode, LocalDateTime fileMtime) {
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
                .build();
    }
}
