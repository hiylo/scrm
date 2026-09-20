/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContentAssetRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmContentAssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * SCRM 内容素材数据访问层。
 * <p>
 * 提供按素材类型聚合统计 (素材数量与使用次数) 能力,
 * 供 {@code ScrmContentMarketingService} 素材管理与使用统计使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmContentAssetRepository extends JpaRepository<ScrmContentAssetEntity, Long>,
        JpaSpecificationExecutor<ScrmContentAssetEntity> {

    /**
     * 按素材类型聚合素材数量与总使用次数 (统计用)。
     *
     * @return Object[]{assetType, assetCount, usageSum}
     */
    @Query(value = "SELECT a.assetType, COUNT(a.id), COALESCE(SUM(a.usageCount), 0) FROM ScrmContentAssetEntity a "
                          + "GROUP BY a.assetType")
    List<Object[]> aggregateByType();
}
