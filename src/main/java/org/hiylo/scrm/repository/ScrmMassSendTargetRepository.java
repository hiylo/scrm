/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMassSendTargetRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmMassSendTargetEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * SCRM 群发目标明细数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmMassSendTargetRepository extends JpaRepository<ScrmMassSendTargetEntity, Long>,
        JpaSpecificationExecutor<ScrmMassSendTargetEntity> {

    /**
     * 根据群发任务 ID 查询目标明细列表。
     *
     * @param taskId 群发任务 ID
     * @return 目标明细列表
     */
    List<ScrmMassSendTargetEntity> findByTaskId(Long taskId);

    /**
     * 根据群发任务 ID 与发送状态分页查询目标明细。
     *
     * @param taskId 群发任务 ID
     * @param status 发送状态: PENDING / SENT / FAILED
     * @param pageable 分页参数
     * @return 目标明细分页结果
     */
    Page<ScrmMassSendTargetEntity> findByTaskIdAndStatus(Long taskId, String status, Pageable pageable);

    /**
     * 根据群发任务 ID 分页查询目标明细。
     *
     * @param taskId 群发任务 ID
     * @param pageable 分页参数
     * @return 目标明细分页结果
     */
    Page<ScrmMassSendTargetEntity> findByTaskId(Long taskId, Pageable pageable);

    /**
     * 删除指定群发任务的所有目标明细 (任务删除时级联清理)。
     *
     * @param taskId 群发任务 ID
     */
    void deleteByTaskId(Long taskId);

    /**
     * 按发送状态聚合指定任务的目标明细数 (报告用, 避免 N+1)。
     *
     * @param taskId 群发任务 ID
     * @return Object[]{status, count}
     */
    @Query("SELECT e.status, COUNT(e.id) FROM ScrmMassSendTargetEntity e WHERE e.taskId = :taskId GROUP BY e.status")
    List<Object[]> countByStatus(@Param("taskId") Long taskId);
}
