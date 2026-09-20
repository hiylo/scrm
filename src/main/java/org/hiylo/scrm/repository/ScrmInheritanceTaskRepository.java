/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInheritanceTaskRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmInheritanceTaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 离职继承任务数据访问层。
 * <p>
 * 提供按离职人 / 接收人 / 状态查询任务的能力, 复杂分页与过滤通过
 * {@link JpaSpecificationExecutor} 在 Service 层构建。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmInheritanceTaskRepository
        extends JpaRepository<ScrmInheritanceTaskEntity, Long>,
        JpaSpecificationExecutor<ScrmInheritanceTaskEntity> {

    /**
     * 按状态分页查询任务 (按创建时间倒序)。
     *
     * @param status   任务状态 (可空表示不过滤)
     * @param pageable 分页参数
     * @return 任务分页结果
     */
    Page<ScrmInheritanceTaskEntity> findByStatus(String status, Pageable pageable);
}
