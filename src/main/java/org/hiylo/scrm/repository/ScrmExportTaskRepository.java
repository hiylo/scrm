/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmExportTaskRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmExportTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * SCRM 数据导出任务数据访问层。
 * <p>
 * 列表过滤 (数据类型/状态/时间范围) 通过 {@link JpaSpecificationExecutor} 实现动态查询。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmExportTaskRepository
        extends JpaRepository<ScrmExportTaskEntity, Long>, JpaSpecificationExecutor<ScrmExportTaskEntity> {
}
