/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFunnelStageRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmFunnelStageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 销售漏斗阶段数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmFunnelStageRepository extends JpaRepository<ScrmFunnelStageEntity, Long>,
        JpaSpecificationExecutor<ScrmFunnelStageEntity> {

    /**
     * 按漏斗 ID 查询全部阶段 (默认按 stageOrder 升序, 由调用方排序)。
     *
     * @param funnelId 漏斗 ID
     * @return 阶段列表
     */
    List<ScrmFunnelStageEntity> findByFunnelId(Long funnelId);

    /**
     * 按漏斗 ID 查询阶段列表, 按 stageOrder 升序排列。
     *
     * @param funnelId 漏斗 ID
     * @return 阶段列表
     */
    List<ScrmFunnelStageEntity> findByFunnelIdOrderByStageOrderAsc(Long funnelId);


    /**
     * 删除指定漏斗下的全部阶段 (漏斗删除时级联清理)。
     *
     * @param funnelId 漏斗 ID
     * @return 删除条数
     */
    long deleteByFunnelId(Long funnelId);
}
