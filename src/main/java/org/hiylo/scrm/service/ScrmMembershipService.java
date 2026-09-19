/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMembershipService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmBenefitRedeemDto;
import org.hiylo.scrm.dto.ScrmCustomerMembershipDto;
import org.hiylo.scrm.dto.ScrmMembershipBenefitDto;
import org.hiylo.scrm.dto.ScrmMembershipEnrollDto;
import org.hiylo.scrm.dto.ScrmMembershipTierDto;
import org.hiylo.scrm.dto.ScrmMembershipUpgradeDto;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户会员权益管理服务门面。
 * <p>
 * 统一暴露会员等级管理 {@link ScrmMembershipTierService}、会员档案管理
 * {@link ScrmMembershipMemberService}、权益管理 {@link ScrmMembershipBenefitService}
 * 与统计趋势 {@link ScrmMembershipStatsService} 四个子域的全部能力, 原各 public 方法
 * 均保留原签名并委托给对应子域服务实现。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmMembershipService {

    /** 会员等级管理服务 */
    private final ScrmMembershipTierService tierService;
    /** 会员档案管理服务 */
    private final ScrmMembershipMemberService memberService;
    /** 会员权益管理服务 */
    private final ScrmMembershipBenefitService benefitService;
    /** 会员统计趋势服务 */
    private final ScrmMembershipStatsService statsService;

    // ============================================================
    // 会员等级 Tier CRUD
    // ============================================================

    /** 创建会员等级 */
    public ScrmMembershipTierDto createTier(ScrmMembershipTierDto dto) throws ScrmException {
        return tierService.createTier(dto);
    }

    /** 更新会员等级（字段非空才覆盖） */
    public ScrmMembershipTierDto updateTier(Long id, ScrmMembershipTierDto dto) throws ScrmException {
        return tierService.updateTier(id, dto);
    }

    /** 删除会员等级 */
    public void deleteTier(Long id) throws ScrmException {
        tierService.deleteTier(id);
    }

    /** 查询会员等级详情 */
    public ScrmMembershipTierDto getTier(Long id) throws ScrmException {
        return tierService.getTier(id);
    }

    /** 按等级编码查询会员等级 */
    public ScrmMembershipTierDto getTierByCode(String code) throws ScrmException {
        return tierService.getTierByCode(code);
    }

    /** 分页查询会员等级, 支持按启用状态与关键词过滤 */
    public Page<ScrmMembershipTierDto> listTiers(Boolean enabled, String keyword, Pageable pageable) {
        return tierService.listTiers(enabled, keyword, pageable);
    }

    /** 启用会员等级 */
    public ScrmMembershipTierDto enableTier(Long id) throws ScrmException {
        return tierService.enableTier(id);
    }

    /** 停用会员等级 */
    public ScrmMembershipTierDto disableTier(Long id) throws ScrmException {
        return tierService.disableTier(id);
    }

    /** 根据消费额获取对应等级 */
    public ScrmMembershipTierDto getTierBySpend(Double totalSpend) throws ScrmException {
        return tierService.getTierBySpend(totalSpend);
    }

    /** 获取下一等级 */
    public ScrmMembershipTierDto getNextTier(Long tierId) throws ScrmException {
        return tierService.getNextTier(tierId);
    }

    /** 获取上一等级 */
    public ScrmMembershipTierDto getPreviousTier(Long tierId) throws ScrmException {
        return tierService.getPreviousTier(tierId);
    }

    /** 更新等级统计 (会员数/总消费/平均消费) */
    public ScrmMembershipTierDto updateTierStats(Long id) throws ScrmException {
        return tierService.updateTierStats(id);
    }

    /** 获取等级树 */
    public List<Map<String, Object>> getTierTree() {
        return tierService.getTierTree();
    }

    /** 计算会员升级进度 (0-1) */
    public Double calculateUpgradeProgress(Long membershipId) throws ScrmException {
        return tierService.calculateUpgradeProgress(membershipId);
    }

    // ============================================================
    // 会员档案 Membership 生命周期
    // ============================================================

    /** 注册会员 (生成卡号→设置初始等级→赠送注册积分) */
    public ScrmCustomerMembershipDto enroll(ScrmMembershipEnrollDto enrollDto) throws ScrmException {
        return memberService.enroll(enrollDto);
    }

    /** 查询会员详情 */
    public ScrmCustomerMembershipDto getMembership(Long id) throws ScrmException {
        return memberService.getMembership(id);
    }

    /** 按客户 ID 查询会员 */
    public ScrmCustomerMembershipDto getMembershipByCustomer(Long customerId) throws ScrmException {
        return memberService.getMembershipByCustomer(customerId);
    }

    /** 按会员卡号查询会员 */
    public ScrmCustomerMembershipDto getMembershipByCardNo(String cardNo) throws ScrmException {
        return memberService.getMembershipByCardNo(cardNo);
    }

    /** 分页查询会员, 支持按等级、状态与关键词过滤 */
    public Page<ScrmCustomerMembershipDto> listMemberships(Long tierId, String status, String keyword,
                                                           Pageable pageable) {
        return memberService.listMemberships(tierId, status, keyword, pageable);
    }

    /** 升级会员 */
    public ScrmCustomerMembershipDto upgrade(ScrmMembershipUpgradeDto upgradeDto) throws ScrmException {
        return memberService.upgrade(upgradeDto);
    }

    /** 降级会员 */
    public ScrmCustomerMembershipDto downgrade(Long membershipId, String reason) throws ScrmException {
        return memberService.downgrade(membershipId, reason);
    }

    /** 冻结会员 */
    public ScrmCustomerMembershipDto freeze(Long membershipId, String reason) throws ScrmException {
        return memberService.freeze(membershipId, reason);
    }

    /** 解冻会员 */
    public ScrmCustomerMembershipDto unfreeze(Long membershipId) throws ScrmException {
        return memberService.unfreeze(membershipId);
    }

    /** 取消会员 (置终态) */
    public ScrmCustomerMembershipDto cancel(Long membershipId, String reason) throws ScrmException {
        return memberService.cancel(membershipId, reason);
    }

    /** 续期会员 */
    public ScrmCustomerMembershipDto renewMembership(Long membershipId) throws ScrmException {
        return memberService.renewMembership(membershipId);
    }

    /** 更新会员消费 */
    public ScrmCustomerMembershipDto updateSpend(Long membershipId, Double amount, Long orderId)
            throws ScrmException {
        return memberService.updateSpend(membershipId, amount, orderId);
    }

    /** 检查会员是否可升级 */
    public Map<String, Object> checkUpgrade(Long membershipId) throws ScrmException {
        return memberService.checkUpgrade(membershipId);
    }

    /** 检查会员降级风险 */
    public Map<String, Object> checkDowngrade(Long membershipId) throws ScrmException {
        return memberService.checkDowngrade(membershipId);
    }

    /** 批量检查到期会员 */
    public Map<String, Object> checkExpiry() {
        return memberService.checkExpiry();
    }

    /** 查询会员消费进度 */
    public Map<String, Object> getSpendProgress(Long membershipId) throws ScrmException {
        return memberService.getSpendProgress(membershipId);
    }

    /** 按等级分页查询会员 */
    public Page<ScrmCustomerMembershipDto> getMembersByTier(Long tierId, Pageable pageable) throws ScrmException {
        return memberService.getMembersByTier(tierId, pageable);
    }

    /** 查询即将到期的会员 */
    public List<ScrmCustomerMembershipDto> getExpiringMemberships(int days) {
        return memberService.getExpiringMemberships(days);
    }

    /** 查询可升级会员 */
    public List<ScrmCustomerMembershipDto> getUpgradeCandidates(int limit) {
        return memberService.getUpgradeCandidates(limit);
    }

    /** 查询降级风险会员 */
    public List<ScrmCustomerMembershipDto> getDowngradeRiskMembers(int limit) {
        return memberService.getDowngradeRiskMembers(limit);
    }

    /** 生成会员卡号 */
    public String generateCardNo() {
        return memberService.generateCardNo();
    }

    // ============================================================
    // 权益 Benefit CRUD
    // ============================================================

    /** 创建会员权益 */
    public ScrmMembershipBenefitDto createBenefit(ScrmMembershipBenefitDto dto) throws ScrmException {
        return benefitService.createBenefit(dto);
    }

    /** 更新会员权益（字段非空才覆盖） */
    public ScrmMembershipBenefitDto updateBenefit(Long id, ScrmMembershipBenefitDto dto) throws ScrmException {
        return benefitService.updateBenefit(id, dto);
    }

    /** 删除会员权益 */
    public void deleteBenefit(Long id) throws ScrmException {
        benefitService.deleteBenefit(id);
    }

    /** 查询权益详情 */
    public ScrmMembershipBenefitDto getBenefit(Long id) throws ScrmException {
        return benefitService.getBenefit(id);
    }

    /** 按权益编码查询权益 */
    public ScrmMembershipBenefitDto getBenefitByCode(String code) throws ScrmException {
        return benefitService.getBenefitByCode(code);
    }

    /** 分页查询权益, 支持按等级、权益类型与状态过滤 */
    public Page<ScrmMembershipBenefitDto> listBenefits(Long tierId, String benefitType, String status,
                                                       Pageable pageable) {
        return benefitService.listBenefits(tierId, benefitType, status, pageable);
    }

    /** 启用权益 */
    public ScrmMembershipBenefitDto enableBenefit(Long id) throws ScrmException {
        return benefitService.enableBenefit(id);
    }

    /** 停用权益 */
    public ScrmMembershipBenefitDto disableBenefit(Long id) throws ScrmException {
        return benefitService.disableBenefit(id);
    }

    /** 按等级获取权益 (含通用权益) */
    public List<ScrmMembershipBenefitDto> getBenefitsByTier(Long tierId) throws ScrmException {
        return benefitService.getBenefitsByTier(tierId);
    }

    /** 兑换权益 */
    public Map<String, Object> redeem(ScrmBenefitRedeemDto redeemDto) throws ScrmException {
        return benefitService.redeem(redeemDto);
    }

    /** 检查权益兑换资格 */
    public Map<String, Object> checkRedeemEligibility(Long membershipId, Long benefitId) throws ScrmException {
        return benefitService.checkRedeemEligibility(membershipId, benefitId);
    }

    /** 查询权益使用记录 */
    public Map<String, Object> getBenefitUsage(Long membershipId, Long benefitId) throws ScrmException {
        return benefitService.getBenefitUsage(membershipId, benefitId);
    }

    /** 查询会员可用权益 */
    public List<ScrmMembershipBenefitDto> getMemberBenefits(Long membershipId) throws ScrmException {
        return benefitService.getMemberBenefits(membershipId);
    }

    /** 查询热门权益 */
    public List<ScrmMembershipBenefitDto> getPopularBenefits(int limit) {
        return benefitService.getPopularBenefits(limit);
    }

    /** 更新权益统计 */
    public ScrmMembershipBenefitDto updateBenefitStats(Long id) throws ScrmException {
        return benefitService.updateBenefitStats(id);
    }

    /** 分配权益到等级 */
    public ScrmMembershipBenefitDto assignBenefitToTier(Long benefitId, Long tierId) throws ScrmException {
        return benefitService.assignBenefitToTier(benefitId, tierId);
    }

    // ============================================================
    // 统计 Stats
    // ============================================================

    /** 会员统计: 总数 / 各等级数 / 活跃率 / 平均消费 */
    public Map<String, Object> getMembershipStats() {
        return statsService.getMembershipStats();
    }

    /** 等级分布: 各等级的会员数 / 总消费 / 平均消费 */
    public List<Map<String, Object>> getTierDistribution() {
        return statsService.getTierDistribution();
    }

    /** 升级统计: 时间范围内各等级的升级次数 */
    public Map<String, Object> getUpgradeStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getUpgradeStats(startTime, endTime);
    }

    /** 留存统计: 按 period 月数统计加入 N 月后仍活跃的会员数 */
    public Map<String, Object> getRetentionStats(int period) {
        return statsService.getRetentionStats(period);
    }

    /** 权益统计: 使用率 / 节省金额 / 热门权益 */
    public Map<String, Object> getBenefitStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getBenefitStats(startTime, endTime);
    }

    /** 会员收入统计: 累计消费 / 各等级收入 */
    public Map<String, Object> getRevenueStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getRevenueStats(startTime, endTime);
    }

    /** 会员增长趋势: 按月统计最近 N 月的新增会员数 */
    public List<Map<String, Object>> getMembershipTrend(int months) {
        return statsService.getMembershipTrend(months);
    }

    /** 同期群分析: 按加入月份分组统计各 cohort 的会员数与活跃数 */
    public List<Map<String, Object>> getCohortAnalysis(int period) {
        return statsService.getCohortAnalysis(period);
    }
}