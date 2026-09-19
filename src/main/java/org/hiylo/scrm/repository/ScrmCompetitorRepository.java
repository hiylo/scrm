/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCompetitorRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCompetitorEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * SCRM 竞品信息数据访问层。
 * <p>
 * 提供按编码查询竞品 (唯一)、按威胁等级 / 行业分页查询等能力,
 * 供 {@code ScrmCompetitorService} 竞品管理与监测使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCompetitorRepository extends JpaRepository<ScrmCompetitorEntity, Long>,
        JpaSpecificationExecutor<ScrmCompetitorEntity> {

    /**
     * 按与编码查询竞品 (编码唯一)。
     *
     * @param competitorCode 竞品编码
     * @return 竞品实体 (可能不存在)
     */
    Optional<ScrmCompetitorEntity> findByCompetitorCode(String competitorCode);

    /**
     * 按与威胁等级分页查询竞品 (按威胁等级排序, 再按更新时间倒序)。
     *
     * @param threatLevel 威胁等级
     * @param pageable   分页参数
     * @return 竞品分页结果
     */
    Page<ScrmCompetitorEntity> findByThreatLevel(String threatLevel, Pageable pageable);

    /**
     * 按与行业分页查询竞品。
     *
     * @param industry 行业
     * @param pageable 分页参数
     * @return 竞品分页结果
     */
    Page<ScrmCompetitorEntity> findByIndustry(String industry, Pageable pageable);
}
