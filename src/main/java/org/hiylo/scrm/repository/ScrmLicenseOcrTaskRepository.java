/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLicenseOcrTaskRepository.java
 * Date : 2026/10/23 14:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmLicenseOcrTaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * 证照 OCR 识别任务数据访问层。
 * <p>
 * 提供按归属账号 / 客户 / 复核状态 / 任务状态查询能力, 支持分页。复合过滤
 * （类型 + 状态 + 关键字）通过 {@link JpaSpecificationExecutor} 实现。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmLicenseOcrTaskRepository
        extends JpaRepository<ScrmLicenseOcrTaskEntity, Long>,
                JpaSpecificationExecutor<ScrmLicenseOcrTaskEntity> {

    /**
     * 按归属账号 ID 分页查询（数据隔离入口）。
     *
     * @param ownerAccountId 归属账号 ID
     * @param pageable       分页参数
     * @return 任务分页
     */
    Page<ScrmLicenseOcrTaskEntity> findByOwnerAccountId(Long ownerAccountId, Pageable pageable);

    /**
     * 按关联客户 ID 查询任务列表。
     *
     * @param customerId 客户 ID
     * @return 任务列表
     */
    List<ScrmLicenseOcrTaskEntity> findByCustomerId(Long customerId);

    /**
     * 查询待人工复核的识别任务（证照域复核队列入口）。
     *
     * @param pageable 分页参数
     * @return 待复核任务分页
     */
    Page<ScrmLicenseOcrTaskEntity> findByReviewStatus(String reviewStatus, Pageable pageable);

    /**
     * 统计该主体关联的业务数据数量（公司 360 聚合用）。
     *
     * @param companyId 公司主体 ID
     * @return 数量
     */
    long countByCompanyId(Long companyId);
}