/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFollowUpRecordRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmFollowUpRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 跟进记录数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmFollowUpRecordRepository extends JpaRepository<ScrmFollowUpRecordEntity, Long>,
        JpaSpecificationExecutor<ScrmFollowUpRecordEntity> {

    /**
     * 按客户查询跟进记录列表。
     *
     * @param customerId 客户 ID
     * @return 跟进记录列表
     */
    List<ScrmFollowUpRecordEntity> findByCustomerId(Long customerId);

    /**
     * 按与任务查询跟进记录列表。
     *
     * @param taskId   任务 ID
     * @return 跟进记录列表
     */
    List<ScrmFollowUpRecordEntity> findByTaskId(Long taskId);
}
