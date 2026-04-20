package com.evelin.loganalysis.logcollection.service;

import com.evelin.loganalysis.logcollection.config.CollectionConfig;
import com.evelin.loganalysis.logcollection.model.CollectionCheckpoint;
import com.evelin.loganalysis.logcollection.repository.CheckpointRepository;
import com.evelin.loganalysis.logcommon.model.LogCheckpoint;
import com.evelin.loganalysis.logcollection.model.LogSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 检查点管理器
 * <p>
 * 负责检查点的加载、保存和管理
 * 采用Redis + 数据库双写策略，确保断点续采的可靠性
 *
 * @author Evelin
 */
@Slf4j
@Service
public class CheckpointManager {

    private static final int MAX_FILE_INODE_LENGTH = 128;

    private final CheckpointRepository checkpointRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CollectionConfig config;

    /**
     * 内存缓存检查点，用于快速访问
     */
    private final ConcurrentMap<String, LogCheckpoint> memoryCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> checkpointLocks = new ConcurrentHashMap<>();

    public CheckpointManager(CheckpointRepository checkpointRepository,
                             RedisTemplate<String, Object> redisTemplate,
                             CollectionConfig config) {
        this.checkpointRepository = checkpointRepository;
        this.redisTemplate = redisTemplate;
        this.config = config;
    }

    /**
     * 加载检查点
     * <p>
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
                // 处理 LinkedHashMap 反序列化问题
                if (redisCheckpoint instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) redisCheckpoint;
                    return convertMapToCollectionCheckpoint(map);
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
            List<LogCheckpoint> dbCheckpoints = checkpointRepository.findAllBySourceIdAndFilePath(
                    source.getId(), filePath);

            if (!dbCheckpoints.isEmpty()) {
                LogCheckpoint checkpoint = dbCheckpoints.get(0);
                if (dbCheckpoints.size() > 1) {
                    log.warn("Found {} duplicate checkpoints for sourceId={}, filePath={}, keeping the latest one",
                            dbCheckpoints.size(), sourceId, filePath);
                    for (int i = 1; i < dbCheckpoints.size(); i++) {
                        checkpointRepository.delete(dbCheckpoints.get(i));
                    }
                }
                syncToRedis(cacheKey, checkpoint);
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
     * <p>
     * 采用双写策略：Redis（快速）+ 数据库（持久化）
     *
     * @param sourceId  日志源ID
     * @param filePath  文件路径
     * @param offset    文件偏移量
     * @param fileSize  文件大小
     * @param fileInode 文件inode
     * @param fileMtime 文件修改时间
     */
    public void save(String sourceId, String filePath, Long offset,
                     Long fileSize, String fileInode, LocalDateTime fileMtime) {
        String cacheKey = buildCacheKey(sourceId, filePath);
        Object lock = checkpointLocks.computeIfAbsent(cacheKey, key -> new Object());
        synchronized (lock) {
            try {
                UUID sourceUuid = UUID.fromString(sourceId);
                LocalDateTime now = LocalDateTime.now();
                String normalizedFileInode = normalizeFileInode(fileInode);
                checkpointRepository.upsertCheckpoint(
                        UUID.randomUUID(),
                        sourceUuid,
                        filePath,
                        offset,
                        fileSize,
                        normalizedFileInode,
                        fileMtime,
                        now,
                        now
                );

                // 回填缓存对象（不依赖数据库自增字段）
                LogCheckpoint checkpoint = memoryCache.get(cacheKey);
                if (checkpoint == null) {
                    checkpoint = new LogCheckpoint();
                    LogSource source = new LogSource();
                    source.setId(sourceUuid);
                    checkpoint.setSourceId(source);
                    checkpoint.setFilePath(filePath);
                }
                fillCheckpointFields(checkpoint, offset, fileSize, normalizedFileInode, fileMtime);
                memoryCache.put(cacheKey, checkpoint);
                syncToRedisAsync(cacheKey, checkpoint);
            } catch (Exception e) {
                log.error("Failed to save checkpoint: sourceId={}, filePath={}, error={}",
                        sourceId, filePath, e.getMessage());
            }
        }
    }

    private void fillCheckpointFields(LogCheckpoint checkpoint, Long offset,
                                      Long fileSize, String fileInode, LocalDateTime fileMtime) {
        checkpoint.setFileOffset(offset);
        checkpoint.setFileSize(fileSize);
        checkpoint.setFileInode(fileInode);
        checkpoint.setFileMtime(fileMtime);
        checkpoint.setUpdatedAt(LocalDateTime.now());
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
            List<LogCheckpoint> checkpoints = checkpointRepository.findAllBySourceIdAndFilePath(source.getId(), filePath);
            for (LogCheckpoint checkpoint : checkpoints) {
                checkpointRepository.delete(checkpoint);
            }

            memoryCache.remove(cacheKey);

            try {
                redisTemplate.delete(cacheKey);
            } catch (Exception e) {
                log.warn("Failed to delete checkpoint from Redis: {}", e.getMessage());
            }

            log.info("Deleted checkpoint: sourceId={}, filePath={}", sourceId, filePath);
        } catch (Exception e) {
            log.error("Failed to delete checkpoint: sourceId={}, filePath={}, error={}", sourceId, filePath, e.getMessage());
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

    /**
     * 将Map转换为CollectionCheckpoint
     */
    private CollectionCheckpoint convertMapToCollectionCheckpoint(Map<String, Object> map) {
        String sourceId = (String) map.get("sourceId");
        String filePath = (String) map.get("filePath");
        // 兼容两种字段命名：
        // 1) CollectionCheckpoint: offset
        // 2) LogCheckpoint: fileOffset
        Object offsetObj = map.get("offset");
        if (offsetObj == null) {
            offsetObj = map.get("fileOffset");
        }
        Long offset = offsetObj instanceof Number ? ((Number) offsetObj).longValue() : 0L;

        Object fileSizeObj = map.get("fileSize");
        Long fileSize = fileSizeObj instanceof Number ? ((Number) fileSizeObj).longValue() : 0L;
        String fileInode = (String) map.get("fileInode");
        Object fileMtimeObj = map.get("fileMtime");
        LocalDateTime fileMtime = null;
        if (fileMtimeObj != null) {
            if (fileMtimeObj instanceof String) {
                fileMtime = LocalDateTime.parse((String) fileMtimeObj);
            } else if (fileMtimeObj instanceof Long) {
                fileMtime = LocalDateTime.ofEpochSecond((Long) fileMtimeObj / 1000, 0, java.time.ZoneOffset.UTC);
            }
        }
        log.debug("Converted checkpoint map: sourceId={}, filePath={}, offset={}, fileSize={}",
                sourceId, filePath, offset, fileSize);
        return CollectionCheckpoint.of(sourceId, filePath, offset, fileSize, fileInode, fileMtime);
    }

    private String normalizeFileInode(String fileInode) {
        if (fileInode == null) {
            return null;
        }
        String normalized = fileInode.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() <= MAX_FILE_INODE_LENGTH) {
            return normalized;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >>> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return "sha256:" + hex;
        } catch (NoSuchAlgorithmException e) {
            log.warn("SHA-256 is unavailable, truncating inode value");
            return normalized.substring(0, MAX_FILE_INODE_LENGTH);
        }
    }
}
