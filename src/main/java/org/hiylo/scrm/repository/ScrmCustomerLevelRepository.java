/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLevelRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCustomerLevelEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 客户等级数据访问层。
 * <p>
 * 提供按查询等级、查询默认等级、按等级编码查询、清空默认标记等能力, 供
 * {@code ScrmCustomerLevelService} 使用。等级列表通过
 * {@link #findOrderByLevelOrderAsc(Long)} 按 level_order 升序加载。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCustomerLevelRepository extends JpaRepository<ScrmCustomerLevelEntity, Long>,
        JpaSpecificationExecutor<ScrmCustomerLevelEntity> {

    /**
     * 按查询全部等级并按 level_order 升序返回 (数字越小等级越低, 越大等级越高)。
     *
     * @return 等级列表 (按 level_order ASC)
     */
    List<ScrmCustomerLevelEntity> findAllByOrderByLevelOrderAsc();

    /**
     * 按分页查询等级。
     *
     * @param pageable  分页参数
     * @return 等级分页结果
     */

    /**
     * 按与等级编码查询等级 (用于唯一性校验)。
     *
     * @param levelCode 等级编码
     * @return 等级 (可能为空)
     */
    Optional<ScrmCustomerLevelEntity> findByLevelCode(String levelCode);

    /**
     * 按查询默认等级 (同账号仅可有一个默认等级)。
     *
     * @return 默认等级 (可能为空)
     */
    Optional<ScrmCustomerLevelEntity> findByIsDefaultTrue();

    /**
     * 清空指定账号下全部等级的默认标记 (设置默认等级前调用)。
     *
     * @return 受影响行数
     */
    @Modifying
    @Query("UPDATE ScrmCustomerLevelEntity l SET l.isDefault = false")
    int clearDefaultFlag();

    /**
     * 按与等级 ID 列表批量查询等级 (批量评估/统计用)。
     *
     * @param ids      等级 ID 列表
     * @return 等级列表
     */
    List<ScrmCustomerLevelEntity> findByIdIn(Iterable<Long> ids);
}
