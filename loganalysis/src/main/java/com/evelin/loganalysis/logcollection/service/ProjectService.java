package com.evelin.loganalysis.logcollection.service;

import com.evelin.loganalysis.logcollection.dto.ProjectCreateRequest;
import com.evelin.loganalysis.logcollection.dto.ProjectResponse;
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

    private final ProjectRepository projectRepository;

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

        if (projectRepository.existsByCode(request.getCode())) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "项目代码已存在: " + request.getCode());
        }

        Project project = new Project();
        project.setName(request.getName());
        project.setCode(request.getCode());
        project.setDescription(request.getDescription());
        project.setOwner(request.getOwner());
        project.setEmail(request.getEmail());
        project.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        project.setRemark(request.getRemark());

        Project saved = projectRepository.save(project);
        log.info("创建项目成功: {} - {}", saved.getId(), saved.getName());

        return toResponse(saved);
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
                    // 检查名称是否与其他项目重复
                    if (request.getName() != null && !request.getName().equals(project.getName())) {
                        if (projectRepository.existsByName(request.getName())) {
                            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "项目名称已存在: " + request.getName());
                        }
                        project.setName(request.getName());
                    }

                    // 检查项目代码是否与其他项目重复
                    if (request.getCode() != null && !request.getCode().equals(project.getCode())) {
                        if (projectRepository.existsByCode(request.getCode())) {
                            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "项目代码已存在: " + request.getCode());
                        }
                        project.setCode(request.getCode());
                    }

                    if (request.getDescription() != null) {
                        project.setDescription(request.getDescription());
                    }
                    if (request.getOwner() != null) {
                        project.setOwner(request.getOwner());
                    }
                    if (request.getEmail() != null) {
                        project.setEmail(request.getEmail());
                    }
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
        response.setEnabled(project.getEnabled());
        response.setRemark(project.getRemark());
        response.setCreatedAt(project.getCreatedAt());
        response.setUpdatedAt(project.getUpdatedAt());
        return response;
    }
}
