/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAbTestAssignmentRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAbTestAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * SCRM A/B 测试客户分配数据访问层。
 * <p>
 * 提供按测试与客户加载分配记录能力, 供 {@code ScrmAbTestService} 客户分配、转化记录
 * 与结果统计使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAbTestAssignmentRepository extends JpaRepository<ScrmAbTestAssignmentEntity, Long>,
        JpaSpecificationExecutor<ScrmAbTestAssignmentEntity> {

    /**
     * 按测试与客户加载分配记录 (同测试同客户唯一)。
     *
     * @param testId     测试 ID
     * @param customerId 客户 ID
     * @return 分配记录 (可能不存在)
     */
    Optional<ScrmAbTestAssignmentEntity> findByTestIdAndCustomerId(Long testId, Long customerId);
}
