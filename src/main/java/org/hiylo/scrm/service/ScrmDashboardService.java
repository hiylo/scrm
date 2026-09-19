/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDashboardService.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAccountHealthRepository;
import org.hiylo.scrm.repository.ScrmAccountRepository;
import org.hiylo.scrm.repository.ScrmCampaignRepository;
import org.hiylo.scrm.repository.ScrmConversationMessageRepository;
import org.hiylo.scrm.repository.ScrmConversationRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmRiskSignalRepository;
import org.hiylo.scrm.vo.AccountOverviewVo;
import org.hiylo.scrm.vo.CampaignOverviewVo;
import org.hiylo.scrm.vo.ConversationOverviewVo;
import org.hiylo.scrm.vo.CustomerOverviewVo;
import org.hiylo.scrm.vo.DailyCountVo;
import org.hiylo.scrm.vo.DashboardOverviewVo;
import org.hiylo.scrm.vo.DashboardTrendVo;
import org.hiylo.scrm.vo.RiskOverviewVo;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 数据看板聚合服务。
 * <p>
 * 聚合账号 / 任务 / 客户 / 会话 / 风控五个维度的概览数据, 供看板前端展示。
 * 所有聚合查询均通过 {@code @Query} 在数据库侧 group by 完成, 避免 N+1。
 * 数据按归属账号隔离, 默认统计近 7 天趋势。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmDashboardService {

    /** 趋势统计窗口（近 7 天, 含今天） */
    private static final int TREND_DAYS = 7;

    /** 日期格式化器（与 native query 中 to_char 一致） */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 账号在线状态值（与 ScrmAccountEntity.loginState 一致） */
    private static final String LOGIN_STATE_ONLINE = "LOGIN";

    /** 账号离线状态值（与 ScrmAccountEntity.loginState 一致） */
    private static final String LOGIN_STATE_OFFLINE = "LOGOUT";

    /** 账号冻结状态值（与 ScrmAccountEntity.loginState 一致） */
    private static final String LOGIN_STATE_FROZEN = "FROZEN";

    /** 账号数据访问层 */
    private final ScrmAccountRepository accountRepository;

    /** 账号健康度检测记录数据访问层（看板展示健康检测覆盖度） */
    private final ScrmAccountHealthRepository accountHealthRepository;

    /** 任务数据访问层 */
    private final ScrmCampaignRepository campaignRepository;

    /** 客户数据访问层 */
    private final ScrmCustomerRepository customerRepository;

    /** 会话数据访问层 */
    private final ScrmConversationRepository conversationRepository;

    /** 会话消息数据访问层 */
    private final ScrmConversationMessageRepository messageRepository;

    /** 风控信号数据访问层 */
    private final ScrmRiskSignalRepository riskSignalRepository;

    /**
     * 账号概览: 总数、各平台分布、登录态分布、在线率、不健康账号数与健康检测记录数。
     * <p>
     * onlineRate 与 unhealthyCount 直接查 {@link ScrmAccountRepository} 获取当前账号池登录态分布;
     * totalHealthChecks 查 {@link ScrmAccountHealthRepository} 反映健康检测覆盖度。
     * </p>
     *
     * @return 账号概览 VO
     */
    public AccountOverviewVo getAccountOverview() {
        long total = accountRepository.count();

        Map<String, Long> platformDistribution = aggregateToMap(accountRepository.countByPlatformType());
        Map<String, Long> loginStateDistribution = aggregateToMap(accountRepository.countByLoginState());

        long onlineCount = accountRepository.countByLoginState(LOGIN_STATE_ONLINE);
        double onlineRate = total > 0 ? (double) onlineCount / total : 0.0;

        // 不健康账号数 = 离线 + 冻结
        long offlineCount = accountRepository.countByLoginState(LOGIN_STATE_OFFLINE);
        long frozenCount = accountRepository.countByLoginState(LOGIN_STATE_FROZEN);
        long unhealthyCount = offlineCount + frozenCount;

        // 健康检测记录总数（反映检测覆盖度）
        long totalHealthChecks = accountHealthRepository.count();

        return AccountOverviewVo.builder()
                .totalAccounts(total)
                .platformDistribution(platformDistribution)
                .loginStateDistribution(loginStateDistribution)
                .onlineRate(onlineRate)
                .unhealthyCount(unhealthyCount)
                .totalHealthChecks(totalHealthChecks)
                .build();
    }

    /**
     * 任务概览: 总数、各状态分布、近 7 天创建趋势。
     *
     * @return 任务概览 VO
     */
    public CampaignOverviewVo getCampaignOverview() {
        long total = campaignRepository.count();

        Map<String, Long> statusDistribution = aggregateToMap(campaignRepository.countByStatus());
        List<DailyCountVo> recentTrend = buildDailyTrend(
                campaignRepository.dailyCountByCreateTime(trendStart()));

        return CampaignOverviewVo.builder()
                .totalCampaigns(total)
                .statusDistribution(statusDistribution)
                .recentTrend(recentTrend)
                .build();
    }

    /**
     * 客户概览: 总数、各生命周期分布、近 7 天新增。
     *
     * @return 客户概览 VO
     */
    public CustomerOverviewVo getCustomerOverview() {
        long total = customerRepository.count();

        Map<String, Long> lifecycleDistribution = aggregateToMap(customerRepository.countByLifecycle());
        List<DailyCountVo> recentNewCustomers = buildDailyTrend(
                customerRepository.dailyCountByCreateTime(trendStart()));

        return CustomerOverviewVo.builder()
                .totalCustomers(total)
                .lifecycleDistribution(lifecycleDistribution)
                .recentNewCustomers(recentNewCustomers)
                .build();
    }

    /**
     * 会话概览: 总数、消息总量、近 7 天消息量、活跃会话数。
     *
     * @return 会话概览 VO
     */
    public ConversationOverviewVo getConversationOverview() {
        long totalConversations = conversationRepository.count();
        long totalMessages = messageRepository.count();

        List<DailyCountVo> recentMessages = buildDailyTrend(
                messageRepository.dailyCountBySentAt(trendStart()));

        long activeConversations = conversationRepository.countByLastMessageAtAfter(trendStart());

        return ConversationOverviewVo.builder()
                .totalConversations(totalConversations)
                .totalMessages(totalMessages)
                .recentMessages(recentMessages)
                .activeConversations(activeConversations)
                .build();
    }

    /**
     * 风控概览: 风控信号总数、各风险等级分布、各信号类型分布、近 7 天触发趋势。
     * <p>
     * 全部聚合查询均带账号隔离, 在数据库侧 group by 完成, 避免 N+1。
     * </p>
     *
     * @return 风控概览 VO
     */
    public RiskOverviewVo getRiskOverview() {
        long total = riskSignalRepository.count();

        Map<String, Long> riskLevelDistribution = aggregateToMap(
                riskSignalRepository.countGroupByRiskLevel());
        Map<String, Long> signalTypeDistribution = aggregateToMap(
                riskSignalRepository.countGroupBySignalType());
        List<DailyCountVo> recentTrend = buildDailyTrend(
                riskSignalRepository.dailyCountByTriggeredAt(trendStart()));

        return RiskOverviewVo.builder()
                .totalRiskSignals(total)
                .riskLevelDistribution(riskLevelDistribution)
                .signalTypeDistribution(signalTypeDistribution)
                .recentTrend(recentTrend)
                .build();
    }

    /**
     * 综合概览: 聚合账号 / 任务 / 客户 / 会话 / 风控全部维度。
     *
     * @return 综合概览 VO
     */
    public DashboardOverviewVo getOverview() {
        return DashboardOverviewVo.builder()
                .accountOverview(getAccountOverview())
                .campaignOverview(getCampaignOverview())
                .customerOverview(getCustomerOverview())
                .conversationOverview(getConversationOverview())
                .riskOverview(getRiskOverview())
                .build();
    }

    /**
     * 查询指定指标的趋势数据 (按日聚合, 支持自定义天数窗口)。
     * <p>
     * 支持的指标:
     * <ul>
     *   <li>{@code customers} - 新增客户数 (按 createTime 聚合)</li>
     *   <li>{@code campaigns} - 新增任务数 (按 createTime 聚合)</li>
     *   <li>{@code messages} - 消息发送量 (按 sentAt 聚合)</li>
     *   <li>{@code risk-signals} - 风控信号数 (按 triggeredAt 聚合)</li>
     * </ul>
     * </p>
     *
     * @param metric 指标名称
     * @param days 统计天数 (1-90, 默认 7, 含今天)
     * @return 趋势数据 VO
     */
    public DashboardTrendVo getTrend(String metric, int days) {
        // 参数规范化
        if (days < 1) {
            days = TREND_DAYS;
        }
        if (days > 90) {
            days = 90;
        }
        LocalDateTime from = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        List<DailyCountVo> trend;
        switch (metric == null ? "" : metric.toLowerCase()) {
            case "customers":
                trend = buildDailyTrend(customerRepository.dailyCountByCreateTime(from), days);
                break;
            case "campaigns":
                trend = buildDailyTrend(campaignRepository.dailyCountByCreateTime(from), days);
                break;
            case "messages":
                trend = buildDailyTrend(messageRepository.dailyCountBySentAt(from), days);
                break;
            case "risk-signals":
                trend = buildDailyTrend(riskSignalRepository.dailyCountByTriggeredAt(from), days);
                break;
            default:
                throw ScrmException.badRequest("不支持的趋势指标: " + metric
                        + " (支持: customers / campaigns / messages / risk-signals)");
        }
        // 聚合统计
        long total = trend.stream().mapToLong(DailyCountVo::getCount).sum();
        double dailyAvg = days > 0 ? (double) total / days : 0.0;
        DailyCountVo peak = trend.stream()
                .max(java.util.Comparator.comparingLong(DailyCountVo::getCount))
                .orElse(null);
        return DashboardTrendVo.builder()
                .metric(metric.toLowerCase())
                .days(days)
                .startDate(LocalDate.now().minusDays(days - 1L).format(DATE_FORMATTER))
                .endDate(LocalDate.now().format(DATE_FORMATTER))
                .totalCount(total)
                .trend(trend)
                .dailyAverage(Math.round(dailyAvg * 100) / 100.0)
                .peakValue(peak != null ? peak.getCount() : 0L)
                .peakDate(peak == null ? null : peak.getDate())
                .build();
    }

    // ==================== 工具方法 ====================

    /**
     * 将 {@code List<Object[]{key, count}>} 聚合为 Map。
     *
     * @param rows 聚合查询结果行
     * @return Map（key=分组键, value=计数）
     */
    private Map<String, Long> aggregateToMap(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        if (rows == null) {
            return map;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null) {
                continue;
            }
            String key = String.valueOf(row[0]);
            Long count = toLong(row[1]);
            map.put(key, count);
        }
        return map;
    }

    /**
     * 将按日聚合结果转为 {@link DailyCountVo} 列表, 并补齐缺失日期为 0。
     *
     * @param rows 按日聚合结果行 {@code Object[]{date(yyyy-MM-dd), count}}
     * @return 完整 7 天日度计数列表
     */
    private List<DailyCountVo> buildDailyTrend(List<Object[]> rows) {
        return buildDailyTrend(rows, TREND_DAYS);
    }

    /**
     * 将按日聚合结果转为 {@link DailyCountVo} 列表, 并补齐缺失日期为 0。
     * 支持自定义天数窗口, 供 {@link #getTrend(String, int)} 使用。
     *
     * @param rows 按日聚合结果行 {@code Object[]{date(yyyy-MM-dd), count}}
     * @param days 统计天数 (含今天)
     * @return 指定天数的日度计数列表, 按日期升序
     */
    private List<DailyCountVo> buildDailyTrend(List<Object[]> rows, int days) {
        Map<String, Long> raw = new LinkedHashMap<>();
        if (rows != null) {
            for (Object[] row : rows) {
                if (row == null || row.length < 2 || row[0] == null) {
                    continue;
                }
                raw.put(String.valueOf(row[0]), toLong(row[1]));
            }
        }
        List<DailyCountVo> trend = new ArrayList<>(days);
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            String dayStr = day.format(DATE_FORMATTER);
            trend.add(DailyCountVo.builder()
                    .date(dayStr)
                    .count(raw.getOrDefault(dayStr, 0L))
                    .build());
        }
        return trend;
    }

    /**
     * 趋势起始时间: 今天 0 点 - 6 天 = 近 7 天（含今天）。
     *
     * @return 起始 LocalDateTime
     */
    private LocalDateTime trendStart() {
        return LocalDate.now().minusDays(TREND_DAYS - 1L).atStartOfDay();
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
