/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommunityMemberRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCommunityMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 社群成员数据访问层。
 * <p>
 * 提供按社群统计成员数 / 活跃成员数, 以及不活跃成员 (最后活跃时间早于阈值) 查询,
 * 供 {@code ScrmCommunityService} 的成员管理与统计接口使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCommunityMemberRepository extends JpaRepository<ScrmCommunityMemberEntity, Long>,
        JpaSpecificationExecutor<ScrmCommunityMemberEntity> {

    /**
     * 统计社群在群成员数 (status=ACTIVE)。
     *
     * @param communityId 社群 ID
     * @param status      成员状态
     * @return 成员数
     */
    long countByCommunityIdAndStatus(Long communityId, String status);

    /**
     * 统计社群活跃成员数 (status=ACTIVE 且 isActive=true)。
     *
     * @param communityId 社群 ID
     * @param status      成员状态
     * @param isActive    是否活跃
     * @return 活跃成员数
     */
    long countByCommunityIdAndStatusAndIsActive(Long communityId, String status, Boolean isActive);

    /**
     * 查询社群中最后活跃时间早于阈值 (或不活跃) 的在群成员 (不活跃成员提醒)。
     *
     * @param communityId 社群 ID
     * @param status      成员状态
     * @param threshold   不活跃阈值时间 (最后活跃时间早于此值)
     * @return 不活跃成员列表
     */
    List<ScrmCommunityMemberEntity> findByCommunityIdAndStatusAndLastActiveAtBefore(Long communityId, String status, LocalDateTime threshold);

    /**
     * 查询社群中最后活跃时间为空 (从未活跃) 的在群成员。
     *
     * @param communityId 社群 ID
     * @param status      成员状态
     * @return 从未活跃成员列表
     */
    List<ScrmCommunityMemberEntity> findByCommunityIdAndStatusAndLastActiveAtIsNull(Long communityId, String status);

    /**
     * 统计社群今日新增成员数 (joinAt 在今天)。
     *
     * @param communityId 社群 ID
     * @param start       今日起点 (含)
     * @param end         今日终点 (不含)
     * @return 今日新增成员数
     */
    @Query(value = "SELECT COUNT(m) FROM ScrmCommunityMemberEntity m WHERE m.communityId = :communityId AND m.joinAt"
                          + ">= :start AND m.joinAt < :end")
    long countTodayJoined(
                          @Param("communityId") Long communityId,
                          @Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end);

    /**
     * 删除指定社群的所有成员记录 (社群删除时级联清理)。
     *
     * @param communityId 社群 ID
     * @return 删除行数
     */
    long deleteByCommunityId(Long communityId);
}
