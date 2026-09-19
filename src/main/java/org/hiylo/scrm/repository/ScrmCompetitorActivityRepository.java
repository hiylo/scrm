/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCompetitorActivityRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCompetitorActivityEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 竞品动态数据访问层。
 * <p>
 * 提供按竞品 / 类型 / 影响等级查询动态、近期动态、重大动态、待响应动态查询、
 * 动态类型与影响等级分布统计等能力, 供 {@code ScrmCompetitorService} 动态追踪与应对流程使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCompetitorActivityRepository extends JpaRepository<ScrmCompetitorActivityEntity, Long>,
        JpaSpecificationExecutor<ScrmCompetitorActivityEntity> {

    /**
     * 按与竞品 ID 分页查询动态 (按动态日期倒序)。
     *
     * @param competitorId 竞品 ID
     * @param pageable     分页参数
     * @return 动态分页结果
     */
    Page<ScrmCompetitorActivityEntity> findByCompetitorId(Long competitorId, Pageable pageable);

    /**
     * 按与动态类型分页查询动态 (按动态日期倒序)。
     *
     * @param activityType 动态类型
     * @param pageable     分页参数
     * @return 动态分页结果
     */
    Page<ScrmCompetitorActivityEntity> findByActivityType(String activityType, Pageable pageable);

    /**
     * 查询账号下最近 days 天内的动态 (按动态日期倒序, 取前 limit 条)。
     *
     * @param since    动态日期起始 (含)
     * @param pageable 分页参数 (limit 由 pageSize 控制)
     * @return 动态分页结果
     */
    Page<ScrmCompetitorActivityEntity> findByActivityDateAfter(LocalDate since, Pageable pageable);

    /**
     * 查询重大动态 (影响等级 HIGH / CRITICAL), 按重要性评分倒序取前 limit 条。
     *
     * @param levels   影响等级列表
     * @param pageable 分页参数 (limit 由 pageSize 控制)
     * @return 动态分页结果
     */
    @Query(value = "SELECT a FROM ScrmCompetitorActivityEntity a WHERE a.impactLevel IN :levels ORDER BY "
                          + "a.importanceScore DESC, a.activityDate DESC")
    Page<ScrmCompetitorActivityEntity> findCritical(
                                                     @Param("levels") List<String> levels,
                                                     Pageable pageable);

    /**
     * 按动态类型聚合数量 (动态统计用)。
     *
     * @param startTime 起始时间 (含, 可空, 按 createTime 过滤)
     * @param endTime   截止时间 (含, 可空)
     * @return Object[]{activityType, count}
     */
    @Query(value = "SELECT a.activityType, COUNT(a.id) FROM ScrmCompetitorActivityEntity a WHERE (:startTime IS NULL "
                          + "OR a.createTime >= :startTime) AND (:endTime IS NULL OR a.createTime <= :endTime) GROUP BY "
                          + "a.activityType")
    List<Object[]> countByActivityType(
                                        @Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime);

    /**
     * 按影响等级聚合数量 (动态统计用)。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return Object[]{impactLevel, count}
     */
    @Query(value = "SELECT a.impactLevel, COUNT(a.id) FROM ScrmCompetitorActivityEntity a WHERE (:startTime IS NULL "
                          + "OR a.createTime >= :startTime) AND (:endTime IS NULL OR a.createTime <= :endTime) GROUP BY "
                          + "a.impactLevel")
    List<Object[]> countByImpactLevel(
                                       @Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime);

    /**
     * 按应对状态聚合数量 (应对统计用)。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return Object[]{responseStatus, count}
     */
    @Query(value = "SELECT a.responseStatus, COUNT(a.id) FROM ScrmCompetitorActivityEntity a WHERE (:startTime IS "
                          + "NULL OR a.createTime >= :startTime) AND (:endTime IS NULL OR a.createTime <= :endTime) GROUP BY "
                          + "a.responseStatus")
    List<Object[]> countByResponseStatus(
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);

    /**
     * 按竞品聚合动态数量 (动态分析用)。
     *
     * @param competitorId 竞品 ID
     * @param startTime    起始时间 (含, 可空, 按 activityDate 过滤)
     * @param endTime      截止时间 (含, 可空)
     * @return 动态总数
     */
    @Query(value = "SELECT COUNT(a.id) FROM ScrmCompetitorActivityEntity a WHERE a.competitorId = :competitorId AND "
                          + "(:startTime IS NULL OR a.activityDate >= :startTime) AND (:endTime IS NULL OR a.activityDate <="
                          + ":endTime)")
    long countByCompetitor(
                            @Param("competitorId") Long competitorId,
                            @Param("startTime") LocalDate startTime,
                            @Param("endTime") LocalDate endTime);
}
