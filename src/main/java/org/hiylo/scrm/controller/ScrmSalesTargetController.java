/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSalesTargetController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmSalesAchievementDto;
import org.hiylo.scrm.dto.ScrmSalesForecastDto;
import org.hiylo.scrm.dto.ScrmSalesRankingDto;
import org.hiylo.scrm.dto.ScrmSalesTargetDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmSalesTargetService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * SCRM 销售目标/业绩管理控制器
 * <p>
 * 提供销售目标的设定/查询/归档, 达成记录的录入/批量/调整, 业绩排名的计算/查询/我的排名,
 * 业绩预测与达成趋势, 以及目标概览/达成率分布/Top 榜等统计接口。
 * 权限由 gateway-server 统一鉴权, 此处通过 {@link RequirePermission} 声明资源与动作元数据。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/sales-targets")
@RequiredArgsConstructor
public class ScrmSalesTargetController {

    /** 销售目标服务 */
    private final ScrmSalesTargetService salesTargetService;

    // ============================================================
    // 销售目标管理
    // ============================================================

    /**
     * 创建销售目标
     *
     * @param dto 目标参数
     * @return 创建后的目标
     */
    @RequirePermission(resource = "scrm_sales_target", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmSalesTargetDto> createTarget(@Valid @RequestBody ScrmSalesTargetDto dto)
            throws ScrmException {
        return OperationResponse.build(salesTargetService.createTarget(dto));
    }

    /**
     * 更新销售目标
     *
     * @param id  目标 ID
     * @param dto 目标参数
     * @return 更新后的目标
     */
    @RequirePermission(resource = "scrm_sales_target", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/{id}")
    public OperationResponse<ScrmSalesTargetDto> updateTarget(@PathVariable Long id,
                                                                @RequestBody ScrmSalesTargetDto dto)
            throws ScrmException {
        return OperationResponse.build(salesTargetService.updateTarget(id, dto));
    }

    /**
     * 删除销售目标
     *
     * @param id 目标 ID
     * @return 空响应
     * @throws ScrmException 目标不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_sales_target", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> deleteTarget(@PathVariable Long id) throws ScrmException {
        salesTargetService.deleteTarget(id);
        return OperationResponse.build();
    }

    /**
     * 查询销售目标详情
     *
     * @param id 目标 ID
     * @return 目标详情
     * @throws ScrmException 目标不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_sales_target", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmSalesTargetDto> getTarget(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(salesTargetService.getTarget(id));
    }

    /**
     * 分页查询销售目标, 支持按对象类型/指标类型/周期类型/状态过滤
     *
     * @param targetType 目标对象类型 (可选)
     * @param metricType 指标类型 (可选)
     * @param periodType 周期类型 (可选)
     * @param status     状态 (可选)
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 目标分页结果
     */
    @RequirePermission(resource = "scrm_sales_target", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmSalesTargetDto>> listTargets(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String metricType,
            @RequestParam(required = false) String periodType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(
                salesTargetService.listTargets(targetType, metricType, periodType, status, pageable));
    }

    /**
     * 查询某人某周期目标
     *
     * @param targetId   目标对象 ID (userId/teamId/deptId)
     * @param periodType 周期类型 (可选)
     * @return 目标列表
     */
    @RequirePermission(resource = "scrm_sales_target", action = "read")
    @GetMapping("/assignee/{targetId}")
    public OperationResponse<List<ScrmSalesTargetDto>> getTargetsByAssignee(
            @PathVariable String targetId,
            @RequestParam(required = false) String periodType) {
        return OperationResponse.build(salesTargetService.getTargetsByAssignee(targetId, periodType));
    }

    /**
     * 归档销售目标
     *
     * @param id 目标 ID
     * @return 更新后的目标
     * @throws ScrmException 目标不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_sales_target", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{id}/archive")
    public OperationResponse<ScrmSalesTargetDto> archiveTarget(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(salesTargetService.archiveTarget(id));
    }

    /**
     * 重算目标实际值与达成率
     *
     * @param id 目标 ID
     * @return 更新后的目标
     * @throws ScrmException 目标不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_sales_target", action = "update")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/{id}/recalculate")
    public OperationResponse<ScrmSalesTargetDto> recalculateTarget(@PathVariable Long id) throws ScrmException {
        salesTargetService.recalculateTarget(id);
        return OperationResponse.build(salesTargetService.getTarget(id));
    }

    // ============================================================
    // 达成记录管理
    // ============================================================

    /**
     * 记录单次销售达成
     *
     * @param dto 达成记录参数
     * @return 创建后的达成记录
     * @throws ScrmException 参数非法 / 目标不存在
     */
    @RequirePermission(resource = "scrm_sales_target", action = "create")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/achievements")
    public OperationResponse<ScrmSalesAchievementDto> recordAchievement(
            @Valid @RequestBody ScrmSalesAchievementDto dto) throws ScrmException {
        return OperationResponse.build(salesTargetService.recordAchievement(dto));
    }

    /**
     * 批量记录销售达成
     *
     * @param achievements 达成记录列表
     * @return 创建后的达成记录列表
     * @throws ScrmException 参数非法 / 批量记录失败
     */
    @RequirePermission(resource = "scrm_sales_target", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/achievements/batch")
    public OperationResponse<List<ScrmSalesAchievementDto>> batchRecordAchievements(
            @RequestBody List<ScrmSalesAchievementDto> achievements) throws ScrmException {
        return OperationResponse.build(salesTargetService.batchRecordAchievements(achievements));
    }

    /**
     * 分页查询达成记录, 支持按目标 ID 与达成日期范围过滤
     *
     * @param targetId  目标 ID (可选)
     * @param startDate 达成日期下限 (可选)
     * @param endDate   达成日期上限 (可选)
     * @param page      页码 (从 0 开始, 默认 0)
     * @param size      每页大小 (默认 20)
     * @return 达成记录分页
     */
    @RequirePermission(resource = "scrm_sales_target", action = "read")
    @GetMapping("/achievements")
    public OperationResponse<Page<ScrmSalesAchievementDto>> getAchievements(
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(
                salesTargetService.getAchievements(targetId, startDate, endDate, pageable));
    }

    /**
     * 分页查询达成记录 (POST 形式, 便于复杂过滤场景)
     *
     * @param targetId  目标 ID (可选)
     * @param startDate 达成日期下限 (可选)
     * @param endDate   达成日期上限 (可选)
     * @param page      页码 (从 0 开始, 默认 0)
     * @param size      每页大小 (默认 20)
     * @return 达成记录分页
     */
    @RequirePermission(resource = "scrm_sales_target", action = "read")
    @PostMapping("/achievements/list")
    public OperationResponse<Page<ScrmSalesAchievementDto>> listAchievements(
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(
                salesTargetService.getAchievements(targetId, startDate, endDate, pageable));
    }

    /**
     * 手动调整目标实际值
     *
     * @param targetId        目标 ID
     * @param adjustmentValue 调整值 (正为增加, 负为减少)
     * @param reason          调整原因 (可选)
     * @return 调整产生的达成记录
     * @throws ScrmException 目标不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_sales_target", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/achievements/{targetId}/adjust")
    public OperationResponse<ScrmSalesAchievementDto> adjustAchievement(
            @PathVariable Long targetId,
            @RequestParam Double adjustmentValue,
            @RequestParam(required = false) String reason) throws ScrmException {
        return OperationResponse.build(
                salesTargetService.adjustAchievement(targetId, adjustmentValue, reason));
    }

    // ============================================================
    // 业绩排名
    // ============================================================

    /**
     * 计算业绩排名
     *
     * @param periodType  周期类型
     * @param periodStart 周期开始
     * @param periodEnd   周期结束
     * @param metricType  指标类型
     * @param targetType  目标对象类型
     * @return 排名列表
     * @throws ScrmException 参数非法 / 计算失败
     */
    @RequirePermission(resource = "scrm_sales_target", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/rankings/calculate")
    public OperationResponse<List<ScrmSalesRankingDto>> calculateRanking(
            @RequestParam String periodType,
            @RequestParam LocalDate periodStart,
            @RequestParam LocalDate periodEnd,
            @RequestParam String metricType,
            @RequestParam String targetType) throws ScrmException {
        return OperationResponse.build(
                salesTargetService.calculateRanking(periodType, periodStart, periodEnd, metricType, targetType));
    }

    /**
     * 分页查询排名
     *
     * @param periodType  周期类型 (可选)
     * @param periodStart 周期开始 (可选)
     * @param periodEnd   周期结束 (可选)
     * @param metricType  指标类型 (可选)
     * @param targetType  目标对象类型 (可选)
     * @param page        页码 (从 0 开始, 默认 0)
     * @param size        每页大小 (默认 20)
     * @return 排名分页结果
     */
    @RequirePermission(resource = "scrm_sales_target", action = "read")
    @GetMapping("/rankings/list")
    public OperationResponse<Page<ScrmSalesRankingDto>> listRankings(
            @RequestParam(required = false) String periodType,
            @RequestParam(required = false) LocalDate periodStart,
            @RequestParam(required = false) LocalDate periodEnd,
            @RequestParam(required = false) String metricType,
            @RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(
                salesTargetService.getRanking(periodType, periodStart, periodEnd, metricType, targetType, pageable));
    }

    /**
     * 我的排名
     *
     * @param userId      用户 ID
     * @param periodType  周期类型
     * @param periodStart 周期开始
     * @param periodEnd   周期结束
     * @return 排名列表
     */
    @RequirePermission(resource = "scrm_sales_target", action = "read")
    @GetMapping("/rankings/my")
    public OperationResponse<List<ScrmSalesRankingDto>> getMyRanking(
            @RequestParam String userId,
            @RequestParam String periodType,
            @RequestParam LocalDate periodStart,
            @RequestParam LocalDate periodEnd) {
        return OperationResponse.build(
                salesTargetService.getMyRanking(userId, periodType, periodStart, periodEnd));
    }

    // ============================================================
    // 业绩预测与趋势
    // ============================================================

    /**
     * 业绩预测
     * <p>
     * 基于历史达成趋势预测未来 forecastDays 天后能否达标。
     * </p>
     *
     * @param targetId     目标 ID
     * @param forecastDays 预测天数 (1-90)
     * @return 预测结果
     */
    @RequirePermission(resource = "scrm_sales_target", action = "read")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/forecast/{targetId}")
    public OperationResponse<Map<String, Object>> forecastAchievement(
            @PathVariable Long targetId,
            @RequestParam @Min(value = 1, message = "预测天数不能小于 1") Integer forecastDays)
            throws ScrmException {
        ScrmSalesForecastDto dto = new ScrmSalesForecastDto();
        dto.setTargetId(targetId);
        dto.setForecastDays(forecastDays);
        return OperationResponse.build(salesTargetService.forecastAchievement(dto));
    }

    /**
     * 达成趋势
     *
     * @param targetId 目标 ID
     * @param days     天数 (默认 7)
     * @return 趋势数据列表
     * @throws ScrmException 目标不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_sales_target", action = "read")
    @GetMapping("/forecast/{targetId}/trend")
    public OperationResponse<List<Map<String, Object>>> getAchievementTrend(
            @PathVariable Long targetId,
            @RequestParam(defaultValue = "7") int days) throws ScrmException {
        return OperationResponse.build(salesTargetService.getAchievementTrend(targetId, days));
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 目标概览
     *
     * @param periodType  周期类型 (可选)
     * @param periodStart 周期开始 (可选)
     * @param periodEnd   周期结束 (可选)
     * @return 概览数据
     */
    @RequirePermission(resource = "scrm_sales_target", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getTargetOverview(
            @RequestParam(required = false) String periodType,
            @RequestParam(required = false) LocalDate periodStart,
            @RequestParam(required = false) LocalDate periodEnd) {
        return OperationResponse.build(
                salesTargetService.getTargetOverview(periodType, periodStart, periodEnd));
    }

    /**
     * 达成率分布
     *
     * @param periodType  周期类型 (可选)
     * @param periodStart 周期开始 (可选)
     * @param periodEnd   周期结束 (可选)
     * @return 分布列表
     */
    @RequirePermission(resource = "scrm_sales_target", action = "read")
    @GetMapping("/stats/distribution")
    public OperationResponse<List<Map<String, Object>>> getAchievementRateDistribution(
            @RequestParam(required = false) String periodType,
            @RequestParam(required = false) LocalDate periodStart,
            @RequestParam(required = false) LocalDate periodEnd) {
        return OperationResponse.build(
                salesTargetService.getAchievementRateDistribution(periodType, periodStart, periodEnd));
    }

    /**
     * 业绩 Top 榜
     *
     * @param periodType  周期类型 (可选)
     * @param periodStart 周期开始 (可选)
     * @param periodEnd   周期结束 (可选)
     * @param limit       返回条数 (默认 10)
     * @return Top 榜列表
     */
    @RequirePermission(resource = "scrm_sales_target", action = "read")
    @GetMapping("/stats/top-performers")
    public OperationResponse<List<Map<String, Object>>> getTopPerformers(
            @RequestParam(required = false) String periodType,
            @RequestParam(required = false) LocalDate periodStart,
            @RequestParam(required = false) LocalDate periodEnd,
            @RequestParam(defaultValue = "10") Integer limit) {
        return OperationResponse.build(
                salesTargetService.getTopPerformers(periodType, periodStart, periodEnd, limit));
    }
}
