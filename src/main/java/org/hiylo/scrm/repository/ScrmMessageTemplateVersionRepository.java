/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTemplateVersionRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmMessageTemplateVersionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 消息模板版本数据访问层。
 * <p>
 * 提供按模板 ID 查询版本列表、最新版本、激活版本与版本归档等能力, 供
 * {@code ScrmMessageTemplateCenterService} 使用。所有查询均按隔离。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmMessageTemplateVersionRepository
        extends JpaRepository<ScrmMessageTemplateVersionEntity, Long>,
                JpaSpecificationExecutor<ScrmMessageTemplateVersionEntity> {

    /**
     * 按与模板 ID 分页查询版本列表, 按版本号降序排列 (最新版本在前)。
     *
     * @param templateId 模板 ID
     * @param pageable   分页参数
     * @return 版本分页结果（按 versionNumber DESC）
     */
    Page<ScrmMessageTemplateVersionEntity> findByTemplateIdOrderByVersionNumberDesc(Long templateId, Pageable pageable);

    /**
     * 查询模板的最新版本 (versionNumber 最大)。
     *
     * @param templateId 模板 ID
     * @return 最新版本实体（可能为空）
     */
    Optional<ScrmMessageTemplateVersionEntity> findTopByTemplateIdOrderByVersionNumberDesc(Long templateId);

    /**
     * 查询模板的当前激活版本。
     *
     * @param templateId 模板 ID
     * @return 激活版本实体（可能为空）
     */
    Optional<ScrmMessageTemplateVersionEntity> findByTemplateIdAndStatus(Long templateId, String status);

    /**
     * 按与版本 ID 查询版本 (归属校验用)。
     *
     * @param id       版本 ID
     * @return 版本实体（可能为空）
     */
    Optional<ScrmMessageTemplateVersionEntity> findById(Long id);

    /**
     * 统计模板的版本数量 (用于 createVersion 计算下一版本号)。
     *
     * @param templateId 模板 ID
     * @return 版本数量
     */
    long countByTemplateId(Long templateId);

    /**
     * 按与模板 ID 查询全部版本 (版本对比用)。
     *
     * @param templateId 模板 ID
     * @return 版本列表
     */
    List<ScrmMessageTemplateVersionEntity> findByTemplateId(Long templateId);

    /**
     * 将模板下其他版本状态归档 (activateVersion 时先将其他 ACTIVE 置为 ARCHIVED)。
     *
     * @param templateId 模板 ID
     * @return 受影响行数
     */
    @Modifying
    @Query(value = "UPDATE ScrmMessageTemplateVersionEntity v SET v.status = 'ARCHIVED' WHERE v.templateId ="
                          + ":templateId AND v.status = 'ACTIVE'")
    int archiveOtherActiveVersions(
                                   @Param("templateId") Long templateId);
}
