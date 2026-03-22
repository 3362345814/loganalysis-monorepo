package com.evelin.loganalysis.logcollection.controller;

import com.evelin.loganalysis.logcollection.dto.ProjectCreateRequest;
import com.evelin.loganalysis.logcollection.dto.ProjectResponse;
import com.evelin.loganalysis.logcollection.service.ProjectService;
import com.evelin.loganalysis.logcommon.model.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 项目管理接口
 *
 * @author Evelin
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    /**
     * 创建项目
     */
    @PostMapping
    public Result<ProjectResponse> create(@Valid @RequestBody ProjectCreateRequest request) {
        ProjectResponse response = projectService.create(request);
        log.info("创建项目: {} - {}", response.getId(), response.getName());
        return Result.success(response);
    }

    /**
     * 查询所有项目
     */
    @GetMapping
    public Result<List<ProjectResponse>> list() {
        List<ProjectResponse> projects = projectService.findAll();
        return Result.success(projects);
    }

    /**
     * 查询所有启用的项目
     */
    @GetMapping("/enabled")
    public Result<List<ProjectResponse>> listEnabled() {
        List<ProjectResponse> projects = projectService.findAllEnabled();
        return Result.success(projects);
    }

    /**
     * 根据ID查询项目
     */
    @GetMapping("/{id}")
    public Result<ProjectResponse> getById(@PathVariable UUID id) {
        Optional<ProjectResponse> project = projectService.findById(id);
        return project.map(Result::success)
                .orElseGet(() -> Result.error("项目不存在: " + id));
    }

    /**
     * 更新项目
     */
    @PutMapping("/{id}")
    public Result<ProjectResponse> update(@PathVariable UUID id, @Valid @RequestBody ProjectCreateRequest request) {
        Optional<ProjectResponse> updated = projectService.update(id, request);
        return updated.map(source -> {
                    log.info("更新项目: {} - {}", id, source.getName());
                    return Result.success(source);
                })
                .orElseGet(() -> Result.error("项目不存在: " + id));
    }

    /**
     * 删除项目
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable UUID id) {
        projectService.delete(id);
        return Result.success(null);
    }
}
