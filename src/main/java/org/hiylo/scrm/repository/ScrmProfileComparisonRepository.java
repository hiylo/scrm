/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmProfileComparisonRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmProfileComparisonEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 画像对比数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmProfileComparisonRepository extends JpaRepository<ScrmProfileComparisonEntity, Long>,
        JpaSpecificationExecutor<ScrmProfileComparisonEntity> {

    /**
     * 按任一客户 ID 查询对比记录 (客户参与的全部对比)。
     *
     * @param customerId1 客户 ID 1
     * @param customerId2 客户 ID 2
     * @return 对比记录列表
     */
    List<ScrmProfileComparisonEntity> findByCustomerId1OrCustomerId2(Long customerId1, Long customerId2);

    /**
     * 按对比类型查询对比记录。
     *
     * @param comparisonType 对比类型
     * @return 对比记录列表
     */
    List<ScrmProfileComparisonEntity> findByComparisonType(String comparisonType);
}
