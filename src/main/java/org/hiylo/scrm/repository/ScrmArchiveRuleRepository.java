/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmArchiveRuleRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmArchiveRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 归档规则数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmArchiveRuleRepository extends JpaRepository<ScrmArchiveRuleEntity, Long>,
        JpaSpecificationExecutor<ScrmArchiveRuleEntity> {

    /**
     * 按 ID 查询全部规则
     *
     * @return 规则列表
     */

    /**
     * 按启用状态查询规则 (按优先级降序)
     *
     * @param enabled  是否启用
     * @return 规则列表
     */
    List<ScrmArchiveRuleEntity> findByEnabledOrderByPriorityDesc(Boolean enabled);

    /**
     * 按规则名称查询 (唯一性校验用)
     *
     * @param ruleName 规则名称
     * @return 规则 (可能为空)
     */
    List<ScrmArchiveRuleEntity> findByRuleName(String ruleName);
}
