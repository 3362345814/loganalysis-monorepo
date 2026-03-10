package com.evelin.loganalysis.logcollection.service;

import com.evelin.loganalysis.logcollection.config.CollectionConfig;
import com.evelin.loganalysis.logcollection.model.CollectionCheckpoint;
import com.evelin.loganalysis.logcollection.repository.CheckpointRepository;
import com.evelin.loganalysis.logcommon.model.LogCheckpoint;
import com.evelin.loganalysis.logcommon.model.LogSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 检查点管理器
 *
 * 负责检查点的加载、保存和管理
 * 采用Redis + 数据库双写策略，确保断点续采的可靠性
 *
 * @author Evelin
 */
@Slf4j
@Service
public class CheckpointManager {

    private final CheckpointRepository checkpointRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CollectionConfig config;

    /**
     * 内存缓存检查点，用于快速访问
     */
    private final ConcurrentMap<String, LogCheckpoint> memoryCache = new ConcurrentHashMap<>();

    public CheckpointManager(CheckpointRepository checkpointRepository,
                            RedisTemplate<String, Object> redisTemplate,
                            CollectionConfig config) {
        this.checkpointRepository = checkpointRepository;
        this.redisTemplate = redisTemplate;
        this.config = config;
    }

    /**
     * 加载检查点
     *
     * 优先从Redis加载，失败则从数据库加载
     *
     * @param sourceId 日志源ID
     * @param filePath 文件路径
     * @return 检查点对象
     */
    public CollectionCheckpoint load(String sourceId, String filePath) {
        String cacheKey = buildCacheKey(sourceId, filePath);

        // 1. 尝试从Redis加载
        try {
            Object redisCheckpoint = redisTemplate.opsForValue().get(cacheKey);
            if (redisCheckpoint != null) {
                log.debug("Loaded checkpoint from Redis: sourceId={}, filePath={}", sourceId, filePath);
                if (redisCheckpoint instanceof CollectionCheckpoint) {
                    return (CollectionCheckpoint) redisCheckpoint;
                }
                // 兼容旧格式的 LogCheckpoint
                return convertToCollectionCheckpoint((LogCheckpoint) redisCheckpoint);
            }
        } catch (Exception e) {
            log.warn("Failed to load checkpoint from Redis, fallback to database: {}", e.getMessage());
        }

        // 2. 从数据库加载
        try {
            LogSource source = new LogSource();
            source.setId(java.util.UUID.fromString(sourceId));
            Optional<LogCheckpoint> dbCheckpoint = checkpointRepository.findBySourceIdAndFilePath(
                    source.getId(), filePath);

            if (dbCheckpoint.isPresent()) {
                LogCheckpoint checkpoint = dbCheckpoint.get();
                // 同步到Redis
                syncToRedis(cacheKey, checkpoint);
                // 放入内存缓存
                memoryCache.put(cacheKey, checkpoint);
                log.debug("Loaded checkpoint from database: sourceId={}, filePath={}, offset={}",
                        sourceId, filePath, checkpoint.getFileOffset());
                return convertToCollectionCheckpoint(checkpoint);
            }
        } catch (Exception e) {
            log.error("Failed to load checkpoint from database: {}", e.getMessage());
        }

        // 3. 返回空检查点
        log.info("No checkpoint found, returning empty checkpoint: sourceId={}, filePath={}", sourceId, filePath);
        return CollectionCheckpoint.empty(sourceId, filePath);
    }

