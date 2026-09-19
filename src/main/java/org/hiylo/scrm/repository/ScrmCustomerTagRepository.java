/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerTagRepository.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCustomerTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * SCRM 客户标签定义数据访问层。
 * <p>
 * 注意: {@code ScrmCustomerTagEntity} 在重构后为<b>标签定义</b> (tagCode/tagType/valueType...),
 * 不再持有 customerId/tagKey/tagValue 赋值字段。客户-标签赋值关系请使用
 * {@link ScrmTagCustomerRepository} + {@code ScrmTagCustomerEntity}。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCustomerTagRepository extends JpaRepository<ScrmCustomerTagEntity, Long> {

    /**
     * 按与标签编码查询标签定义 (用于 tagKey → tagId 解析)。
     *
     * @param tagCode  标签编码
     * @return 标签定义 (可能为空)
     */
    Optional<ScrmCustomerTagEntity> findByTagCode(String tagCode);
}
