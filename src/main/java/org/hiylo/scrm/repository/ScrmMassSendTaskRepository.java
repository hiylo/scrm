/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMassSendTaskRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmMassSendTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 群发任务数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmMassSendTaskRepository extends JpaRepository<ScrmMassSendTaskEntity, Long>,
        JpaSpecificationExecutor<ScrmMassSendTaskEntity> {

    /**
     * 根据任务状态查询群发任务列表。
     *
     * @param status 任务状态: DRAFT / PENDING / RUNNING / PAUSED / COMPLETED / FAILED
     * @return 任务列表
     */
    List<ScrmMassSendTaskEntity> findByStatus(String status);

    /**
     * 根据平台类型查询群发任务列表。
     *
     * @param platformType 平台类型
     * @return 任务列表
     */
    List<ScrmMassSendTaskEntity> findByPlatformType(String platformType);

    /**
     * 根据账号 ID 查询群发任务列表。
     *
     * @return 任务列表
     */

    /**
     * 统计指定账号下的群发任务总数。
     *
     * @return 任务总数
     */
}
