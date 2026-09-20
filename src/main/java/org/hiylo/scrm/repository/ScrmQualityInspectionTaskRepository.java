/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQualityInspectionTaskRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmQualityInspectionTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * SCRM 质检任务数据访问层。
 * <p>
 * 任务列表过滤通过 {@link JpaSpecificationExecutor} 实现多条件动态查询。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmQualityInspectionTaskRepository
        extends JpaRepository<ScrmQualityInspectionTaskEntity, Long>,
                JpaSpecificationExecutor<ScrmQualityInspectionTaskEntity> {
}
