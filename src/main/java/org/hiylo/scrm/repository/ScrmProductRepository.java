/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmProductRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * SCRM 商品数据访问层。
 * <p>
 * 提供按商品编码查询、按与编码查询 (数据隔离校验), 以及按统计商品数与库存汇总,
 * 供 {@code ScrmProductOrderService} 使用。复合过滤 (分类 / 品牌 / 状态 / 关键词)
 * 通过 {@link JpaSpecificationExecutor} 动态构建查询条件实现。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmProductRepository extends JpaRepository<ScrmProductEntity, Long>,
        JpaSpecificationExecutor<ScrmProductEntity> {

    /**
     * 按商品编码查询商品 (全局唯一, 编码校验用)。
     *
     * @param productCode 商品编码
     * @return 商品 (可能为空)
     */
    Optional<ScrmProductEntity> findByProductCode(String productCode);


    /**
     * 按统计商品总数 (商品统计用)。
     *
     * @return 商品总数
     */

    /**
     * 按状态统计商品数量 (商品统计 / 状态分布用)。
     *
     * @param status   商品状态
     * @return 数量
     */
    long countByStatus(String status);

    /**
     * 按统计库存总量 (商品统计用)。
     *
     * @return 库存总量 (无记录返回 0)
     */
    @Query("SELECT COALESCE(SUM(p.stock), 0) FROM ScrmProductEntity p")
    Integer sumStock();

    /**
     * 按统计销量总量 (商品统计用)。
     *
     * @return 销量总量 (无记录返回 0)
     */
    @Query("SELECT COALESCE(SUM(p.salesCount), 0) FROM ScrmProductEntity p")
    Integer sumSalesCount();
}
