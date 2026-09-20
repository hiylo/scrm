/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInteractionCalendarRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmInteractionCalendarEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 客户互动日历数据访问层。
 * <p>
 * 日历管理与视图聚合使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmInteractionCalendarRepository extends JpaRepository<ScrmInteractionCalendarEntity, Long>,
        JpaSpecificationExecutor<ScrmInteractionCalendarEntity> {

    /**
     * 按日历编码查询 (数据隔离)。
     *
     * @param calendarCode 日历编码
     * @return 日历实体 (不存在返回 empty)
     */
    Optional<ScrmInteractionCalendarEntity> findByCalendarCode(String calendarCode);

    /**
     * 按所有者查询日历列表 (数据隔离)。
     *
     * @param ownerId  所有者 ID
     * @return 日历列表
     */
    List<ScrmInteractionCalendarEntity> findByOwnerId(String ownerId);
}
