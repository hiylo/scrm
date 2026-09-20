/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmJourneyStepRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmJourneyStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 旅程步骤数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmJourneyStepRepository extends JpaRepository<ScrmJourneyStepEntity, Long>,
        JpaSpecificationExecutor<ScrmJourneyStepEntity> {

    /**
     * 按旅程 ID 查询全部步骤 (顺序由调用方排序)。
     *
     * @param journeyId 旅程 ID
     * @return 步骤列表
     */
    List<ScrmJourneyStepEntity> findByJourneyId(Long journeyId);

    /**
     * 按旅程 ID 查询步骤列表, 按 stepOrder 升序。
     *
     * @param journeyId 旅程 ID
     * @return 步骤列表
     */
    List<ScrmJourneyStepEntity> findByJourneyIdOrderByStepOrderAsc(Long journeyId);


    /**
     * 查询指定旅程的入口步骤 (isEntryPoint=true)。
     *
     * @param journeyId 旅程 ID
     * @return 入口步骤列表
     */
    List<ScrmJourneyStepEntity> findByJourneyIdAndIsEntryPointTrue(Long journeyId);

    /**
     * 删除指定旅程下的全部步骤 (旅程删除时级联清理)。
     *
     * @param journeyId 旅程 ID
     * @return 删除条数
     */
    long deleteByJourneyId(Long journeyId);
}
