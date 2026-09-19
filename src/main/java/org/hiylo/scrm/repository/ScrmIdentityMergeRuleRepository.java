/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmIdentityMergeRuleRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmIdentityMergeRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 客户身份合并规则数据访问层。
 * <p>
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmIdentityMergeRuleRepository extends JpaRepository<ScrmIdentityMergeRuleEntity, Long>,
        JpaSpecificationExecutor<ScrmIdentityMergeRuleEntity> {

    /**
     * 按查询全部启用的规则 (按 priority DESC)。
     *
     * @param enabled  启用状态
     * @return 规则列表
     */
    List<ScrmIdentityMergeRuleEntity> findByEnabledOrderByPriorityDesc(Boolean enabled);

    /**
     * 按查询全部规则 (按 priority DESC)。
     *
     * @return 规则列表
     */
    List<ScrmIdentityMergeRuleEntity> findAllByOrderByPriorityDesc();
}
