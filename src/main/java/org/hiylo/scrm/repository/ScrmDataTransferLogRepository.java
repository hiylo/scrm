/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDataTransferLogRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmDataTransferLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 数据导入导出日志数据访问层。
 * <p>
 * 提供按任务查询日志、按任务+状态查询错误日志等能力。复合过滤通过
 * {@link JpaSpecificationExecutor} 实现动态查询。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmDataTransferLogRepository
        extends JpaRepository<ScrmDataTransferLogEntity, Long>, JpaSpecificationExecutor<ScrmDataTransferLogEntity> {

    /**
     * 按与任务 ID 查询全部日志 (按处理时间升序)。
     *
     * @param taskId   任务 ID
     * @return 日志列表
     */
    List<ScrmDataTransferLogEntity> findByTaskIdOrderByProcessedAtAsc(Long taskId);

    /**
     * 按、任务 ID 与状态查询日志 (用于错误日志查询)。
     *
     * @param taskId   任务 ID
     * @param status   状态: SUCCESS/WARNING/ERROR
     * @return 日志列表
     */
    List<ScrmDataTransferLogEntity> findByTaskIdAndStatusOrderByProcessedAtAsc(Long taskId, String status);
}
