/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDashboardController.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmCustomerDto;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmCustomerService;
import org.hiylo.scrm.service.ScrmDashboardService;
import org.hiylo.scrm.vo.AccountOverviewVo;
import org.hiylo.scrm.vo.CampaignOverviewVo;
import org.hiylo.scrm.vo.ConversationOverviewVo;
import org.hiylo.scrm.vo.CustomerOverviewVo;
import org.hiylo.scrm.vo.DashboardOverviewVo;
import org.hiylo.scrm.vo.DashboardTrendVo;
import org.hiylo.scrm.vo.RiskOverviewVo;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SCRM 数据看板 Controller。
 * <p>
 * 提供账号 / 任务 / 客户 / 会话 / 风控 / 综合 6 个看板聚合接口,
 * 供前端看板页面拉取概览数据。权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/dashboard")
@RequiredArgsConstructor
public class ScrmDashboardController {

    /** 看板聚合服务 */
    private final ScrmDashboardService dashboardService;

    /** 客户服务 (用于跟进提醒查询) */
    private final ScrmCustomerService customerService;

    /**
     * 账号概览。
     *
     * @return 账号概览 VO
     */
    @RequirePermission(resource = "scrm_dashboard", action = "read")
    @GetMapping("/accounts")
    public OperationResponse<AccountOverviewVo> accountOverview() {
        return OperationResponse.build(dashboardService.getAccountOverview());
    }

    /**
     * 任务概览。
     *
     * @return 任务概览 VO
     */
    @RequirePermission(resource = "scrm_dashboard", action = "read")
    @GetMapping("/campaigns")
    public OperationResponse<CampaignOverviewVo> campaignOverview() {
        return OperationResponse.build(dashboardService.getCampaignOverview());
    }

    /**
     * 客户概览。
     *
     * @return 客户概览 VO
     */
    @RequirePermission(resource = "scrm_dashboard", action = "read")
    @GetMapping("/customers")
    public OperationResponse<CustomerOverviewVo> customerOverview() {
        return OperationResponse.build(dashboardService.getCustomerOverview());
    }

    /**
     * 会话概览。
     *
     * @return 会话概览 VO
     */
    @RequirePermission(resource = "scrm_dashboard", action = "read")
    @GetMapping("/conversations")
    public OperationResponse<ConversationOverviewVo> conversationOverview() {
        return OperationResponse.build(dashboardService.getConversationOverview());
    }

    /**
     * 风控概览。
     *
     * @return 风控概览 VO
     */
    @RequirePermission(resource = "scrm_dashboard", action = "read")
    @GetMapping("/risk")
    public OperationResponse<RiskOverviewVo> riskOverview() {
        return OperationResponse.build(dashboardService.getRiskOverview());
    }

    /**
     * 综合概览: 聚合账号 / 任务 / 客户 / 会话 / 风控全部维度。
     *
     * @return 综合概览 VO
     */
    @RequirePermission(resource = "scrm_dashboard", action = "read")
    @GetMapping("/overview")
    public OperationResponse<DashboardOverviewVo> overview() {
        return OperationResponse.build(dashboardService.getOverview());
    }

    /**
     * 趋势数据: 按日聚合指定指标的趋势, 支持自定义天数窗口。
     * <p>
     * 支持的指标: customers / campaigns / messages / risk-signals
     * </p>
     *
     * @param metric 指标名称 (默认 customers)
     * @param days 统计天数 (1-90, 默认 7, 含今天)
     * @return 趋势数据 VO
     */
    @RequirePermission(resource = "scrm_dashboard", action = "read")
    @GetMapping("/trend")
    public OperationResponse<DashboardTrendVo> trend(
            @RequestParam(defaultValue = "customers") String metric,
            @RequestParam(defaultValue = "7") int days) {
        return OperationResponse.build(dashboardService.getTrend(metric, days));
    }

    /**
     * 跟进提醒: 返回即将到期或已逾期的跟进客户列表。
     * <p>
     * 查询 nextFollowUpAt 在当前时间 + 24h 内的客户 (含已逾期),
     * 按紧急程度升序排列, 供仪表盘展示待跟进事项。
     * </p>
     *
     * @param limit 最多返回条数 (默认 10, 上限 50)
     * @return 待跟进客户列表
     */
    @RequirePermission(resource = "scrm_dashboard", action = "read")
    @GetMapping("/follow-ups")
    public OperationResponse<List<ScrmCustomerDto>> followUpReminders(
            @RequestParam(defaultValue = "10") int limit) {
        // 限制最大查询条数, 防止一次性返回过多数据
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return OperationResponse.build(customerService.getFollowUpReminders(safeLimit));
    }
}
