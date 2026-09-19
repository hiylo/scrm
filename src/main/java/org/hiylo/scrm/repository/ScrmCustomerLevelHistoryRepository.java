/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLevelHistoryRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCustomerLevelHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 客户等级变更历史数据访问层。
 * <p>
 * 提供按客户 ID 查询等级变更历史与最新等级的能力。客户当前等级查询通过
 * {@link #findFirstByCustomerIdOrderByChangedAtDescIdDesc(Long, Long)} 取最新一条
 * 历史记录的 toLevelId 实现。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCustomerLevelHistoryRepository extends JpaRepository<ScrmCustomerLevelHistoryEntity, Long>,
        JpaSpecificationExecutor<ScrmCustomerLevelHistoryEntity> {

    /**
     * 按客户 ID 查询变更历史并按变更时间倒序返回 (分页)。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 历史分页结果 (按 changed_at DESC, id DESC)
     */
    Page<ScrmCustomerLevelHistoryEntity> findByCustomerIdOrderByChangedAtDescIdDesc(Long customerId, Pageable pageable);

    /**
     * 查询客户当前等级: 取最新一条历史记录 (按 changed_at DESC, id DESC 兜底同毫秒排序)。
     *
     * @param customerId 客户 ID
     * @return 最新一条历史记录 (无则返回空, 表示客户未分级)
     */
    Optional<ScrmCustomerLevelHistoryEntity> findFirstByCustomerIdOrderByChangedAtDescIdDesc(Long customerId);

    /**
     * 查询指定客户的全部历史记录 (按变更时间升序, 用于内部溯源)。
     *
     * @param customerId 客户 ID
     * @return 历史记录列表 (按 changed_at ASC)
     */
    List<ScrmCustomerLevelHistoryEntity> findByCustomerIdOrderByChangedAtAsc(Long customerId);

    /**
     * 查询全部已分级客户的去重客户 ID 列表 (用于批量等级评估)。
     *
     * @return 去重后的客户 ID 列表
     */
    @Query("SELECT DISTINCT h.customerId FROM ScrmCustomerLevelHistoryEntity h")
    List<Long> findDistinctCustomerId();

    /**
     * 查询当前账号下全部客户的最新等级记录 (用于等级分布统计)。
     * <p>
     * 通过子查询取每个 customer_id 的最新一条历史记录 (按 changed_at DESC + id DESC) 的
     * to_level_id, 然后在外层按 to_level_id 分组聚合, 返回 [toLevelId, count] 二元组列表。
     * </p>
     *
     * @return 聚合结果列表, 每项 [toLevelId(Long), count(Long)]
     */
    @Query(value = "SELECT h.toLevelId, COUNT(h) FROM ScrmCustomerLevelHistoryEntity h WHERE h.id = (SELECT "
                          + "MAX(h2.id) FROM ScrmCustomerLevelHistoryEntity h2 WHERE h2.customerId = h.customerId) "
                          + "GROUP BY h.toLevelId")
    List<Object[]> getLevelDistribution();

}
