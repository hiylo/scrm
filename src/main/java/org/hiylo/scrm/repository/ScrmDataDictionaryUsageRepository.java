/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDataDictionaryUsageRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmDataDictionaryUsageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 数据字典使用记录数据访问层。
 * <p>
 * 提供按字典 / 字典项 / 使用模块查询使用记录、按时间范围清理过期记录、
 * 按字典项统计累计使用次数等便捷方法, 供 {@code ScrmDataDictionaryService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmDataDictionaryUsageRepository extends JpaRepository<ScrmDataDictionaryUsageEntity, Long>,
        JpaSpecificationExecutor<ScrmDataDictionaryUsageEntity> {

    /**
     * 按与字典 ID 查询全部使用记录。
     *
     * @param dictId   字典 ID
     * @return 使用记录列表
     */
    List<ScrmDataDictionaryUsageEntity> findByDictId(Long dictId);

    /**
     *
     * @param dictId      字典 ID
     * @param itemId      字典项 ID (可空)
     * @param usageModule 使用模块
     * @return 使用记录 (可能为空)
     */
    Optional<ScrmDataDictionaryUsageEntity> findByDictIdAndItemIdAndUsageModule(Long dictId, Long itemId, String usageModule);

    /**
     * 按与字典 ID 查询使用记录 (按最近使用时间倒序, 取前 N 条用于热门项统计)。
     *
     * @param dictId   字典 ID
     * @return 使用记录列表 (按 usageCount 倒序)
     */
    @Query(value = "SELECT u FROM ScrmDataDictionaryUsageEntity u WHERE u.dictId = :dictId ORDER BY u.usageCount "
                          + "DESC")
    List<ScrmDataDictionaryUsageEntity> findPopularByDict(@Param("dictId") Long dictId);

    /**
     * 清理早于阈值时间的使用记录 (按 lastUsedAt 过滤)。
     *
     * @param threshold 阈值时间 (lastUsedAt 早于此时间)
     * @return 受影响行数
     */
    @Modifying
    @Query(value = "DELETE FROM ScrmDataDictionaryUsageEntity u WHERE u.lastUsedAt IS NOT NULL AND u.lastUsedAt <"
                          + ":threshold")
    int deleteExpired(@Param("threshold") LocalDateTime threshold);

    /**
     * 按与字典 ID 删除全部使用记录 (字典删除时级联清理)。
     *
     * @param dictId   字典 ID
     * @return 受影响行数
     */
    long deleteByDictId(Long dictId);
}
