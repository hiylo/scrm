/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmAccountOpeningBalanceRepository.java
 * Date : 2026/11/10 17:35:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAccountOpeningBalanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 期初余额数据访问层（V29）。
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmAccountOpeningBalanceRepository
        extends JpaRepository<ScrmAccountOpeningBalanceEntity, Long> {

    /**
     * 取某户全部期初余额（按科目编码升序）。
     *
     * @param companyId 公司主体 ID
     * @return 期初余额列表
     */
    List<ScrmAccountOpeningBalanceEntity> findByCompanyIdOrderBySubjectCodeAsc(Long companyId);

    /**
     * 统计某户是否已设置期初余额。
     *
     * @param companyId 公司主体 ID
     * @return 记录数
     */
    long countByCompanyId(Long companyId);

    /**
     * 删除某户全部期初余额（重设时整户替换）。
     *
     * @param companyId 公司主体 ID
     * @return 删除行数
     */
    @Modifying
    @Query("delete from ScrmAccountOpeningBalanceEntity o where o.companyId = :companyId")
    int deleteByCompanyId(@Param("companyId") Long companyId);

    /**
     * 批量汇总各主体的期初录入状态（一次查询，避免逐户 N+1）。
     * <p>
     * 返回 {@code [companyId, openingPeriod, totalDebit, totalCredit, subjectCount, updateTime]}，
     * 只覆盖已录过期初的主体；未录的主体由服务层补齐。
     * </p>
     *
     * @return 按主体分组的期初汇总
     */
    @Query("select o.companyId, min(o.openingPeriod), sum(o.debitAmount), sum(o.creditAmount), "
            + "count(o), max(o.updateTime) from ScrmAccountOpeningBalanceEntity o "
            + "group by o.companyId")
    List<Object[]> summarizeByCompany();

    /**
     * 统计某主体某科目在期初余额表里的引用行数（科目删除前检查用）。
     *
     * @param companyId   公司主体 ID
     * @param subjectCode 科目编码
     * @return 引用行数，0 表示期初未使用该科目
     */
    long countByCompanyIdAndSubjectCode(@Param("companyId") Long companyId,
                                        @Param("subjectCode") String subjectCode);
}
