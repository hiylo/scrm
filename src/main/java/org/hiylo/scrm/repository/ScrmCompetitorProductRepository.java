/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCompetitorProductRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCompetitorProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 竞品产品数据访问层。
 * <p>
 * 提供按竞品 / 分类查询产品、促销产品查询、近期价格变化产品查询、价格统计聚合等能力,
 * 供 {@code ScrmCompetitorService} 产品管理与价格监测使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCompetitorProductRepository extends JpaRepository<ScrmCompetitorProductEntity, Long>,
        JpaSpecificationExecutor<ScrmCompetitorProductEntity> {

    /**
     * 按与竞品 ID 分页查询产品 (按更新时间倒序)。
     *
     * @param competitorId 竞品 ID
     * @param pageable     分页参数
     * @return 产品分页结果
     */
    Page<ScrmCompetitorProductEntity> findByCompetitorId(Long competitorId, Pageable pageable);

    /**
     * 按与产品分类分页查询产品。
     *
     * @param productCategory 产品分类
     * @param pageable       分页参数
     * @return 产品分页结果
     */
    Page<ScrmCompetitorProductEntity> findByProductCategory(String productCategory, Pageable pageable);

    /**
     * 按查询所有在售且启用监测的产品下最近价格变化的产品列表。
     * <p>查询最近 days 天内有价格变化 (lastPriceChangeDate 在区间内) 的产品。</p>
     *
     * @param since    价格变化起始日期 (含)
     * @return 产品列表
     */
    @Query(value = "SELECT p FROM ScrmCompetitorProductEntity p WHERE p.lastPriceChangeDate IS NOT NULL AND "
                          + "p.lastPriceChangeDate >= :since ORDER BY p.lastPriceChangeDate DESC")
    List<ScrmCompetitorProductEntity> findRecentPriceChanged(
                                                              @Param("since") java.time.LocalDate since);

    /**
     * 按竞品 ID 聚合价格统计 (价格分析用)。
     *
     * @param competitorId 竞品 ID
     * @return Object[]{count, avgCurrent, minCurrent, maxCurrent, avgDiscount}
     */
    @Query("SELECT COUNT(p.id), AVG(p.currentPrice), MIN(p.currentPrice), MAX(p.currentPrice), AVG(p.discountRate) "
            + "FROM ScrmCompetitorProductEntity p WHERE p.competitorId = :competitorId")
    Object[] aggregatePriceStats(@Param("competitorId") Long competitorId);

    /**
     * 统计时间区间内价格变化次数总和与平均涨跌幅 (价格统计用)。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return Object[]{totalChanges, avgChangePercent}
     */
    @Query(value = "SELECT COALESCE(SUM(p.priceChangeCount), 0), COALESCE(AVG(p.lastPriceChangePercent), 0) FROM "
                          + "ScrmCompetitorProductEntity p WHERE (:startTime IS NULL OR p.lastMonitoredAt >= :startTime) AND "
                          + "(:endTime IS NULL OR p.lastMonitoredAt <= :endTime)")
    Object[] aggregatePriceChangeStats(
                                        @Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime);
}
