package com.evelin.loganalysis.logcollection.repository;

import com.evelin.loganalysis.logcommon.model.LogCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 检查点数据访问接口
 *
 * @author Evelin
 */
@Repository
public interface CheckpointRepository extends JpaRepository<LogCheckpoint, UUID>, CheckpointRepositoryCustom {

    /**
     * 根据日志源ID和文件路径查找检查点
     *
     * @param sourceId 日志源ID
     * @param filePath 文件路径
     * @return 检查点信息
     */
    @Query("SELECT c FROM LogCheckpoint c WHERE c.sourceId.id = :sourceId AND c.filePath = :filePath ORDER BY c.updatedAt DESC")
    Optional<LogCheckpoint> findBySourceIdAndFilePath(@Param("sourceId") UUID sourceId, @Param("filePath") String filePath);

    /**
     * 根据日志源ID和文件路径查找所有检查点（用于处理重复数据）
     *
     * @param sourceId 日志源ID
     * @param filePath 文件路径
     * @return 检查点列表
     */
    @Query("SELECT c FROM LogCheckpoint c WHERE c.sourceId.id = :sourceId AND c.filePath = :filePath ORDER BY c.updatedAt DESC")
    List<LogCheckpoint> findAllBySourceIdAndFilePath(@Param("sourceId") UUID sourceId, @Param("filePath") String filePath);

    /**
     * 根据日志源ID查找所有检查点
     *
     * @param sourceId 日志源ID
     * @return 检查点列表
     */
    @Query("SELECT c FROM LogCheckpoint c WHERE c.sourceId.id = :sourceId")
    List<LogCheckpoint> findBySourceId(@Param("sourceId") UUID sourceId);

    /**
     * 删除指定日志源的检查点
     *
     * @param sourceId 日志源ID
     */
    @Modifying
    @Query("DELETE FROM LogCheckpoint c WHERE c.sourceId.id = :sourceId")
    void deleteBySourceId(@Param("sourceId") UUID sourceId);

    /**
     * 更新检查点偏移量
     *
     * @param id 检查点ID
     * @param offset 文件偏移量
     * @param fileSize 文件大小
     * @param fileMtime 文件修改时间
     * @param updateTime 更新时间
     */
    @Modifying
    @Query("UPDATE LogCheckpoint c SET c.fileOffset = :offset, c.fileSize = :fileSize, " +
           "c.fileMtime = :fileMtime, c.updatedAt = :updateTime WHERE c.id = :id")
    void updateCheckpoint(@Param("id") UUID id,
                         @Param("offset") Long offset,
                         @Param("fileSize") Long fileSize,
                         @Param("fileMtime") LocalDateTime fileMtime,
                         @Param("updateTime") LocalDateTime updateTime);

    /**
     * 查找过期的检查点（用于清理）
     *
     * @param beforeTime 截止时间
     * @return 检查点列表
     */
    List<LogCheckpoint> findByUpdatedAtBefore(LocalDateTime beforeTime);
}
