/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAssetRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAssetEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 营销素材数据访问层。
 * <p>
 * 提供按编码查询、按分类 / 类型统计、热门 / 最新 / 即将过期素材查询等能力,
 * 供 {@code ScrmAssetLibraryService} 使用。复杂多条件过滤通过
 * {@link JpaSpecificationExecutor} 由 Service 层动态构造。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAssetRepository extends JpaRepository<ScrmAssetEntity, Long>,
        JpaSpecificationExecutor<ScrmAssetEntity> {

    /**
     * 按与素材编码查询素材。
     *
     * @param assetCode 素材编码
     * @return 素材 (可能为空)
     */
    Optional<ScrmAssetEntity> findByAssetCode(String assetCode);

    /**
     * 判断素材编码是否已存在。
     *
     * @param assetCode 素材编码
     * @return true 表示已存在
     */
    boolean existsByAssetCode(String assetCode);

    /**
     * 按统计素材总数。
     *
     * @return 素材总数
     */

    /**
     * 按与分类 ID 统计素材数。
     *
     * @param categoryId 分类 ID
     * @return 素材数
     */
    long countByCategoryId(Long categoryId);

    /**
     * 按与分类 ID 统计素材总大小。
     *
     * @param categoryId 分类 ID
     * @return 总大小字节
     */
    @Query("SELECT COALESCE(SUM(a.fileSizeBytes), 0) FROM ScrmAssetEntity a WHERE a.categoryId = :categoryId")
    Long sumFileSizeBytesByCategoryId(
                                                  @Param("categoryId") Long categoryId);

    /**
     * 按统计素材总大小。
     *
     * @return 总大小字节
     */
    @Query("SELECT COALESCE(SUM(a.fileSizeBytes), 0) FROM ScrmAssetEntity a")
    Long sumFileSizeBytes();

    /**
     * 热门素材 (按使用次数倒序, 取前 N 条)。
     *
     * @param pageable 分页参数 (控制取前 N 条)
     * @return 素材分页结果
     */
    Page<ScrmAssetEntity> findAllByOrderByUseCountDesc(Pageable pageable);

    /**
     * 最新素材 (按上传时间倒序)。
     *
     * @param pageable 分页参数
     * @return 素材分页结果
     */
    Page<ScrmAssetEntity> findAllByOrderByUploadedAtDesc(Pageable pageable);

    /**
     * 即将过期素材 (过期日期在指定阈值之前且未过期的素材)。
     *
     * @param threshold   过期阈值日期
     * @param now         当前日期
     * @param pageable    分页参数
     * @return 素材分页结果
     */
    Page<ScrmAssetEntity> findByExpiryDateBetweenAndIsExpiredFalse(java.time.LocalDate threshold, java.time.LocalDate now, Pageable pageable);

    /**
     * 查询最近上传时间早于阈值的素材 (用于趋势统计)。
     *
     * @param start    起始时间
     * @param end      截止时间
     * @return 素材列表
     */
    List<ScrmAssetEntity> findByUploadedAtBetween(LocalDateTime start, LocalDateTime end);
}
