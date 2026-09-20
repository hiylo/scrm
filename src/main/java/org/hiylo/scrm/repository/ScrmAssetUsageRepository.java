/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAssetUsageRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAssetUsageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 营销素材使用记录数据访问层。
 * <p>
 * 提供按素材 / 使用类型 / 使用模块 / 时间范围查询使用记录、按时间清理过期记录、
 * 按素材统计累计使用次数等便捷方法, 供 {@code ScrmAssetLibraryService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAssetUsageRepository extends JpaRepository<ScrmAssetUsageEntity, Long>,
        JpaSpecificationExecutor<ScrmAssetUsageEntity> {

    /**
     * 按与素材 ID 查询全部使用记录。
     *
     * @param assetId  素材 ID
     * @return 使用记录列表
     */
    List<ScrmAssetUsageEntity> findByAssetId(Long assetId);

    /**
     * 按与素材 ID 删除全部使用记录 (素材删除时级联清理)。
     *
     * @param assetId  素材 ID
     * @return 受影响行数
     */
    long deleteByAssetId(Long assetId);

    /**
     * 热门素材 (按素材聚合使用次数倒序, 取前 N)。
     *
     * @param limit    取前 N 条
     * @return 素材 ID 与累计使用次数列表 (Object[]{assetId, totalUsage})
     */
    @Query(value = "SELECT u.assetId, COALESCE(SUM(u.usageCount), 0) FROM ScrmAssetUsageEntity u WHERE u.usageType ="
                          + "'USE' GROUP BY u.assetId ORDER BY COALESCE(SUM(u.usageCount), 0) DESC")
    List<Object[]> findPopularAssetIdsByUsage(@Param("limit") int limit);

    /**
     * 清理早于阈值时间的使用记录 (按 usedAt 过滤)。
     *
     * @param threshold 阈值时间 (usedAt 早于此时间)
     * @return 受影响行数
     */
    @Modifying
    @Query("DELETE FROM ScrmAssetUsageEntity u WHERE u.usedAt < :threshold")
    int deleteExpired(@Param("threshold") LocalDateTime threshold);
}
