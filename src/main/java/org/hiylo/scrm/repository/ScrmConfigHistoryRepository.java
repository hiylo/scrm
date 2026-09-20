/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmConfigHistoryRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmConfigHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 配置变更历史数据访问层。
 * <p>
 * 提供按配置 ID / 配置键 / 变更类型 / 变更人 / 时间区间等条件查询能力,
 * 供 {@code ScrmSystemConfigService} 的变更历史管理与统计使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmConfigHistoryRepository extends JpaRepository<ScrmConfigHistoryEntity, Long>,
        JpaSpecificationExecutor<ScrmConfigHistoryEntity> {

    /**
     * 按与配置 ID 分页查询历史 (按 changedAt DESC)。
     *
     * @param configId 配置 ID
     * @param pageable 分页参数
     * @return 历史分页结果
     */
    Page<ScrmConfigHistoryEntity> findByConfigIdOrderByChangedAtDesc(Long configId, Pageable pageable);

    /**
     * 按与配置键分页查询历史 (按 changedAt DESC)。
     *
     * @param configKey 配置键
     * @param pageable  分页参数
     * @return 历史分页结果
     */
    Page<ScrmConfigHistoryEntity> findByConfigKeyOrderByChangedAtDesc(String configKey, Pageable pageable);

    /**
     * 按与变更人分页查询历史 (按 changedAt DESC)。
     *
     * @param changedBy 变更人
     * @param pageable  分页参数
     * @return 历史分页结果
     */
    Page<ScrmConfigHistoryEntity> findByChangedByOrderByChangedAtDesc(String changedBy, Pageable pageable);

    /**
     * 按查询指定时间区间内的历史 (按 changedAt DESC)。
     *
     * @param startTime 起始时间 (含)
     * @param endTime   结束时间 (含)
     * @return 历史列表
     */
    List<ScrmConfigHistoryEntity> findByChangedAtBetweenOrderByChangedAtDesc(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 按与回滚关联历史 ID 查询回滚记录 (用于回滚历史)。
     *
     * @param rollbackById  回滚关联历史 ID
     * @return 历史列表
     */
    List<ScrmConfigHistoryEntity> findByRollbackById(Long rollbackById);

    /**
     * 按与变更类型聚合历史数 (统计用)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   结束时间 (可空)
     * @return Object[]{changeType, count}
     */
    @Query(value = "SELECT h.changeType, COUNT(h.id) FROM ScrmConfigHistoryEntity h WHERE (:startTime IS NULL OR "
                          + "h.changedAt >= :startTime) AND (:endTime IS NULL OR h.changedAt <= :endTime) GROUP BY "
                          + "h.changeType")
    List<Object[]> countByChangeType(
                                      @Param("startTime") LocalDateTime startTime,
                                      @Param("endTime") LocalDateTime endTime);

    /**
     * 按与变更人聚合历史数 (统计用)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   结束时间 (可空)
     * @return Object[]{changedBy, count}
     */
    @Query(value = "SELECT h.changedBy, COUNT(h.id) FROM ScrmConfigHistoryEntity h WHERE (:startTime IS NULL OR "
                          + "h.changedAt >= :startTime) AND (:endTime IS NULL OR h.changedAt <= :endTime) GROUP BY "
                          + "h.changedBy")
    List<Object[]> countByChangedBy(
                                     @Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);

    /**
     * 统计指定账号与时间区间内的变更总数。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   结束时间 (可空)
     * @return 记录数
     */
    @Query(value = "SELECT COUNT(h.id) FROM ScrmConfigHistoryEntity h WHERE (:startTime IS NULL OR h.changedAt >="
                          + ":startTime) AND (:endTime IS NULL OR h.changedAt <= :endTime)")
    long countByTimeRange(
                                      @Param("startTime") LocalDateTime startTime,
                                      @Param("endTime") LocalDateTime endTime);
}
