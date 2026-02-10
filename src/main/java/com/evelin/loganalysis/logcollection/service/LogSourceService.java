package com.evelin.loganalysis.logcollection.service;

import com.evelin.loganalysis.logcollection.dto.LogSourceCreateRequest;
import com.evelin.loganalysis.logcollection.dto.LogSourceResponse;
import com.evelin.loganalysis.logcollection.dto.LogSourceUpdateRequest;
import com.evelin.loganalysis.logcollection.repository.LogSourceRepository;
import com.evelin.loganalysis.logcommon.enums.CollectionStatus;
import com.evelin.loganalysis.logcommon.enums.LogSourceType;
import com.evelin.loganalysis.logcommon.model.LogSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 日志源服务层
 *
 * @author Evelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogSourceService {

    private final LogSourceRepository logSourceRepository;

    /**
     * 创建日志源
     *
     * @param request 创建请求
     * @return 创建的日志源
     */
    @Transactional
    public LogSourceResponse create(LogSourceCreateRequest request) {
        // 检查名称是否已存在
        if (logSourceRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("日志源名称已存在: " + request.getName());
        }

        LogSource logSource = new LogSource();
        logSource.setName(request.getName());
        logSource.setDescription(request.getDescription());
        logSource.setPath(request.getPath());
        logSource.setHost(request.getHost());
        logSource.setPort(request.getPort());
        logSource.setUsername(request.getUsername());
        logSource.setPassword(request.getPassword());
        logSource.setEncoding(request.getEncoding() != null ? request.getEncoding() : "UTF-8");
        logSource.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        logSource.setSourceType(LogSourceType.valueOf(request.getSourceType() != null ? request.getSourceType() : "LOCAL_FILE"));
        logSource.setStatus(CollectionStatus.STOPPED);
        logSource.setConfig(request.getConfig());
        logSource.setRemark(request.getRemark());

        LogSource saved = logSourceRepository.save(logSource);
        log.info("创建日志源成功: {} - {}", saved.getId(), saved.getName());

        return toResponse(saved);
    }

    /**
     * 根据ID查询日志源
     *
     * @param id 日志源ID
     * @return 日志源信息
     */
    public Optional<LogSourceResponse> findById(UUID id) {
        return logSourceRepository.findById(id).map(this::toResponse);
    }

    /**
     * 查询所有日志源
     *
     * @return 日志源列表
     */
    public List<LogSourceResponse> findAll() {
        return logSourceRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 根据状态查询日志源
     *
     * @param status 采集状态
     * @return 日志源列表
     */
    public List<LogSourceResponse> findByStatus(CollectionStatus status) {
        return logSourceRepository.findByStatus(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 根据状态查询日志源实体列表
     *
     * @param status 采集状态
     * @return 日志源实体列表
     */
    public List<LogSource> findEntitiesByStatus(CollectionStatus status) {
        return logSourceRepository.findByStatus(status);
    }

    /**
     * 查询所有启用的日志源
     *
     * @return 日志源列表
     */
    public List<LogSourceResponse> findAllEnabled() {
        return logSourceRepository.findByEnabledTrue().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 更新日志源
     *
     * @param id      日志源ID
     * @param request 更新请求
     * @return 更新后的日志源
     */
    @Transactional
    public Optional<LogSourceResponse> update(UUID id, LogSourceUpdateRequest request) {
        return logSourceRepository.findById(id).map(existing -> {
            if (request.getName() != null) {
                // 检查新名称是否被其他日志源使用
                Optional<LogSource> byName = logSourceRepository.findByName(request.getName());
                if (byName.isPresent() && !byName.get().getId().equals(id)) {
                    throw new IllegalArgumentException("日志源名称已存在: " + request.getName());
                }
                existing.setName(request.getName());
            }
            if (request.getDescription() != null) {
                existing.setDescription(request.getDescription());
            }
            if (request.getPath() != null) {
                existing.setPath(request.getPath());
            }
            if (request.getHost() != null) {
                existing.setHost(request.getHost());
            }
            if (request.getPort() != null) {
                existing.setPort(request.getPort());
            }
            if (request.getUsername() != null) {
                existing.setUsername(request.getUsername());
            }
            if (request.getPassword() != null) {
                existing.setPassword(request.getPassword());
            }
            if (request.getEncoding() != null) {
                existing.setEncoding(request.getEncoding());
            }
            if (request.getEnabled() != null) {
                existing.setEnabled(request.getEnabled());
            }
            if (request.getConfig() != null) {
                existing.setConfig(request.getConfig());
            }
            if (request.getRemark() != null) {
                existing.setRemark(request.getRemark());
            }

            LogSource saved = logSourceRepository.save(existing);
            log.info("更新日志源成功: {} - {}", saved.getId(), saved.getName());

            return toResponse(saved);
        });
    }

    /**
     * 删除日志源
     *
     * @param id 日志源ID
     * @return 是否删除成功
     */
    @Transactional
    public boolean delete(UUID id) {
        if (logSourceRepository.existsById(id)) {
            logSourceRepository.deleteById(id);
            log.info("删除日志源成功: {}", id);
            return true;
        }
        return false;
    }

    /**
     * 更新采集状态
     *
     * @param id     日志源ID
     * @param status 新的采集状态
     */
    @Transactional
    public void updateStatus(UUID id, CollectionStatus status) {
        logSourceRepository.updateStatus(id, status, LocalDateTime.now());
        log.info("更新日志源状态: {} -> {}", id, status);
    }

    /**
     * 更新最后采集时间
     *
     * @param id 日志源ID
     */
    @Transactional
    public void updateLastCollectionTime(UUID id) {
        logSourceRepository.updateLastCollectionTime(id, LocalDateTime.now(), LocalDateTime.now());
    }

    /**
     *
     *
     更新最后心跳时间 * @param id 日志源ID
     */
    @Transactional
    public void updateLastHeartbeatTime(UUID id) {
        logSourceRepository.updateLastHeartbeatTime(id, LocalDateTime.now(), LocalDateTime.now());
    }

    /**
     * 获取原始日志源实体（用于Collector）
     *
     * @param id 日志源ID
     * @return 日志源实体
     */
    public Optional<LogSource> getEntityById(UUID id) {
        return logSourceRepository.findById(id);
    }

    /**
     * 转换为响应DTO
     *
     * @param logSource 实体
     * @return 响应DTO
     */
    private LogSourceResponse toResponse(LogSource logSource) {
        LogSourceResponse response = new LogSourceResponse();
        response.setId(logSource.getId());
        response.setName(logSource.getName());
        response.setDescription(logSource.getDescription());
        response.setSourceType(logSource.getSourceType() != null ? logSource.getSourceType().name() : null);
        response.setPath(logSource.getPath());
        response.setHost(logSource.getHost());
        response.setPort(logSource.getPort());
        response.setEncoding(logSource.getEncoding());
        response.setEnabled(logSource.getEnabled());
        response.setStatus(logSource.getStatus() != null ? logSource.getStatus().name() : null);
        response.setLastCollectionTime(logSource.getLastCollectionTime());
        response.setLastHeartbeatTime(logSource.getLastHeartbeatTime());
        response.setCreatedAt(logSource.getCreatedAt());
        response.setUpdatedAt(logSource.getUpdatedAt());
        response.setRemark(logSource.getRemark());
        return response;
    }
}
