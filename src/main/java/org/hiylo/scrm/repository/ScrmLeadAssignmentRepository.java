/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLeadAssignmentRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmLeadAssignmentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * 线索分配流水数据访问层。
 * <p>
 * 提供按公海客户 ID 与归属人查询分配历史的能力, 支撑客户详情页分配时间线
 * 与销售工作量统计。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmLeadAssignmentRepository
        extends JpaRepository<ScrmLeadAssignmentEntity, Long>,
        JpaSpecificationExecutor<ScrmLeadAssignmentEntity> {

    /**
     * 按公海客户 ID 查询分配历史, 按 ID 倒序 (最新分配在前)。
     *
     * @param publicSeaCustomerId 公海客户 ID
     * @return 分配流水列表
     */
    List<ScrmLeadAssignmentEntity> findByPublicSeaCustomerIdOrderByIdDesc(Long publicSeaCustomerId);

    /**
     * 按归属人与状态分页查询分配记录 (我的线索流水)。
     *
     *      * @param assignedTo 归属人 userId
     * @param status      分配状态 (可空表示不过滤)
     * @param pageable    分页参数
     * @return 分配流水分页
     */
    Page<ScrmLeadAssignmentEntity> findByAssignedToAndStatus(String assignedTo, String status, Pageable pageable);
}
