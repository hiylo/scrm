/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationTemplateRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmNotificationTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * SCRM 通知模板数据访问层。
 * <p>
 * 提供按 + 模板编码查询 (用于渲染与唯一性校验), 以及模板使用次数自增
 * (供 {@code ScrmNotificationCenterService.sendNotification} 发送成功后刷新)。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmNotificationTemplateRepository extends JpaRepository<ScrmNotificationTemplateEntity, Long>,
        JpaSpecificationExecutor<ScrmNotificationTemplateEntity> {

    /**
     * 按与模板编码查询模板。
     *
     * @param templateCode 模板编码
     * @return 模板实体 (可能为空)
     */
    Optional<ScrmNotificationTemplateEntity> findByTemplateCode(String templateCode);

    /**
     * 统计账号下指定模板编码的数量 (用于唯一性校验)。
     *
     * @param templateCode 模板编码
     * @return 数量
     */
    long countByTemplateCode(String templateCode);

    /**
     * 自增模板使用次数。
     *
     * @param id 模板 ID
     * @return 影响行数
     */
    @Modifying
    @Query("UPDATE ScrmNotificationTemplateEntity t SET t.usageCount = COALESCE(t.usageCount, 0) + 1 "
            + "WHERE t.id = :id")
    int incrementUsageCount(@Param("id") Long id);
}
