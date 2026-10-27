/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmEmployeeRepository.java
 * Date : 2026/10/25 13:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmEmployeeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * 员工档案数据访问层（薪酬域）。
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmEmployeeRepository
        extends JpaRepository<ScrmEmployeeEntity, Long>,
        JpaSpecificationExecutor<ScrmEmployeeEntity> {

    /**
     * 按公司查询在职员工（社保/个税按人摊算的数据源）。
     *
     * @param companyId 公司主体 ID
     * @param status    状态（ACTIVE）
     * @return 员工列表（按入职日期升序）
     */
    List<ScrmEmployeeEntity> findByCompanyIdAndStatusOrderByHireDateAsc(Long companyId, String status);

    /**
     * 按公司查询全部员工。
     *
     * @param companyId 公司主体 ID
     * @return 员工列表
     */
    List<ScrmEmployeeEntity> findByCompanyIdOrderByHireDateAsc(Long companyId);

    /**
     * 统计公司在职员工数。
     *
     * @param companyId 公司主体 ID
     * @param status    状态
     * @return 数量
     */
    long countByCompanyIdAndStatus(Long companyId, String status);
}
