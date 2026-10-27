/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxDeclarationRecordRepository.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmTaxDeclarationRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 税种申报记录数据访问层（报税日历域）。
 * <p>
 * 提供预警扫描主路径（状态 + 截止日）、逾期扫描（OVERDUE 清单）、档案/客户维度
 * 查询与周期唯一校验, 供 {@code ScrmTaxDeclarationService} 状态机与预警调度使用。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmTaxDeclarationRecordRepository
        extends JpaRepository<ScrmTaxDeclarationRecordEntity, Long>,
        JpaSpecificationExecutor<ScrmTaxDeclarationRecordEntity> {

    /**
     * 按档案 + 所属期查询申报记录（周期唯一校验 / 手工补录去重）。
     *
     * @param profileId   档案 ID
     * @param periodStart 所属期开始
     * @param periodEnd   所属期结束
     * @return 申报记录（可能为空）
     */
    Optional<ScrmTaxDeclarationRecordEntity> findByProfileIdAndPeriodStartAndPeriodEnd(
            Long profileId, LocalDate periodStart, LocalDate periodEnd);

    /**
     * 按档案 ID 查询全部申报记录（客户时间线, 按所属期升序）。
     *
     * @param profileId 档案 ID
     * @return 申报记录列表
     */
    List<ScrmTaxDeclarationRecordEntity> findByProfileIdOrderByPeriodStartAsc(Long profileId);

    /**
     * 按客户 ID 查询全部申报记录（客户钻取, 按所属期升序）。
     *
     * @param customerId 客户 ID
     * @return 申报记录列表
     */
    List<ScrmTaxDeclarationRecordEntity> findByCustomerIdOrderByPeriodStartAsc(Long customerId);

    /**
     * 按档案 ID 集合查询申报记录（公司维度钻取: 先按公司查出档案, 再按档案取记录）。
     * <p>
     * 申报记录表本身不冗余 {@code company_id}（避免主体变更后数据不一致），
     * 公司维度的记录查询一律经由档案关联，保证与档案的主体归属始终一致。
     * </p>
     *
     * @param profileIds 档案 ID 集合（不可为空集合, 调用方需先判空）
     * @return 申报记录列表（按所属期升序）
     */
    List<ScrmTaxDeclarationRecordEntity> findByProfileIdInOrderByPeriodStartAsc(List<Long> profileIds);

    /**
     * 按截止日期区间查询申报记录（日历月视图）。
     * <p>
     * 月视图需要呈现整月的全部申报状态（待申报/已申报/已缴款/逾期），
     * 不能只查 PENDING——否则已完成的申报在日历上会凭空消失。
     * </p>
     *
     * @param from 截止日期下限（含）
     * @param to   截止日期上限（含）
     * @return 申报记录列表
     */
    List<ScrmTaxDeclarationRecordEntity> findByDueDateBetween(LocalDate from, LocalDate to);

    /**
     * 查询存在申报记录的「截止年月」集合（月历默认定位用）。
     * <p>
     * 月历默认停在本月时常常是空的——申报截止日通常落在次月，
     * 会计打开页面看到的是一片空白，只能手动翻月。此查询返回所有有记录的
     * 年月，由服务层挑「本月或下一个有记录的月」作为默认视图。
     * </p>
     *
     * @return 每行为 [year(Integer), month(Integer)]，按年月升序
     */
    @Query(value = "select distinct cast(extract(year from due_date) as int), "
            + "cast(extract(month from due_date) as int) "
            + "from scrm.scrm_tax_declaration_record "
            + "where declaration_status <> 'CANCELED' "
            + "order by 1, 2", nativeQuery = true)
    List<Object[]> findDistinctDueMonths();

    /**
     * 预警扫描主路径: 待申报状态且截止日在指定区间内。
     *
     * @param status      状态（PENDING）
     * @param from        截止日期下限（含）
     * @param to          截止日期上限（含）
     * @return 待申报记录列表
     */
    List<ScrmTaxDeclarationRecordEntity> findByDeclarationStatusAndDueDateBetween(
            String status, LocalDate from, LocalDate to);

    /**
     * 逾期清单: OVERDUE 状态的全部记录（按逾期天数倒序）。
     *
     * @param status 状态（OVERDUE）
     * @return 逾期记录列表
     */
    @Query("SELECT r FROM ScrmTaxDeclarationRecordEntity r WHERE r.declarationStatus = :status "
            + "ORDER BY r.overdueDays DESC")
    List<ScrmTaxDeclarationRecordEntity> findOverdueByStatus(@Param("status") String status);

    /**
     * 按状态 + 档案 ID 查询记录（状态推进校验 / 预警幂等）。
     *
     * @param profileId 档案 ID
     * @param status    状态
     * @return 记录列表
     */
    List<ScrmTaxDeclarationRecordEntity> findByProfileIdAndDeclarationStatus(Long profileId, String status);
}
