/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerRepository.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SCRM 客户数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCustomerRepository extends JpaRepository<ScrmCustomerEntity, Long>,
        JpaSpecificationExecutor<ScrmCustomerEntity> {

    /**
     * 根据归属账号 ID 查询客户列表。
     *
     * @param ownerAccountId 归属账号 ID
     * @return 客户列表
     */
    List<ScrmCustomerEntity> findByOwnerAccountId(Long ownerAccountId);

    /**
     * 根据平台类型与平台客户 UID 查询客户。
     *
     * @param platformType       平台类型
     * @param platformCustomerUid 平台客户 UID
     * @return 客户（可能为空）
     */
    Optional<ScrmCustomerEntity> findByPlatformTypeAndPlatformCustomerUid(
            String platformType, String platformCustomerUid);

    /**
     * 根据平台类型、平台客户 UID 与归属账号 ID 查询客户（唯一性校验用）。
     *
     * @param platformType        平台类型
     * @param platformCustomerUid 平台客户 UID
     * @param ownerAccountId      归属账号 ID
     * @return 客户（可能为空）
     */
    Optional<ScrmCustomerEntity> findByPlatformTypeAndPlatformCustomerUidAndOwnerAccountId(
            String platformType, String platformCustomerUid, Long ownerAccountId);

    /**
     * 根据生命周期阶段查询客户列表。
     *
     * @param lifecycle 生命周期：NEW / ACTIVE / DORMANT / LOST
     * @return 客户列表
     */
    List<ScrmCustomerEntity> findByLifecycle(String lifecycle);

    /**
     * 根据账号 ID 查询客户列表。
     *
     * @return 客户列表
     */

    /**
     * 统计指定账号下的客户总数。
     *
     * @return 客户总数
     */

    /**
     * 按生命周期聚合客户数（看板用, 避免 N+1）。
     *
     * @return Object[]{lifecycle, count}
     */
    @Query(
"SELECT e.lifecycle, COUNT(e.id) FROM ScrmCustomerEntity e GROUP BY e.lifecycle")
    List<Object[]> countByLifecycle();

    /**
     * 按日聚合客户新增数（看板近 7 天新增用, native query 借助 PostgreSQL to_char）。
     *
     * @param from     起始时间（含）
     * @return Object[]{date(yyyy-MM-dd), count}
     */
    default List<Object[]> dailyCountByCreateTime(LocalDateTime from) {
        return DailyStatsRepository.toRows(DailyStatsRepository.getInstance().countByDay(
                "scrm.scrm_customer", "create_time", "",
                Map.of(), from, null));
    }

    /**
     * 查询指定账号下需要跟进提醒的客户 (nextFollowUpAt 非空且早于等于阈值时间)。
     * <p>
     * 由 {@code FollowUpReminderScheduler} 每 10 分钟调用, 阈值通常为当前时间 + 30 分钟,
     * 命中即将到期或已逾期的跟进任务, 触发 FOLLOWUP_REMINDER 通知。
     * </p>
     *
     * @param threshold 阈值时间 (含)
     * @return 待跟进提醒的客户列表
     */
    @Query("SELECT c FROM ScrmCustomerEntity c WHERE c.nextFollowUpAt IS NOT NULL"
            + " AND c.nextFollowUpAt <= :threshold")
    List<ScrmCustomerEntity> findCustomersNeedingFollowUp(
                                                          @Param("threshold") LocalDateTime threshold);

}
