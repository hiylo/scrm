/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChannelCodeScanRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmChannelCodeScanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * SCRM 渠道活码扫码记录数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmChannelCodeScanRepository extends JpaRepository<ScrmChannelCodeScanEntity, Long>,
        JpaSpecificationExecutor<ScrmChannelCodeScanEntity> {

    /**
     * 根据渠道活码 ID 查询扫码记录列表。
     *
     * @param channelCodeId 渠道活码 ID
     * @return 扫码记录列表
     */
    List<ScrmChannelCodeScanEntity> findByChannelCodeId(Long channelCodeId);

    /**
     * 删除指定渠道活码的所有扫码记录 (活码删除时级联清理)。
     *
     * @param channelCodeId 渠道活码 ID
     */
    void deleteByChannelCodeId(Long channelCodeId);

    /**
     * 按添加状态聚合指定活码的扫码记录数 (统计用, 避免 N+1)。
     *
     * @param channelCodeId 渠道活码 ID
     * @return Object[]{added, count}
     */
    @Query(
"SELECT e.added, COUNT(e.id) FROM ScrmChannelCodeScanEntity e WHERE e.channelCodeId = :channelCodeId GROUP BY e.added")
    List<Object[]> countByAdded(@Param("channelCodeId") Long channelCodeId);

    /**
     * 统计指定活码的扫码总数。
     *
     * @param channelCodeId 渠道活码 ID
     * @return 扫码总数
     */
    long countByChannelCodeId(Long channelCodeId);
}
