/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerMergeRecordRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCustomerMergeRecordEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 客户合并记录数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCustomerMergeRecordRepository extends JpaRepository<ScrmCustomerMergeRecordEntity, Long>,
        JpaSpecificationExecutor<ScrmCustomerMergeRecordEntity> {

    /**
     * 按 ID 分页查询合并记录
     *
     * @param pageable 分页参数
     * @return 合并记录分页
     */
    Page<ScrmCustomerMergeRecordEntity> findAllByOrderByMergedAtDesc(Pageable pageable);

    /**
     * 按状态分页查询合并记录
     *
     * @param status   合并状态
     * @param pageable 分页参数
     * @return 合并记录分页
     */
    Page<ScrmCustomerMergeRecordEntity> findByStatusOrderByMergedAtDesc(String status, Pageable pageable);

    /**
     * 按主客户 ID 查询合并记录
     *
     * @param primaryCustomerId 主客户 ID
     * @return 合并记录列表
     */
    List<ScrmCustomerMergeRecordEntity> findByPrimaryCustomerId(Long primaryCustomerId);

    /**
     * 统计指定账号的合并记录数
     *
     * @return 合并记录数
     */
}
