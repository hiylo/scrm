/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRiskRuleRepository.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmRiskRuleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 风险规则数据访问层。
 * <p>
 * 提供按规则代码查询、加载启用规则、分页过滤等能力, 供 {@code ScrmRiskRuleService} 与
 * 规则评估引擎使用。规则评估时通过 {@link #findByEnabledTrueOrderByPriorityAsc()}
 * 加载全部启用规则并按优先级升序遍历。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmRiskRuleRepository extends JpaRepository<ScrmRiskRuleEntity, Long>,
        JpaSpecificationExecutor<ScrmRiskRuleEntity> {

    /**
     * 根据规则代码查询规则。
     *
     * @param ruleCode 规则代码
     * @return 规则实体（可能为空）
     */
    Optional<ScrmRiskRuleEntity> findByRuleCode(String ruleCode);

    /**
     * 加载全部启用规则并按优先级升序排列（数字越小越优先）。
     * <p>规则评估引擎调用此方法获取待评估规则集。</p>
     *
     * @return 启用规则列表（按 priority ASC）
     */
    List<ScrmRiskRuleEntity> findByEnabledTrueOrderByPriorityAsc();

    /**
     * 按启用状态分页查询规则。
     *
     * @param enabled  启用状态
     * @param pageable 分页参数
     * @return 规则分页结果
     */
    Page<ScrmRiskRuleEntity> findByEnabled(Boolean enabled, Pageable pageable);

    /**
     * 按规则名称或代码模糊匹配分页查询。
     *
     * @param name     规则名称关键字
     * @param code     规则代码关键字
     * @param pageable 分页参数
     * @return 规则分页结果
     */
    Page<ScrmRiskRuleEntity> findByRuleNameContainingOrRuleCodeContaining(String name, String code, Pageable pageable);

    /**
     * 按查询全部规则并按优先级升序排列。
     *
     * @return 规则列表（按 priority ASC）
     */
    @Query("SELECT r FROM ScrmRiskRuleEntity r ORDER BY r.priority ASC")
    List<ScrmRiskRuleEntity> findAllByOrderByPriorityAsc();

}
