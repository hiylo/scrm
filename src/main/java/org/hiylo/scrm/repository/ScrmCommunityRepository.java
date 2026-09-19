/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommunityRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCommunityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 社群数据访问层。
 * <p>
 * 提供按统计社群数 / 成员总数 / 活跃成员总数 / 总消息数, 以及活跃度排行榜查询,
 * 供 {@code ScrmCommunityService} 的统计接口与活跃度刷新使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCommunityRepository extends JpaRepository<ScrmCommunityEntity, Long>,
        JpaSpecificationExecutor<ScrmCommunityEntity> {

    /**
     * 统计账号下指定状态的社群数量。
     *
     * @param status   社群状态
     * @return 社群数量
     */
    long countByStatus(String status);

    /**
     * 统计账号下社群的总成员数 (仅活跃社群)。
     *
     * @param status   社群状态
     * @return 总成员数
     */
    @Query("SELECT COALESCE(SUM(c.memberCount), 0) FROM ScrmCommunityEntity c WHERE c.status = :status")
    long sumMemberCountByStatus(
                                         @Param("status") String status);

    /**
     * 统计账号下社群的总活跃成员数 (仅活跃社群)。
     *
     * @param status   社群状态
     * @return 总活跃成员数
     */
    @Query("SELECT COALESCE(SUM(c.activeMembers), 0) FROM ScrmCommunityEntity c WHERE c.status = :status")
    long sumActiveMembersByStatus(
                                           @Param("status") String status);

    /**
     * 统计账号下社群的总消息数 (仅活跃社群)。
     *
     * @param status   社群状态
     * @return 总消息数
     */
    @Query("SELECT COALESCE(SUM(c.todayMessages), 0) FROM ScrmCommunityEntity c WHERE c.status = :status")
    long sumTodayMessagesByStatus(
                                           @Param("status") String status);

    /**
     * 按活跃度评分倒序取前 N 个活跃社群 (活跃群排行榜)。
     *
     * @param status   社群状态
     * @param limit    限制条数
     * @return 社群列表
     */
    List<ScrmCommunityEntity> findTop10ByStatusOrderByActivityScoreDesc(String status);

    /**
     * 加载建群时间在指定时间之后且状态为活跃的社群 (用于按建群时间过滤统计)。
     *
     * @param start    建群时间起点 (含)
     * @return 社群列表
     */
    List<ScrmCommunityEntity> findByCreatedAtAfter(LocalDateTime start);
}
