/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerJourneyRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCustomerJourneyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 营销 SOP 客户旅程数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCustomerJourneyRepository extends JpaRepository<ScrmCustomerJourneyEntity, Long>,
        JpaSpecificationExecutor<ScrmCustomerJourneyEntity> {

    /**
     * 按 ID 查询旅程列表。
     *
     * @return 旅程列表
     */

    /**
     * 按状态查询旅程列表。
     *
     * @param status   旅程状态: DRAFT / PUBLISHED / PAUSED / ARCHIVED
     * @return 旅程列表
     */
    List<ScrmCustomerJourneyEntity> findByStatus(String status);
}
