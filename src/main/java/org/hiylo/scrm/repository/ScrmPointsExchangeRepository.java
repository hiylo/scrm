/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsExchangeRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import jakarta.persistence.LockModeType;
import org.hiylo.scrm.entity.ScrmPointsExchangeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * SCRM 积分兑换商品数据访问层。
 * <p>
 * 提供兑换商品的 CRUD 与分页查询 (基于 {@link JpaSpecificationExecutor} 支持按分类/状态过滤)。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmPointsExchangeRepository extends JpaRepository<ScrmPointsExchangeEntity, Long>,
        JpaSpecificationExecutor<ScrmPointsExchangeEntity> {

    /**
     * 按主键查询兑换商品并加悲观写锁 (兑换路径使用, 串行化同一商品并发兑换, 防止库存超卖)。
     *
     * @param id 商品 ID
     * @return 兑换商品 (可能为空)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM ScrmPointsExchangeEntity e WHERE e.id = :id")
    Optional<ScrmPointsExchangeEntity> findByIdForUpdate(@Param("id") Long id);

    /**
     * 原子扣减库存并累计已兑换数量 (避免基于读到的旧值回写)。
     * <p>WHERE 中带 {@code stock_quantity >= :qty} 守卫, 库存不足时更新 0 行, 由调用方
     * 将 0 行视为库存不足并回滚事务。</p>
     *
     * @param id  商品 ID
     * @param qty 兑换数量
     * @return 受影响行数 (0 表示库存不足)
     */
    @Modifying
    @Query(value = "UPDATE ScrmPointsExchangeEntity e SET e.stockQuantity = e.stockQuantity - :qty,"
                          + "e.exchangedQuantity = COALESCE(e.exchangedQuantity, 0) + :qty WHERE e.id = :id AND "
                          + "e.stockQuantity >= :qty")
    int deductStock(@Param("id") Long id, @Param("qty") int qty);
}
