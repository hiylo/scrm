/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmEngagementLevelRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmEngagementLevelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 互动活跃度等级数据访问层。
 * <p>
 * 提供按加载启用等级 (供 {@code ScrmEngagementScoreService.determineLevel} 匹配),
 * 由 Service 层按 priority 倒序选择首个 minScore <= score 的等级。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmEngagementLevelRepository extends JpaRepository<ScrmEngagementLevelEntity, Long>,
        JpaSpecificationExecutor<ScrmEngagementLevelEntity> {

    /**
     * 加载账号下所有启用等级 (供 determineLevel 匹配, 由 Service 排序)。
     *
     * @return 启用等级列表
     */
    List<ScrmEngagementLevelEntity> findByEnabledTrue();
}
