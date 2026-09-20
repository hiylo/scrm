/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTaskDependencyRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmTaskDependencyEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 任务依赖关系数据访问层。
 * <p>
 * 提供按主任务 ID、按依赖任务 ID 查询依赖关系能力, 供 {@code ScrmTaskSchedulerService}
 * 在调度前校验依赖 ({@code checkDependencies}) 与父执行完成后触发依赖任务
 * ({@code executeDependentTasks}) 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmTaskDependencyRepository extends JpaRepository<ScrmTaskDependencyEntity, Long>,
        JpaSpecificationExecutor<ScrmTaskDependencyEntity> {

    /**
     * 按主任务 ID 查询全部依赖关系。
     *
     * @param taskId 主任务 ID
     * @return 依赖关系列表
     */
    List<ScrmTaskDependencyEntity> findByTaskId(Long taskId);

    /**
     * 按与主任务 ID 分页查询依赖关系。
     *
     * @param taskId   主任务 ID
     * @param pageable 分页参数
     * @return 依赖关系分页结果
     */
    Page<ScrmTaskDependencyEntity> findByTaskId(Long taskId, Pageable pageable);

    /**
     * 按与依赖任务 ID 分页查询依赖关系（反向: 查询哪些任务依赖此任务）。
     *
     * @param dependsOnTaskId 依赖任务 ID
     * @param pageable        分页参数
     * @return 依赖关系分页结果
     */
    Page<ScrmTaskDependencyEntity> findByDependsOnTaskId(Long dependsOnTaskId,
                                                                      Pageable pageable);
}
