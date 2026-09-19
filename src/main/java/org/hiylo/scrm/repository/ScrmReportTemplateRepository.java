/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReportTemplateRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmReportTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 自定义报表模板数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmReportTemplateRepository extends JpaRepository<ScrmReportTemplateEntity, Long>,
        JpaSpecificationExecutor<ScrmReportTemplateEntity> {

    /**
     * 按 ID 查询公开报表模板 (公开模板同账号所有用户可见)。
     *
     * @param isPublic 是否公开
     * @return 模板列表
     */
    List<ScrmReportTemplateEntity> findByIsPublic(Boolean isPublic);

    /**
     * 按报表类型查询模板列表。
     *
     * @param reportType 报表类型: CUSTOMER / OPPORTUNITY / CAMPAIGN / ...
     * @return 模板列表
     */
    List<ScrmReportTemplateEntity> findByReportType(String reportType);

    /**
     * 按创建人查询模板列表 (我创建的模板)。
     *
     * @param createdBy 创建人用户 ID
     * @return 模板列表
     */
    List<ScrmReportTemplateEntity> findByCreatedBy(String createdBy);
}
