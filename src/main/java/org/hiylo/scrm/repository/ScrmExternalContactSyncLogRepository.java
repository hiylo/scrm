/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmExternalContactSyncLogRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmExternalContactSyncLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 外部联系人同步日志数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmExternalContactSyncLogRepository
        extends JpaRepository<ScrmExternalContactSyncLogEntity, Long>,
        JpaSpecificationExecutor<ScrmExternalContactSyncLogEntity> {

    /**
     * 按同步任务 ID 查询全部日志 (按处理时间倒序)。
     *
     * @param taskId 同步任务 ID
     * @return 日志列表
     */
    List<ScrmExternalContactSyncLogEntity> findByTaskIdOrderByProcessedAtDesc(Long taskId);

}
