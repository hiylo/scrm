/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTagCustomerRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 客户标签关联数据访问层。
 * <p>
 * 提供按客户 ID 查询标签、按标签 ID 分页查询客户、唯一性查询 (覆盖更新)、计数 (刷新
 * 标签客户数)、批量迁移 (标签合并)、批量删除 (规则批量去标) 等能力, 供
 * {@code ScrmTagSystemService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmTagCustomerRepository extends JpaRepository<ScrmTagCustomerEntity, Long>,
        JpaSpecificationExecutor<ScrmTagCustomerEntity> {

    /**
     * 按客户 ID 查询所有标签关联 (获取客户标签列表)。
     *
     * @param customerId 客户 ID
     * @return 标签关联列表
     */
    List<ScrmTagCustomerEntity> findByCustomerId(Long customerId);

    /**
     * 按与标签 ID 分页查询客户关联 (获取标签下客户列表)。
     *
     * @param tagId    标签 ID
     * @param pageable 分页参数
     * @return 客户关联分页结果
     */
    Page<ScrmTagCustomerEntity> findByTagId(Long tagId, Pageable pageable);

    /**
     * 按、客户 ID 与标签 ID 查询关联 (唯一性查询, 用于覆盖更新或检查)。
     *
     * @param customerId 客户 ID
     * @param tagId      标签 ID
     * @return 关联 (可能为空)
     */
    Optional<ScrmTagCustomerEntity> findByCustomerIdAndTagId(Long customerId, Long tagId);

    /**
     * 按与标签 ID 统计客户数 (用于刷新标签的 customer_count)。
     *
     * @param tagId    标签 ID
     * @return 客户数
     */
    long countByTagId(Long tagId);

    /**
     * 按客户 ID 统计标签数 (用于查询客户标签数)。
     *
     * @param customerId 客户 ID
     * @return 标签数
     */
    long countByCustomerId(Long customerId);

    /**
     * 按客户 ID 列表批量查询关联 (批量获取客户标签)。
     *
     * @param customerIds 客户 ID 列表
     * @return 关联列表
     */
    List<ScrmTagCustomerEntity> findByCustomerIdIn(Collection<Long> customerIds);

    /**
     * 按与标签 ID 删除全部关联 (标签删除前级联清理)。
     *
     * @param tagId    标签 ID
     * @return 受影响行数
     */
    @Modifying
    @Query("DELETE FROM ScrmTagCustomerEntity t WHERE t.tagId = :tagId")
    int deleteByTagId(@Param("tagId") Long tagId);

    /**
     * 按客户 ID 删除全部标签关联 (身份合并后清理源客户标签)。
     *
     * @param customerId 客户 ID
     * @return 受影响行数
     */
    @Modifying
    @Query("DELETE FROM ScrmTagCustomerEntity t WHERE t.customerId = :customerId")
    int deleteByCustomerId(@Param("customerId") Long customerId);

    /**
     * 按、客户 ID 与标签 ID 列表批量删除关联 (批量去标)。
     *
     * @param customerId 客户 ID
     * @param tagIds     标签 ID 列表
     * @return 受影响行数
     */
    @Modifying
    @Query("DELETE FROM ScrmTagCustomerEntity t WHERE t.customerId = :customerId AND t.tagId IN :tagIds")
    int deleteByCustomerIdAndTagIdIn(
                                                 @Param("customerId") Long customerId,
                                                 @Param("tagIds") Collection<Long> tagIds);

    /**
     * 按与源标签 ID 将全部客户关联迁移到目标标签 ID (标签合并)。
     *
     * @param sourceTagId   源标签 ID
     * @param targetTagId   目标标签 ID
     * @return 受影响行数
     */
    @Modifying
    @Query("UPDATE ScrmTagCustomerEntity t SET t.tagId = :targetTagId WHERE t.tagId = :sourceTagId")
    int migrateTagRelations(
                            @Param("sourceTagId") Long sourceTagId,
                            @Param("targetTagId") Long targetTagId);
}
