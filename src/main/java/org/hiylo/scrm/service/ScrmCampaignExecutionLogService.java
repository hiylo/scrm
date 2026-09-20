/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignExecutionLogService.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmCampaignExecutionLogEntity;
import org.hiylo.scrm.repository.ScrmCampaignExecutionLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 营销任务执行日志服务。
 * <p>
 * 负责营销任务执行日志的写入与查询, 支持按任务分页查询、近 N 小时日志查询、
 * 失败日志查询与执行统计聚合。日志写入由 {@link org.hiylo.scrm.controller.ScrmCallbackController}
 * 与 {@link org.hiylo.scrm.scheduler.CampaignScheduler} 触发。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCampaignExecutionLogService {

    /** 动作类型: 启动 */
    public static final String ACTION_START = "START";
    /** 动作类型: 暂停 */
    public static final String ACTION_PAUSE = "PAUSE";
    /** 动作类型: 恢复 */
    public static final String ACTION_RESUME = "RESUME";
    /** 动作类型: 停止 */
    public static final String ACTION_STOP = "STOP";
    /** 动作类型: 回调 */
    public static final String ACTION_CALLBACK = "CALLBACK";

    /** 执行状态: 成功 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 执行状态: 失败 */
    public static final String STATUS_FAILED = "FAILED";
    /** 执行状态: 运行中 */
    public static final String STATUS_RUNNING = "RUNNING";

    /** 执行日志数据访问层 */
    private final ScrmCampaignExecutionLogRepository executionLogRepository;

    /**
     * 写入一条执行日志。
     * <p>
     * 由 Controller / Scheduler 在任务生命周期关键节点调用, 自动填充归属账号 ID 与操作时间。
     * 写入失败仅记录日志, 不抛异常以避免阻断主流程 (日志写入属于辅助功能)。
     * </p>
     *
     * @param campaignId     营销任务 ID
     * @param behaviorFlowId 关联执行任务 ID (可空)
     * @param action         动作类型: START / PAUSE / RESUME / STOP / CALLBACK
     * @param status         执行状态: SUCCESS / FAILED / RUNNING
     * @param errorCode      错误码 (可空)
     * @param errorMessage   错误消息 (可空)
     * @param operatedBy     操作人 (可空)
     * @return 写入后的日志实体
     */
    @Transactional
    public ScrmCampaignExecutionLogEntity log(Long campaignId, Long behaviorFlowId, String action,
                                               String status, String errorCode, String errorMessage,
                                               String operatedBy) {
        ScrmCampaignExecutionLogEntity entity = new ScrmCampaignExecutionLogEntity();
        entity.setCampaignId(campaignId);
        entity.setBehaviorFlowId(behaviorFlowId);
        entity.setAction(action);
        entity.setStatus(status);
        entity.setErrorCode(errorCode);
        entity.setErrorMessage(errorMessage);
        entity.setOperatedBy(operatedBy);
        entity.setOperatedAt(LocalDateTime.now());
        ScrmCampaignExecutionLogEntity saved = executionLogRepository.save(entity);
        log.info("写入营销任务执行日志: campaignId={}, action={}, status={}, logId={}",
                campaignId, action, status, saved.getId());
        return saved;
    }

    /**
     * 分页查询指定营销任务的执行日志, 操作时间倒序返回。
     *
     * @param campaignId 营销任务 ID
     * @param page       页码 (从 0 开始)
     * @param size       每页大小
     * @return 执行日志分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCampaignExecutionLogEntity> getExecutionLogs(Long campaignId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "operatedAt"));
        return executionLogRepository.findByCampaignIdOrderByOperatedAtDesc(campaignId, pageable);
    }

    /**
     * 查询近 N 小时的执行日志 (跨任务, 按当前账号隔离), 操作时间倒序返回。
     *
     * @param hours 时间窗口 (小时)
     * @param page  页码 (从 0 开始)
     * @param size  每页大小
     * @return 执行日志分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCampaignExecutionLogEntity> getRecentLogs(int hours, int page, int size) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusHours(hours);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "operatedAt"));
        return executionLogRepository.findByOperatedAtBetween(start, end, pageable);
    }

    /**
     * 查询失败日志 (status=FAILED), 跨任务, 按当前账号隔离。
     * <p>
     * 通过内存分页返回, 适用于失败日志总量可控的场景; 数据量增长后可改为 Repository 层分页。
     * </p>
     *
     * @param page 页码 (从 0 开始)
     * @param size 每页大小
     * @return 失败日志分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCampaignExecutionLogEntity> getFailedLogs(int page, int size) {
        List<ScrmCampaignExecutionLogEntity> all = executionLogRepository.findByStatus(STATUS_FAILED);
        // 操作时间倒序
        all.sort((a, b) -> b.getOperatedAt().compareTo(a.getOperatedAt()));
        PageRequest pageable = PageRequest.of(page, size);
        int total = all.size();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), total);
        List<ScrmCampaignExecutionLogEntity> sub = start > total ? List.of() : all.subList(start, end);
        return new org.springframework.data.domain.PageImpl<>(sub, pageable, total);
    }

    /**
     * 统计指定营销任务的执行情况。
     * <p>
     * 聚合指标:
     * <ul>
     *   <li>totalCount: 日志总条数</li>
     *   <li>successCount: status=SUCCESS 的日志数</li>
     *   <li>failedCount: status=FAILED 的日志数</li>
     *   <li>avgDurationMillis: START → 终态 CALLBACK 的平均耗时 (毫秒)</li>
     * </ul>
     * 平均耗时计算: 遍历日志 (按 operatedAt 升序), 记录最近一次 START 时间,
     * 命中终态 CALLBACK (SUCCESS/FAILED) 时累加耗时, 最终求平均。
     * </p>
     *
     * @param campaignId 营销任务 ID
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getExecutionStats(Long campaignId) {
        List<ScrmCampaignExecutionLogEntity> logs = executionLogRepository
                .findByCampaignIdOrderByOperatedAtDesc(campaignId, PageRequest.of(0, Integer.MAX_VALUE))
                .getContent();
        long totalCount = logs.size();
        long successCount = logs.stream().filter(e -> STATUS_SUCCESS.equals(e.getStatus())).count();
        long failedCount = logs.stream().filter(e -> STATUS_FAILED.equals(e.getStatus())).count();

        // 计算 START → 终态 CALLBACK 的平均耗时 (毫秒)
        // 按 operatedAt 升序遍历, 命中 START 记录起点, 命中终态 CALLBACK 计算一次耗时
        List<ScrmCampaignExecutionLogEntity> ascLogs = logs.stream()
                .sorted((a, b) -> a.getOperatedAt().compareTo(b.getOperatedAt()))
                .toList();
        long totalDurationMillis = 0L;
        int durationSampleCount = 0;
        LocalDateTime startAt = null;
        for (ScrmCampaignExecutionLogEntity logEntry : ascLogs) {
            String action = logEntry.getAction();
            if (ACTION_START.equals(action)) {
                startAt = logEntry.getOperatedAt();
            } else if (ACTION_CALLBACK.equals(action) && (STATUS_SUCCESS.equals(logEntry.getStatus()) || STATUS_FAILED.equals(logEntry.getStatus())) && startAt != null) {
                totalDurationMillis += Duration.between(startAt, logEntry.getOperatedAt()).toMillis();
                durationSampleCount++;
                startAt = null;
            }
        }
        long avgDurationMillis = durationSampleCount > 0 ? totalDurationMillis / durationSampleCount : 0L;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", totalCount);
        stats.put("successCount", successCount);
        stats.put("failedCount", failedCount);
        stats.put("avgDurationMillis", avgDurationMillis);
        stats.put("durationSampleCount", durationSampleCount);
        return stats;
    }

    /**
     * 清理超过指定天数的执行日志 (定时调度调用)。
     *
     * @param days 保留天数, 早于此天数的日志将被删除
     * @return 删除记录数
     */
    @Transactional
    public int cleanupOldLogs(int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        int deleted = executionLogRepository.deleteByOperatedAtBefore(cutoff);
        log.info("清理超过 {} 天的执行日志: cutoff={}, deleted={}", days, cutoff, deleted);
        return deleted;
    }
}
