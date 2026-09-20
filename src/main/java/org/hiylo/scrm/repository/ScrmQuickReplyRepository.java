/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQuickReplyRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmQuickReplyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 快捷回复条目数据访问层。
 * <p>
 * 提供快捷回复条目的基础 CRUD 与多条件过滤能力, 复杂多条件过滤通过
 * {@link JpaSpecificationExecutor} 由 Service 层动态构造。按快捷键匹配通过
 * {@link #findByShortcut} 提供初步查询, 个人专属回复过滤在 Service 层完成。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmQuickReplyRepository extends JpaRepository<ScrmQuickReplyEntity, Long>,
        JpaSpecificationExecutor<ScrmQuickReplyEntity> {

    /**
     * 按与快捷键查询回复 (匹配多条时由 Service 层按个人专属 / 平台 / 状态二次过滤)。
     *
     * @param shortcut 快捷键
     * @return 候选回复列表
     */
    List<ScrmQuickReplyEntity> findByShortcut(String shortcut);

    /**
     * 按与分类 ID 查询回复 (用于级联删除前的引用检查 / 排序)。
     *
     * @param categoryId 分类 ID
     * @return 回复列表
     */
    List<ScrmQuickReplyEntity> findByCategoryId(Long categoryId);

    /**
     * 按与快捷键查询唯一回复 (用于快捷键唯一性校验)。
     *
     * @param shortcut 快捷键
     * @return 回复 (可能为空)
     */
    Optional<ScrmQuickReplyEntity> findFirstByShortcut(String shortcut);
}
