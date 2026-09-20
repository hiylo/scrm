/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMembershipTierRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmMembershipTierEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 会员等级数据访问层。
 * <p>
 * 提供按等级编码查询等级、按查询启用等级列表 (按等级序号升序)、
 * 按与等级序号查询上下相邻等级 (升级/降级进度计算用),
 * 供 {@code ScrmMembershipService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmMembershipTierRepository extends JpaRepository<ScrmMembershipTierEntity, Long>,
        JpaSpecificationExecutor<ScrmMembershipTierEntity> {

    /**
     * 按等级编码查询等级。
     *
     * @param tierCode 等级编码
     * @return 等级 (可能为空)
     */
    Optional<ScrmMembershipTierEntity> findByTierCode(String tierCode);

    /**
     * 按查询启用的等级列表 (按等级序号升序)。
     *
     * @param enabled  是否启用
     * @return 等级列表
     */
    List<ScrmMembershipTierEntity> findByEnabledOrderByTierLevelAsc(Boolean enabled);

    /**
     * 按查询所有等级列表 (按等级序号升序)。
     *
     * @return 等级列表
     */
    List<ScrmMembershipTierEntity> findAllByOrderByTierLevelAsc();

    /**
     * 按与等级序号查询等级 (序号唯一性校验用)。
     *
     * @param tierLevel 等级序号
     * @return 等级 (可能为空)
     */
    Optional<ScrmMembershipTierEntity> findByTierLevel(Integer tierLevel);

    /**
     * 按查询等级序号大于指定值的等级列表 (按等级序号升序, 下一等级查询用)。
     *
     * @param tierLevel 当前等级序号
     * @return 等级列表
     */
    List<ScrmMembershipTierEntity> findByTierLevelGreaterThanOrderByTierLevelAsc(Integer tierLevel);

    /**
     * 按查询等级序号小于指定值的等级列表 (按等级序号降序, 上一等级查询用)。
     *
     * @param tierLevel 当前等级序号
     * @return 等级列表
     */
    List<ScrmMembershipTierEntity> findByTierLevelLessThanOrderByTierLevelDesc(Integer tierLevel);
}
