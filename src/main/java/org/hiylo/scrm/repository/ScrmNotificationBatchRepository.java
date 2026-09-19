/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationBatchRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmNotificationBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * SCRM 通知批次数据访问层。
 * <p>
 * 提供标准的 JPA 与 Specification 查询能力, 供
 * {@code ScrmNotificationCenterService} 的批次列表与进度查询使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmNotificationBatchRepository extends JpaRepository<ScrmNotificationBatchEntity, Long>,
        JpaSpecificationExecutor<ScrmNotificationBatchEntity> {
}
