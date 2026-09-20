/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCareTaskRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCareTaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;

/**
 * SCRM 客户关怀任务数据访问层。
 * <p>
 * 提供按规则 / 客户 / 关怀日期查询任务等能力, 供 {@code ScrmCustomerCareService}
 * 任务管理与调度生成引擎使用。任务生成时通过按 careDate 查询避免重复创建。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCareTaskRepository extends JpaRepository<ScrmCareTaskEntity, Long>,
        JpaSpecificationExecutor<ScrmCareTaskEntity> {

    /**
     * 按与关怀日期查询全部任务 (调度去重用)。
     *
     * @param careDate 关怀日期
     * @return 任务列表
     */
    List<ScrmCareTaskEntity> findByCareDate(LocalDate careDate);

    /**
     * 按、规则、客户与关怀日期查询任务 (规则生成去重用)。
     *
     * @param ruleId    规则 ID
     * @param customerId 客户 ID
     * @param careDate  关怀日期
     * @return 任务列表
     */
    List<ScrmCareTaskEntity> findByRuleIdAndCustomerIdAndCareDate(Long ruleId, Long customerId, LocalDate careDate);

    /**
     * 按状态分页查询任务 (按计划执行时间倒序)。
     *
     * @param status   任务状态 (可空)
     * @param pageable 分页参数
     * @return 任务分页结果
     */
    Page<ScrmCareTaskEntity> findByStatus(String status, Pageable pageable);

    /**
     * 按分页查询任务 (按计划执行时间倒序)。
     *
     * @param pageable 分页参数
     * @return 任务分页结果
     */
}
