/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmIitRecordRepository.java
 * Date : 2026/10/25 13:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmIitRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 个税扣缴明细数据访问层（薪酬域）。
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmIitRecordRepository
        extends JpaRepository<ScrmIitRecordEntity, Long>,
        JpaSpecificationExecutor<ScrmIitRecordEntity> {

    /**
     * 查某员工某月明细（生成幂等键：一人一月一条）。
     *
     * @param employeeId  员工 ID
     * @param periodYear  年
     * @param periodMonth 月
     * @return 明细（可能为空）
     */
    Optional<ScrmIitRecordEntity> findByEmployeeIdAndPeriodYearAndPeriodMonth(
            Long employeeId, Integer periodYear, Integer periodMonth);

    /**
     * 查某公司某月全部个税明细。
     *
     * @param companyId   公司主体 ID
     * @param periodYear  年
     * @param periodMonth 月
     * @return 明细列表
     */
    List<ScrmIitRecordEntity> findByCompanyIdAndPeriodYearAndPeriodMonthOrderByEmployeeNameAsc(
            Long companyId, Integer periodYear, Integer periodMonth);

    /**
     * 查某员工某年全部明细（累计预扣法累加用）。
     *
     * @param employeeId 员工 ID
     * @param periodYear 年
     * @return 明细列表（按月升序）
     */
    List<ScrmIitRecordEntity> findByEmployeeIdAndPeriodYearOrderByPeriodMonthAsc(
            Long employeeId, Integer periodYear);
}
