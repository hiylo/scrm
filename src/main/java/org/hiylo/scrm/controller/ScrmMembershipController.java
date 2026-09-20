/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMembershipController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmBenefitRedeemDto;
import org.hiylo.scrm.dto.ScrmCustomerMembershipDto;
import org.hiylo.scrm.dto.ScrmMembershipBenefitDto;
import org.hiylo.scrm.dto.ScrmMembershipEnrollDto;
import org.hiylo.scrm.dto.ScrmMembershipTierDto;
import org.hiylo.scrm.dto.ScrmMembershipUpgradeDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmMembershipService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
 * SCRM 客户会员权益管理控制器。
 * <p>
 * 提供会员等级管理 (增删改查/启停/等级树/按消费额匹配/上下相邻等级/升级进度计算/统计刷新)、
 * 会员档案管理 (注册/查询/升级/降级/冻结/解冻/取消/续期/消费更新/升降级检查/到期扫描/进度查询/
 * 按等级查询会员/即将到期/可升级/降级风险会员)、权益管理 (增删改查/启停/按等级获取/兑换/资格检查/
 * 使用记录/会员可用权益/热门权益/统计刷新/分配到等级) 以及会员统计 (总览/等级分布/升级统计/留存统计/
 * 权益统计/收入统计/增长趋势/同期群分析) 接口。权限由 gateway-server 统一鉴权,
 * {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/memberships")
@RequiredArgsConstructor
public class ScrmMembershipController {

    /** 会员权益服务 */
    private final ScrmMembershipService scrmMembershipService;

    // ============================================================
    // 会员等级 Tier
    // ============================================================

    /**
     * 创建会员等级。
     *
     * @param dto 等级参数
     * @return 创建后的等级
     * @throws ScrmException 参数非法 / 等级编码重复 / 等级序号重复
     */
    @RequirePermission(resource = "scrm_membership", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60, message = "创建会员等级过于频繁，请稍后重试")
    @PostMapping("/tiers")
    public OperationResponse<ScrmMembershipTierDto> createTier(@Valid @RequestBody ScrmMembershipTierDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmMembershipService.createTier(dto));
    }

    /**
     * 更新会员等级 (字段非空才覆盖)。
     *
     * @param id  等级 ID
     * @param dto 等级参数
     * @return 更新后的等级
     * @throws ScrmException 等级不存在 / 参数非法 / 等级编码重复
     */
    @RequirePermission(resource = "scrm_membership", action = "update")
    @PutMapping("/tiers/{id}")
    public OperationResponse<ScrmMembershipTierDto> updateTier(@PathVariable Long id,
                                                                @RequestBody ScrmMembershipTierDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmMembershipService.updateTier(id, dto));
    }

    /**
     * 删除会员等级。
     *
     * @param id 等级 ID
     * @return 空响应
     * @throws ScrmException 等级不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "delete")
    @DeleteMapping("/tiers/{id}")
    public OperationResponse<Void> deleteTier(@PathVariable Long id) throws ScrmException {
        scrmMembershipService.deleteTier(id);
        return OperationResponse.build();
    }

    /**
     * 查询会员等级详情。
     *
     * @param id 等级 ID
     * @return 等级详情
     * @throws ScrmException 等级不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/tiers/{id}")
    public OperationResponse<ScrmMembershipTierDto> getTier(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmMembershipService.getTier(id));
    }

    /**
     * 按等级编码查询会员等级。
     *
     * @param code 等级编码
     * @return 等级详情
     * @throws ScrmException 等级不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/tiers/code/{code}")
    public OperationResponse<ScrmMembershipTierDto> getTierByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmMembershipService.getTierByCode(code));
    }

    /**
     * 分页查询会员等级, 支持按启用状态与关键词过滤。
     *
     * @param enabled 启用状态过滤 (可空)
     * @param keyword 关键词过滤 (可空)
     * @param page    页码 (从 0 开始, 默认 0)
     * @param size    每页大小 (默认 20)
     * @return 等级分页结果 (按等级序号升序)
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/tiers/list")
    public OperationResponse<Page<ScrmMembershipTierDto>> listTiers(
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmMembershipService.listTiers(enabled, keyword, pageable));
    }

    /**
     * 启用会员等级。
     *
     * @param id 等级 ID
     * @return 更新后的等级
     * @throws ScrmException 等级不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "execute")
    @PostMapping("/tiers/{id}/enable")
    public OperationResponse<ScrmMembershipTierDto> enableTier(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmMembershipService.enableTier(id));
    }

    /**
     * 停用会员等级。
     *
     * @param id 等级 ID
     * @return 更新后的等级
     * @throws ScrmException 等级不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "execute")
    @PostMapping("/tiers/{id}/disable")
    public OperationResponse<ScrmMembershipTierDto> disableTier(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmMembershipService.disableTier(id));
    }

    /**
     * 根据消费额获取对应等级。
     *
     * @param totalSpend 累计消费额
     * @return 等级详情
     * @throws ScrmException 无可用等级
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @PostMapping("/tiers/by-spend")
    public OperationResponse<ScrmMembershipTierDto> getTierBySpend(@RequestParam Double totalSpend)
            throws ScrmException {
        return OperationResponse.build(scrmMembershipService.getTierBySpend(totalSpend));
    }

    /**
     * 获取下一等级。
     *
     * @param id 当前等级 ID
     * @return 下一等级详情 (已是最高等级时返回 null)
     * @throws ScrmException 等级不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/tiers/{id}/next")
    public OperationResponse<ScrmMembershipTierDto> getNextTier(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmMembershipService.getNextTier(id));
    }

    /**
     * 获取上一等级。
     *
     * @param id 当前等级 ID
     * @return 上一等级详情 (已是最低等级时返回 null)
     * @throws ScrmException 等级不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/tiers/{id}/previous")
    public OperationResponse<ScrmMembershipTierDto> getPreviousTier(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmMembershipService.getPreviousTier(id));
    }

    /**
     * 更新等级统计 (会员数/总消费/平均消费)。
     *
     * @param id 等级 ID
     * @return 更新后的等级
     * @throws ScrmException 等级不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "execute")
    @PostMapping("/tiers/{id}/stats")
    public OperationResponse<ScrmMembershipTierDto> updateTierStats(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmMembershipService.updateTierStats(id));
    }

    /**
     * 获取等级树 (含上下相邻等级信息)。
     *
     * @return 等级树列表
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/tiers/tree")
    public OperationResponse<List<Map<String, Object>>> getTierTree() {
        return OperationResponse.build(scrmMembershipService.getTierTree());
    }

    /**
     * 计算会员升级进度 (0-1)。
     *
     * @param membershipId 会员记录 ID
     * @return 升级进度
     * @throws ScrmException 会员不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @PostMapping("/tiers/upgrade-progress/{membershipId}")
    public OperationResponse<Double> calculateUpgradeProgress(@PathVariable Long membershipId)
            throws ScrmException {
        return OperationResponse.build(scrmMembershipService.calculateUpgradeProgress(membershipId));
    }

    // ============================================================
    // 会员档案 Membership
    // ============================================================

    /**
     * 注册会员 (生成卡号→设置初始等级→赠送注册积分)。
     *
     * @param enrollDto 注册参数
     * @return 创建后的会员
     * @throws ScrmException 参数非法 / 客户已注册 / 等级不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60, message = "注册会员过于频繁，请稍后重试")
    @PostMapping("/enroll")
    public OperationResponse<ScrmCustomerMembershipDto> enroll(@Valid @RequestBody ScrmMembershipEnrollDto enrollDto)
            throws ScrmException {
        return OperationResponse.build(scrmMembershipService.enroll(enrollDto));
    }

    /**
     * 查询会员详情。
     *
     * @param id 会员记录 ID
     * @return 会员详情
     * @throws ScrmException 会员不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmCustomerMembershipDto> getMembership(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmMembershipService.getMembership(id));
    }

    /**
     * 按客户 ID 查询会员。
     *
     * @param customerId 客户 ID
     * @return 会员详情
     * @throws ScrmException 会员不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/by-customer/{customerId}")
    public OperationResponse<ScrmCustomerMembershipDto> getMembershipByCustomer(@PathVariable Long customerId)
            throws ScrmException {
        return OperationResponse.build(scrmMembershipService.getMembershipByCustomer(customerId));
    }

    /**
     * 按会员卡号查询会员。
     *
     * @param cardNo 会员卡号
     * @return 会员详情
     * @throws ScrmException 会员不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/by-card/{cardNo}")
    public OperationResponse<ScrmCustomerMembershipDto> getMembershipByCardNo(@PathVariable String cardNo)
            throws ScrmException {
        return OperationResponse.build(scrmMembershipService.getMembershipByCardNo(cardNo));
    }

    /**
     * 分页查询会员, 支持按等级、状态与关键词过滤。
     *
     * @param tierId  等级过滤 (可空)
     * @param status  状态过滤 (可空)
     * @param keyword 关键词过滤 (可空)
     * @param page    页码 (从 0 开始, 默认 0)
     * @param size    每页大小 (默认 20)
     * @return 会员分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmCustomerMembershipDto>> listMemberships(
            @RequestParam(required = false) Long tierId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmMembershipService.listMemberships(tierId, status, keyword, pageable));
    }

    /**
     * 升级会员 (校验→更新等级→赠送升级积分→记录历史)。
     *
     * @param upgradeDto 升级参数
     * @return 更新后的会员
     * @throws ScrmException 会员不存在 / 状态非法 / 目标等级不高于当前等级
     */
    @RequirePermission(resource = "scrm_membership", action = "execute")
    @PostMapping("/upgrade")
    public OperationResponse<ScrmCustomerMembershipDto> upgrade(@Valid @RequestBody ScrmMembershipUpgradeDto upgradeDto)
            throws ScrmException {
        return OperationResponse.build(scrmMembershipService.upgrade(upgradeDto));
    }

    /**
     * 降级会员。
     *
     * @param membershipId 会员记录 ID
     * @param reason       降级原因 (可空)
     * @return 更新后的会员
     * @throws ScrmException 会员不存在 / 状态非法 / 已是最低等级
     */
    @RequirePermission(resource = "scrm_membership", action = "execute")
    @PostMapping("/downgrade")
    public OperationResponse<ScrmCustomerMembershipDto> downgrade(
            @RequestParam Long membershipId,
            @RequestParam(required = false) String reason) throws ScrmException {
        return OperationResponse.build(scrmMembershipService.downgrade(membershipId, reason));
    }

    /**
     * 冻结会员。
     *
     * @param membershipId 会员记录 ID
     * @param reason       冻结原因 (可空)
     * @return 更新后的会员
     * @throws ScrmException 会员不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_membership", action = "execute")
    @PostMapping("/freeze")
    public OperationResponse<ScrmCustomerMembershipDto> freeze(
            @RequestParam Long membershipId,
            @RequestParam(required = false) String reason) throws ScrmException {
        return OperationResponse.build(scrmMembershipService.freeze(membershipId, reason));
    }

    /**
     * 解冻会员。
     *
     * @param membershipId 会员记录 ID
     * @return 更新后的会员
     * @throws ScrmException 会员不存在 / 状态非冻结
     */
    @RequirePermission(resource = "scrm_membership", action = "execute")
    @PostMapping("/unfreeze")
    public OperationResponse<ScrmCustomerMembershipDto> unfreeze(@RequestParam Long membershipId)
            throws ScrmException {
        return OperationResponse.build(scrmMembershipService.unfreeze(membershipId));
    }

    /**
     * 取消会员。
     *
     * @param membershipId 会员记录 ID
     * @param reason       取消原因 (可空)
     * @return 更新后的会员
     * @throws ScrmException 会员不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_membership", action = "execute")
    @PostMapping("/cancel")
    public OperationResponse<ScrmCustomerMembershipDto> cancel(
            @RequestParam Long membershipId,
            @RequestParam(required = false) String reason) throws ScrmException {
        return OperationResponse.build(scrmMembershipService.cancel(membershipId, reason));
    }

    /**
     * 续期会员。
     *
     * @param membershipId 会员记录 ID
     * @return 更新后的会员
     * @throws ScrmException 会员不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_membership", action = "execute")
    @PostMapping("/renew")
    public OperationResponse<ScrmCustomerMembershipDto> renewMembership(@RequestParam Long membershipId)
            throws ScrmException {
        return OperationResponse.build(scrmMembershipService.renewMembership(membershipId));
    }

    /**
     * 更新会员消费 (累计消费/订单/积分)。
     *
     * @param membershipId 会员记录 ID
     * @param amount       消费金额
     * @param orderId      订单 ID (可空)
     * @return 更新后的会员
     * @throws ScrmException 会员不存在 / 状态非法 / 金额非法
     */
    @RequirePermission(resource = "scrm_membership", action = "execute")
    @PostMapping("/spend")
    public OperationResponse<ScrmCustomerMembershipDto> updateSpend(
            @RequestParam Long membershipId,
            @RequestParam Double amount,
            @RequestParam(required = false) Long orderId) throws ScrmException {
        return OperationResponse.build(scrmMembershipService.updateSpend(membershipId, amount, orderId));
    }

    /**
     * 检查会员是否可升级。
     *
     * @param membershipId 会员记录 ID
     * @return 检查结果
     * @throws ScrmException 会员不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @PostMapping("/check-upgrade")
    public OperationResponse<Map<String, Object>> checkUpgrade(@RequestParam Long membershipId)
            throws ScrmException {
        return OperationResponse.build(scrmMembershipService.checkUpgrade(membershipId));
    }

    /**
     * 检查会员降级风险。
     *
     * @param membershipId 会员记录 ID
     * @return 检查结果
     * @throws ScrmException 会员不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @PostMapping("/check-downgrade")
    public OperationResponse<Map<String, Object>> checkDowngrade(@RequestParam Long membershipId)
            throws ScrmException {
        return OperationResponse.build(scrmMembershipService.checkDowngrade(membershipId));
    }

    /**
     * 批量检查到期会员 (扫描已过期会员置终态)。
     *
     * @return 处理结果
     */
    @RequirePermission(resource = "scrm_membership", action = "execute")
    @PostMapping("/check-expiry")
    public OperationResponse<Map<String, Object>> checkExpiry() {
        return OperationResponse.build(scrmMembershipService.checkExpiry());
    }

    /**
     * 查询会员消费进度。
     *
     * @param membershipId 会员记录 ID
     * @return 消费进度
     * @throws ScrmException 会员不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/progress/{membershipId}")
    public OperationResponse<Map<String, Object>> getSpendProgress(@PathVariable Long membershipId)
            throws ScrmException {
        return OperationResponse.build(scrmMembershipService.getSpendProgress(membershipId));
    }

    /**
     * 按等级分页查询会员。
     *
     * @param tierId 等级 ID
     * @param page   页码 (从 0 开始, 默认 0)
     * @param size   每页大小 (默认 20)
     * @return 会员分页结果 (按创建时间倒序)
     * @throws ScrmException 等级不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/by-tier/{tierId}")
    public OperationResponse<Page<ScrmCustomerMembershipDto>> getMembersByTier(
            @PathVariable Long tierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws ScrmException {
        PageRequest pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmMembershipService.getMembersByTier(tierId, pageable));
    }

    /**
     * 查询即将到期的会员。
     *
     * @param days 天数 (默认 30)
     * @return 会员列表 (按到期日升序)
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/expiring")
    public OperationResponse<List<ScrmCustomerMembershipDto>> getExpiringMemberships(
            @RequestParam(defaultValue = "30") int days) {
        return OperationResponse.build(scrmMembershipService.getExpiringMemberships(days));
    }

    /**
     * 查询可升级会员。
     *
     * @param limit 返回条数 (默认 10)
     * @return 会员列表
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/upgrade-candidates")
    public OperationResponse<List<ScrmCustomerMembershipDto>> getUpgradeCandidates(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmMembershipService.getUpgradeCandidates(limit));
    }

    /**
     * 查询降级风险会员。
     *
     * @param limit 返回条数 (默认 10)
     * @return 会员列表
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/downgrade-risk")
    public OperationResponse<List<ScrmCustomerMembershipDto>> getDowngradeRiskMembers(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmMembershipService.getDowngradeRiskMembers(limit));
    }

    // ============================================================
    // 权益 Benefit
    // ============================================================

    /**
     * 创建会员权益。
     *
     * @param dto 权益参数
     * @return 创建后的权益
     * @throws ScrmException 参数非法 / 权益编码重复
     */
    @RequirePermission(resource = "scrm_membership", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60, message = "创建会员权益过于频繁，请稍后重试")
    @PostMapping("/benefits")
    public OperationResponse<ScrmMembershipBenefitDto> createBenefit(@Valid @RequestBody ScrmMembershipBenefitDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmMembershipService.createBenefit(dto));
    }

    /**
     * 更新会员权益 (字段非空才覆盖)。
     *
     * @param id  权益 ID
     * @param dto 权益参数
     * @return 更新后的权益
     * @throws ScrmException 权益不存在 / 参数非法 / 权益编码重复
     */
    @RequirePermission(resource = "scrm_membership", action = "update")
    @PutMapping("/benefits/{id}")
    public OperationResponse<ScrmMembershipBenefitDto> updateBenefit(@PathVariable Long id,
                                                                     @RequestBody ScrmMembershipBenefitDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmMembershipService.updateBenefit(id, dto));
    }

    /**
     * 删除会员权益。
     *
     * @param id 权益 ID
     * @return 空响应
     * @throws ScrmException 权益不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "delete")
    @DeleteMapping("/benefits/{id}")
    public OperationResponse<Void> deleteBenefit(@PathVariable Long id) throws ScrmException {
        scrmMembershipService.deleteBenefit(id);
        return OperationResponse.build();
    }

    /**
     * 查询权益详情。
     *
     * @param id 权益 ID
     * @return 权益详情
     * @throws ScrmException 权益不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/benefits/{id}")
    public OperationResponse<ScrmMembershipBenefitDto> getBenefit(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmMembershipService.getBenefit(id));
    }

    /**
     * 按权益编码查询权益。
     *
     * @param code 权益编码
     * @return 权益详情
     * @throws ScrmException 权益不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/benefits/code/{code}")
    public OperationResponse<ScrmMembershipBenefitDto> getBenefitByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmMembershipService.getBenefitByCode(code));
    }

    /**
     * 分页查询权益, 支持按等级、权益类型与状态过滤。
     *
     * @param tierId      等级过滤 (可空)
     * @param benefitType 权益类型过滤 (可空)
     * @param status      状态过滤 (可空)
     * @param page        页码 (从 0 开始, 默认 0)
     * @param size        每页大小 (默认 20)
     * @return 权益分页结果 (按展示顺序升序)
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/benefits/list")
    public OperationResponse<Page<ScrmMembershipBenefitDto>> listBenefits(
            @RequestParam(required = false) Long tierId,
            @RequestParam(required = false) String benefitType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmMembershipService.listBenefits(tierId, benefitType, status, pageable));
    }

    /**
     * 启用权益。
     *
     * @param id 权益 ID
     * @return 更新后的权益
     * @throws ScrmException 权益不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "execute")
    @PostMapping("/benefits/{id}/enable")
    public OperationResponse<ScrmMembershipBenefitDto> enableBenefit(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmMembershipService.enableBenefit(id));
    }

    /**
     * 停用权益。
     *
     * @param id 权益 ID
     * @return 更新后的权益
     * @throws ScrmException 权益不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "execute")
    @PostMapping("/benefits/{id}/disable")
    public OperationResponse<ScrmMembershipBenefitDto> disableBenefit(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmMembershipService.disableBenefit(id));
    }

    /**
     * 按等级获取权益 (含通用权益)。
     *
     * @param tierId 等级 ID
     * @return 权益列表
     * @throws ScrmException 等级不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/benefits/by-tier/{tierId}")
    public OperationResponse<List<ScrmMembershipBenefitDto>> getBenefitsByTier(@PathVariable Long tierId)
            throws ScrmException {
        return OperationResponse.build(scrmMembershipService.getBenefitsByTier(tierId));
    }

    /**
     * 兑换权益 (校验→记录→更新统计→返回优惠)。
     *
     * @param redeemDto 兑换参数
     * @return 兑换结果
     * @throws ScrmException 会员不存在 / 权益不存在 / 状态非法 / 超出使用限制
     */
    @RequirePermission(resource = "scrm_membership", action = "execute")
    @PostMapping("/benefits/redeem")
    public OperationResponse<Map<String, Object>> redeem(@Valid @RequestBody ScrmBenefitRedeemDto redeemDto)
            throws ScrmException {
        return OperationResponse.build(scrmMembershipService.redeem(redeemDto));
    }

    /**
     * 检查权益兑换资格。
     *
     * @param membershipId 会员记录 ID
     * @param benefitId    权益 ID
     * @return 检查结果
     * @throws ScrmException 会员不存在 / 权益不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @PostMapping("/benefits/check-eligibility")
    public OperationResponse<Map<String, Object>> checkRedeemEligibility(
            @RequestParam Long membershipId,
            @RequestParam Long benefitId) throws ScrmException {
        return OperationResponse.build(scrmMembershipService.checkRedeemEligibility(membershipId, benefitId));
    }

    /**
     * 查询权益使用记录。
     *
     * @param membershipId 会员记录 ID
     * @param benefitId    权益 ID
     * @return 权益使用统计
     * @throws ScrmException 会员不存在 / 权益不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/benefits/usage")
    public OperationResponse<Map<String, Object>> getBenefitUsage(
            @RequestParam Long membershipId,
            @RequestParam Long benefitId) throws ScrmException {
        return OperationResponse.build(scrmMembershipService.getBenefitUsage(membershipId, benefitId));
    }

    /**
     * 查询会员可用权益。
     *
     * @param membershipId 会员记录 ID
     * @return 权益列表
     * @throws ScrmException 会员不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/benefits/by-member/{membershipId}")
    public OperationResponse<List<ScrmMembershipBenefitDto>> getMemberBenefits(@PathVariable Long membershipId)
            throws ScrmException {
        return OperationResponse.build(scrmMembershipService.getMemberBenefits(membershipId));
    }

    /**
     * 查询热门权益。
     *
     * @param limit 返回条数 (默认 10)
     * @return 权益列表
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/benefits/popular")
    public OperationResponse<List<ScrmMembershipBenefitDto>> getPopularBenefits(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmMembershipService.getPopularBenefits(limit));
    }

    /**
     * 更新权益统计。
     *
     * @param id 权益 ID
     * @return 更新后的权益
     * @throws ScrmException 权益不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "execute")
    @PostMapping("/benefits/{id}/stats")
    public OperationResponse<ScrmMembershipBenefitDto> updateBenefitStats(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmMembershipService.updateBenefitStats(id));
    }

    /**
     * 分配权益到等级。
     *
     * @param benefitId 权益 ID
     * @param tierId    等级 ID
     * @return 更新后的权益
     * @throws ScrmException 权益不存在 / 等级不存在
     */
    @RequirePermission(resource = "scrm_membership", action = "execute")
    @PostMapping("/benefits/assign")
    public OperationResponse<ScrmMembershipBenefitDto> assignBenefitToTier(
            @RequestParam Long benefitId,
            @RequestParam Long tierId) throws ScrmException {
        return OperationResponse.build(scrmMembershipService.assignBenefitToTier(benefitId, tierId));
    }

    // ============================================================
    // 统计 Stats
    // ============================================================

    /**
     * 会员统计: 总数 / 各等级数 / 活跃率 / 平均消费。
     *
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getMembershipStats() {
        return OperationResponse.build(scrmMembershipService.getMembershipStats());
    }

    /**
     * 等级分布: 各等级的会员数 / 总消费 / 平均消费。
     *
     * @return 等级分布列表
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/stats/tier-distribution")
    public OperationResponse<List<Map<String, Object>>> getTierDistribution() {
        return OperationResponse.build(scrmMembershipService.getTierDistribution());
    }

    /**
     * 升级统计: 时间范围内各等级的升级次数。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 升级统计
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/stats/upgrades")
    public OperationResponse<Map<String, Object>> getUpgradeStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmMembershipService.getUpgradeStats(startTime, endTime));
    }

    /**
     * 留存统计: 按 period 月数统计加入 N 月后仍活跃的会员数。
     *
     * @param period 月数 (默认 3)
     * @return 留存统计
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/stats/retention")
    public OperationResponse<Map<String, Object>> getRetentionStats(
            @RequestParam(defaultValue = "3") int period) {
        return OperationResponse.build(scrmMembershipService.getRetentionStats(period));
    }

    /**
     * 权益统计: 使用率 / 节省金额 / 热门权益。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/stats/benefits")
    public OperationResponse<Map<String, Object>> getBenefitStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmMembershipService.getBenefitStats(startTime, endTime));
    }

    /**
     * 会员收入统计: 累计消费 / 各等级收入。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 收入统计
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/stats/revenue")
    public OperationResponse<Map<String, Object>> getRevenueStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmMembershipService.getRevenueStats(startTime, endTime));
    }

    /**
     * 会员增长趋势: 按月统计最近 N 月的新增会员数。
     *
     * @param months 月数 (默认 6)
     * @return 趋势数据 (month + count)
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/stats/trend")
    public OperationResponse<List<Map<String, Object>>> getMembershipTrend(
            @RequestParam(defaultValue = "6") int months) {
        return OperationResponse.build(scrmMembershipService.getMembershipTrend(months));
    }

    /**
     * 同期群分析: 按加入月份分组统计各 cohort 的会员数与活跃数。
     *
     * @param period 月数 (默认 6, 统计最近 N 月各月加入的 cohort)
     * @return 同期群分析
     */
    @RequirePermission(resource = "scrm_membership", action = "read")
    @GetMapping("/stats/cohort")
    public OperationResponse<List<Map<String, Object>>> getCohortAnalysis(
            @RequestParam(defaultValue = "6") int period) {
        return OperationResponse.build(scrmMembershipService.getCohortAnalysis(period));
    }
}
