package com.evelin.loganalysis.logcollection.repository;

import com.evelin.loganalysis.logcollection.enums.CollectionStatus;
import com.evelin.loganalysis.logcollection.enums.LogSourceType;
import com.evelin.loganalysis.logcollection.model.LogSource;
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
 * 日志源数据访问接口
 *
 * @author Evelin
 */
@Repository
public interface LogSourceRepository extends JpaRepository<LogSource, UUID> {

    /**
     * 根据名称查找日志源
     *
     * @param name 日志源名称
     * @return 日志源信息
     */
    Optional<LogSource> findByName(String name);

    /**
     * 根据状态查找日志源
     *
     * @param status 采集状态
     * @return 日志源列表
     */
    List<LogSource> findByStatus(CollectionStatus status);

    /**
     * 根据类型查找日志源
     *
     * @param sourceType 日志源类型
     * @return 日志源列表
     */
    List<LogSource> findBySourceType(LogSourceType sourceType);

    /**
     * 查找所有启用的日志源
     *
     * @return 日志源列表
     */
    List<LogSource> findByEnabledTrue();

    /**
     * 根据状态更新日志源
     *
     * @param id     日志源ID
     * @param status 新状态
     */
    @Modifying
    @Query("UPDATE LogSource s SET s.status = :status, s.updatedAt = :updateTime WHERE s.id = :id")
    void updateStatus(@Param("id") UUID id,
                      @Param("status") CollectionStatus status,
                      @Param("updateTime") LocalDateTime updateTime);

    /**
     * 更新最后采集时间
     *
     * @param id              日志源ID
     * @param lastCollectionTime 最后采集时间
     */
    @Modifying
    @Query("UPDATE LogSource s SET s.lastCollectionTime = :lastCollectionTime, s.updatedAt = :updateTime WHERE s.id = :id")
    void updateLastCollectionTime(@Param("id") UUID id,
                                   @Param("lastCollectionTime") LocalDateTime lastCollectionTime,
                                   @Param("updateTime") LocalDateTime updateTime);

    /**
     * 更新最后心跳时间
     *
     * @param id              日志源ID
     * @param lastHeartbeatTime 最后心跳时间
     */
    @Modifying
    @Query("UPDATE LogSource s SET s.lastHeartbeatTime = :lastHeartbeatTime, s.updatedAt = :updateTime WHERE s.id = :id")
    void updateLastHeartbeatTime(@Param("id") UUID id,
                                  @Param("lastHeartbeatTime") LocalDateTime lastHeartbeatTime,
                                  @Param("updateTime") LocalDateTime updateTime);

    /**
     * 检查名称是否存在
     *
     * @param name 日志源名称
     * @return 是否存在
     */
    boolean existsByName(String name);

    /**
     * 根据项目ID查找日志源
     *
     * @param projectId 项目ID
     * @return 日志源列表
     */
    List<LogSource> findByProjectId(UUID projectId);
}
