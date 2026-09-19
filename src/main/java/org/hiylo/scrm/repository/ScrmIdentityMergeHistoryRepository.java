/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmIdentityMergeHistoryRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmIdentityMergeHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 客户身份合并历史数据访问层。
 * <p>
 * 提供按任务 ID / 客户 ID 查询历史能力, 供
 * {@code ScrmIdentityMergeService} 历史查询与回滚使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmIdentityMergeHistoryRepository extends JpaRepository<ScrmIdentityMergeHistoryEntity, Long>,
        JpaSpecificationExecutor<ScrmIdentityMergeHistoryEntity> {

    /**
     * 按任务 ID 查询合并历史。
     *
     * @param taskId   任务 ID
     * @return 合并历史 (可能为空)
     */
    Optional<ScrmIdentityMergeHistoryEntity> findByTaskId(Long taskId);

    /**
     * 按与源客户查询合并历史 (按 mergedAt 降序)。
     *
     * @param sourceCustomerId 源客户 ID
     * @return 历史列表
     */
    List<ScrmIdentityMergeHistoryEntity> findBySourceCustomerIdOrderByMergedAtDesc(Long sourceCustomerId);

    /**
     * 按与目标客户查询合并历史 (按 mergedAt 降序)。
     *
     * @param targetCustomerId 目标客户 ID
     * @return 历史列表
     */
    List<ScrmIdentityMergeHistoryEntity> findByTargetCustomerIdOrderByMergedAtDesc(Long targetCustomerId);

    /**
     * 统计指定账号的合并历史总数。
     *
     * @return 历史总数
     */

    /**
     * 统计指定账号的已回滚合并数。
     *
     * @param rolledBack 是否已回滚
     * @return 已回滚合并数
     */
    long countByRolledBack(Boolean rolledBack);
}
