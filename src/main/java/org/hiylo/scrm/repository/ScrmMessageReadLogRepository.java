/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageReadLogRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmMessageReadLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 消息阅读日志数据访问层。
 * <p>
 * 提供按消息跟踪 ID 查询阅读日志列表、按阅读者查询、按跟踪 ID + 阅读者查询最近一次阅读
 * (用于判断重复阅读与计算阅读序号), 以及阅读统计 (阅读次数 / 独立阅读者 / 平均阅读时长 /
 * 重复阅读数) 所需的聚合查询, 供 {@code ScrmMessageTrackingService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmMessageReadLogRepository extends JpaRepository<ScrmMessageReadLogEntity, Long>,
        JpaSpecificationExecutor<ScrmMessageReadLogEntity> {

    /**
     * 按消息跟踪 ID 查询阅读日志列表 (按阅读时间升序)。
     *
     * @param messageTrackingId 消息跟踪 ID
     * @return 阅读日志列表 (readAt ASC)
     */
    List<ScrmMessageReadLogEntity> findByMessageTrackingIdOrderByReadAtAsc(Long messageTrackingId);

    /**
     * 按消息跟踪 ID 分页查询阅读日志 (按阅读时间倒序)。
     *
     * @param messageTrackingId 消息跟踪 ID
     * @param pageable          分页参数
     * @return 阅读日志分页结果
     */
    Page<ScrmMessageReadLogEntity> findByMessageTrackingId(Long messageTrackingId, Pageable pageable);

    /**
     * 按消息跟踪 ID + 阅读者 ID 查询最近一次阅读日志 (用于判断重复阅读与计算阅读序号)。
     *
     * @param messageTrackingId 消息跟踪 ID
     * @param readerId          阅读者 ID
     * @return 最近一次阅读日志 (可能为空)
     */
    Optional<ScrmMessageReadLogEntity> findFirstByMessageTrackingIdAndReaderIdOrderBySequenceDesc(
            Long messageTrackingId, String readerId);

    /**
     * 统计消息跟踪记录的阅读日志总数。
     *
     * @param messageTrackingId 消息跟踪 ID
     * @return 阅读日志总数
     */
    long countByMessageTrackingId(Long messageTrackingId);

    /**
     * 统计消息跟踪记录的独立阅读者数。
     *
     * @param messageTrackingId 消息跟踪 ID
     * @return 独立阅读者数
     */
    @Query(value = "SELECT COUNT(DISTINCT l.readerId) FROM ScrmMessageReadLogEntity l WHERE l.messageTrackingId ="
                          + ":messageTrackingId")
    long countDistinctReader(
                             @Param("messageTrackingId") Long messageTrackingId);

    /**
     * 统计消息跟踪记录的重复阅读数。
     *
     * @param messageTrackingId 消息跟踪 ID
     * @return 重复阅读数
     */
    @Query(value = "SELECT COUNT(l) FROM ScrmMessageReadLogEntity l WHERE l.messageTrackingId = :messageTrackingId "
                          + "AND l.isRepeatedRead = true")
    long countRepeatedRead(
                           @Param("messageTrackingId") Long messageTrackingId);

    /**
     * 统计消息跟踪记录的平均阅读时长 (秒)。
     *
     * @param messageTrackingId 消息跟踪 ID
     * @return 平均阅读时长 (无记录返回 null)
     */
    @Query(value = "SELECT AVG(l.readDurationSeconds) FROM ScrmMessageReadLogEntity l WHERE l.messageTrackingId ="
                          + ":messageTrackingId")
    Double avgReadDuration(
                           @Param("messageTrackingId") Long messageTrackingId);

    /**
     * 按消息跟踪 ID 查询已阅读者列表 (用于撤回时通知已阅读者)。
     *
     * @param messageTrackingId 消息跟踪 ID
     * @return 阅读日志列表 (含阅读者信息)
     */
    @Query(value = "SELECT l FROM ScrmMessageReadLogEntity l WHERE l.messageTrackingId = :messageTrackingId ORDER BY "
                          + "l.readAt ASC")
    List<ScrmMessageReadLogEntity> findReadersByTrackingId(
                                                           @Param("messageTrackingId") Long messageTrackingId);
}
