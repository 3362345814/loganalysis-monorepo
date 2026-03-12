package com.evelin.loganalysis.logcollection.repository;

import com.evelin.loganalysis.logcommon.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 项目数据访问接口
 *
 * @author Evelin
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    /**
     * 根据名称查找项目
     *
     * @param name 项目名称
     * @return 项目信息
     */
    Optional<Project> findByName(String name);

    /**
     * 根据项目代码查找项目
     *
     * @param code 项目代码
     * @return 项目信息
     */
    Optional<Project> findByCode(String code);

    /**
     * 查找所有启用的项目
     *
     * @return 项目列表
     */
    List<Project> findByEnabledTrue();

    /**
     * 检查名称是否存在
     *
     * @param name 项目名称
     * @return 是否存在
     */
    boolean existsByName(String name);

    /**
     * 检查项目代码是否存在
     *
     * @param code 项目代码
     * @return 是否存在
     */
    boolean existsByCode(String code);
}
