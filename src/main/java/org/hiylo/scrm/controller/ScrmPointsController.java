/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmPointsAccountDto;
import org.hiylo.scrm.dto.ScrmPointsExchangeDto;
import org.hiylo.scrm.dto.ScrmPointsExchangeRecordDto;
import org.hiylo.scrm.dto.ScrmPointsOperationDto;
import org.hiylo.scrm.dto.ScrmPointsRuleDto;
import org.hiylo.scrm.dto.ScrmPointsTransactionDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmPointsService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 积分/会员体系控制器
 * <p>
 * 提供积分规则、积分账户、积分流水、积分兑换商品与兑换动作、过期处理及统计等接口。
 * 权限由 gateway-server 统一鉴权, 此处通过 {@link RequirePermission} 声明资源与动作元数据。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/points")
@RequiredArgsConstructor
public class ScrmPointsController {

    /** 积分服务 */
    private final ScrmPointsService pointsService;

    // ============================================================
    // 积分规则
    // ============================================================

    /**
     * 创建积分规则
     *
     * @param dto 规则参数
     * @return 创建后的规则
     */
    @RequirePermission(resource = "scrm_points_rule", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules")
    public OperationResponse<ScrmPointsRuleDto> createRule(@Valid @RequestBody ScrmPointsRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(pointsService.createRule(dto));
    }

    /**
     * 更新积分规则
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     */
    @RequirePermission(resource = "scrm_points_rule", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/rules/{id}")
    public OperationResponse<ScrmPointsRuleDto> updateRule(@PathVariable Long id,
                                                            @RequestBody ScrmPointsRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(pointsService.updateRule(id, dto));
    }

    /**
     * 删除积分规则
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_points_rule", action = "delete")
    @DeleteMapping("/rules/{id}")
    public OperationResponse<Void> deleteRule(@PathVariable Long id) throws ScrmException {
        pointsService.deleteRule(id);
        return OperationResponse.build();
    }

    /**
     * 查询积分规则详情
     *
     * @param id 规则 ID
     * @return 规则详情
     * @throws ScrmException 规则不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_points_rule", action = "read")
    @GetMapping("/rules/{id}")
    public OperationResponse<ScrmPointsRuleDto> getRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(pointsService.getRule(id));
    }

    /**
     * 分页查询积分规则, 支持按规则类型 / 触发事件 / 启用状态过滤
     *
     * @param ruleType     规则类型过滤: EARN / REDEEM (可选)
     * @param triggerEvent 触发事件过滤 (可选)
     * @param enabled      启用状态过滤 (可选)
     * @param page         页码 (从 0 开始, 默认 0)
     * @param size         每页大小 (默认 20)
     * @return 规则分页结果
     */
    @RequirePermission(resource = "scrm_points_rule", action = "read")
    @GetMapping("/rules/list")
    public OperationResponse<Page<ScrmPointsRuleDto>> listRules(
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) String triggerEvent,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(pointsService.listRules(ruleType, triggerEvent, enabled, pageable));
    }

    /**
     * 启用积分规则
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_points_rule", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules/{id}/enable")
    public OperationResponse<ScrmPointsRuleDto> enableRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(pointsService.enableRule(id));
    }

    /**
     * 禁用积分规则
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_points_rule", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules/{id}/disable")
    public OperationResponse<ScrmPointsRuleDto> disableRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(pointsService.disableRule(id));
    }

    // ============================================================
    // 积分账户
    // ============================================================

    /**
     * 获取或创建客户积分账户
     *
     * @param customerId 客户 ID
     * @return 积分账户
     */
    @RequirePermission(resource = "scrm_points_account", action = "read")
    @GetMapping("/accounts/{customerId}")
    public OperationResponse<ScrmPointsAccountDto> getOrCreateAccount(@PathVariable Long customerId)
            throws ScrmException {
        return OperationResponse.build(pointsService.getOrCreateAccount(customerId));
    }

    /**
     * 分页查询积分账户, 支持按积分区间与客户名称关键字过滤
     *
     * @param minPoints 最小积分过滤 (可选)
     * @param maxPoints 最大积分过滤 (可选)
     * @param keyword   客户名称关键字过滤 (可选)
     * @param page      页码 (从 0 开始, 默认 0)
     * @param size      每页大小 (默认 20)
     * @return 账户分页结果
     */
    @RequirePermission(resource = "scrm_points_account", action = "read")
    @GetMapping("/accounts/list")
    public OperationResponse<Page<ScrmPointsAccountDto>> listAccounts(
            @RequestParam(required = false) Integer minPoints,
            @RequestParam(required = false) Integer maxPoints,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(pointsService.listAccounts(minPoints, maxPoints, keyword, pageable));
    }

    /**
     * 手动调整积分 (管理员加/减积分)
     *
     * @param dto 操作参数 (customerId + points + type + reason)
     * @return 更新后的账户
     */
    @RequirePermission(resource = "scrm_points_account", action = "adjust")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/accounts/adjust")
    public OperationResponse<ScrmPointsAccountDto> adjustPoints(@Valid @RequestBody ScrmPointsOperationDto dto)
            throws ScrmException {
        return OperationResponse.build(pointsService.adjustPoints(dto));
    }

    /**
     * 冻结积分
     *
     * @param customerId 客户 ID
     * @param points     冻结积分数量
     * @param reason     冻结原因 (可选)
     * @return 更新后的账户
     */
    @RequirePermission(resource = "scrm_points_account", action = "freeze")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/accounts/{customerId}/freeze")
    public OperationResponse<ScrmPointsAccountDto> freezePoints(@PathVariable Long customerId,
                                                                 @RequestParam Integer points,
                                                                 @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(pointsService.freezePoints(customerId, points, reason));
    }

    /**
     * 解冻积分
     *
     * @param customerId 客户 ID
     * @param points     解冻积分数量
     * @param reason     解冻原因 (可选)
     * @return 更新后的账户
     */
    @RequirePermission(resource = "scrm_points_account", action = "freeze")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/accounts/{customerId}/unfreeze")
    public OperationResponse<ScrmPointsAccountDto> unfreezePoints(@PathVariable Long customerId,
                                                                   @RequestParam Integer points,
                                                                   @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(pointsService.unfreezePoints(customerId, points, reason));
    }

    // ============================================================
    // 积分流水
    // ============================================================

    /**
     * 获取积分 (匹配规则→计算积分→更新账户→记录流水)
     *
     * @param customerId   客户 ID
     * @param triggerEvent 触发事件
     * @param basisValue   基准值 (如订单金额, PERCENTAGE 规则使用, 可选)
     * @return 更新后的账户
     */
    @RequirePermission(resource = "scrm_points_transaction", action = "earn")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/transactions/earn")
    public OperationResponse<ScrmPointsAccountDto> earnPoints(
            @RequestParam Long customerId,
            @RequestParam String triggerEvent,
            @RequestParam(required = false) Double basisValue)
            throws ScrmException {
        return OperationResponse.build(pointsService.earnPoints(customerId, triggerEvent, basisValue));
    }

    /**
     * 消耗积分
     *
     * @param customerId 客户 ID
     * @param points     消耗积分数量
     * @param reason     消耗原因 (可选)
     * @return 更新后的账户
     */
    @RequirePermission(resource = "scrm_points_transaction", action = "redeem")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/transactions/redeem")
    public OperationResponse<ScrmPointsAccountDto> redeemPoints(
            @RequestParam Long customerId,
            @RequestParam Integer points,
            @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(pointsService.redeemPoints(customerId, points, reason));
    }

    /**
     * 分页查询积分流水, 支持按客户 / 交易类型 / 时间区间过滤
     *
     * @param customerId      客户 ID 过滤 (可选)
     * @param transactionType 交易类型过滤 (可选)
     * @param startTime       起始时间 (可选, yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime         截止时间 (可选, yyyy-MM-dd'T'HH:mm:ss)
     * @param page            页码 (从 0 开始, 默认 0)
     * @param size            每页大小 (默认 20)
     * @return 流水分页结果
     */
    @RequirePermission(resource = "scrm_points_transaction", action = "read")
    @GetMapping("/transactions/list")
    public OperationResponse<Page<ScrmPointsTransactionDto>> getTransactions(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(pointsService.getTransactions(customerId, transactionType,
                startTime, endTime, pageable));
    }

    /**
     * 查询积分流水详情
     *
     * @param id 流水 ID
     * @return 流水详情
     */
    @RequirePermission(resource = "scrm_points_transaction", action = "read")
    @GetMapping("/transactions/{id}")
    public OperationResponse<ScrmPointsTransactionDto> getTransaction(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(pointsService.getTransaction(id));
    }

    // ============================================================
    // 积分兑换商品
    // ============================================================

    /**
     * 创建积分兑换商品
     *
     * @param dto 商品参数
     * @return 创建后的商品
     */
    @RequirePermission(resource = "scrm_points_exchange", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/exchanges")
    public OperationResponse<ScrmPointsExchangeDto> createExchangeItem(@Valid @RequestBody ScrmPointsExchangeDto dto)
            throws ScrmException {
        return OperationResponse.build(pointsService.createExchangeItem(dto));
    }

    /**
     * 更新兑换商品
     *
     * @param id  商品 ID
     * @param dto 商品参数
     * @return 更新后的商品
     */
    @RequirePermission(resource = "scrm_points_exchange", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/exchanges/{id}")
    public OperationResponse<ScrmPointsExchangeDto> updateExchangeItem(@PathVariable Long id,
                                                                       @RequestBody ScrmPointsExchangeDto dto)
            throws ScrmException {
        return OperationResponse.build(pointsService.updateExchangeItem(id, dto));
    }

    /**
     * 删除兑换商品
     *
     * @param id 商品 ID
     * @return 空响应
     * @throws ScrmException 商品不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_points_exchange", action = "delete")
    @DeleteMapping("/exchanges/{id}")
    public OperationResponse<Void> deleteExchangeItem(@PathVariable Long id) throws ScrmException {
        pointsService.deleteExchangeItem(id);
        return OperationResponse.build();
    }

    /**
     * 查询兑换商品详情
     *
     * @param id 商品 ID
     * @return 商品详情
     * @throws ScrmException 商品不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_points_exchange", action = "read")
    @GetMapping("/exchanges/{id}")
    public OperationResponse<ScrmPointsExchangeDto> getExchangeItem(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(pointsService.getExchangeItem(id));
    }

    /**
     * 分页查询兑换商品, 支持按分类 / 状态过滤
     *
     * @param category 分类过滤 (可选)
     * @param status   状态过滤 (可选)
     * @param page     页码 (从 0 开始, 默认 0)
     * @param size     每页大小 (默认 20)
     * @return 商品分页结果
     */
    @RequirePermission(resource = "scrm_points_exchange", action = "read")
    @GetMapping("/exchanges/list")
    public OperationResponse<Page<ScrmPointsExchangeDto>> listExchangeItems(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(pointsService.listExchangeItems(category, status, pageable));
    }

    /**
     * 上架兑换商品
     *
     * @param id 商品 ID
     * @return 更新后的商品
     */
    @RequirePermission(resource = "scrm_points_exchange", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/exchanges/{id}/activate")
    public OperationResponse<ScrmPointsExchangeDto> activateExchangeItem(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(pointsService.activateExchangeItem(id));
    }

    /**
     * 下架兑换商品
     *
     * @param id 商品 ID
     * @return 更新后的商品
     */
    @RequirePermission(resource = "scrm_points_exchange", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/exchanges/{id}/deactivate")
    public OperationResponse<ScrmPointsExchangeDto> deactivateExchangeItem(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(pointsService.deactivateExchangeItem(id));
    }

    // ============================================================
    // 兑换动作 (兑换 / 完成 / 取消 / 记录查询)
    // ============================================================

    /**
     * 积分兑换商品 (校验积分→扣减→生成兑换码→记录)
     *
     * @param customerId 客户 ID
     * @param exchangeId 兑换商品 ID
     * @param quantity   兑换数量 (默认 1)
     * @return 兑换记录
     */
    @RequirePermission(resource = "scrm_points_exchange", action = "exchange")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/exchanges/exchange")
    public OperationResponse<ScrmPointsExchangeRecordDto> exchange(
            @RequestParam Long customerId,
            @RequestParam Long exchangeId,
            @RequestParam(defaultValue = "1") Integer quantity)
            throws ScrmException {
        return OperationResponse.build(pointsService.exchange(customerId, exchangeId, quantity));
    }

    /**
     * 完成兑换 (填写物流单号并标记完成)
     *
     * @param id         兑换记录 ID
     * @param shippingNo 物流单号 (可选)
     * @return 更新后的兑换记录
     */
    @RequirePermission(resource = "scrm_points_exchange", action = "complete")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/exchanges/{id}/complete")
    public OperationResponse<ScrmPointsExchangeRecordDto> completeExchange(
            @PathVariable Long id,
            @RequestParam(required = false) String shippingNo)
            throws ScrmException {
        return OperationResponse.build(pointsService.completeExchange(id, shippingNo));
    }

    /**
     * 取消兑换 (返还积分并恢复库存)
     *
     * @param id     兑换记录 ID
     * @param reason 取消原因 (可选)
     * @return 更新后的兑换记录
     */
    @RequirePermission(resource = "scrm_points_exchange", action = "cancel")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/exchanges/{id}/cancel")
    public OperationResponse<ScrmPointsExchangeRecordDto> cancelExchange(
            @PathVariable Long id,
            @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(pointsService.cancelExchange(id, reason));
    }

    /**
     * 分页查询兑换记录, 支持按客户 / 商品 / 状态过滤
     *
     * @param customerId 客户 ID 过滤 (可选)
     * @param exchangeId 兑换商品 ID 过滤 (可选)
     * @param status     状态过滤 (可选)
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 兑换记录分页结果
     */
    @RequirePermission(resource = "scrm_points_exchange", action = "read")
    @GetMapping("/exchanges/records/list")
    public OperationResponse<Page<ScrmPointsExchangeRecordDto>> getExchangeRecords(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long exchangeId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(pointsService.getExchangeRecords(customerId, exchangeId, status, pageable));
    }

    // ============================================================
    // 过期处理
    // ============================================================

    /**
     * 过期处理 (定时任务, 清零过期积分)
     *
     * @return 处理结果 (total 待处理数 / processed 实际处理数 / expiredPoints 过期积分总数)
     */
    @RequirePermission(resource = "scrm_points_transaction", action = "expire")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/expiry/process")
    public OperationResponse<Map<String, Object>> processExpiredPoints() {
        return OperationResponse.build(pointsService.processExpiredPoints());
    }

    /**
     * 查询客户即将过期的积分
     *
     * @param customerId 客户 ID
     * @param days       天数 (查询未来 days 天内将过期的流水, 默认 30)
     * @return 即将过期的流水列表
     */
    @RequirePermission(resource = "scrm_points_transaction", action = "read")
    @GetMapping("/expiry/expiring/{customerId}")
    public OperationResponse<List<ScrmPointsTransactionDto>> getExpiringPoints(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "30") int days)
            throws ScrmException {
        return OperationResponse.build(pointsService.getExpiringPoints(customerId, days));
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 积分总览 (总发放 / 总消耗 / 总过期 / 活跃账户数)
     *
     * @return 积分总览统计
     */
    @RequirePermission(resource = "scrm_points_stats", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getPointsStats() {
        return OperationResponse.build(pointsService.getPointsStats());
    }

    /**
     * 积分排行榜 (按当前可用积分倒序)
     *
     * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 账户分页结果
     */
    @RequirePermission(resource = "scrm_points_stats", action = "read")
    @GetMapping("/stats/ranking")
    public OperationResponse<Page<ScrmPointsAccountDto>> getCustomerPointsRanking(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(pointsService.getCustomerPointsRanking(pageable));
    }

    /**
     * 兑换统计 (指定时间区间内的兑换记录数与消耗积分总额)
     *
     * @param startTime 起始时间 (可选, yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可选, yyyy-MM-dd'T'HH:mm:ss)
     * @return 兑换统计
     */
    @RequirePermission(resource = "scrm_points_stats", action = "read")
    @GetMapping("/stats/exchange")
    public OperationResponse<Map<String, Object>> getExchangeStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(pointsService.getExchangeStats(startTime, endTime));
    }
}
