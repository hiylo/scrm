/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmProfileTemplateRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmProfileTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 画像模板数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmProfileTemplateRepository extends JpaRepository<ScrmProfileTemplateEntity, Long>,
        JpaSpecificationExecutor<ScrmProfileTemplateEntity> {

    /**
     * 按与模板编码查询模板。
     *
     * @param templateCode 模板编码
     * @return 模板 (可能为空)
     */
    Optional<ScrmProfileTemplateEntity> findByTemplateCode(String templateCode);

    /**
     * 校验模板编码是否已存在 (同账号唯一性校验)。
     *
     * @param templateCode 模板编码
     * @return 已存在返回 true
     */
    boolean existsByTemplateCode(String templateCode);

    /**
     * 按与启用状态查询模板列表。
     *
     * @param enabled  是否启用
     * @return 模板列表
     */
    List<ScrmProfileTemplateEntity> findByEnabled(Boolean enabled);

    /**
     * 查询账号下的默认模板。
     *
     * @return 默认模板 (可能为空)
     */
    Optional<ScrmProfileTemplateEntity> findByIsDefaultTrue();

    /**
     * 清除账号下其他模板的默认标记 (设置新默认模板时调用)。
     *
     * @param excludeTemplateId 排除的模板 ID
     * @return 更新条数
     */
    @Modifying
    @Query("UPDATE ScrmProfileTemplateEntity t SET t.isDefault = false WHERE t.id <> :excludeTemplateId")
    int clearDefaultFlag(
                          @Param("excludeTemplateId") Long excludeTemplateId);
}
