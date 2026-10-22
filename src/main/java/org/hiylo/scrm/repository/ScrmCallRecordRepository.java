/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCallRecordRepository.java
 * Date : 2026/10/22 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCallRecordEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 销售通话记录数据访问层。
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmCallRecordRepository extends JpaRepository<ScrmCallRecordEntity, Long> {

    /**
     * 按用户分页查询通话记录（App 通话记录页）。
     *
     * @param userId   用户 ID
     * @param pageable 分页参数
     * @return 通话记录分页
     */
    Page<ScrmCallRecordEntity> findByUserIdOrderByStartAtDesc(String userId, Pageable pageable);

    /**
     * 按客户查询通话记录。
     *
     * @param customerId 客户 ID
     * @return 通话记录列表（按开始时间倒序）
     */
    List<ScrmCallRecordEntity> findByCustomerIdOrderByStartAtDesc(Long customerId);

    /**
     * 按归属类型 + 归属对象查询通话记录（客户详情页）。
     *
     * @param customerType 归属类型（CUSTOMER / PUBLIC_SEA）
     * @param customerId   归属对象 ID
     * @return 通话记录列表（按开始时间倒序）
     */
    List<ScrmCallRecordEntity> findByCustomerTypeAndCustomerIdOrderByStartAtDesc(
            String customerType, Long customerId);

    /**
     * 客户端去重：同一设备同一系统话单 ID。
     *
     * @param deviceId  设备 ID
     * @param callLogId 系统话单 ID
     * @return 已存在记录（可能为空）
     */
    Optional<ScrmCallRecordEntity> findByDeviceIdAndCallLogId(String deviceId, Long callLogId);

    /**
     * 无系统话单 ID 时的近似去重：同设备 + 同对端哈希 + 时间落在窗口内。
     *
     * @param deviceId 设备 ID
     * @param peerHash 对端号码哈希
     * @param from     窗口起点
     * @param to       窗口终点
     * @return 匹配到的记录列表
     */
    List<ScrmCallRecordEntity> findByDeviceIdAndPeerHashAndStartAtBetween(
            String deviceId, String peerHash, LocalDateTime from, LocalDateTime to);

    /**
     * 统计用户时间区间内的通话数量（覆盖率口径）。
     *
     * @param userId 用户 ID
     * @param from   起点
     * @param to     终点
     * @return 通话数
     */
    long countByUserIdAndStartAtBetween(String userId, LocalDateTime from, LocalDateTime to);
}
