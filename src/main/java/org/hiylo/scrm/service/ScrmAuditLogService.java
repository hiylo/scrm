/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAuditLogService.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmAuditLogEntity;
import org.hiylo.scrm.repository.ScrmAuditLogRepository;
import org.hiylo.scrm.vo.AuditStatsVo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 操作审计日志服务。
 * <p>
 * 提供 {@code AuditLogAspect} 切面调用的异步保存入口, 以及 Controller 层的
 * 审计日志查询、统计与最近活动能力。所有查询按当前用户可见账号范围过滤, 实现数据隔离。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmAuditLogService {

    /** 操作结果: 成功 */
    public static final String RESULT_SUCCESS = "SUCCESS";

    /** 操作结果: 失败 */
    public static final String RESULT_FAILED = "FAILED";

    /** 审计日志数据访问层 */
    private final ScrmAuditLogRepository auditLogRepository;

    /**
     * 保存审计日志 (同步)。
     *
     * @param entity 审计日志实体
     * @return 保存后的实体 (含主键)
     */
    @Transactional
    public ScrmAuditLogEntity save(ScrmAuditLogEntity entity) {
        return auditLogRepository.save(entity);
    }

    /**
     * 异步保存审计日志, 不阻塞主流程。
     * <p>
     * 切面在 Controller 写操作完成后调用此方法, 异常时仅记录日志, 不影响业务。
     * 使用 {@code asyncTaskExecutor} (虚拟线程) 执行, 避免占用请求线程。
     * </p>
     *
     * @param entity 审计日志实体
     */
    @Async("asyncTaskExecutor")
    @Transactional
    public void saveAsync(ScrmAuditLogEntity entity) {
        try {
            auditLogRepository.save(entity);
        } catch (Exception e) {
            // 审计日志失败不应影响主流程, 仅记录错误日志
            log.error("异步保存审计日志失败: userId={}, resource={}, action={}, uri={}, err={}",
                    entity.getUserId(), entity.getResource(), entity.getAction(),
                    entity.getRequestUri(), e.getMessage(), e);
        }
    }

    /**
     * 按操作人用户 ID 分页查询审计日志。
     *
     * @param userId 操作人用户 ID
     * @param page   页码 (从 0 开始)
     * @param size   每页大小
     * @return 审计日志分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmAuditLogEntity> getByUser(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        return auditLogRepository.findByUserIdOrderByOperatedAtDesc(userId, pageable);
    }

    /**
     * 按操作资源分页查询审计日志。
     *
     * @param resource 资源标识
     * @param page     页码 (从 0 开始)
     * @param size     每页大小
     * @return 审计日志分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmAuditLogEntity> getByResource(String resource, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        return auditLogRepository.findByResource(resource, pageable);
    }

    /**
     * 按当前账号分页查询审计日志 (默认倒序)。
     * <p>
     * 用于无过滤条件的综合查询, 走数据隔离。
     * </p>
     *
     * @param page 页码 (从 0 开始)
     * @param size 每页大小
     * @return 审计日志分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmAuditLogEntity> list(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        return auditLogRepository.findAllByOrderByOperatedAtDesc(pageable);
    }

    /**
     * 按操作时间区间分页查询审计日志 (按当前账号隔离)。
     *
     * @param start 起始时间 (含)
     * @param end   结束时间 (含)
     * @param page  页码 (从 0 开始)
     * @param size  每页大小
     * @return 审计日志分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmAuditLogEntity> getByTimeRange(LocalDateTime start, LocalDateTime end, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        return auditLogRepository.findByOperatedAtBetween(start, end, pageable);
    }

    /**
     * 分页查询失败操作 (按当前账号隔离)。
     *
     * @param page 页码 (从 0 开始)
     * @param size 每页大小
     * @return 失败操作分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmAuditLogEntity> getFailedOperations(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "operatedAt"));
        return auditLogRepository.findByResultOrderByOperatedAtDesc(RESULT_FAILED, pageable);
    }

    /**
     * 查询最近 N 条审计日志 (按当前账号隔离, 按操作时间倒序)。
     *
     * @param limit 返回条数
     * @return 审计日志列表
     */
    @Transactional(readOnly = true)
    public List<ScrmAuditLogEntity> getRecentActivities(int limit) {
        Pageable pageable = PageRequest.of(0, Math.max(limit, 1));
        return auditLogRepository.findAllByOrderByOperatedAtDesc(pageable).getContent();
    }

    /**
     * 审计日志统计: 总操作数 / 成功数 / 失败数 / 成功率 / 按资源分布 (按当前账号隔离)。
     *
     * @return 审计统计 VO
     */
    @Transactional(readOnly = true)
    public AuditStatsVo getAuditStats() {
        // 通过资源聚合查询拿到总数与资源分布, 避免多次 count 查询
        List<Object[]> rows = auditLogRepository.countGroupByResource();
        long total = 0L;
        Map<String, Long> resourceDistribution = new LinkedHashMap<>();
        if (rows != null) {
            for (Object[] row : rows) {
                if (row == null || row.length < 2 || row[0] == null) {
                    continue;
                }
                String resource = String.valueOf(row[0]);
                Long count = toLong(row[1]);
                resourceDistribution.put(resource, count);
                total += count;
            }
        }
        long successCount = auditLogRepository.countByResult(RESULT_SUCCESS);
        long failedCount = auditLogRepository.countByResult(RESULT_FAILED);
        double successRate = total > 0 ? (double) successCount / total : 0.0;
        return AuditStatsVo.builder()
                .totalOperations(total)
                .successCount(successCount)
                .failedCount(failedCount)
                .successRate(successRate)
                .resourceDistribution(resourceDistribution)
                .build();
    }

    /**
     * 安全转 Long, 兼容 Number / String 等聚合返回类型。
     *
     * @param value 聚合值
     * @return Long 值
     */
    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
