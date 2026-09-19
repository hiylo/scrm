/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLifecycleHistoryRepository.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCustomerLifecycleHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 客户生命周期变更历史数据访问层。
 * <p>
 * 提供按客户 ID 查询变更历史的能力, 支撑客户详情页生命周期追溯展示。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCustomerLifecycleHistoryRepository
        extends JpaRepository<ScrmCustomerLifecycleHistoryEntity, Long> {

    /**
     * 按客户 ID 分页查询变更历史, 默认按变更时间倒序 (由调用方传入 Pageable 排序)。
     *
     *      * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 变更历史分页
     */
    Page<ScrmCustomerLifecycleHistoryEntity> findByCustomerId(Long customerId, Pageable pageable);

    /**
     * 按客户 ID 查询全部变更历史 (不分页, 用于客户详情页展示完整时间线)。
     * 按 changedAt 倒序排列, 限制由调用方控制 (通常取前 50 条)。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数 (控制返回条数与排序)
     * @return 变更历史列表
     */
    List<ScrmCustomerLifecycleHistoryEntity> findByCustomerIdOrderByIdDesc(Long customerId);
}
