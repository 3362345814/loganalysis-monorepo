package com.evelin.loganalysis.logcollection.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 采集检查点
 *
 * 记录采集器在某个文件上的采集位置，用于断点续采
 *
 * @author Evelin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionCheckpoint {

    /**
     * 日志源ID
     */
    private String sourceId;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 文件偏移量（字节位置）
     */
    private Long offset;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件inode
     */
    private String fileInode;

    /**
     * 文件最后修改时间
     */
    private LocalDateTime fileMtime;

    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdateTime;

    /**
     * 创建工厂方法
     */
    public static CollectionCheckpoint of(String sourceId, String filePath, Long offset,
                                          Long fileSize, String fileInode, LocalDateTime fileMtime) {
        return CollectionCheckpoint.builder()
                .sourceId(sourceId)
                .filePath(filePath)
                .offset(offset)
                .fileSize(fileSize)
                .fileInode(fileInode)
                .fileMtime(fileMtime)
                .lastUpdateTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建空检查点
     */
    public static CollectionCheckpoint empty(String sourceId, String filePath) {
        return CollectionCheckpoint.builder()
                .sourceId(sourceId)
                .filePath(filePath)
                .offset(0L)
                .fileSize(0L)
                .build();
    }
}
