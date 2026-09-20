/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerMembershipRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCustomerMembershipEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 客户会员数据访问层。
 * <p>
 * 提供按客户 ID 与会员卡号查询会员、按与等级统计会员数 (等级统计刷新用)、
 * 按状态统计会员数 (活跃率统计用)、按与等级序号查询可升级/降级风险会员列表,
 * 供 {@code ScrmMembershipService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCustomerMembershipRepository extends JpaRepository<ScrmCustomerMembershipEntity, Long>,
        JpaSpecificationExecutor<ScrmCustomerMembershipEntity> {

    /**
     * 按会员卡号查询会员。
     *
     * @param memberCardNo 会员卡号
     * @return 会员 (可能为空)
     */
    Optional<ScrmCustomerMembershipEntity> findByMemberCardNo(String memberCardNo);

    /**
     * 按客户 ID 查询会员 (同账号下客户唯一)。
     *
     * @param customerId 客户 ID
     * @return 会员 (可能为空)
     */
    Optional<ScrmCustomerMembershipEntity> findByCustomerId(Long customerId);

    /**
     * 按与等级统计会员数量 (等级统计刷新用)。
     *
     * @param tierId   等级 ID
     * @return 会员数量
     */
    long countByTierId(Long tierId);

    /**
     * 按状态统计会员数量 (活跃率统计用)。
     *
     * @param status   会员状态
     * @return 会员数量
     */
    long countByMembershipStatus(String status);

    /**
     * 按状态集合统计会员数量 (留存统计用)。
     *
     * @param statuses 状态集合
     * @return 会员数量
     */
    long countByMembershipStatusIn(List<String> statuses);

    /**
     * 按统计累计消费 (会员收入统计用, 时间范围按创建时间过滤)。
     * <p>startTime/endTime 为可空, 为空时不限制对应时间边界。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 累计消费 (无记录返回 null)
     */
    @Query(value = "SELECT COALESCE(SUM(m.totalSpend), 0) FROM ScrmCustomerMembershipEntity m WHERE (:startTime IS "
                          + "NULL OR m.createTime >= :startTime) AND (:endTime IS NULL OR m.createTime <= :endTime)")
    Double sumTotalSpend(
                                  @Param("startTime") java.time.LocalDateTime startTime,
                                  @Param("endTime") java.time.LocalDateTime endTime);

    /**
     * 按与等级到期日范围查询会员列表 (即将到期会员扫描用, 按到期日升序)。
     *
     * @param start    起始到期日 (含)
     * @param end      截止到期日 (含)
     * @return 会员列表
     */
    List<ScrmCustomerMembershipEntity> findByTierExpiryDateBetweenOrderByTierExpiryDateAsc(LocalDate start, LocalDate end);
}
