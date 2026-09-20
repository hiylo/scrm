/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCalendarConflictRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCalendarConflictEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * SCRM 营销日历冲突检测数据访问层。
 * <p>
 * {@code ScrmMarketingCalendarService} 冲突检测与解决使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCalendarConflictRepository extends JpaRepository<ScrmCalendarConflictEntity, Long>,
        JpaSpecificationExecutor<ScrmCalendarConflictEntity> {

    /**
     * 按事件 ID 查询相关冲突 (event1Id 或 event2Id 等于指定值)。
     *
     * @param eventId  事件 ID
     * @return 冲突列表
     */
    @Query(value = "SELECT c FROM ScrmCalendarConflictEntity c WHERE (c.event1Id = :eventId OR c.event2Id ="
                          + ":eventId) ORDER BY c.detectedAt DESC")
    List<ScrmCalendarConflictEntity> findByEventId(
                                                  @Param("eventId") Long eventId);

    /**
     * 按事件对查询冲突 (避免重复检测)。
     *
     * @param event1Id 事件1 ID
     * @param event2Id 事件2 ID
     * @return 冲突记录 (存在返回)
     */
    @Query(value = "SELECT c FROM ScrmCalendarConflictEntity c WHERE ((c.event1Id = :event1Id AND c.event2Id ="
                          + ":event2Id) OR (c.event1Id = :event2Id AND c.event2Id = :event1Id))")
    List<ScrmCalendarConflictEntity> findByEventPair(
                                                     @Param("event1Id") Long event1Id,
                                                     @Param("event2Id") Long event2Id);

    /**
     * 按解决状态聚合冲突数 (统计用)。
     *
     * @return Object[]{resolvedStatus, count}
     */
    @Query("SELECT c.resolvedStatus, COUNT(c.id) FROM ScrmCalendarConflictEntity c GROUP BY c.resolvedStatus")
    List<Object[]> countByResolvedStatus();
}
