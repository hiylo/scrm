/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmInspectionTaskRepository.java
 * Date : 2026/10/23 09:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmInspectionTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * 工商税务巡检任务数据访问层。
 * <p>
 * 提供按企业名称 / 统一社会信用代码查询任务、按启用与巡检维度批量加载待巡检任务、
 * 以及按周期类型加载任务（调度侧分组执行）能力。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmInspectionTaskRepository
        extends JpaRepository<ScrmInspectionTaskEntity, Long>, JpaSpecificationExecutor<ScrmInspectionTaskEntity> {

    /**
     * 按统一社会信用代码查询巡检任务（去重校验与按企业维度的巡检入口）。
     *
     * @param creditCode 统一社会信用代码
     * @return 巡检任务（不存在时返回空）
     */
    Optional<ScrmInspectionTaskEntity> findByCreditCode(String creditCode);

    /**
     * 按企业名称查询巡检任务（无信用代码时的兜底匹配）。
     *
     * @param enterpriseName 企业名称
     * @return 巡检任务列表
     */
    List<ScrmInspectionTaskEntity> findByEnterpriseName(String enterpriseName);

    /**
     * 查询启用且开启工商 API 巡检的任务。
     *
     * @return 待工商巡检任务列表
     */
    @Query("SELECT t FROM ScrmInspectionTaskEntity t WHERE t.isEnabled = true AND t.inspectBusiness = true")
    List<ScrmInspectionTaskEntity> findEnabledBusinessTasks();

    /**
     * 查询启用且开启税务公告抓取的任务。
     *
     * @return 待税务抓取任务列表
     */
    @Query("SELECT t FROM ScrmInspectionTaskEntity t WHERE t.isEnabled = true AND t.inspectTax = true")
    List<ScrmInspectionTaskEntity> findEnabledTaxTasks();

    /**
     * 按巡检周期批量加载启用任务（调度侧按周期分组执行）。
     *
     * @param cycleType 巡检周期
     * @return 该周期的启用任务列表
     */
    List<ScrmInspectionTaskEntity> findByIsEnabledTrueAndCycleType(String cycleType);

    /**
     * 按关联客户 ID 查询巡检任务。
     *
     * @param customerId 客户 ID
     * @return 巡检任务列表
     */
    List<ScrmInspectionTaskEntity> findByCustomerId(Long customerId);

    /**
     * 按归属账号 ID 查询巡检任务（数据隔离）。
     *
     * @param ownerAccountId 归属账号 ID
     * @return 巡检任务列表
     */
    List<ScrmInspectionTaskEntity> findByOwnerAccountId(Long ownerAccountId);

    /**
     * 统计该主体关联的业务数据数量（公司 360 聚合用）。
     *
     * @param companyId 公司主体 ID
     * @return 数量
     */
    long countByCompanyId(Long companyId);
}