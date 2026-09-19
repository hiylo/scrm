/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSalesAchievementRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmSalesAchievementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;

/**
 * SCRM 销售达成记录数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmSalesAchievementRepository extends JpaRepository<ScrmSalesAchievementEntity, Long>,
        JpaSpecificationExecutor<ScrmSalesAchievementEntity> {

    /**
     * 按目标 ID 查询达成记录列表 (按达成日期升序)。
     *
     * @param targetId 目标 ID
     * @return 达成记录列表
     */
    List<ScrmSalesAchievementEntity> findByTargetIdOrderByAchievementDateAsc(Long targetId);

    /**
     * 按 ID、目标 ID 与达成日期范围查询达成记录。
     *
     * @param targetId        目标 ID
     * @param achievementDate 达成日期下限 (含)
     * @param achievementDate2 达成日期上限 (含)
     * @return 达成记录列表
     */
    List<ScrmSalesAchievementEntity> findByTargetIdAndAchievementDateBetweenOrderByAchievementDateAsc(Long targetId, LocalDate achievementDate, LocalDate achievementDate2);

    /**
     * 按目标 ID 删除达成记录 (重算目标时清理)。
     *
     * @param targetId 目标 ID
     * @return 删除条数
     */
    long deleteByTargetId(Long targetId);
}
