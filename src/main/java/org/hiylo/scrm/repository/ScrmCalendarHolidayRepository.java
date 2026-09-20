/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCalendarHolidayRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCalendarHolidayEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 营销日历节日/纪念日数据访问层。
 * <p>
 * {@code ScrmMarketingCalendarService} 节日管理与营销建议使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCalendarHolidayRepository extends JpaRepository<ScrmCalendarHolidayEntity, Long>,
        JpaSpecificationExecutor<ScrmCalendarHolidayEntity> {

    /**
     * 按与启用状态加载全部节日。
     *
     * @param isActive 启用状态
     * @return 节日列表
     */
    List<ScrmCalendarHolidayEntity> findByIsActiveOrderByHolidayDateAsc(Boolean isActive);

    /**
     * 按与节日类型加载节日。
     *
     * @param holidayType 节日类型
     * @return 节日列表
     */
    List<ScrmCalendarHolidayEntity> findByHolidayTypeOrderByHolidayDateAsc(String holidayType);

    /**
     * 按与节日类型与启用状态加载节日。
     *
     * @param holidayType 节日类型
     * @param isActive    启用状态
     * @return 节日列表
     */
    List<ScrmCalendarHolidayEntity> findByHolidayTypeAndIsActiveOrderByHolidayDateAsc(String holidayType, Boolean isActive);
}