    /**
     * 保存检查点
     *
     * 采用双写策略：Redis（快速）+ 数据库（持久化）
     *
     * @param sourceId 日志源ID
     * @param filePath 文件路径
     * @param offset 文件偏移量
     * @param fileSize 文件大小
     * @param fileInode 文件inode
     * @param fileMtime 文件修改时间
     */
    @Transactional
    public void save(String sourceId, String filePath, Long offset,
                     Long fileSize, String fileInode, LocalDateTime fileMtime) {
        String cacheKey = buildCacheKey(sourceId, filePath);

        try {
            // 1. 获取或创建检查点
            LogCheckpoint checkpoint = memoryCache.get(cacheKey);
            if (checkpoint == null) {
                LogSource source = new LogSource();
                source.setId(java.util.UUID.fromString(sourceId));
                checkpoint = checkpointRepository.findBySourceIdAndFilePath(source.getId(), filePath)
                        .orElseGet(() -> {
                            LogCheckpoint newCheckpoint = new LogCheckpoint();
                            newCheckpoint.setSourceId(source);
                            newCheckpoint.setFilePath(filePath);
                            return newCheckpoint;
                        });
            }

            // 2. 更新检查点信息
            checkpoint.setFileOffset(offset);
            checkpoint.setFileSize(fileSize);
            checkpoint.setFileInode(fileInode);
            checkpoint.setFileMtime(fileMtime);
            checkpoint.setUpdatedAt(LocalDateTime.now());

            // 3. 同步到数据库
            checkpointRepository.save(checkpoint);
            memoryCache.put(cacheKey, checkpoint);

            // 4. 异步同步到Redis（不阻塞主流程）
            syncToRedisAsync(cacheKey, checkpoint);

            log.debug("Saved checkpoint: sourceId={}, filePath={}, offset={}", sourceId, filePath, offset);
        } catch (Exception e) {
            log.error("Failed to save checkpoint: sourceId={}, filePath={}, error={}",
                    sourceId, filePath, e.getMessage());
        }
    }

    /**
     * 删除检查点
     *
     * @param sourceId 日志源ID
     * @param filePath 文件路径
     */
    @Transactional
    public void delete(String sourceId, String filePath) {
        String cacheKey = buildCacheKey(sourceId, filePath);

        try {
            LogSource source = new LogSource();
            source.setId(java.util.UUID.fromString(sourceId));
            checkpointRepository.findBySourceIdAndFilePath(source.getId(), filePath)
                    .ifPresent(checkpointRepository::delete);

            // 从内存缓存移除
            memoryCache.remove(cacheKey);

            // 从Redis删除
            try {
                redisTemplate.delete(cacheKey);
            } catch (Exception e) {
                log.warn("Failed to delete checkpoint from Redis: {}", e.getMessage());
            }

            log.info("Deleted checkpoint: sourceId={}, filePath={}", sourceId, filePath);
        } catch (Exception e) {
            log.error("Failed to delete checkpoint: {}", e.getMessage());
        }
    }

    /**
     * 清理指定日志源的所有检查点
     *
     * @param sourceId 日志源ID
     */
    @Transactional
    public void clearBySourceId(String sourceId) {
        try {
            checkpointRepository.deleteBySourceId(java.util.UUID.fromString(sourceId));
            memoryCache.entrySet().removeIf(entry -> entry.getKey().startsWith(sourceId));
            log.info("Cleared all checkpoints for sourceId={}", sourceId);
        } catch (Exception e) {
            log.error("Failed to clear checkpoints for sourceId={}: {}", sourceId, e.getMessage());
        }
    }

    /**
     * 构建缓存Key
     */
    private String buildCacheKey(String sourceId, String filePath) {
        return config.getRedisCheckpointPrefix() + sourceId + ":" + filePath;
    }

    /**
     * 同步检查点到Redis
     */
    private void syncToRedis(String cacheKey, LogCheckpoint checkpoint) {
        try {
            CollectionCheckpoint collectionCheckpoint = convertToCollectionCheckpoint(checkpoint);
            redisTemplate.opsForValue().set(cacheKey, collectionCheckpoint,
                    Duration.ofSeconds(config.getRedisCheckpointTtlSeconds()));
        } catch (Exception e) {
            log.warn("Failed to sync checkpoint to Redis: {}", e.getMessage());
        }
    }

    /**
     * 异步同步检查点到Redis
     */
    private void syncToRedisAsync(String cacheKey, LogCheckpoint checkpoint) {
        try {
            CollectionCheckpoint collectionCheckpoint = convertToCollectionCheckpoint(checkpoint);
            redisTemplate.opsForValue().set(cacheKey, collectionCheckpoint,
                    Duration.ofSeconds(config.getRedisCheckpointTtlSeconds()));
        } catch (Exception e) {
            log.warn("Failed to async sync checkpoint to Redis: {}", e.getMessage());
        }
    }

    /**
     * 将LogCheckpoint转换为CollectionCheckpoint
     */
    private CollectionCheckpoint convertToCollectionCheckpoint(LogCheckpoint checkpoint) {
        return CollectionCheckpoint.of(
                checkpoint.getSourceId().getId().toString(),
                checkpoint.getFilePath(),
                checkpoint.getFileOffset(),
                checkpoint.getFileSize(),
                checkpoint.getFileInode(),
                checkpoint.getFileMtime()
        );
    }
}
