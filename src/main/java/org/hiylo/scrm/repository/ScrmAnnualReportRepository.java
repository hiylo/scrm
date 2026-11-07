/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmAnnualReportRepository.java
 * Date : 2026/10/25 10:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAnnualReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 工商年报数据访问层（合规域）。
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmAnnualReportRepository
        extends JpaRepository<ScrmAnnualReportEntity, Long>,
        JpaSpecificationExecutor<ScrmAnnualReportEntity> {

    /**
     * 按公司 + 年度查询（生成幂等键：一户一年一条）。
     *
     * @param companyId  公司主体 ID
     * @param reportYear 报送年度
     * @return 年报记录（可能为空）
     */
    Optional<ScrmAnnualReportEntity> findByCompanyIdAndReportYear(Long companyId, Integer reportYear);

    /**
     * 按公司查询全部年报（公司 360 钻取，年度倒序）。
     *
     * @param companyId 公司主体 ID
     * @return 年报列表
     */
    List<ScrmAnnualReportEntity> findByCompanyIdOrderByReportYearDesc(Long companyId);

    /**
     * 按状态 + 截止日区间查询（逾期推进用）。
     *
     * @param status 状态
     * @param from   截止日下限（含）
     * @param to     截止日上限（含）
     * @return 年报列表
     */
    List<ScrmAnnualReportEntity> findByStatusAndDueDateBetween(String status, LocalDate from, LocalDate to);

    /**
     * 按状态 + 截止日查询（提醒扫描用）。
     *
     * @param status 状态
     * @param dueDate 截止日
     * @return 年报列表
     */
    List<ScrmAnnualReportEntity> findByStatusAndDueDate(String status, LocalDate dueDate);

    /**
     * 统计某主体关联的年报数量（公司 360 聚合）。
     *
     * @param companyId 公司主体 ID
     * @return 数量
     */
    long countByCompanyId(Long companyId);
}
