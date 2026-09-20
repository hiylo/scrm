/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFollowUpTemplateRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmFollowUpTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 跟进模板数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmFollowUpTemplateRepository extends JpaRepository<ScrmFollowUpTemplateEntity, Long>,
        JpaSpecificationExecutor<ScrmFollowUpTemplateEntity> {

    /**
     * 按查询全部模板。
     *
     * @return 模板列表
     */

    /**
     * 按与启用状态查询模板。
     *
     * @param enabled  启用状态
     * @return 模板列表
     */
    List<ScrmFollowUpTemplateEntity> findByEnabled(Boolean enabled);
}
