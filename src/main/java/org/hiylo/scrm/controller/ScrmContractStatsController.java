/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractStatsController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmContractDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmContractStatsService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 合同统计与分析控制器。
 * <p>
 * 提供合同统计分析能力: 合同概览、合同/付款/变更统计、金额趋势、类型与状态分布、
 * 到期统计、Top 客户与 Top 合同排行、合同风险计算、合同摘要/导出/分享/克隆。
 * 与 {@link ScrmContractController} 中的基础统计接口互补, 本控制器聚焦于
 * {@link ScrmContractStatsService} 提供的高级分析与查询能力。权限由 gateway-server
 * 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/contract-analytics")
@RequiredArgsConstructor
public class ScrmContractStatsController {

    /** 合同统计与查询服务 */
    private final ScrmContractStatsService scrmContractStatsService;

    // ============================================================
    // 概览与统计
    // ============================================================

    /**
     * 合同概览: 总合同数/总金额/各状态数/即将到期数/逾期付款数/待审变更数。
     *
     * @return 概览数据
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/overview")
    public OperationResponse<Map<String, Object>> getContractOverview() {
        return OperationResponse.build(scrmContractStatsService.getContractOverview());
    }

    /**
     * 合同统计: 总数 / 各类型 / 各状态 / 总金额 / 平均金额。
     * <p>时间范围按合同创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/contracts")
    public OperationResponse<Map<String, Object>> getContractStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmContractStatsService.getContractStats(startTime, endTime));
    }

    /**
     * 付款统计: 总数 / 各状态 / 计划/已付/未付金额 / 逾期数量。
     * <p>时间范围按付款创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/payments")
    public OperationResponse<Map<String, Object>> getPaymentStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmContractStatsService.getPaymentStats(startTime, endTime));
    }

    /**
     * 变更统计: 总数 / 各类型 / 各状态 / 金额变化总和。
     * <p>时间范围按变更创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/changes")
    public OperationResponse<Map<String, Object>> getChangeStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmContractStatsService.getChangeStats(startTime, endTime));
    }

    /**
     * 合同金额趋势: 过去 N 个月每月新增合同数与金额。
     *
     * @param months 月数 (默认 6)
     * @return 趋势数据
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/trend")
    public OperationResponse<Map<String, Object>> getContractValueTrend(
            @RequestParam(defaultValue = "6") int months) {
        return OperationResponse.build(scrmContractStatsService.getContractValueTrend(months));
    }

    /**
     * 合同类型分布: 各类型的合同数量。
     *
     * @return 类型分布
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/distribution/type")
    public OperationResponse<Map<String, Object>> getContractTypeDistribution() {
        return OperationResponse.build(scrmContractStatsService.getContractTypeDistribution());
    }

    /**
     * 合同状态分布: 各状态的合同数量。
     *
     * @return 状态分布
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/distribution/status")
    public OperationResponse<Map<String, Object>> getContractStatusDistribution() {
        return OperationResponse.build(scrmContractStatsService.getContractStatusDistribution());
    }

    /**
     * 到期统计: 未来 N 个月内到期的合同数量与金额。
     *
     * @param months 月数 (默认 3)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/expiring")
    public OperationResponse<Map<String, Object>> getExpiringStats(
            @RequestParam(defaultValue = "3") int months) {
        return OperationResponse.build(scrmContractStatsService.getExpiringStats(months));
    }

    // ============================================================
    // Top 排行
    // ============================================================

    /**
     * Top 客户: 按合同总金额降序排列前 N 个客户。
     *
     * @param limit 数量 (默认 10)
     * @return Top 客户列表
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/top/customers")
    public OperationResponse<List<Map<String, Object>>> getTopCustomers(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmContractStatsService.getTopCustomers(limit));
    }

    /**
     * Top 合同: 按合同金额降序排列前 N 个合同。
     *
     * @param limit 数量 (默认 10)
     * @return Top 合同列表
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/top/contracts")
    public OperationResponse<List<Map<String, Object>>> getTopContracts(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmContractStatsService.getTopContracts(limit));
    }

    // ============================================================
    // 合同风险
    // ============================================================

    /**
     * 计算合同风险: 综合评估合同金额、期限、付款逾期、变更次数等因素, 返回风险等级与评估详情。
     * <p>风险等级: LOW / MEDIUM / HIGH / CRITICAL。</p>
     *
     * @param contractId 合同 ID
     * @return 风险评估结果
     * @throws ScrmException 合同不存在
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/risk/{contractId}")
    public OperationResponse<Map<String, Object>> calculateContractRisk(@PathVariable Long contractId)
            throws ScrmException {
        return OperationResponse.build(scrmContractStatsService.calculateContractRisk(contractId));
    }

    // ============================================================
    // 合同摘要/导出/分享/克隆
    // ============================================================

    /**
     * 合同摘要: 合同基本信息 + 付款统计 + 变更数量。
     *
     * @param id 合同 ID
     * @return 摘要信息
     * @throws ScrmException 合同不存在
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/summary/{id}")
    public OperationResponse<Map<String, Object>> getContractSummary(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmContractStatsService.getContractSummary(id));
    }

    /**
     * 导出合同: 返回合同完整信息 (含付款与变更)。
     *
     * @param id 合同 ID
     * @return 导出数据
     * @throws ScrmException 合同不存在
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60, message = "导出过于频繁，请稍后重试")
    @GetMapping("/export/{id}")
    public OperationResponse<Map<String, Object>> exportContract(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmContractStatsService.exportContract(id));
    }

    /**
     * 分享合同: 返回分享信息 (合同 ID + 用户 ID + 分享时间 + 分享链接)。
     *
     * @param id     合同 ID
     * @param userId 目标用户 ID
     * @return 分享信息
     * @throws ScrmException 合同不存在
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @PostMapping("/share/{id}")
    public OperationResponse<Map<String, Object>> shareContract(@PathVariable Long id,
                                                                 @RequestParam Long userId)
            throws ScrmException {
        return OperationResponse.build(scrmContractStatsService.shareContract(id, userId));
    }

    /**
     * 克隆合同: 基于已有合同创建新合同, 新合同编号由参数指定。
     * <p>克隆后的合同状态为 DRAFT, 不继承审批/签署信息。</p>
     *
     * @param id    源合同 ID
     * @param newNo 新合同编号
     * @return 新合同
     * @throws ScrmException 源合同不存在 / 新编号重复
     */
    @RequirePermission(resource = "scrm_contract", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60, message = "克隆合同过于频繁，请稍后重试")
    @PostMapping("/clone/{id}")
    public OperationResponse<ScrmContractDto> cloneContract(@PathVariable Long id,
                                                             @RequestParam String newNo)
            throws ScrmException {
        return OperationResponse.build(scrmContractStatsService.cloneContract(id, newNo));
    }
}
