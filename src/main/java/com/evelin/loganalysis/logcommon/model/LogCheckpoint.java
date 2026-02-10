package com.evelin.loganalysis.logcommon.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 检查点实体
 *
 * @author Evelin
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "checkpoints")
public class LogCheckpoint extends BaseEntity {

    /**
     * 日志源ID
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private LogSource sourceId;

    /**
     * 文件路径
     */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    /**
     * 文件偏移量
     */
    @Column(name = "file_offset", nullable = false)
    @Builder.Default
    private Long fileOffset = 0L;

    /**
     * 文件大小
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * 文件inode
     */
    @Column(name = "file_inode", length = 128)
    private String fileInode;

    /**
     * 文件修改时间
     */
    @Column(name = "file_mtime")
    private LocalDateTime fileMtime;
}
