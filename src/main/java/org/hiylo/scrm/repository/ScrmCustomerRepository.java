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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * 按归属账号与手机号查询（撞单检测 / 电话联系人去重）。
     *
     * @param ownerAccountId 归属账号 ID
     * @param phone          归一化手机号
     * @return 客户列表（同一号码可能有多条历史记录）
     */
    List<ScrmCustomerEntity> findByOwnerAccountIdAndPhone(Long ownerAccountId, String phone);

    /**
     * 按归属账号与公司主体查询联系人列表（公司 360 视图）。
     *
     * @param ownerAccountId 归属账号 ID
     * @param companyId      公司主体 ID
     * @return 联系人列表
     */
    List<ScrmCustomerEntity> findByOwnerAccountIdAndCompanyId(Long ownerAccountId, Long companyId);

    /**
     * 分页查询「尚未归入任何公司主体」的联系人（待归并清单）。
     *
     * @param ownerAccountId 归属账号 ID
     * @param pageable       分页参数
     * @return 待归并联系人分页
     */
    Page<ScrmCustomerEntity> findByOwnerAccountIdAndCompanyIdIsNull(
            Long ownerAccountId, Pageable pageable);

    /**
     * 按昵称模糊匹配同一归属下的联系人（候选匹配用）。
     *
     * @param ownerAccountId 归属账号 ID
     * @param nicknameLike   昵称 like 模式（调用方拼 %）
     * @return 联系人列表
     */
    List<ScrmCustomerEntity> findByOwnerAccountIdAndNicknameLike(
            Long ownerAccountId, String nicknameLike);

    /**
     * 只取联系人的所属公司主体 ID（避免加载整实体，供业务数据自动挂公司使用）。
     *
     * @param customerId 联系人 ID
     * @return 公司主体 ID（未归属返回 null）
     */
    @Query("select c.companyId from ScrmCustomerEntity c where c.id = :customerId")
    Long findCompanyIdByCustomerId(@Param("customerId") Long customerId);

    /**
     * 统计公司主体下联系人数量。
     *
     * @param companyId 公司主体 ID
     * @return 联系人数量
     */
    long countByCompanyId(Long companyId);

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


    /**
     * 按公司主体统计联系人角色分布（公司 360 用）。
     *
     * @param companyId 公司主体 ID
     * @return 每行 [角色, 数量]
     */
    @Query("select c.contactRole, count(c) from ScrmCustomerEntity c "
            + "where c.companyId = :companyId group by c.contactRole")
    List<Object[]> findByCompanyIdOrderByRole(@Param("companyId") Long companyId);
}
