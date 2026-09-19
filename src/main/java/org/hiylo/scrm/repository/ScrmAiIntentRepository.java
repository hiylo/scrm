/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiIntentRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAiIntentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * SCRM AI 意图数据访问层。
 * <p>
 * 提供按加载启用意图 (按优先级降序, 用于意图识别遍历),
 * 以及增量更新意图匹配次数等能力, 供 {@code ScrmAiAssistantService.detectIntent} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAiIntentRepository extends JpaRepository<ScrmAiIntentEntity, Long>,
        JpaSpecificationExecutor<ScrmAiIntentEntity> {

    /**
     * 按加载全部启用意图并按优先级降序排列 (数字越大越优先)。
     *
     * @return 启用意图列表 (按 priority DESC)
     */
    List<ScrmAiIntentEntity> findByEnabledTrueOrderByPriorityDesc();

    /**
     * 按与意图类别加载启用意图并按优先级降序排列。
     *
     * @param intentCategory 意图类别
     * @return 启用意图列表 (按 priority DESC)
     */
    List<ScrmAiIntentEntity> findByIntentCategoryAndEnabledTrueOrderByPriorityDesc(String intentCategory);

    /**
     * 增量更新意图匹配次数 (避免乐观锁冲突, 直接 SQL 更新)。
     *
     * @param intentId 意图 ID
     * @return 受影响行数 (1 表示更新成功)
     */
    @Modifying
    @Query("UPDATE ScrmAiIntentEntity i SET i.matchCount = i.matchCount + 1 WHERE i.id = :intentId")
    int incrementMatchCount(@Param("intentId") Long intentId);
}
