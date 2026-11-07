/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxHolidayRepository.java
 * Date : 2026/10/27 10:40:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmTaxHolidayEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 税务假期数据访问层。
 * <p>
 * 申报截止日顺延只需要「某段时间内是否有假期」，因此按区间查询而不是加载全表，
 * 避免每年数据累积后每次顺延都全表扫描。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Repository
public interface ScrmTaxHolidayRepository extends JpaRepository<ScrmTaxHolidayEntity, Long> {

    /**
     * 查询与指定区间有交集的启用假期。
     * <p>
     * 交集判定：假期起点 ≤ 区间终点 且 假期终点 ≥ 区间起点。
     * </p>
     *
     * @param from 区间起点
     * @param to   区间终点
     * @return 命中的假期列表
     */
    @Query("SELECT h FROM ScrmTaxHolidayEntity h "
            + "WHERE h.isActive = true AND h.startDate <= :to AND h.endDate >= :from "
            + "ORDER BY h.startDate ASC")
    List<ScrmTaxHolidayEntity> findActiveBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 按年份查询启用假期（运维核对数据完整性用）。
     *
     * @param year 年份
     * @return 该年假期列表
     */
    List<ScrmTaxHolidayEntity> findByHolidayYearAndIsActiveTrueOrderByStartDateAsc(Integer year);
}
