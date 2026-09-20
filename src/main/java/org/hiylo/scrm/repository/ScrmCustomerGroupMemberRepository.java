/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerGroupMemberRepository.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCustomerGroupMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * SCRM 客户-分组关联数据访问层。
 * <p>
 * 提供按分组 / 客户维度的查询、存在性校验与删除能力，支撑
 * {@code ScrmCustomerService} 的加入分组、移出分组与成员查询逻辑。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCustomerGroupMemberRepository extends JpaRepository<ScrmCustomerGroupMemberEntity, Long> {

    /**
     * 根据分组 ID 查询全部成员关联记录。
     *
     * @param groupId 分组 ID
     * @return 成员关联列表
     */
    List<ScrmCustomerGroupMemberEntity> findByGroupId(Long groupId);

    /**
     * 根据客户 ID 查询其归属的全部分组关联记录。
     *
     * @param customerId 客户 ID
     * @return 分组关联列表
     */
    List<ScrmCustomerGroupMemberEntity> findByCustomerId(Long customerId);

    /**
     * 删除指定分组与客户的关联记录（移出分组）。
     *
     * @param groupId    分组 ID
     * @param customerId 客户 ID
     */
    void deleteByGroupIdAndCustomerId(Long groupId, Long customerId);

    /**
     * 判断指定客户是否已存在于指定分组（幂等校验）。
     *
     * @param groupId    分组 ID
     * @param customerId 客户 ID
     * @return 已存在返回 true
     */
    boolean existsByGroupIdAndCustomerId(Long groupId, Long customerId);

    /**
     * 删除指定分组的全部成员关联记录（用于重新同步群成员）。
     *
     * @param groupId 分组 ID
     */
    void deleteByGroupId(Long groupId);

    /**
     * 删除指定客户在所有分组中的成员关联记录（级联清理用）。
     *
     * @param customerId 客户 ID
     */
    void deleteByCustomerId(Long customerId);
}
