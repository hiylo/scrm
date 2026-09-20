/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQualityInspectionRuleRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmQualityInspectionRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 质检规则数据访问层。
 * <p>
 * 提供按加载启用规则、按 ID 列表批量查询等能力, 供 {@code ScrmQualityInspectionService} 使用。
 * 列表过滤通过 {@link JpaSpecificationExecutor} 实现多条件动态查询。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmQualityInspectionRuleRepository
        extends JpaRepository<ScrmQualityInspectionRuleEntity, Long>,
                JpaSpecificationExecutor<ScrmQualityInspectionRuleEntity> {

    /**
     * 按加载全部启用规则 (数据隔离用)。
     *
     * @return 启用规则列表
     */
    List<ScrmQualityInspectionRuleEntity> findByEnabledTrue();
}
