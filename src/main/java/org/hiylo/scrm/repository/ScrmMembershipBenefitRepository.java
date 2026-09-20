/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMembershipBenefitRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmMembershipBenefitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 会员权益数据访问层。
 * <p>
 * 提供按权益编码查询权益、按与等级查询权益列表 (会员可用权益用)、
 * 按状态查询可见权益列表 (热门权益用),
 * 供 {@code ScrmMembershipService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmMembershipBenefitRepository extends JpaRepository<ScrmMembershipBenefitEntity, Long>,
        JpaSpecificationExecutor<ScrmMembershipBenefitEntity> {

    /**
     * 按权益编码查询权益。
     *
     * @param benefitCode 权益编码
     * @return 权益 (可能为空)
     */
    Optional<ScrmMembershipBenefitEntity> findByBenefitCode(String benefitCode);

    /**
     * 按与等级查询权益列表 (按展示顺序升序, 含通用权益 tierId=null)。
     *
     * @param tierId   等级 ID
     * @return 权益列表
     */
    List<ScrmMembershipBenefitEntity> findByTierIdOrderByDisplayOrderAsc(Long tierId);

    /**
     * 按查询通用权益 (tierId 为空, 按展示顺序升序)。
     *
     * @return 权益列表
     */
    List<ScrmMembershipBenefitEntity> findByTierIdIsNullOrderByDisplayOrderAsc();

    /**
     * 按状态查询可见权益列表 (按展示顺序升序, 热门权益用)。
     *
     * @param status    状态
     * @param isVisible 是否可见
     * @return 权益列表
     */
    List<ScrmMembershipBenefitEntity> findByStatusAndIsVisibleOrderByDisplayOrderAsc(String status, Boolean isVisible);

    /**
     * 按与等级查询权益数量 (等级权益分配校验用)。
     *
     * @param tierId   等级 ID
     * @return 权益数量
     */
    long countByTierId(Long tierId);
}
