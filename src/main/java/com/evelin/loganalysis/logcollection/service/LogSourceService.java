package com.evelin.loganalysis.logcollection.service;

import com.evelin.loganalysis.logcollection.dto.LogSourceCreateRequest;
import com.evelin.loganalysis.logcollection.dto.LogSourceResponse;
import com.evelin.loganalysis.logcollection.dto.LogSourceUpdateRequest;
import com.evelin.loganalysis.logcollection.repository.CheckpointRepository;
import com.evelin.loganalysis.logcollection.repository.LogSourceRepository;
import com.evelin.loganalysis.logcollection.repository.ProjectRepository;
import com.evelin.loganalysis.logcollection.repository.RawLogEventRepository;
import com.evelin.loganalysis.logcollection.util.LogPathSerializer;
import com.evelin.loganalysis.logcollection.validation.LogPathValidator;
import com.evelin.loganalysis.logcommon.enums.CollectionStatus;
import com.evelin.loganalysis.logcommon.enums.LogFormat;
import com.evelin.loganalysis.logcommon.enums.LogSourceType;
import com.evelin.loganalysis.logcommon.model.LogSource;
import com.evelin.loganalysis.logcommon.model.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    private final ProjectRepository projectRepository;
    private final LogPathValidator logPathValidator;
    private final CheckpointRepository checkpointRepository;
    private final RawLogEventRepository rawLogEventRepository;

    /**
     * 创建日志源
     *
     * @param request 创建请求
     * @return 创建的日志源
     */
    @Transactional
    public LogSourceResponse create(LogSourceCreateRequest request) {
        if (logSourceRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("日志源名称已存在: " + request.getName());
        }

        LogFormat logFormat = null;
        if (request.getLogFormat() != null) {
            logFormat = LogFormat.valueOf(request.getLogFormat());
        }

        if (logFormat != null) {
            LogPathValidator.ValidationResult validationResult = logPathValidator.validatePaths(
                logFormat, 
                request.getPaths()
            );
            if (!validationResult.isValid()) {
                throw new IllegalArgumentException(validationResult.getErrorMessage());
            }
        }

        LogSource logSource = new LogSource();
        logSource.setName(request.getName());
        logSource.setDescription(request.getDescription());
        
        Map<String, Object> pathsMap = LogPathSerializer.createPathsMap(
            logFormat,
            request.getPaths(),
            request.getLogFormatPattern()
        );
        logSource.setPaths(pathsMap);
        
        logSource.setHost(request.getHost());
        logSource.setPort(request.getPort());
        logSource.setUsername(request.getUsername());
        logSource.setPassword(request.getPassword());
        logSource.setEncoding(request.getEncoding() != null ? request.getEncoding() : "UTF-8");
        logSource.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        logSource.setSourceType(LogSourceType.valueOf(request.getSourceType() != null ? request.getSourceType() : "LOCAL_FILE"));
        logSource.setStatus(CollectionStatus.STOPPED);
        
        if (logFormat != null) {
            logSource.setLogFormat(logFormat);
        }
        logSource.setCustomPattern(request.getCustomPattern());
        logSource.setLogFormatPattern(request.getLogFormatPattern());
        logSource.setConfig(request.getConfig());
        logSource.setRemark(request.getRemark());
        
        logSource.setDesensitizationEnabled(request.getDesensitizationEnabled() != null ? request.getDesensitizationEnabled() : false);
        logSource.setEnabledRuleIds(request.getEnabledRuleIds());
        logSource.setCustomRules(convertToCustomRules(request.getCustomRules()));
        
        if (request.getProjectId() != null) {
            Optional<Project> projectOpt = projectRepository.findById(request.getProjectId());
            if (projectOpt.isEmpty()) {
                throw new IllegalArgumentException("项目不存在: " + request.getProjectId());
            }
            logSource.setProjectId(request.getProjectId());
        }

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
     * 根据项目ID查询日志源
     *
     * @param projectId 项目ID
     * @return 日志源列表
     */
    public List<LogSourceResponse> findByProjectId(UUID projectId) {
        return logSourceRepository.findByProjectId(projectId).stream()
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
                Optional<LogSource> byName = logSourceRepository.findByName(request.getName());
                if (byName.isPresent() && !byName.get().getId().equals(id)) {
                    throw new IllegalArgumentException("日志源名称已存在: " + request.getName());
                }
                existing.setName(request.getName());
            }
            if (request.getDescription() != null) {
                existing.setDescription(request.getDescription());
            }
            
            if (request.getPaths() != null) {
                LogFormat logFormat = existing.getLogFormat();
                if (logFormat != null) {
                    LogPathValidator.ValidationResult validationResult = logPathValidator.validatePaths(
                        logFormat,
                        request.getPaths()
                    );
                    if (!validationResult.isValid()) {
                        throw new IllegalArgumentException(validationResult.getErrorMessage());
                    }
                }
                
                Map<String, Object> pathsMap = LogPathSerializer.createPathsMap(
                    existing.getLogFormat(),
                    request.getPaths(),
                    request.getLogFormatPattern() != null ? request.getLogFormatPattern() : existing.getLogFormatPattern()
                );
                existing.setPaths(pathsMap);
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
            
            if (request.getLogFormat() != null) {
                existing.setLogFormat(LogFormat.valueOf(request.getLogFormat()));
            }
            if (request.getCustomPattern() != null) {
                existing.setCustomPattern(request.getCustomPattern());
            }
            if (request.getLogFormatPattern() != null) {
                existing.setLogFormatPattern(request.getLogFormatPattern());
            }
            
            if (request.getDesensitizationEnabled() != null) {
                existing.setDesensitizationEnabled(request.getDesensitizationEnabled());
            }
            if (request.getEnabledRuleIds() != null) {
                existing.setEnabledRuleIds(request.getEnabledRuleIds());
            }
            if (request.getCustomRules() != null) {
                existing.setCustomRules(convertToCustomRules(request.getCustomRules()));
            }
            
            if (request.getProjectId() != null) {
                Optional<Project> projectOpt = projectRepository.findById(request.getProjectId());
                if (projectOpt.isEmpty()) {
                    throw new IllegalArgumentException("项目不存在: " + request.getProjectId());
                }
                existing.setProjectId(request.getProjectId());
            } else {
                existing.setProjectId(null);
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
     */
    @Transactional
    public void delete(UUID id) {
        // 检查日志源是否存在
        LogSource logSource = logSourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("日志源不存在: " + id));

        // 统计关联数据数量
        long logCount = rawLogEventRepository.countBySourceId(id);
        
        // 删除关联的日志数据
        if (logCount > 0) {
            int deletedLogs = rawLogEventRepository.deleteBySourceId(id);
            log.info("删除日志源 {} 关联的日志数据: {} 条", id, deletedLogs);
        }
        
        // 删除关联的检查点
        checkpointRepository.deleteBySourceId(id);
        
        // 删除日志源
        logSourceRepository.delete(logSource);
        
        log.info("删除日志源成功: {}, 关联日志: {} 条", id, logCount);
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
    /**
     * 将请求中的自定义规则转换为实体中的自定义规则
     */
    private List<LogSource.CustomDesensitizeRule> convertToCustomRules(List<LogSourceCreateRequest.CustomRule> customRules) {
        if (customRules == null) {
            return null;
        }
        return customRules.stream()
                .map(rule -> new LogSource.CustomDesensitizeRule(
                        rule.getId(),
                        rule.getName(),
                        rule.getPattern(),
                        rule.getMaskType(),
                        rule.getReplacement()
                ))
                .collect(Collectors.toList());
    }

    /**
     * 将实体中的自定义规则转换为响应DTO中的自定义规则
     */
    private List<LogSourceCreateRequest.CustomRule> convertFromCustomRules(List<LogSource.CustomDesensitizeRule> customRules) {
        if (customRules == null) {
            return null;
        }
        return customRules.stream()
                .map(rule -> {
                    LogSourceCreateRequest.CustomRule result = new LogSourceCreateRequest.CustomRule();
                    result.setId(rule.getId());
                    result.setName(rule.getName());
                    result.setPattern(rule.getPattern());
                    result.setMaskType(rule.getMaskType());
                    result.setReplacement(rule.getReplacement());
                    return result;
                })
                .collect(Collectors.toList());
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
        response.setLogFormat(logSource.getLogFormat() != null ? logSource.getLogFormat().name() : null);
        response.setLogFormatPattern(logSource.getLogFormatPattern());
        response.setCustomPattern(logSource.getCustomPattern());
        response.setConfig(logSource.getConfig());
        
        List<String> paths = LogPathSerializer.deserializePaths(logSource.getPaths());
        response.setPaths(paths);
        
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
        
        response.setDesensitizationEnabled(logSource.getDesensitizationEnabled());
        response.setEnabledRuleIds(logSource.getEnabledRuleIds());
        response.setCustomRules(convertFromCustomRules(logSource.getCustomRules()));
        
        response.setProjectId(logSource.getProjectId());
        if (logSource.getProjectId() != null) {
            Optional<Project> projectOpt = projectRepository.findById(logSource.getProjectId());
            projectOpt.ifPresent(project -> response.setProjectName(project.getName()));
        }
        return response;
    }
}
