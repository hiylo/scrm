/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSegmentRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmSegmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 客户分群定义数据访问层。
 * <p>
 * 提供按编码查询、按状态枚举活跃分群等能力, 供
 * {@code ScrmSegmentService} 分群管理与批量计算使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmSegmentRepository extends JpaRepository<ScrmSegmentEntity, Long>,
        JpaSpecificationExecutor<ScrmSegmentEntity> {

    /**
     * 按与分群编码查询分群 (编码唯一)。
     *
     * @param segmentCode 分群编码
     * @return 分群 (可能为空)
     */
    Optional<ScrmSegmentEntity> findBySegmentCode(String segmentCode);

    /**
     * 按状态查询分群列表 (批量计算活跃分群用)。
     *
     * @param status   状态
     * @return 分群列表
     */
    List<ScrmSegmentEntity> findByStatus(String status);

    /**
     * 检查账号下分群编码是否已存在 (唯一性校验用)。
     *
     * @param segmentCode 分群编码
     * @return 是否存在
     */
    boolean existsBySegmentCode(String segmentCode);
}
