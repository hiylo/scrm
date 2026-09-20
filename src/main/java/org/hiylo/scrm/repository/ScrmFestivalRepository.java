/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFestivalRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmFestivalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 节日配置数据访问层。
 * <p>
 * 提供按节日类型 / 启用状态加载节日, 供 {@code ScrmCustomerCareService.generateFestivalTasks}
 * 节日关怀任务生成与 {@code getUpcomingFestivals} 即将到来的节日查询使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmFestivalRepository extends JpaRepository<ScrmFestivalEntity, Long>,
        JpaSpecificationExecutor<ScrmFestivalEntity> {

    /**
     * 按加载全部启用节日。
     *
     * @return 启用节日列表
     */
    List<ScrmFestivalEntity> findByEnabledTrue();

    /**
     * 按与节日类型加载启用节日。
     *
     * @param festivalType 节日类型: SOLAR / LUNAR / FIXED / CUSTOM
     * @return 启用节日列表
     */
    List<ScrmFestivalEntity> findByFestivalTypeAndEnabledTrue(String festivalType);
}
