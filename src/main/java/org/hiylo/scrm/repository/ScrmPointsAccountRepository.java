/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsAccountRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import jakarta.persistence.LockModeType;
import org.hiylo.scrm.entity.ScrmPointsAccountEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * SCRM 积分账户数据访问层。
 * <p>
 * 提供按 + 客户定位唯一账户 (供 {@code ScrmPointsService.getOrCreateAccount} 使用),
 * 积分排行榜查询与积分总览统计。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmPointsAccountRepository extends JpaRepository<ScrmPointsAccountEntity, Long>,
        JpaSpecificationExecutor<ScrmPointsAccountEntity> {

    /**
     * 按客户查询积分账户 (customer_id 唯一)。
     *
     * @param customerId 客户 ID
     * @return 积分账户 (可能为空)
     */
    Optional<ScrmPointsAccountEntity> findByCustomerId(Long customerId);

    /**
     * 按客户查询积分账户并加悲观写锁 (兑换路径使用, 串行化同一客户并发扣减, 防止积分透支)。
     *
     * @param customerId 客户 ID
     * @return 积分账户 (可能为空)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ScrmPointsAccountEntity a WHERE a.customerId = :customerId")
    Optional<ScrmPointsAccountEntity> findByCustomerIdForUpdate(
                                                                           @Param("customerId") Long customerId);

    /**
     * 积分排行榜: 按当前可用积分倒序分页。
     *
     * @param pageable 分页参数
     * @return 账户分页结果 (按 currentPoints DESC)
     */
    Page<ScrmPointsAccountEntity> findAllByOrderByCurrentPointsDesc(Pageable pageable);

    /**
     * 积分总览统计: 累计获取 / 累计消耗 / 累计过期 / 活跃账户数。
     *
     * @return Object[]: [totalEarned, totalRedeemed, totalExpired, accountCount]
     */
    @Query(value = "SELECT COALESCE(SUM(a.totalEarned), 0), COALESCE(SUM(a.totalRedeemed), 0),"
                          + "COALESCE(SUM(a.totalExpired), 0), COUNT(a) FROM ScrmPointsAccountEntity a")
    Object[] getPointsStats();
}
