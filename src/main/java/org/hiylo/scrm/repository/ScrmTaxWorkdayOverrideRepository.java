/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxWorkdayOverrideRepository.java
 * Date : 2026/10/27 10:42:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmTaxWorkdayOverrideEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 调休上班日数据访问层。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Repository
public interface ScrmTaxWorkdayOverrideRepository extends JpaRepository<ScrmTaxWorkdayOverrideEntity, Long> {

    /**
     * 查询区间内需要上班的日期（通常是周末补班）。
     *
     * @param from 区间起点
     * @param to   区间终点
     * @return 补班日列表
     */
    @Query("SELECT w FROM ScrmTaxWorkdayOverrideEntity w "
            + "WHERE w.isActive = true AND w.workDate BETWEEN :from AND :to "
            + "ORDER BY w.workDate ASC")
    List<ScrmTaxWorkdayOverrideEntity> findActiveBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
