/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmImportTemplateRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmImportTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * SCRM 数据导入模板数据访问层。
 * <p>
 * 提供按加载数据类型的模板能力。列表过滤通过 {@link JpaSpecificationExecutor}
 * 实现多条件动态查询 (数据类型/启用状态/关键字)。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmImportTemplateRepository
        extends JpaRepository<ScrmImportTemplateEntity, Long>, JpaSpecificationExecutor<ScrmImportTemplateEntity> {
}
