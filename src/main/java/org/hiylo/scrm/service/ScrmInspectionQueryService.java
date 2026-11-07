/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmInspectionQueryService.java
 * Date : 2026/10/23 10:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.component.InspectionRateLimiter;
import org.hiylo.scrm.entity.ScrmInspectionSnapshotEntity;
import org.hiylo.scrm.entity.ScrmInspectionTaskEntity;
import org.hiylo.scrm.feign.QixinApiException;
import org.hiylo.scrm.feign.QixinEnterpriseApi;
import org.hiylo.scrm.feign.dto.QixinEnterpriseProfile;
import org.hiylo.scrm.repository.ScrmInspectionTaskRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工商状态第三方 API 巡检服务。
 * <p>
 * 按任务逐企业调用 {@link QixinEnterpriseApi}（主选启信宝, 备用企查查待接入）:
 * 工商照面（经营状态 / 注销 / 吊销 / 法人 / 股东）+ 经营异常名录, 归一化后
 * 生成稳定明细 JSON → 快照服务做 SHA-256 哈希比对 → 变化才告警。
 * </p>
 * <p><b>启用闸门</b>: 启信宝未启用（默认）或未配置 token 时, 本轮<b>整体跳过</b>,
 * 不逐个任务调用 API、不写任何失败快照。若按「逐任务调用再记失败」处理,
 * 会在合约未签约的过渡期每天给每个任务刷一条假失败并把任务标成 FAILED。</p>
 * <p><b>失败语义</b>: 已启用情况下的单企业失败（网络 / 业务码错误）记录一条错误快照
 * 并计 PARTIAL, 不中断整轮巡检; 失败快照与任务状态只在 {@code inspectOne} 内记录一次,
 * 外层只计数, 避免重复写快照。API 用量与频率由调度器与任务周期控制
 * （T02: 500 家 × 每季 1 次 ≈ 日均 6 次, 余量极大）。</p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmInspectionQueryService {

    /** 工商维度快照来源标记 */
    public static final String SOURCE_QIXIN = "QIXIN";

    /** 工商维度快照来源标记（备用企查查） */
    public static final String SOURCE_QCC = "QCC";

    /** 巡检任务数据访问层 */
    private final ScrmInspectionTaskRepository taskRepository;

    /** 启信宝工商查询客户端 */
    private final QixinEnterpriseApi qixinEnterpriseApi;

    /** 启信宝 API 配置（用于「未启用 / 未配 token」时整体跳过） */
    private final org.hiylo.scrm.feign.QixinApiConfig qixinApiConfig;

    /** 巡检快照服务（变化比对入库） */
    private final ScrmInspectionSnapshotService snapshotService;

    /** 巡检告警服务（变化才告警） */
    private final ScrmInspectionAlertService alertService;

    /** 巡检频率令牌桶（T02: 工商 API 日调用预算兜底） */
    private final InspectionRateLimiter rateLimiter;

    /** JSON 序列化器 */
    private final ObjectMapper objectMapper;

    /** 工商 API 单日调用预算（T02: 500 家/季 ≈ 日均 6 次, 放宽到 200 次/天留余量） */
    private static final int QIXIN_DAILY_BUDGET = 200;

    /**
     * 巡检汇总。
     *
     * @param taskScanned 扫描任务数
     * @param success     成功数
     * @param failed      失败数
     * @param changed     状态变化数
     * @param skipReason  本轮整体跳过的原因（未跳过时为 null）
     */
    public record BusinessSummary(int taskScanned, int success, int failed, int changed,
                                  String skipReason) {

        /**
         * 兼容构造（无跳过原因）。
         *
         * @param taskScanned 扫描任务数
         * @param success     成功数
         * @param failed      失败数
         * @param changed     状态变化数
         */
        public BusinessSummary(int taskScanned, int success, int failed, int changed) {
            this(taskScanned, success, failed, changed, null);
        }
    }

    /**
     * 执行一轮全量工商巡检（按启用任务列表）。
     *
     * @return 巡检汇总
     */
    public BusinessSummary inspectAll() {
        // 启用闸门: 未启用或未配 token 时整体跳过。
        // 不能逐任务调 API 再记失败——那会在合约未就绪的过渡期每天给每个任务刷假失败快照。
        if (!qixinApiConfig.isEnabled()) {
            log.info("[工商巡检] 启信宝未启用 (scrm.inspection.qixin.enabled=false), 本轮整体跳过");
            return new BusinessSummary(0, 0, 0, 0, "启信宝未启用（enabled=false），本轮跳过");
        }
        if (qixinApiConfig.getToken() == null || qixinApiConfig.getToken().isBlank()) {
            log.warn("[工商巡检] 启信宝已启用但未配置 token, 本轮整体跳过");
            return new BusinessSummary(0, 0, 0, 0, "启信宝已启用但未配置 token，本轮跳过");
        }
        List<ScrmInspectionTaskEntity> tasks = taskRepository.findEnabledBusinessTasks();
        if (tasks.isEmpty()) {
            log.info("[工商巡检] 无启用工商巡检任务, 跳过");
            return new BusinessSummary(0, 0, 0, 0);
        }
        log.info("[工商巡检] 开始全量巡检: taskCount={}", tasks.size());
        int success = 0;
        int failed = 0;
        int changed = 0;
        for (ScrmInspectionTaskEntity task : tasks) {
            try {
                ScrmInspectionSnapshotEntity snapshot = inspectOne(task);
                if (Boolean.TRUE.equals(snapshot.getChanged())) {
                    changed++;
                }
                success++;
            } catch (QixinApiException e) {
                // inspectOne 内部已记录失败快照与任务状态, 外层只计数,
                // 否则同一失败会写两条快照、runCount 加两次。
                failed++;
            } catch (Exception e) {
                // 非预期异常（inspectOne 未兜住的）才由外层补记失败
                failed++;
                log.error("[工商巡检] 任务 {} 巡检异常: {}", task.getId(), e.getMessage(), e);
                markFailed(task, e.getMessage());
            }
        }
        log.info("[工商巡检] 本轮巡检完成: scanned={}, success={}, failed={}, changed={}",
                tasks.size(), success, failed, changed);
        return new BusinessSummary(tasks.size(), success, failed, changed);
    }

    /**
     * 启信宝接入状态（供运维自检: 加 token 后不必消耗额度即可确认是否就绪）。
     *
     * @return 状态描述（不返回 token 本身）
     */
    public Map<String, Object> apiStatus() {
        boolean enabled = qixinApiConfig.isEnabled();
        boolean tokenConfigured = qixinApiConfig.getToken() != null
                && !qixinApiConfig.getToken().isBlank();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", enabled);
        status.put("tokenConfigured", tokenConfigured);
        status.put("baseUrl", qixinApiConfig.getBaseUrl());
        status.put("dailyBudget", QIXIN_DAILY_BUDGET);
        status.put("ready", enabled && tokenConfigured);
        return status;
    }

    /**
     * 执行单个企业的工商巡检（照面 + 经营异常两接口）。
     *
     * @param task 巡检任务
     * @return 新快照（{@code changed} 表明是否变化）
     */
    public ScrmInspectionSnapshotEntity inspectOne(ScrmInspectionTaskEntity task) {
        String creditCode = task.getCreditCode();
        String name = task.getEnterpriseName();
        // 频率令牌桶: 工商 API 当日预算（每次企业巡检消耗 1 配额, 覆盖照面+异常两接口）
        if (!rateLimiter.tryAcquire("QIXIN_API", QIXIN_DAILY_BUDGET)) {
            markFailed(task, "工商 API 当日预算已用尽, 跳过本轮 (key=QIXIN_API)");
            throw new QixinApiException("工商 API 当日预算已用尽", false);
        }
        try {
            QixinEnterpriseProfile profile = qixinEnterpriseApi.queryProfile(name, creditCode);
            QixinEnterpriseProfile abnormal = qixinEnterpriseApi.queryAbnormal(name, creditCode);
            if (abnormal.isAbnormal()) {
                profile.setAbnormal(true);
                profile.setAbnormalReason(abnormal.getAbnormalReason());
                profile.setAbnormalDate(abnormal.getAbnormalDate());
            }
            String status = normalizeStatus(profile);
            String detailJson = buildDetailJson(task, profile);
            ScrmInspectionSnapshotEntity snapshot = snapshotService.saveSnapshot(
                    task.getId(), ScrmInspectionSnapshotService.DIM_BUSINESS,
                    profile.getCreditCode() != null ? profile.getCreditCode() : creditCode,
                    profile.getEnterpriseName() != null ? profile.getEnterpriseName() : name,
                    SOURCE_QIXIN, status, detailJson, null);
            // 变化才告警
            if (Boolean.TRUE.equals(snapshot.getChanged())) {
                ScrmInspectionSnapshotEntity old = snapshotService
                        .latest(task.getId(), ScrmInspectionSnapshotService.DIM_BUSINESS)
                        .orElse(null);
                alertService.notifyBusinessChange(task, snapshot, old);
            }
            // 更新任务工商游标与执行状态
            task.setLastBusinessRunAt(LocalDateTime.now());
            task.setLastRunAt(LocalDateTime.now());
            task.setLastResult(Boolean.TRUE.equals(snapshot.getChanged()) ? "ALERTED" : "SUCCESS");
            task.setRunCount((task.getRunCount() == null ? 0 : task.getRunCount()) + 1);
            taskRepository.save(task);
            return snapshot;
        } catch (QixinApiException e) {
            log.warn("[工商巡检] 企业查询失败: taskId={}, name={}, err={}, retryable={}",
                    task.getId(), name, e.getMessage(), e.isRetryable());
            markFailed(task, e.getMessage());
            throw e;
        }
    }

    /**
     * 记录单企业失败快照并更新任务状态（失败不阻断本轮）。
     *
     * @param task     巡检任务
     * @param errorMsg 错误描述
     */
    private void markFailed(ScrmInspectionTaskEntity task, String errorMsg) {
        try {
            snapshotService.saveSnapshot(task.getId(), ScrmInspectionSnapshotService.DIM_BUSINESS,
                    task.getCreditCode(), task.getEnterpriseName(), SOURCE_QIXIN, "QUERY_FAILED",
                    buildFailureJson(errorMsg), errorMsg);
        } catch (Exception e) {
            log.warn("[工商巡检] 失败快照入库失败: taskId={}, err={}", task.getId(), e.getMessage());
        }
        try {
            task.setLastRunAt(LocalDateTime.now());
            task.setLastResult("FAILED");
            task.setLastErrorMessage(errorMsg);
            task.setRunCount((task.getRunCount() == null ? 0 : task.getRunCount()) + 1);
            taskRepository.save(task);
        } catch (Exception e) {
            log.warn("[工商巡检] 任务失败状态更新失败: taskId={}, err={}", task.getId(), e.getMessage());
        }
    }

    /**
     * 归一化经营状态（供看板聚合与告警语义映射）。
     *
     * @param profile 工商快照
     * @return 归一化状态
     */
    private String normalizeStatus(QixinEnterpriseProfile profile) {
        String raw = profile.getBusinessStatus();
        if (profile.isAbnormal()) {
            return "经营异常";
        }
        if (raw == null || raw.isBlank()) {
            return "未知";
        }
        if (raw.contains("注销") || raw.contains("吊销") || raw.contains("迁出")
                || raw.contains("停业") || raw.contains("清算")) {
            return raw;
        }
        if (raw.contains("在营") || raw.contains("存续") || raw.contains("正常")) {
            return "正常";
        }
        return raw;
    }

    /**
     * 构建工商状态明细 JSON（稳定字段序, 供哈希比对与人工复核）。
     *
     * @param task    巡检任务
     * @param profile 工商快照
     * @return 明细 JSON 字符串
     */
    private String buildDetailJson(ScrmInspectionTaskEntity task, QixinEnterpriseProfile profile) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("enterpriseName", profile.getEnterpriseName() != null
                ? profile.getEnterpriseName() : task.getEnterpriseName());
        detail.put("creditCode", profile.getCreditCode() != null
                ? profile.getCreditCode() : task.getCreditCode());
        detail.put("legalPerson", profile.getLegalPerson());
        detail.put("businessStatus", profile.getBusinessStatus());
        detail.put("cancelDate", profile.getCancelDate());
        detail.put("revokeDate", profile.getRevokeDate());
        detail.put("abnormal", profile.isAbnormal());
        detail.put("abnormalReason", profile.getAbnormalReason());
        detail.put("abnormalDate", profile.getAbnormalDate());
        detail.put("shareholders", profile.getShareholders());
        detail.put("administrativePenalty", profile.getAdministrativePenalty());
        detail.put("queryTime", profile.getQueryTime());
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            log.warn("[工商巡检] 明细 JSON 序列化失败, 回落文本: taskId={}, err={}",
                    task.getId(), e.getMessage());
            return String.valueOf(detail);
        }
    }

    /**
     * 构建失败快照明细 JSON。
     *
     * @param errorMsg 错误描述
     * @return 明细 JSON 字符串
     */
    private String buildFailureJson(String errorMsg) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("error", errorMsg);
        detail.put("inspectAt", LocalDateTime.now().toString());
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            return "{\"error\":\"" + errorMsg.replace("\"", "'") + "\"}";
        }
    }
}