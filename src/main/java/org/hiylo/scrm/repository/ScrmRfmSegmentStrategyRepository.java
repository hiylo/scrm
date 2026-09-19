/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRfmSegmentStrategyRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmRfmSegmentStrategyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM RFM 分群策略数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmRfmSegmentStrategyRepository extends JpaRepository<ScrmRfmSegmentStrategyEntity, Long>,
        JpaSpecificationExecutor<ScrmRfmSegmentStrategyEntity> {

    /**
     * 按分群大类查询启用策略列表 (按优先级降序)。
     *
     * @param segmentCategory 分群大类
     * @return 策略列表
     */
    List<ScrmRfmSegmentStrategyEntity> findBySegmentCategoryAndEnabledTrueOrderByPriorityDesc(String segmentCategory);
}
