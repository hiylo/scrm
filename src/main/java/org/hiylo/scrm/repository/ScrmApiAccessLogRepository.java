/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApiAccessLogRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmApiAccessLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 开放API 调用日志数据访问层。
 * <p>
 * 提供按应用 / IP / 时间窗口的查询, 以及端点聚合、错误聚合与平均响应时间统计能力,
 * 支撑 {@code ScrmOpenApiService} 的日志检索与统计接口。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmApiAccessLogRepository extends JpaRepository<ScrmApiAccessLogEntity, Long>,
        JpaSpecificationExecutor<ScrmApiAccessLogEntity> {

    /**
     * 按与应用分页查询调用日志, 按访问时间倒序返回。
     *
     * @param appId    应用 ID
     * @param pageable 分页参数
     * @return 调用日志分页结果
     */
    Page<ScrmApiAccessLogEntity> findByAppIdOrderByAccessedAtDesc(Long appId, Pageable pageable);

    /**
     * 按与请求 IP 分页查询调用日志, 按访问时间倒序返回。
     *
     * @param requestIp 请求 IP
     * @param pageable  分页参数
     * @return 调用日志分页结果
     */
    Page<ScrmApiAccessLogEntity> findByRequestIpOrderByAccessedAtDesc(String requestIp,
                                                                                  Pageable pageable);

    /**
     * 查询账号下最近 N 条错误日志 (HTTP 状态码 >= 400)。
     *
     * @param minStatus 最小 HTTP 状态码 (含)
     * @param pageable  分页参数 (取 limit 条)
     * @return 错误日志列表
     */
    @Query("SELECT l FROM ScrmApiAccessLogEntity l WHERE l.responseStatus >= :minStatus "
            + "ORDER BY l.accessedAt DESC")
    List<ScrmApiAccessLogEntity> findRecentErrors(
                                                   @Param("minStatus") int minStatus,
                                                   Pageable pageable);

    /**
     * 统计时间窗口内账号的总请求数。
     *
     * @param start    起始时间 (含)
     * @param end      结束时间 (不含)
     * @return 总请求数
     */
    @Query(value = "SELECT COUNT(l.id) FROM ScrmApiAccessLogEntity l WHERE l.accessedAt >= :start AND l.accessedAt <"
                          + ":end")
    long countByTimeRange(
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);

    /**
     * 统计时间窗口内账号的错误请求数 (HTTP 状态码 >= minStatus)。
     *
     * @param minStatus 最小 HTTP 状态码 (含)
     * @param start     起始时间 (含)
     * @param end       结束时间 (不含)
     * @return 错误请求数
     */
    @Query(value = "SELECT COUNT(l.id) FROM ScrmApiAccessLogEntity l WHERE l.responseStatus >= :minStatus AND "
                          + "l.accessedAt >= :start AND l.accessedAt < :end")
    long countErrorsByTimeRange(
                                         @Param("minStatus") int minStatus,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    /**
     * 统计时间窗口内账号的平均响应时间 (毫秒)。
     *
     * @param start    起始时间 (含)
     * @param end      结束时间 (不含)
     * @return 平均响应时间, 无数据返回 0
     */
    @Query(value = "SELECT COALESCE(AVG(l.responseTimeMs), 0) FROM ScrmApiAccessLogEntity l WHERE l.responseTimeMs "
                          + "IS NOT NULL AND l.accessedAt >= :start AND l.accessedAt < :end")
    double avgResponseTimeByTimeRange(
                                               @Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end);

    /**
     * 按端点聚合请求次数与平均响应时间 (热门端点排行)。
     *
     * @param start    起始时间 (含)
     * @param end      结束时间 (不含)
     * @return Object[]{endpoint, requestCount, avgResponseTimeMs}
     */
    @Query(value = "SELECT l.endpoint, COUNT(l.id), COALESCE(AVG(l.responseTimeMs), 0) FROM ScrmApiAccessLogEntity l "
                          + "WHERE l.accessedAt >= :start AND l.accessedAt < :end GROUP BY l.endpoint ORDER BY COUNT(l.id) "
                          + "DESC")
    List<Object[]> endpointStats(
                                 @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);

    /**
     * 按错误码聚合错误次数 (错误分布统计)。
     *
     * @param minStatus 最小 HTTP 状态码 (含)
     * @param start     起始时间 (含)
     * @param end       结束时间 (不含)
     * @return Object[]{errorCode, errorCount}
     */
    @Query(value = "SELECT COALESCE(l.errorCode, 'HTTP_' || l.responseStatus), COUNT(l.id) FROM "
                          + "ScrmApiAccessLogEntity l WHERE l.responseStatus >= :minStatus AND l.accessedAt >= :start AND "
                          + "l.accessedAt < :end GROUP BY COALESCE(l.errorCode, 'HTTP_' || l.responseStatus) ORDER BY "
                          + "COUNT(l.id) DESC")
    List<Object[]> errorStats(
                              @Param("minStatus") int minStatus,
                              @Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);
}
