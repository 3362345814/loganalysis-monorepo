package com.evelin.loganalysis.logcollection.service;

import com.evelin.loganalysis.logcollection.dto.ProjectCreateRequest;
import com.evelin.loganalysis.logcollection.dto.ProjectResponse;
import com.evelin.loganalysis.logcollection.dto.ConnectionTestRequest;
import com.evelin.loganalysis.logcollection.dto.ConnectionTestResponse;
import com.evelin.loganalysis.logcollection.enums.LogSourceType;
import com.evelin.loganalysis.logcollection.repository.ProjectRepository;
import com.evelin.loganalysis.logcommon.exception.BusinessException;
import com.evelin.loganalysis.logcommon.constant.ResultCode;
import com.evelin.loganalysis.logcommon.model.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 项目服务层
 *
 * @author Evelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private static final String MASK_PREFIX = "******";

    private final ProjectRepository projectRepository;
    private final ConnectionTestService connectionTestService;

    /**
     * 创建项目
     *
     * @param request 创建请求
     * @return 创建的项目
     */
    @Transactional
    public ProjectResponse create(ProjectCreateRequest request) {
        // 检查名称是否已存在
        if (projectRepository.existsByName(request.getName())) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "项目名称已存在: " + request.getName());
        }

        validateSshConnectionForSave(request, null);

        // 自动生成项目代码
        String code = generateProjectCode(request.getName());
        int suffix = 1;
        String originalCode = code;
        while (projectRepository.existsByCode(code)) {
            code = originalCode + suffix++;
        }

        Project project = new Project();
        project.setName(request.getName());
        project.setCode(code);
        project.setDescription(request.getDescription());
        project.setOwner(request.getOwner());
        project.setEmail(request.getEmail());
        applyConnectionConfig(project, request);
        project.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        project.setRemark(request.getRemark());

        Project saved = projectRepository.save(project);
        log.info("创建项目成功: {} - {}, 代码: {}", saved.getId(), saved.getName(), saved.getCode());

        return toResponse(saved);
    }

    /**
     * 根据项目名称生成项目代码
     */
    private String generateProjectCode(String name) {
        if (name == null || name.isEmpty()) {
            return "PROJ";
        }
        // 提取名称中的字母并转大写，最多4个字符
        StringBuilder code = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isLetter(c)) {
                code.append(Character.toUpperCase(c));
                if (code.length() >= 4) break;
            }
        }
        // 如果没有提取到字母，使用固定前缀
        if (code.length() == 0) {
            code.append("PROJ");
        }
        // 补齐到4位
        while (code.length() < 4) {
            code.append("0");
        }
        return code.toString();
    }

    /**
     * 更新项目
     *
     * @param id      项目ID
     * @param request 更新请求
     * @return 更新后的项目
     */
    @Transactional
    public Optional<ProjectResponse> update(UUID id, ProjectCreateRequest request) {
        return projectRepository.findById(id)
                .map(project -> {
                    validateSshConnectionForSave(request, project);

                    // 检查名称是否与其他项目重复
                    if (request.getName() != null && !request.getName().equals(project.getName())) {
                        if (projectRepository.existsByName(request.getName())) {
                            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "项目名称已存在: " + request.getName());
                        }
                        project.setName(request.getName());
                    }

                    // 项目代码不允许修改

                    if (request.getDescription() != null) {
                        project.setDescription(request.getDescription());
                    }
                    if (request.getOwner() != null) {
                        project.setOwner(request.getOwner());
                    }
                    if (request.getEmail() != null) {
                        project.setEmail(request.getEmail());
                    }
                    applyConnectionConfig(project, request);
                    if (request.getEnabled() != null) {
                        project.setEnabled(request.getEnabled());
                    }
                    if (request.getRemark() != null) {
                        project.setRemark(request.getRemark());
                    }

                    Project saved = projectRepository.save(project);
                    log.info("更新项目成功: {} - {}", saved.getId(), saved.getName());

                    return toResponse(saved);
                });
    }

    /**
     * 根据ID查询项目
     *
     * @param id 项目ID
     * @return 项目信息
     */
    public Optional<ProjectResponse> findById(UUID id) {
        return projectRepository.findById(id)
                .map(this::toResponse);
    }

    /**
     * 查询所有项目
     *
     * @return 项目列表
     */
    public List<ProjectResponse> findAll() {
        return projectRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 查询所有启用的项目
     *
     * @return 项目列表
     */
    public List<ProjectResponse> findAllEnabled() {
        return projectRepository.findByEnabledTrue().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 删除项目
     *
     * @param id 项目ID
     */
    @Transactional
    public void delete(UUID id) {
        if (projectRepository.existsById(id)) {
            projectRepository.deleteById(id);
            log.info("删除项目成功: {}", id);
        }
    }

    /**
     * 转换为响应DTO
     *
     * @param project 项目实体
     * @return 响应DTO
     */
    private ProjectResponse toResponse(Project project) {
        ProjectResponse response = new ProjectResponse();
        response.setId(project.getId());
        response.setName(project.getName());
        response.setCode(project.getCode());
        response.setDescription(project.getDescription());
        response.setOwner(project.getOwner());
        response.setEmail(project.getEmail());
        response.setCollectionSourceType(project.getCollectionSourceType() != null ? project.getCollectionSourceType().name() : null);
        response.setSshHost(project.getSshHost());
        response.setSshPort(project.getSshPort());
        response.setSshUsername(project.getSshUsername());
        boolean sshPasswordConfigured = project.getSshPassword() != null && !project.getSshPassword().isBlank();
        response.setSshPasswordConfigured(sshPasswordConfigured);
        response.setSshPassword(sshPasswordConfigured ? maskSecret(project.getSshPassword()) : null);
        response.setEnabled(project.getEnabled());
        response.setRemark(project.getRemark());
        response.setCreatedAt(project.getCreatedAt());
        response.setUpdatedAt(project.getUpdatedAt());
        return response;
    }

    private void applyConnectionConfig(Project project, ProjectCreateRequest request) {
        project.setCollectionSourceType(parseSourceType(request.getCollectionSourceType()));
        project.setSshHost(trimToNull(request.getSshHost()));
        project.setSshPort(request.getSshPort());
        project.setSshUsername(trimToNull(request.getSshUsername()));
        if (shouldUpdateSecret(request.getSshPassword())) {
            project.setSshPassword(request.getSshPassword());
        }
    }

    private LogSourceType parseSourceType(String sourceType) {
        String normalized = trimToNull(sourceType);
        return normalized == null ? null : LogSourceType.valueOf(normalized);
    }

    private boolean shouldUpdateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return false;
        }
        return !secret.startsWith(MASK_PREFIX);
    }

    private String maskSecret(String secret) {
        int visibleCount = Math.min(4, secret.length());
        return MASK_PREFIX + secret.substring(secret.length() - visibleCount);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateSshConnectionForSave(ProjectCreateRequest request, Project existingProject) {
        LogSourceType sourceType = parseSourceType(request.getCollectionSourceType());
        if (sourceType != LogSourceType.SSH) {
            return;
        }

        String host = trimToNull(request.getSshHost());
        String username = trimToNull(request.getSshUsername());
        Integer port = request.getSshPort();
        String password = resolvePasswordForValidation(request.getSshPassword(), existingProject);

        if (host == null) {
            throw new BusinessException(ResultCode.RULE_VALIDATION_ERROR, "请输入SSH主机地址");
        }
        if (username == null) {
            throw new BusinessException(ResultCode.RULE_VALIDATION_ERROR, "请输入SSH用户名");
        }
        if (password == null || password.isBlank()) {
            throw new BusinessException(ResultCode.RULE_VALIDATION_ERROR, "请输入SSH密码");
        }

        ConnectionTestRequest connectionTestRequest = new ConnectionTestRequest();
        connectionTestRequest.setHost(host);
        connectionTestRequest.setPort(port);
        connectionTestRequest.setUsername(username);
        connectionTestRequest.setPassword(password);

        ConnectionTestResponse response = connectionTestService.testSshConnection(connectionTestRequest);
        if (response == null || !response.isSuccess()) {
            String message = response != null ? response.getMessage() : "SSH连接测试失败";
            throw new BusinessException(ResultCode.RULE_VALIDATION_ERROR, "项目 SSH 连接测试失败: " + message);
        }
    }

    private String resolvePasswordForValidation(String submittedPassword, Project existingProject) {
        if (shouldUpdateSecret(submittedPassword)) {
            return submittedPassword;
        }
        return existingProject != null ? existingProject.getSshPassword() : null;
    }
}
