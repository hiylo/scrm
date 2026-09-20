/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerDuplicateRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCustomerDuplicateEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

/**
 * SCRM 客户重复检测数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCustomerDuplicateRepository extends JpaRepository<ScrmCustomerDuplicateEntity, Long>,
        JpaSpecificationExecutor<ScrmCustomerDuplicateEntity> {

    /**
     * 按状态分页查询重复检测结果
     *
     * @param status   处理状态
     * @param pageable 分页参数
     * @return 重复检测分页
     */
    Page<ScrmCustomerDuplicateEntity> findByStatusOrderByDetectedAtDesc(String status, Pageable pageable);

    /**
     * 按 ID 分页查询全部重复检测结果
     *
     * @param pageable 分页参数
     * @return 重复检测分页
     */
    Page<ScrmCustomerDuplicateEntity> findAllByOrderByDetectedAtDesc(Pageable pageable);

    /**
     * 按客户 ID 查询其重复检测结果
     *
     * @param customerId 客户 ID
     * @return 重复检测列表
     */
    List<ScrmCustomerDuplicateEntity> findByCustomerId(Long customerId);

    /**
     * 按客户 ID 集合批量查询重复检测结果
     * <p>
     * 用于一次加载多个客户的已有重复记录, 避免循环内逐条查询的 N+1 问题。
     * </p>
     *
     * @param customerIds 客户 ID 集合 (可为空集合, 返回空列表)
     * @return 匹配客户的重复检测列表
     */
    List<ScrmCustomerDuplicateEntity> findByCustomerIdIn(Collection<Long> customerIds);

    /**
     * 按状态统计重复检测数
     *
     * @param status   处理状态
     * @return 数量
     */
    long countByStatus(String status);

    /**
     * 统计指定账号的重复检测总数
     *
     * @return 总数
     */
}
