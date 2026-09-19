/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInheritanceItemRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmInheritanceItemEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * 离职继承明细数据访问层。
 * <p>
 * 提供按任务 ID 查询明细与按状态过滤的能力, 支撑任务详情列表与失败项重试。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmInheritanceItemRepository
        extends JpaRepository<ScrmInheritanceItemEntity, Long>,
        JpaSpecificationExecutor<ScrmInheritanceItemEntity> {

    /**
     * 按任务 ID 查询全部明细 (按 ID 升序, 与创建顺序一致)。
     *
     * @param taskId 任务 ID
     * @return 明细列表
     */
    List<ScrmInheritanceItemEntity> findByTaskIdOrderByIdAsc(Long taskId);

    /**
     * 按任务 ID 与状态分页查询明细。
     *
     *      * @param taskId   任务 ID
     * @param status   明细状态 (可空表示不过滤)
     * @param pageable 分页参数
     * @return 明细分页结果
     */
    Page<ScrmInheritanceItemEntity> findByTaskIdAndStatus(Long taskId, String status, Pageable pageable);
}
