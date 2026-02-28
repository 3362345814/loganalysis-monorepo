package com.evelin.loganalysis.logcollection.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 存储策略配置
 * 实现数据的分级存储：
 * - 热数据：内存缓冲区 + PostgreSQL 最近的数据
 * - 温数据：PostgreSQL 历史数据（可配置保留期）
 * - 冷数据：MinIO 对象存储（定期归档）
 *
 * @author Evelin
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "storage.strategy")
public class StorageStrategyConfig {

    /**
     * 是否启用分级存储
     */
    private boolean enabled = true;

    /**
     * 热数据保留天数（超过此天数的数据会被归档）
     */
    private int hotDataRetentionDays = 7;

    /**
     * 温数据保留天数（超过此天数的数据会被删除）
     */
    private int warmDataRetentionDays = 30;

    /**
     * 冷存储类型: local / minio
     */
    private String coldStorageType = "minio";

    /**
     * MinIO Bucket 名称（冷存储类型为 minio 时生效）
     */
    private String bucket = "log-archive";

    /**
     * 归档目录（本地存储时使用，冷存储类型为 local 时生效）
     */
    private String archivePath = "./logs/archive";

    /**
     * 归档文件最大大小（MB），超过则分卷
     */
    private int archiveMaxFileSizeMb = 100;

    /**
     * 是否压缩归档文件
     */
    private boolean compressArchive = true;

    /**
     * 归档任务执行间隔（小时）
     */
    private int archiveScheduleHours = 24;

    /**
     * 是否在归档后删除原数据
     */
    private boolean deleteAfterArchive = true;
}
