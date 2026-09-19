/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMembershipTierService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmMembershipTierDto;
import org.hiylo.scrm.entity.ScrmCustomerMembershipEntity;
import org.hiylo.scrm.entity.ScrmMembershipTierEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerMembershipRepository;
import org.hiylo.scrm.repository.ScrmMembershipTierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SCRM 客户会员等级管理服务。
 * <p>
 * 承载会员等级体系的全部能力: 等级增删改查、按编码查询、分页过滤、启停、按消费额匹配等级、上下
 * 相邻等级、等级树、统计刷新等。等级相关能力由 {@link ScrmMembershipService} 门面统一暴露,
 * 本类仅聚焦等级管理子域。
 * </p>
 * <p>
 * 校验失败抛出 {@link ScrmException} 携带通用错误码 (NOT_FOUND / BAD_REQUEST / CONFLICT)。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmMembershipTierService {

    // ==================== 升级规则类型 ====================
    /** 升级规则: 消费 */
    private static final String RULE_SPEND = "SPEND";
    /** 升级规则: 积分 */
    private static final String RULE_POINTS = "POINTS";
    /** 升级规则: 订单数 */
    private static final String RULE_ORDER_COUNT = "ORDER_COUNT";
    /** 升级规则: 手动 */
    private static final String RULE_MANUAL = "MANUAL";

    /** 默认有效期月 */
    private static final int DEFAULT_VALIDITY_MONTHS = 12;
    /** 默认最大兑换比例 */
    private static final double DEFAULT_MAX_REDEEM_RATE = 0.5;
    /** 默认积分倍数 */
    private static final double DEFAULT_POINT_MULTIPLIER = 1.0;
    /** 默认折扣率 */
    private static final double DEFAULT_DISCOUNT_RATE = 1.0;
    /** 默认免费退货天数 */
    private static final int DEFAULT_FREE_RETURN_DAYS = 7;

    /** 合法升级规则集合 */
    private static final Set<String> VALID_RULE_TYPES = new HashSet<>(Arrays.asList(
            RULE_SPEND, RULE_POINTS, RULE_ORDER_COUNT, RULE_MANUAL));

    /** 会员等级数据访问层 */
    private final ScrmMembershipTierRepository tierRepository;
    /** 客户会员数据访问层 */
    private final ScrmCustomerMembershipRepository membershipRepository;

    /**
     * 创建会员等级。
     * <p>校验等级编码唯一与等级序号合法后写入账号 ID 持久化, 升级阈值缺省 0, 升级规则缺省 SPEND,
     * 有效期月缺省 12, 积分倍数缺省 1.0, 折扣率缺省 1.0, 免费退货天数缺省 7, 最大兑换比例缺省 0.5,
     * 启用与可见缺省 TRUE, 统计字段缺省 0。</p>
     *
     * @param dto 等级参数
     * @return 创建后的等级
     * @throws ScrmException 参数非法 / 等级编码重复 / 等级序号重复
     */
    @Transactional
    public ScrmMembershipTierDto createTier(ScrmMembershipTierDto dto) throws ScrmException {
        validateTierDto(dto, false);
        if (tierRepository.findByTierCode(dto.getTierCode()).isPresent()) {
            throw ScrmException.conflict("等级编码已存在: " + dto.getTierCode());
        }
        if (tierRepository.findByTierLevel(dto.getTierLevel()).isPresent()) {
            throw ScrmException.conflict("等级序号已存在: " + dto.getTierLevel());
        }
        ScrmMembershipTierEntity entity = new ScrmMembershipTierEntity();
        entity.setTierName(dto.getTierName());
        entity.setTierCode(dto.getTierCode());
        entity.setTierLevel(dto.getTierLevel());
        entity.setDescription(dto.getDescription());
        entity.setTierColor(dto.getTierColor());
        entity.setTierIcon(dto.getTierIcon());
        entity.setUpgradeThreshold(dto.getUpgradeThreshold() != null ? dto.getUpgradeThreshold() : 0d);
        entity.setDowngradeThreshold(dto.getDowngradeThreshold() != null ? dto.getDowngradeThreshold() : 0d);
        entity.setValidityPeriodMonths(
                dto.getValidityPeriodMonths() != null ? dto.getValidityPeriodMonths() : DEFAULT_VALIDITY_MONTHS);
        entity.setUpgradeRuleType(dto.getUpgradeRuleType() != null ? dto.getUpgradeRuleType() : RULE_SPEND);
        entity.setBenefitsSummary(dto.getBenefitsSummary());
        entity.setPointMultiplier(
                dto.getPointMultiplier() != null ? dto.getPointMultiplier() : DEFAULT_POINT_MULTIPLIER);
        entity.setDiscountRate(dto.getDiscountRate() != null ? dto.getDiscountRate() : DEFAULT_DISCOUNT_RATE);
        entity.setFreeShipping(dto.getFreeShipping() != null ? dto.getFreeShipping() : Boolean.FALSE);
        entity.setPrioritySupport(dto.getPrioritySupport() != null ? dto.getPrioritySupport() : Boolean.FALSE);
        entity.setExclusiveProducts(dto.getExclusiveProducts() != null ? dto.getExclusiveProducts() : Boolean.FALSE);
        entity.setBirthdayBonus(dto.getBirthdayBonus() != null ? dto.getBirthdayBonus() : 0d);
        entity.setSignupBonusPoints(dto.getSignupBonusPoints() != null ? dto.getSignupBonusPoints() : 0);
        entity.setMonthlyBonusPoints(dto.getMonthlyBonusPoints() != null ? dto.getMonthlyBonusPoints() : 0);
        entity.setAnnualBonusPoints(dto.getAnnualBonusPoints() != null ? dto.getAnnualBonusPoints() : 0);
        entity.setTierUpBonusPoints(dto.getTierUpBonusPoints() != null ? dto.getTierUpBonusPoints() : 0);
        entity.setMaxRedeemRate(dto.getMaxRedeemRate() != null ? dto.getMaxRedeemRate() : DEFAULT_MAX_REDEEM_RATE);
        entity.setFreeReturnDays(dto.getFreeReturnDays() != null ? dto.getFreeReturnDays() : DEFAULT_FREE_RETURN_DAYS);
        entity.setCustomBenefits(dto.getCustomBenefits());
        entity.setApplicableProducts(dto.getApplicableProducts());
        entity.setApplicableChannels(dto.getApplicableChannels());
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setIsVisible(dto.getIsVisible() != null ? dto.getIsVisible() : Boolean.TRUE);
        entity.setMemberCount(0);
        entity.setTotalSpend(0d);
        entity.setAvgSpend(0d);
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = tierRepository.save(entity);
        log.info("创建会员等级: id={}, code={}", entity.getId(), entity.getTierCode());
        return toTierDto(entity);
    }

    /**
     * 更新会员等级（字段非空才覆盖）。
     * <p>统计字段通过专用接口维护, 此处不直接修改。等级编码变更时校验唯一性。</p>
     *
     * @param id  等级 ID
     * @param dto 等级参数
     * @return 更新后的等级
     * @throws ScrmException 等级不存在 / 参数非法 / 等级编码重复
     */
    @Transactional
    public ScrmMembershipTierDto updateTier(Long id, ScrmMembershipTierDto dto) throws ScrmException {
        ScrmMembershipTierEntity entity = findTierOrThrow(id);
        validateTierDto(dto, true);
        if (dto.getTierCode() != null && !dto.getTierCode().equals(entity.getTierCode())) {
            tierRepository.findByTierCode(dto.getTierCode()).ifPresent(t -> {
                throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                        "等级编码已存在: " + dto.getTierCode());
            });
            entity.setTierCode(dto.getTierCode());
        }
        if (dto.getTierLevel() != null && !dto.getTierLevel().equals(entity.getTierLevel())) {

            entity.setTierLevel(dto.getTierLevel());
        }
        if (dto.getTierName() != null) entity.setTierName(dto.getTierName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getTierColor() != null) entity.setTierColor(dto.getTierColor());
        if (dto.getTierIcon() != null) entity.setTierIcon(dto.getTierIcon());
        if (dto.getUpgradeThreshold() != null) entity.setUpgradeThreshold(dto.getUpgradeThreshold());
        if (dto.getDowngradeThreshold() != null) entity.setDowngradeThreshold(dto.getDowngradeThreshold());
        if (dto.getValidityPeriodMonths() != null) entity.setValidityPeriodMonths(dto.getValidityPeriodMonths());
        if (dto.getUpgradeRuleType() != null) {
            validateRuleType(dto.getUpgradeRuleType());
            entity.setUpgradeRuleType(dto.getUpgradeRuleType());
        }
        if (dto.getBenefitsSummary() != null) entity.setBenefitsSummary(dto.getBenefitsSummary());
        if (dto.getPointMultiplier() != null) entity.setPointMultiplier(dto.getPointMultiplier());
        if (dto.getDiscountRate() != null) entity.setDiscountRate(dto.getDiscountRate());
        if (dto.getFreeShipping() != null) entity.setFreeShipping(dto.getFreeShipping());
        if (dto.getPrioritySupport() != null) entity.setPrioritySupport(dto.getPrioritySupport());
        if (dto.getExclusiveProducts() != null) entity.setExclusiveProducts(dto.getExclusiveProducts());
        if (dto.getBirthdayBonus() != null) entity.setBirthdayBonus(dto.getBirthdayBonus());
        if (dto.getSignupBonusPoints() != null) entity.setSignupBonusPoints(dto.getSignupBonusPoints());
        if (dto.getMonthlyBonusPoints() != null) entity.setMonthlyBonusPoints(dto.getMonthlyBonusPoints());
        if (dto.getAnnualBonusPoints() != null) entity.setAnnualBonusPoints(dto.getAnnualBonusPoints());
        if (dto.getTierUpBonusPoints() != null) entity.setTierUpBonusPoints(dto.getTierUpBonusPoints());
        if (dto.getMaxRedeemRate() != null) entity.setMaxRedeemRate(dto.getMaxRedeemRate());
        if (dto.getFreeReturnDays() != null) entity.setFreeReturnDays(dto.getFreeReturnDays());
        if (dto.getCustomBenefits() != null) entity.setCustomBenefits(dto.getCustomBenefits());
        if (dto.getApplicableProducts() != null) entity.setApplicableProducts(dto.getApplicableProducts());
        if (dto.getApplicableChannels() != null) entity.setApplicableChannels(dto.getApplicableChannels());
        if (dto.getIsVisible() != null) entity.setIsVisible(dto.getIsVisible());
        if (dto.getSortOrder() != null) entity.setSortOrder(dto.getSortOrder());
        entity = tierRepository.save(entity);
        log.info("更新会员等级: id={}", id);
        return toTierDto(entity);
    }

    /**
     * 删除会员等级。
     *
     * @param id 等级 ID
     * @throws ScrmException 等级不存在
     */
    @Transactional
    public void deleteTier(Long id) throws ScrmException {
        ScrmMembershipTierEntity entity = findTierOrThrow(id);
        tierRepository.delete(entity);
        log.info("删除会员等级: id={}", id);
    }

    /**
     * 查询会员等级详情。
     *
     * @param id 等级 ID
     * @return 等级 DTO
     * @throws ScrmException 等级不存在
     */
    @Transactional(readOnly = true)
    public ScrmMembershipTierDto getTier(Long id) throws ScrmException {
        return toTierDto(findTierOrThrow(id));
    }

    /**
     * 按等级编码查询会员等级。
     *
     * @param code 等级编码
     * @return 等级 DTO
     * @throws ScrmException 等级不存在
     */
    @Transactional(readOnly = true)
    public ScrmMembershipTierDto getTierByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("等级编码不能为空");
        }
        ScrmMembershipTierEntity entity = tierRepository.findByTierCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "会员等级不存在: code=" + code));

        return toTierDto(entity);
    }

    /**
     * 分页查询会员等级, 支持按启用状态与关键词过滤。
     *
     * @param enabled 启用状态过滤 (可空)
     * @param keyword 关键词过滤, 匹配等级名称/编码/描述 (可空)
     * @param pageable 分页参数
     * @return 等级分页结果 (按等级序号升序)
     */
    @Transactional(readOnly = true)
    public Page<ScrmMembershipTierDto> listTiers(Boolean enabled, String keyword, Pageable pageable) {
        Specification<ScrmMembershipTierEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("tierName")), like),
                        cb.like(cb.lower(root.get("tierCode")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "tierLevel"));
        return tierRepository.findAll(spec, sorted).map(this::toTierDto);
    }

    /**
     * 启用会员等级。
     *
     * @param id 等级 ID
     * @return 更新后的等级
     * @throws ScrmException 等级不存在
     */
    @Transactional
    public ScrmMembershipTierDto enableTier(Long id) throws ScrmException {
        ScrmMembershipTierEntity entity = findTierOrThrow(id);
        entity.setEnabled(Boolean.TRUE);
        entity = tierRepository.save(entity);
        log.info("启用会员等级: id={}", id);
        return toTierDto(entity);
    }

    /**
     * 停用会员等级。
     *
     * @param id 等级 ID
     * @return 更新后的等级
     * @throws ScrmException 等级不存在
     */
    @Transactional
    public ScrmMembershipTierDto disableTier(Long id) throws ScrmException {
        ScrmMembershipTierEntity entity = findTierOrThrow(id);
        entity.setEnabled(Boolean.FALSE);
        entity = tierRepository.save(entity);
        log.info("停用会员等级: id={}", id);
        return toTierDto(entity);
    }

    /**
     * 根据消费额获取对应等级 (取升级阈值不超过消费额的最高等级)。
     *
     * @param totalSpend 累计消费额
     * @return 等级 DTO, 无匹配时返回最低等级
     * @throws ScrmException 无可用等级
     */
    @Transactional(readOnly = true)
    public ScrmMembershipTierDto getTierBySpend(Double totalSpend) throws ScrmException {
        if (totalSpend == null || totalSpend < 0) {
            throw ScrmException.badRequest("消费额不能为空且不能为负");
        }
        List<ScrmMembershipTierEntity> tiers = tierRepository
                .findByEnabledOrderByTierLevelAsc(Boolean.TRUE);
        if (tiers.isEmpty()) {
            throw new ScrmException(ScrmExceptionConstants.NOT_FOUND, "无可用会员等级");
        }
        ScrmMembershipTierEntity matched = tiers.get(0);
        for (ScrmMembershipTierEntity tier : tiers) {
            double threshold = tier.getUpgradeThreshold() != null ? tier.getUpgradeThreshold() : 0d;
            if (totalSpend >= threshold) {
                matched = tier;
            }
        }
        return toTierDto(matched);
    }

    /**
     * 获取下一等级 (等级序号大于当前等级的最小等级)。
     *
     * @param tierId 当前等级 ID
     * @return 下一等级 DTO, 已是最高等级时返回 null
     * @throws ScrmException 等级不存在
     */
    @Transactional(readOnly = true)
    public ScrmMembershipTierDto getNextTier(Long tierId) throws ScrmException {
        ScrmMembershipTierEntity current = findTierOrThrow(tierId);
        List<ScrmMembershipTierEntity> nexts = tierRepository.findByTierLevelGreaterThanOrderByTierLevelAsc(
                current.getTierLevel());
        return nexts.isEmpty() ? null : toTierDto(nexts.get(0));
    }

    /**
     * 获取上一等级 (等级序号小于当前等级的最大等级)。
     *
     * @param tierId 当前等级 ID
     * @return 上一等级 DTO, 已是最低等级时返回 null
     * @throws ScrmException 等级不存在
     */
    @Transactional(readOnly = true)
    public ScrmMembershipTierDto getPreviousTier(Long tierId) throws ScrmException {
        ScrmMembershipTierEntity current = findTierOrThrow(tierId);
        List<ScrmMembershipTierEntity> prevs = tierRepository.findByTierLevelLessThanOrderByTierLevelDesc(
                current.getTierLevel());
        return prevs.isEmpty() ? null : toTierDto(prevs.get(0));
    }

    /**
     * 更新等级统计 (会员数/总消费/平均消费)。
     *
     * @param id 等级 ID
     * @return 更新后的等级
     * @throws ScrmException 等级不存在
     */
    @Transactional
    public ScrmMembershipTierDto updateTierStats(Long id) throws ScrmException {
        ScrmMembershipTierEntity entity = findTierOrThrow(id);
        long count = membershipRepository.countByTierId(id);
        Specification<ScrmCustomerMembershipEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tierId"), id));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCustomerMembershipEntity> members = membershipRepository.findAll(spec);
        double totalSpend = members.stream()
                .filter(m -> m.getTotalSpend() != null)
                .mapToDouble(ScrmCustomerMembershipEntity::getTotalSpend).sum();
        entity.setMemberCount((int) count);
        entity.setTotalSpend(Math.round(totalSpend * 100d) / 100d);
        entity.setAvgSpend(count > 0 ? Math.round(totalSpend / count * 100d) / 100d : 0d);
        entity = tierRepository.save(entity);
        log.info("更新会员等级统计: id={}, memberCount={}, totalSpend={}", id, count, totalSpend);
        return toTierDto(entity);
    }

    /**
     * 获取等级树 (按等级序号升序的等级列表, 含下一等级信息)。
     *
     * @return 等级树列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTierTree() {
        List<ScrmMembershipTierEntity> tiers = tierRepository
                .findAllByOrderByTierLevelAsc();
        List<Map<String, Object>> tree = new ArrayList<>();
        for (int i = 0; i < tiers.size(); i++) {
            ScrmMembershipTierEntity tier = tiers.get(i);
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("tier", toTierDto(tier));
            node.put("nextTier", i + 1 < tiers.size() ? toTierDto(tiers.get(i + 1)) : null);
            node.put("previousTier", i > 0 ? toTierDto(tiers.get(i - 1)) : null);
            tree.add(node);
        }
        return tree;
    }

    /**
     * 计算会员升级进度 (周期内消费 / 下一等级升级阈值, 0-1)。
     *
     * @param membershipId 会员记录 ID
     * @return 升级进度 (0-1, 已是最高等级返回 1)
     * @throws ScrmException 会员不存在
     */
    @Transactional(readOnly = true)
    public Double calculateUpgradeProgress(Long membershipId) throws ScrmException {
        ScrmCustomerMembershipEntity membership = findMembershipOrThrow(membershipId);
        ScrmMembershipTierEntity nextTier = tierRepository
                .findByTierLevelGreaterThanOrderByTierLevelAsc(
                        membership.getTierLevel()).stream().findFirst().orElse(null);
        if (nextTier == null) {
            return 1.0d;
        }
        double threshold = nextTier.getUpgradeThreshold() != null ? nextTier.getUpgradeThreshold() : 0d;
        if (threshold <= 0) {
            return 1.0d;
        }
        double spend = membership.getSpendInPeriod() != null ? membership.getSpendInPeriod() : 0d;
        double progress = spend / threshold;
        return Math.min(1.0d, Math.round(progress * 10000d) / 10000d);
    }

    /**
     * 校验会员等级参数。
     *
     * @param dto     等级参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateTierDto(ScrmMembershipTierDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("等级参数不能为空");
        }
        if (!partial) {
            if (dto.getTierName() == null || dto.getTierName().isBlank()) {
                throw ScrmException.badRequest("等级名称不能为空");
            }
            if (dto.getTierCode() == null || dto.getTierCode().isBlank()) {
                throw ScrmException.badRequest("等级编码不能为空");
            }
            if (dto.getTierLevel() == null) {
                throw ScrmException.badRequest("等级序号不能为空");
            }
            if (dto.getTierLevel() < 1 || dto.getTierLevel() > 10) {
                throw ScrmException.badRequest("等级序号需在 1-10 之间: " + dto.getTierLevel());
            }
        }
        if (dto.getTierLevel() != null && (dto.getTierLevel() < 1 || dto.getTierLevel() > 10)) {
            throw ScrmException.badRequest("等级序号需在 1-10 之间: " + dto.getTierLevel());
        }
        if (dto.getUpgradeRuleType() != null) {
            validateRuleType(dto.getUpgradeRuleType());
        }
    }

    /** 校验升级规则类型合法性 */
    private void validateRuleType(String type) throws ScrmException {
        if (!VALID_RULE_TYPES.contains(type)) {
            throw ScrmException.badRequest("升级规则类型非法: " + type
                    + ", 合法值: SPEND / POINTS / ORDER_COUNT / MANUAL");
        }
    }

    /**
     * 按主键查询会员等级, 不存在或越权抛异常
     */
    ScrmMembershipTierEntity findTierOrThrow(Long id) throws ScrmException {
        ScrmMembershipTierEntity entity = tierRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "会员等级不存在: id=" + id));

        return entity;
    }

    /**
     * 按主键查询会员, 不存在或越权抛异常
     */
    private ScrmCustomerMembershipEntity findMembershipOrThrow(Long id) throws ScrmException {
        ScrmCustomerMembershipEntity entity = membershipRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "会员不存在: id=" + id));

        return entity;
    }

    /**
     * 等级实体转 DTO
     */
    ScrmMembershipTierDto toTierDto(ScrmMembershipTierEntity entity) {
        ScrmMembershipTierDto dto = new ScrmMembershipTierDto();
        dto.setId(entity.getId());
        dto.setTierName(entity.getTierName());
        dto.setTierCode(entity.getTierCode());
        dto.setTierLevel(entity.getTierLevel());
        dto.setDescription(entity.getDescription());
        dto.setTierColor(entity.getTierColor());
        dto.setTierIcon(entity.getTierIcon());
        dto.setUpgradeThreshold(entity.getUpgradeThreshold());
        dto.setDowngradeThreshold(entity.getDowngradeThreshold());
        dto.setValidityPeriodMonths(entity.getValidityPeriodMonths());
        dto.setUpgradeRuleType(entity.getUpgradeRuleType());
        dto.setBenefitsSummary(entity.getBenefitsSummary());
        dto.setPointMultiplier(entity.getPointMultiplier());
        dto.setDiscountRate(entity.getDiscountRate());
        dto.setFreeShipping(entity.getFreeShipping());
        dto.setPrioritySupport(entity.getPrioritySupport());
        dto.setExclusiveProducts(entity.getExclusiveProducts());
        dto.setBirthdayBonus(entity.getBirthdayBonus());
        dto.setSignupBonusPoints(entity.getSignupBonusPoints());
        dto.setMonthlyBonusPoints(entity.getMonthlyBonusPoints());
        dto.setAnnualBonusPoints(entity.getAnnualBonusPoints());
        dto.setTierUpBonusPoints(entity.getTierUpBonusPoints());
        dto.setMaxRedeemRate(entity.getMaxRedeemRate());
        dto.setFreeReturnDays(entity.getFreeReturnDays());
        dto.setCustomBenefits(entity.getCustomBenefits());
        dto.setApplicableProducts(entity.getApplicableProducts());
        dto.setApplicableChannels(entity.getApplicableChannels());
        dto.setEnabled(entity.getEnabled());
        dto.setIsVisible(entity.getIsVisible());
        dto.setMemberCount(entity.getMemberCount());
        dto.setTotalSpend(entity.getTotalSpend());
        dto.setAvgSpend(entity.getAvgSpend());
        dto.setSortOrder(entity.getSortOrder());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
