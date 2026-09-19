/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignReportService.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmCampaignEntity;
import org.hiylo.scrm.entity.ScrmCampaignExecutionLogEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCampaignExecutionLogRepository;
import org.hiylo.scrm.repository.ScrmCampaignRepository;
import org.hiylo.scrm.vo.CampaignReportVo;
import org.hiylo.scrm.vo.CampaignReportVo.DailyExecutionVo;
import org.hiylo.scrm.vo.CampaignReportVo.ErrorStatsVo;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SCRM 营销任务效果分析服务。
 * <p>
 * 基于执行日志聚合生成任务效果报告, 包括执行计数、成功率 / 失败率、
 * 涉及账号数与行为流数、首末执行时间、平均执行间隔、按天执行趋势与 Top 错误统计。
 * 报告生成只读, 不加 {@code @Transactional}。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmCampaignReportService {

    /** 错误消息截取长度上限, 避免 VO 过大 */
    private static final int ERROR_MESSAGE_MAX_LENGTH = 100;

    /** Top 错误统计返回数量 */
    private static final int TOP_ERRORS_LIMIT = 5;

    /** 百分比换算基数 */
    private static final double PERCENT_BASE = 100.0;

    /** 比率小数保留位数 */
    private static final int RATE_SCALE = 2;

    /** 营销任务数据访问层 */
    private final ScrmCampaignRepository campaignRepository;

    /** 执行日志数据访问层 */
    private final ScrmCampaignExecutionLogRepository executionLogRepository;

    /** 执行日志服务 (复用状态 / 动作常量) */
    private final ScrmCampaignExecutionLogService executionLogService;

    /**
     * 生成指定营销任务的效果分析报告。
     * <p>
     * 流程: 校验任务存在 → 拉取全部执行日志 (升序) → 聚合统计指标 → 构建 VO。
     * </p>
     *
     * @param campaignId 营销任务 ID
     * @return 效果报告 VO
     * @throws ScrmException 任务不存在时抛出 notFound
     */
    public CampaignReportVo generateReport(Long campaignId) {
        // 1. 校验任务存在
        ScrmCampaignEntity campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> ScrmException.notFound("营销任务不存在: " + campaignId));

        // 2. 拉取全部执行日志 (按 operatedAt 升序)
        List<ScrmCampaignExecutionLogEntity> logs = executionLogRepository
                .findByCampaignIdOrderByOperatedAtAsc(campaignId);

        // 3. 聚合统计指标
        long totalExecutions = logs.size();
        long successCount = logs.stream()
                .filter(e -> ScrmCampaignExecutionLogService.STATUS_SUCCESS.equals(e.getStatus()))
                .count();
        long failedCount = logs.stream()
                .filter(e -> ScrmCampaignExecutionLogService.STATUS_FAILED.equals(e.getStatus()))
                .count();
        long runningCount = logs.stream()
                .filter(e -> ScrmCampaignExecutionLogService.STATUS_RUNNING.equals(e.getStatus()))
                .count();

        // 成功率 / 失败率: 分母为 0 时返回 0.0
        long denom = successCount + failedCount;
        double successRate = denom > 0
                ? roundRate((double) successCount / denom * PERCENT_BASE)
                : 0.0;
        double failureRate = denom > 0
                ? roundRate((double) failedCount / denom * PERCENT_BASE)
                : 0.0;

        // 涉及账号数 (distinct operatedBy, 过滤 null)
        long uniqueAccounts = logs.stream()
                .map(ScrmCampaignExecutionLogEntity::getOperatedBy)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        // 涉及行为流数 (distinct behaviorFlowId, 过滤 null)
        long uniqueBehaviorFlows = logs.stream()
                .map(ScrmCampaignExecutionLogEntity::getBehaviorFlowId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        // 首次 / 最近执行时间 (logs 已升序, 首尾即 min / max)
        LocalDateTime firstExecutionAt = logs.isEmpty() ? null : logs.get(0).getOperatedAt();
        LocalDateTime lastExecutionAt = logs.isEmpty() ? null : logs.get(logs.size() - 1).getOperatedAt();

        // 平均执行间隔: 相邻日志 operatedAt 差值 (分钟) 的平均
        long avgIntervalMinutes = computeAvgIntervalMinutes(logs);

        // 执行天数: startDate 到 endDate (或现在) 的天数
        Integer durationDays = computeDurationDays(campaign.getStartTime(), campaign.getEndTime());

        // 按天分组统计
        List<DailyExecutionVo> dailyTrend = buildDailyTrend(logs);

        // Top 5 错误统计 (status=FAILED 的日志)
        List<ErrorStatsVo> topErrors = buildTopErrors(logs);

        return CampaignReportVo.builder()
                .campaignId(campaign.getId())
                .campaignName(campaign.getCampaignName())
                .campaignType(campaign.getCampaignType())
                .status(campaign.getStatus())
                .startDate(campaign.getStartTime())
                .endDate(campaign.getEndTime())
                .durationDays(durationDays)
                .totalExecutions(totalExecutions)
                .successCount(successCount)
                .failedCount(failedCount)
                .runningCount(runningCount)
                .successRate(successRate)
                .failureRate(failureRate)
                .uniqueAccounts(uniqueAccounts)
                .uniqueBehaviorFlows(uniqueBehaviorFlows)
                .firstExecutionAt(firstExecutionAt)
                .lastExecutionAt(lastExecutionAt)
                .avgExecutionIntervalMinutes(avgIntervalMinutes)
                .dailyTrend(dailyTrend)
                .topErrors(topErrors)
                .build();
    }

    /**
     * 计算相邻日志 operatedAt 时间差 (分钟) 的平均值。
     * <p>
     * logs 已按 operatedAt 升序, 少于 2 条返回 0。
     * </p>
     *
     * @param logs 执行日志列表 (升序)
     * @return 平均间隔分钟数
     */
    private long computeAvgIntervalMinutes(List<ScrmCampaignExecutionLogEntity> logs) {
        if (logs.size() < 2) {
            return 0L;
        }
        long totalMinutes = 0L;
        for (int i = 1; i < logs.size(); i++) {
            LocalDateTime prev = logs.get(i - 1).getOperatedAt();
            LocalDateTime curr = logs.get(i).getOperatedAt();
            if (prev != null && curr != null) {
                totalMinutes += Duration.between(prev, curr).toMinutes();
            }
        }
        return totalMinutes / (logs.size() - 1);
    }

    /**
     * 计算执行天数: startDate 到 endDate (或现在) 的天数差。
     * <p>
     * startDate 为 null 时返回 0; endDate 为 null 时使用 LocalDateTime.now()。
     * </p>
     *
     * @param startDate 任务开始时间
     * @param endDate   任务结束时间 (可空)
     * @return 天数差 (非负)
     */
    private Integer computeDurationDays(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null) {
            return 0;
        }
        LocalDateTime end = endDate != null ? endDate : LocalDateTime.now();
        long days = Duration.between(startDate, end).toDays();
        return days < 0 ? 0 : (int) days;
    }

    /**
     * 按天分组统计执行情况, 返回升序的每日趋势列表。
     *
     * @param logs 执行日志列表
     * @return 按天聚合的执行统计列表
     */
    private List<DailyExecutionVo> buildDailyTrend(List<ScrmCampaignExecutionLogEntity> logs) {
        // key = yyyy-MM-dd
        Map<String, DailyAggregator> grouped = new LinkedHashMap<>();
        for (ScrmCampaignExecutionLogEntity logEntry : logs) {
            if (logEntry.getOperatedAt() == null) {
                continue;
            }
            LocalDate localDate = logEntry.getOperatedAt().toLocalDate();
            String dateKey = localDate.toString();
            DailyAggregator aggregator = grouped.computeIfAbsent(dateKey, k -> new DailyAggregator(k));
            aggregator.totalCount++;
            String status = logEntry.getStatus();
            if (ScrmCampaignExecutionLogService.STATUS_SUCCESS.equals(status)) {
                aggregator.successCount++;
            } else if (ScrmCampaignExecutionLogService.STATUS_FAILED.equals(status)) {
                aggregator.failedCount++;
            }
        }
        // 按日期升序排序
        return grouped.values().stream()
                .sorted(Comparator.comparing(a -> a.date))
                .map(a -> DailyExecutionVo.builder()
                        .date(a.date)
                        .totalCount(a.totalCount)
                        .successCount(a.successCount)
                        .failedCount(a.failedCount)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 聚合 Top 5 错误统计 (status=FAILED 的日志)。
     * <p>
     * 按 errorCode + errorMessage 分组计数, 截取 errorMessage 前 100 字符,
     * 按出现次数倒序返回前 5 条。
     * </p>
     *
     * @param logs 执行日志列表
     * @return Top 5 错误统计列表
     */
    private List<ErrorStatsVo> buildTopErrors(List<ScrmCampaignExecutionLogEntity> logs) {
        // key = errorCode|errorMessage(截断后)
        Map<String, ErrorAggregator> grouped = new LinkedHashMap<>();
        for (ScrmCampaignExecutionLogEntity logEntry : logs) {
            if (!ScrmCampaignExecutionLogService.STATUS_FAILED.equals(logEntry.getStatus())) {
                continue;
            }
            String code = logEntry.getErrorCode() != null ? logEntry.getErrorCode() : "";
            String message = truncate(logEntry.getErrorMessage());
            String key = code + "|" + message;
            ErrorAggregator aggregator = grouped.computeIfAbsent(key, k -> new ErrorAggregator(code, message));
            aggregator.count++;
        }
        return grouped.values().stream()
                .sorted(Comparator.comparingLong((ErrorAggregator a) -> a.count).reversed())
                .limit(TOP_ERRORS_LIMIT)
                .map(a -> ErrorStatsVo.builder()
                        .errorCode(a.errorCode)
                        .errorMessage(a.errorMessage)
                        .count(a.count)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 截取错误消息前 {@link #ERROR_MESSAGE_MAX_LENGTH} 字符。
     *
     * @param message 原始错误消息 (可空)
     * @return 截断后的错误消息
     */
    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > ERROR_MESSAGE_MAX_LENGTH
                ? message.substring(0, ERROR_MESSAGE_MAX_LENGTH)
                : message;
    }

    /**
     * 将比率四舍五入到 {@link #RATE_SCALE} 位小数。
     *
     * @param value 原始比率
     * @return 保留 2 位小数后的比率
     */
    private double roundRate(double value) {
        return BigDecimal.valueOf(value)
                .setScale(RATE_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * 按天聚合临时容器。
     *
 * @since V1.0
     * @author Hsi Chu
     */
    private static class DailyAggregator {

        /** 日期（yyyy-MM-dd） */
        private final String date;

        /** 当日执行总数 */
        private long totalCount;

        /** 当日成功数 */
        private long successCount;

        /** 当日失败数 */
        private long failedCount;

        DailyAggregator(String date) {
            this.date = date;
        }
    }

    /**
     * 错误聚合临时容器。
     *
 * @since V1.0
     * @author Hsi Chu
     */
    private static class ErrorAggregator {

        /** 错误码 */
        private final String errorCode;

        /** 错误消息（已截断） */
        private final String errorMessage;

        /** 出现次数 */
        private long count;

        ErrorAggregator(String errorCode, String errorMessage) {
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }
    }
}
