/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoReplyTemplateRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAutoReplyTemplateEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * SCRM 消息自动回复模板数据访问层。
 * <p>
 * 提供按与模板类型/分类查询、启用/禁用状态过滤与使用次数增量更新等能力,
 * 供 {@code ScrmAutoReplyService} 模板管理与渲染使用。模板由规则通过
 * {@code replyTemplateId} 引用, 命中后由 renderTemplate 渲染为最终回复内容。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAutoReplyTemplateRepository extends JpaRepository<ScrmAutoReplyTemplateEntity, Long>,
        JpaSpecificationExecutor<ScrmAutoReplyTemplateEntity> {

    /**
     * 按分页查询模板并按创建时间倒序返回。
     *
     * @param pageable 分页参数
     * @return 模板分页结果
     */

    /**
     * 按与模板类型分页查询模板。
     *
     * @param templateType 模板类型
     * @param pageable     分页参数
     * @return 模板分页结果
     */
    Page<ScrmAutoReplyTemplateEntity> findByTemplateType(String templateType, Pageable pageable);

    /**
     * 增量更新模板的使用次数（避免乐观锁冲突, 直接 SQL 更新）。
     *
     * @param templateId 模板 ID
     * @return 受影响行数 (1 表示更新成功)
     */
    @Modifying
    @Query("UPDATE ScrmAutoReplyTemplateEntity t SET t.usageCount = t.usageCount + 1 "
            + "WHERE t.id = :templateId")
    int incrementUsageCount(@Param("templateId") Long templateId);
}
