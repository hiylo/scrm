/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmVoucherEntryRepository.java
 * Date : 2026/10/25 18:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmVoucherEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 凭证分录数据访问层（凭证域）。
 * <p>
 * 余额聚合走原生 SQL：需要「已记账凭证」与分录做 join 后按科目汇总，
 * JPQL 跨实体无关联时表达力不足（本项目实体间刻意不建 JPA 关联）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmVoucherEntryRepository
        extends JpaRepository<ScrmVoucherEntryEntity, Long> {

    /**
     * 按凭证查询分录（按行号升序）。
     *
     * @param voucherId 凭证 ID
     * @return 分录列表
     */
    List<ScrmVoucherEntryEntity> findByVoucherIdOrderByLineNoAsc(Long voucherId);

    /**
     * 删除某凭证下的全部分录（草稿重新保存时先删后建）。
     *
     * @param voucherId 凭证 ID
     */
    void deleteByVoucherId(Long voucherId);

    /**
     * 统计某科目是否已被分录引用（科目删除前校验）。
     *
     * @param companyId   公司主体 ID
     * @param subjectCode 科目编码
     * @return 分录条数
     */
    long countByCompanyIdAndSubjectCode(Long companyId, String subjectCode);

    /**
     * 按科目汇总「指定期之前的累计发生额」（期初余额计算）。
     * <p>只统计已记账凭证，草稿不参与余额。</p>
     *
     * @param companyId 公司主体 ID
     * @param yyyymm    期（形如 202610）；统计严格小于该期的凭证
     * @return 每行 [subjectCode, subjectName, sumDebit, sumCredit]
     */
    @Query(value = "select e.subject_code, max(e.subject_name), "
            + "coalesce(sum(e.debit_amount), 0), coalesce(sum(e.credit_amount), 0) "
            + "from scrm.scrm_voucher_entry e "
            + "join scrm.scrm_voucher v on v.id = e.voucher_id "
            + "where v.company_id = :companyId and v.status = 'POSTED' "
            + "and (v.period_year * 100 + v.period_month) < :yyyymm "
            + "group by e.subject_code", nativeQuery = true)
    List<Object[]> sumBeforePeriod(@Param("companyId") Long companyId, @Param("yyyymm") Integer yyyymm);

    /**
     * 按科目汇总「截至指定期的累计发生额」（含该期）。
     * <p>资产负债表取「期末余额」用它；期初则用 {@link #sumBeforePeriod}。</p>
     *
     * @param companyId 公司主体 ID
     * @param yyyymm    期（形如 202610），统计小于等于该期的凭证
     * @return 每行 [subjectCode, subjectName, sumDebit, sumCredit]
     */
    @Query(value = "select e.subject_code, max(e.subject_name), "
            + "coalesce(sum(e.debit_amount), 0), coalesce(sum(e.credit_amount), 0) "
            + "from scrm.scrm_voucher_entry e "
            + "join scrm.scrm_voucher v on v.id = e.voucher_id "
            + "where v.company_id = :companyId and v.status = 'POSTED' "
            + "and (v.period_year * 100 + v.period_month) <= :yyyymm "
            + "group by e.subject_code", nativeQuery = true)
    List<Object[]> sumUpTo(@Param("companyId") Long companyId, @Param("yyyymm") Integer yyyymm);

    /**
     * 按科目汇总「区间内发生额」（利润表本年累计用它）。
     *
     * @param companyId 公司主体 ID
     * @param fromYyyymm 起始期（含）
     * @param toYyyymm   结束期（含）
     * @return 每行 [subjectCode, subjectName, sumDebit, sumCredit]
     */
    @Query(value = "select e.subject_code, max(e.subject_name), "
            + "coalesce(sum(e.debit_amount), 0), coalesce(sum(e.credit_amount), 0) "
            + "from scrm.scrm_voucher_entry e "
            + "join scrm.scrm_voucher v on v.id = e.voucher_id "
            + "where v.company_id = :companyId and v.status = 'POSTED' "
            + "and (v.period_year * 100 + v.period_month) between :fromYyyymm and :toYyyymm "
            + "group by e.subject_code", nativeQuery = true)
    List<Object[]> sumBetween(@Param("companyId") Long companyId,
                              @Param("fromYyyymm") Integer fromYyyymm,
                              @Param("toYyyymm") Integer toYyyymm);

    /**
     * 按科目汇总「指定期的发生额」（本期发生额）。
     *
     * @param companyId 公司主体 ID
     * @param yyyymm    期（形如 202610）
     * @return 每行 [subjectCode, subjectName, sumDebit, sumCredit]
     */
    @Query(value = "select e.subject_code, max(e.subject_name), "
            + "coalesce(sum(e.debit_amount), 0), coalesce(sum(e.credit_amount), 0) "
            + "from scrm.scrm_voucher_entry e "
            + "join scrm.scrm_voucher v on v.id = e.voucher_id "
            + "where v.company_id = :companyId and v.status = 'POSTED' "
            + "and (v.period_year * 100 + v.period_month) = :yyyymm "
            + "group by e.subject_code", nativeQuery = true)
    List<Object[]> sumInPeriod(@Param("companyId") Long companyId, @Param("yyyymm") Integer yyyymm);

    /**
     * 按科目汇总区间发生额，<b>排除结转损益凭证</b>。
     * <p>
     * 利润表必须用这个：结转凭证的分录也落在损益科目上，
     * 混进来会导致「结账后利润变 0」。
     * </p>
     *
     * @param companyId  公司主体 ID
     * @param fromYyyymm 起始期（含）
     * @param toYyyymm   结束期（含）
     * @return 每行 [subjectCode, subjectName, sumDebit, sumCredit]
     */
    @Query(value = "select e.subject_code, max(e.subject_name), "
            + "coalesce(sum(e.debit_amount), 0), coalesce(sum(e.credit_amount), 0) "
            + "from scrm.scrm_voucher_entry e "
            + "join scrm.scrm_voucher v on v.id = e.voucher_id "
            + "where v.company_id = :companyId and v.status = 'POSTED' "
            + "and v.is_closing = false "
            + "and (v.period_year * 100 + v.period_month) between :fromYyyymm and :toYyyymm "
            + "group by e.subject_code", nativeQuery = true)
    List<Object[]> sumBetweenExcludingClosing(@Param("companyId") Long companyId,
                                              @Param("fromYyyymm") Integer fromYyyymm,
                                              @Param("toYyyymm") Integer toYyyymm);

    /**
     * 凭证分录明细（序时账 / 明细账用）。
     * <p>
     * 返回列顺序：voucher_date, voucher_word, voucher_no, summary, subject_code,
     * subject_name, debit_amount, credit_amount, voucher_remark, voucher_id
     * </p>
     *
     * @param companyId 公司主体 ID
     * @param fromYyyymm 起始期（含）
     * @param toYyyymm   结束期（含）
     * @param subjectPrefix 科目前缀（如 1002，可空表示不限）
     * @return 分录明细行
     */
    @Query(value = "select v.voucher_date, v.voucher_word, v.voucher_no, e.summary, "
            + "e.subject_code, e.subject_name, e.debit_amount, e.credit_amount, "
            + "v.remark, v.id "
            + "from scrm.scrm_voucher_entry e "
            + "join scrm.scrm_voucher v on v.id = e.voucher_id "
            + "where v.company_id = :companyId and v.status = 'POSTED' "
            + "and (v.period_year * 100 + v.period_month) between :fromYyyymm and :toYyyymm "
            + "and (:subjectPrefix is null or e.subject_code like concat(:subjectPrefix, '%')) "
            + "order by v.voucher_date, v.voucher_word, v.voucher_no, e.line_no", nativeQuery = true)
    List<Object[]> findEntryDetails(@Param("companyId") Long companyId,
                                    @Param("fromYyyymm") Integer fromYyyymm,
                                    @Param("toYyyymm") Integer toYyyymm,
                                    @Param("subjectPrefix") String subjectPrefix);
}
