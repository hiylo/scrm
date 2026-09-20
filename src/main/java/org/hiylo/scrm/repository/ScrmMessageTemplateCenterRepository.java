/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTemplateCenterRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmMessageTemplateCenterEntity;
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
 * SCRM 消息模板中心模板定义数据访问层。
 * <p>
 * 提供按编码、分组、渠道、场景、状态、审核状态查询与使用统计更新等能力, 供
 * {@code ScrmMessageTemplateCenterService} 使用。所有查询均按隔离。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmMessageTemplateCenterRepository
        extends JpaRepository<ScrmMessageTemplateCenterEntity, Long>,
                JpaSpecificationExecutor<ScrmMessageTemplateCenterEntity> {

    /**
     * 按与模板编码查询模板 (数据隔离 + 业务编码唯一)。
     *
     * @param templateCode 模板编码
     * @return 模板实体（可能为空）
     */
    Optional<ScrmMessageTemplateCenterEntity> findByTemplateCode(String templateCode);

    /**
     * 按与分组 ID 分页查询模板。
     *
     * @param groupId  分组 ID
     * @param pageable 分页参数
     * @return 模板分页结果
     */
    Page<ScrmMessageTemplateCenterEntity> findByGroupId(Long groupId, Pageable pageable);

    /**
     * 统计账号下指定分组的模板数量 (用于 updateGroupStats)。
     *
     * @param groupId  分组 ID
     * @return 模板数量
     */
    long countByGroupId(Long groupId);

    /**
     * 按与审核状态分页查询模板 (待审核列表)。
     *
     * @param reviewStatus 审核状态
     * @param pageable     分页参数
     * @return 模板分页结果
     */
    Page<ScrmMessageTemplateCenterEntity> findByReviewStatus(String reviewStatus, Pageable pageable);

    /**
     * 按状态分页查询模板。
     *
     * @param status   状态
     * @param pageable 分页参数
     * @return 模板分页结果
     */
    Page<ScrmMessageTemplateCenterEntity> findByStatus(String status, Pageable pageable);

    /**
     * 查询账号下使用次数最高的模板 (热门模板)。
     *
     * @param pageable 分页参数 (limit 通过 pageable 控制)
     * @return 模板分页结果（按 usageCount DESC）
     */
    Page<ScrmMessageTemplateCenterEntity> findAllByOrderByUsageCountDesc(Pageable pageable);

    /**
     * 查询账号下指定渠道的模板 (channels 字段包含该渠道)。
     *
     * @param channel  渠道（模糊匹配）
     * @param pageable 分页参数
     * @return 模板分页结果
     */
    Page<ScrmMessageTemplateCenterEntity> findByChannelsContaining(String channel, Pageable pageable);

    /**
     * 查询账号下指定场景的模板 (applicableScenarios 字段包含该场景)。
     *
     * @param scenario   场景（模糊匹配）
     * @param pageable   分页参数
     * @return 模板分页结果
     */
    Page<ScrmMessageTemplateCenterEntity> findByApplicableScenariosContaining(String scenario, Pageable pageable);

    /**
     * 查询账号下全量已发布模板 (渠道适配加载用)。
     *
     * @return 已发布模板列表
     */
    List<ScrmMessageTemplateCenterEntity> findByStatus(String status);

    /**
     * 将指定分组下的模板迁移到目标分组 (moveTemplatesToGroup 用)。
     *
     * @param sourceGroupId  源分组 ID
     * @param targetGroupId  目标分组 ID
     * @param targetGroupName 目标分组名称
     * @return 受影响行数
     */
    @Modifying
    @Query(value = "UPDATE ScrmMessageTemplateCenterEntity t SET t.groupId = :targetGroupId, t.groupName ="
                          + ":targetGroupName WHERE t.groupId = :sourceGroupId")
    int moveTemplatesToGroup(
                             @Param("sourceGroupId") Long sourceGroupId,
                             @Param("targetGroupId") Long targetGroupId,
                             @Param("targetGroupName") String targetGroupName);
}
