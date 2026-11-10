/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmVoucherRepository.java
 * Date : 2026/10/25 18:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmVoucherEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 记账凭证数据访问层（凭证域）。
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmVoucherRepository
        extends JpaRepository<ScrmVoucherEntity, Long>,
        JpaSpecificationExecutor<ScrmVoucherEntity> {

    /**
     * 取同期同字的最大凭证号（新增凭证时自动续号）。
     *
     * @param companyId   公司主体 ID
     * @param periodYear  年
     * @param periodMonth 月
     * @param voucherWord 凭证字
     * @return 最大凭证号，无匹配返回 null
     */
    @Query("select max(v.voucherNo) from ScrmVoucherEntity v "
            + "where v.companyId = :companyId and v.periodYear = :year "
            + "and v.periodMonth = :month and v.voucherWord = :word")
    Integer findMaxVoucherNo(@Param("companyId") Long companyId,
                             @Param("year") Integer periodYear,
                             @Param("month") Integer periodMonth,
                             @Param("word") String voucherWord);

    /**
     * 按企业 + 期 + 字 + 号查询（唯一键定位，用于导入/查重）。
     *
     * @param companyId   公司主体 ID
     * @param periodYear  年
     * @param periodMonth 月
     * @param voucherWord 凭证字
     * @param voucherNo   凭证号
     * @return 凭证（可能为空）
     */
    Optional<ScrmVoucherEntity> findByCompanyIdAndPeriodYearAndPeriodMonthAndVoucherWordAndVoucherNo(
            Long companyId, Integer periodYear, Integer periodMonth,
            String voucherWord, Integer voucherNo);

    /**
     * 统计某企业某期某状态凭证数（期末检查用）。
     *
     * @param companyId   公司主体 ID
     * @param periodYear  年
     * @param periodMonth 月
     * @param status      状态
     * @return 数量
     */
    long countByCompanyIdAndPeriodYearAndPeriodMonthAndStatus(
            Long companyId, Integer periodYear, Integer periodMonth, String status);

    /**
     * 统计某企业「指定期之前」已存在的凭证数（排除已作废）。
     * <p>
     * 期初余额录入前的防重复计算检查用。<b>不能只看已记账</b>：
     * 草稿日后一旦记账，就会与期初余额叠加，余额凭空变大。
     * 作废凭证已退出余额，不影响，因此排除。
     * </p>
     *
     * @param companyId 公司主体 ID
     * @param yyyymm    期（形如 202610）；统计严格小于该期的凭证
     * @return 凭证数
     */
    @Query("select count(v) from ScrmVoucherEntity v where v.companyId = :companyId "
            + "and v.status <> org.hiylo.scrm.entity.ScrmVoucherEntity.STATUS_VOID "
            + "and (v.periodYear * 100 + v.periodMonth) < :yyyymm")
    long countEffectiveBeforePeriod(@Param("companyId") Long companyId,
                                    @Param("yyyymm") Integer yyyymm);
}
