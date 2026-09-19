/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBlacklistRuleRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmBlacklistRuleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 黑名单风控规则数据访问层。
 * <p>
 * 提供按规则代码查询 (唯一)、按风险类别 / 适用模块分页查询、加载启用规则等能力,
 * 供 {@code ScrmBlacklistService} 规则管理与风险评估使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmBlacklistRuleRepository extends JpaRepository<ScrmBlacklistRuleEntity, Long>,
        JpaSpecificationExecutor<ScrmBlacklistRuleEntity> {

    /**
     * 按与规则代码查询规则 (代码唯一)。
     *
     * @param ruleCode 规则代码
     * @return 规则实体 (可能不存在)
     */
    Optional<ScrmBlacklistRuleEntity> findByRuleCode(String ruleCode);

    /**
     * 按与风险类别分页查询规则。
     *
     * @param riskCategory 风险类别
     * @param pageable     分页参数
     * @return 规则分页结果
     */
    Page<ScrmBlacklistRuleEntity> findByRiskCategory(String riskCategory, Pageable pageable);

    /**
     * 按与适用模块分页查询规则 (LIKE 匹配)。
     *
     * @param applicableModules 适用模块 (LIKE)
     * @param pageable         分页参数
     * @return 规则分页结果
     */
    Page<ScrmBlacklistRuleEntity> findByApplicableModulesContaining(String applicableModules,
                                                                                Pageable pageable);

    /**
     * 加载所有启用的规则 (按优先级升序)。
     *
     * @param enabled  是否启用
     * @return 规则列表
     */
    List<ScrmBlacklistRuleEntity> findByEnabledOrderByPriorityAsc(Boolean enabled);

    /**
     * 按与规则 ID 查询触发历史相关事件 (供触发历史查询使用, 返回规则本身)。
     *
     * @param enabled  是否启用
     * @param pageable 分页参数
     * @return 规则分页结果
     */
    Page<ScrmBlacklistRuleEntity> findByEnabled(Boolean enabled, Pageable pageable);
}
