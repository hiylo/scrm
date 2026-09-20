/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTicketHistoryRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmTicketHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 工单流转历史数据访问层。
 * <p>
 * 提供按工单 ID 查询流转历史列表, 供 {@code ScrmTicketService} 历史查询与时间线组装使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmTicketHistoryRepository extends JpaRepository<ScrmTicketHistoryEntity, Long>,
        JpaSpecificationExecutor<ScrmTicketHistoryEntity> {

    /**
     * 按与工单 ID 查询流转历史列表 (按动作时间升序, 时间线组装用)。
     *
     * @param ticketId 工单 ID
     * @return 历史列表
     */
    List<ScrmTicketHistoryEntity> findByTicketIdOrderByActionTimeAsc(Long ticketId);
}
