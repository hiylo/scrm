/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkOrderLogRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmWorkOrderLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 工单日志数据访问层。
 * <p>
 * 提供按工单 ID、日志类型、操作人 ID 查询日志能力, 供 {@code ScrmWorkOrderService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmWorkOrderLogRepository extends JpaRepository<ScrmWorkOrderLogEntity, Long>,
        JpaSpecificationExecutor<ScrmWorkOrderLogEntity> {

    /**
     * 按与工单 ID 查询日志 (按创建时间升序)。
     *
     * @param orderId  工单 ID
     * @return 日志列表
     */
    List<ScrmWorkOrderLogEntity> findByOrderIdOrderByCreateTimeAsc(Long orderId);

    /**
     * 按与工单 ID 分页查询日志 (按创建时间升序)。
     *
     * @param orderId  工单 ID
     * @param pageable 分页参数
     * @return 日志分页结果
     */
    Page<ScrmWorkOrderLogEntity> findByOrderIdOrderByCreateTimeAsc(Long orderId, Pageable pageable);

    /**
     * 按与日志类型查询日志 (按创建时间升序)。
     *
     * @param logType  日志类型
     * @return 日志列表
     */
    List<ScrmWorkOrderLogEntity> findByLogTypeOrderByCreateTimeAsc(String logType);

    /**
     * 按与操作人 ID 查询日志 (按创建时间升序)。
     *
     * @param operatorId 操作人 ID
     * @return 日志列表
     */
    List<ScrmWorkOrderLogEntity> findByOperatorIdOrderByCreateTimeAsc(Long operatorId);
}
