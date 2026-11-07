/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmInspectionRunService.java
 * Date : 2026/10/24 16:40:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.entity.ScrmInspectionRunEntity;
import org.hiylo.scrm.repository.ScrmInspectionRunRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 巡检/抓取执行历史服务。
 * <p>
 * 调度器与手动触发端点每执行完一轮就调用本服务写一条历史，解决
 * 「过去几天跑没跑、成功几次、命中几条、为什么跳过」无法回溯的问题。
 * </p>
 * <p>
 * <b>状态判定</b>（写入口径，避免各处自行解释）：
 * </p>
 * <ul>
 *   <li>{@code SKIPPED}：按开关整体跳过（如启信宝未启用）——<b>不是失败</b></li>
 *   <li>{@code FAILED}：有失败且没有任何成功</li>
 *   <li>{@code PARTIAL}：有失败但也有成功/命中</li>
 *   <li>{@code SUCCESS}：无失败</li>
 * </ul>
 * <p>
 * <b>不抛异常</b>：记录历史是旁路能力，写库失败只记日志，绝不因为「记日志失败」
 * 而让已经跑完的巡检被判定为失败。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmInspectionRunService {

    /** 默认统计天数 */
    private static final int DEFAULT_STAT_DAYS = 7;

    /** 执行历史数据访问层 */
    private final ScrmInspectionRunRepository runRepository;

    /**
     * 记录一轮工商巡检结果。
     *
     * @param summary     巡检汇总
     * @param triggerType 触发方式（SCHEDULED / MANUAL）
     * @param startedAt   开始时间
     * @param costMs      耗时（毫秒）
     * @return 写入的记录（失败时返回 null，不抛出）
     */
    @Transactional
    public ScrmInspectionRunEntity recordBusiness(ScrmInspectionQueryService.BusinessSummary summary,
                                                  String triggerType,
                                                  LocalDateTime startedAt, long costMs) {
        if (summary == null) {
            return null;
        }
        ScrmInspectionRunEntity entity = base(ScrmInspectionRunEntity.TYPE_BUSINESS, triggerType, startedAt, costMs);
        entity.setTaskScanned(summary.taskScanned());
        entity.setSuccessCount(summary.success());
        entity.setFailedCount(summary.failed());
        entity.setChangedCount(summary.changed());
        entity.setSkipReason(summary.skipReason());
        entity.setStatus(deriveStatus(summary.skipReason() != null, summary.success(), summary.failed()));
        return persist(entity);
    }

    /**
     * 记录一轮税务公告抓取结果。
     *
     * @param summary     抓取汇总
     * @param triggerType 触发方式（SCHEDULED / MANUAL）
     * @param startedAt   开始时间
     * @param costMs      耗时（毫秒）
     * @return 写入的记录（失败时返回 null，不抛出）
     */
    @Transactional
    public ScrmInspectionRunEntity recordTax(TaxNoticeScrapeService.ScrapeSummary summary,
                                             String triggerType,
                                             LocalDateTime startedAt, long costMs) {
        if (summary == null) {
            return null;
        }
        ScrmInspectionRunEntity entity = base(ScrmInspectionRunEntity.TYPE_TAX, triggerType, startedAt, costMs);
        entity.setTaskScanned(summary.taskScanned());
        // 税务维度: 命中记录数记为 success_count, 验证码阻断数记为 changed_count
        entity.setSuccessCount(summary.recordsMatched());
        entity.setFailedCount(summary.failed());
        entity.setChangedCount(summary.captchaBlocked());
        entity.setStatus(deriveStatus(false, summary.recordsMatched(), summary.failed()));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("recordsMatched", summary.recordsMatched());
        detail.put("captchaBlocked", summary.captchaBlocked());
        entity.setDetail(detail.toString());
        return persist(entity);
    }

    /**
     * 分页查询执行历史。
     *
     * @param runType 执行类型（可空，空则查全部）
     * @param page    页码（0 起）
     * @param size    每页条数
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmInspectionRunEntity> list(String runType, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 200)));
        if (runType == null || runType.isBlank()) {
            return runRepository.findAllByOrderByStartedAtDesc(pageable);
        }
        return runRepository.findByRunTypeOrderByStartedAtDesc(runType.trim().toUpperCase(), pageable);
    }

    /**
     * 最近 N 天的执行统计（按类型汇总次数/成功/失败/跳过/命中）。
     *
     * @param days 统计天数（空则默认 7 天，最小 1，最大 90）
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> recentStats(Integer days) {
        int window = days == null ? DEFAULT_STAT_DAYS : Math.max(1, Math.min(days, 90));
        LocalDateTime since = LocalDateTime.now().minusDays(window);
        List<ScrmInspectionRunEntity> runs = runRepository.findByStartedAtAfter(since);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", window);
        result.put("since", since.toString());
        List<Map<String, Object>> byType = new ArrayList<>();
        for (String type : List.of(ScrmInspectionRunEntity.TYPE_BUSINESS,
                ScrmInspectionRunEntity.TYPE_TAX)) {
            List<ScrmInspectionRunEntity> subset = runs.stream()
                    .filter(r -> type.equals(r.getRunType()))
                    .toList();
            Map<String, Object> agg = new LinkedHashMap<>();
            agg.put("runType", type);
            agg.put("totalRuns", subset.size());
            agg.put("success", subset.stream()
                    .filter(r -> ScrmInspectionRunEntity.STATUS_SUCCESS.equals(r.getStatus())).count());
            agg.put("partial", subset.stream()
                    .filter(r -> ScrmInspectionRunEntity.STATUS_PARTIAL.equals(r.getStatus())).count());
            agg.put("failed", subset.stream()
                    .filter(r -> ScrmInspectionRunEntity.STATUS_FAILED.equals(r.getStatus())).count());
            agg.put("skipped", subset.stream()
                    .filter(r -> ScrmInspectionRunEntity.STATUS_SKIPPED.equals(r.getStatus())).count());
            agg.put("taskScanned", subset.stream()
                    .mapToInt(r -> r.getTaskScanned() == null ? 0 : r.getTaskScanned()).sum());
            agg.put("successCount", subset.stream()
                    .mapToInt(r -> r.getSuccessCount() == null ? 0 : r.getSuccessCount()).sum());
            agg.put("failedCount", subset.stream()
                    .mapToInt(r -> r.getFailedCount() == null ? 0 : r.getFailedCount()).sum());
            agg.put("lastRunAt", subset.isEmpty() ? null
                    : subset.get(subset.size() - 1).getStartedAt());
            agg.put("lastStatus", subset.isEmpty() ? null : subset.get(subset.size() - 1).getStatus());
            byType.add(agg);
        }
        result.put("byType", byType);
        return result;
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 构造基础记录（公共字段）。
     *
     * @param runType     执行类型
     * @param triggerType 触发方式
     * @param startedAt   开始时间
     * @param costMs      耗时
     * @return 实体
     */
    private ScrmInspectionRunEntity base(String runType, String triggerType,
                                         LocalDateTime startedAt, long costMs) {
        ScrmInspectionRunEntity entity = new ScrmInspectionRunEntity();
        entity.setRunType(runType);
        entity.setTriggerType(triggerType == null || triggerType.isBlank()
                ? ScrmInspectionRunEntity.TRIGGER_SCHEDULED : triggerType);
        entity.setStartedAt(startedAt == null ? LocalDateTime.now() : startedAt);
        entity.setFinishedAt(LocalDateTime.now());
        entity.setCostMs(costMs);
        entity.setOperator(UserContext.getUsername());
        return entity;
    }

    /**
     * 推导执行结果状态。
     *
     * @param skipped  是否整体跳过
     * @param success  成功（或命中）数
     * @param failed   失败数
     * @return 状态
     */
    private String deriveStatus(boolean skipped, int success, int failed) {
        if (skipped) {
            return ScrmInspectionRunEntity.STATUS_SKIPPED;
        }
        if (failed <= 0) {
            return ScrmInspectionRunEntity.STATUS_SUCCESS;
        }
        return success > 0 ? ScrmInspectionRunEntity.STATUS_PARTIAL
                : ScrmInspectionRunEntity.STATUS_FAILED;
    }

    /**
     * 落库（失败只记日志，不影响已完成的巡检）。
     *
     * @param entity 实体
     * @return 落库后的实体（失败返回 null）
     */
    private ScrmInspectionRunEntity persist(ScrmInspectionRunEntity entity) {
        try {
            return runRepository.save(entity);
        } catch (Exception e) {
            log.warn("[巡检历史] 记录失败（不影响巡检结果）: runType={}, status={}, err={}",
                    entity.getRunType(), entity.getStatus(), e.getMessage());
            return null;
        }
    }
}
