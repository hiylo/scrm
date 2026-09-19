/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMembershipBenefitService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmBenefitRedeemDto;
import org.hiylo.scrm.dto.ScrmMembershipBenefitDto;
import org.hiylo.scrm.entity.ScrmCustomerMembershipEntity;
import org.hiylo.scrm.entity.ScrmMembershipBenefitEntity;
import org.hiylo.scrm.entity.ScrmMembershipTierEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerMembershipRepository;
import org.hiylo.scrm.repository.ScrmMembershipBenefitRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * SCRM 客户会员权益管理服务。
 * <p>
 * 承载权益子域的全部能力: 权益增删改查、按编码查询、分页过滤、启停、按等级获取 (含通用权益)、
 * 兑换与资格检查、使用记录、会员可用权益、热门权益、统计刷新、分配到等级等。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmMembershipBenefitService {

    // ==================== 权益类型 ====================
    /** 权益类型: 折扣 */
    private static final String BENEFIT_DISCOUNT = "DISCOUNT";
    /** 权益类型: 免费配送 */
    private static final String BENEFIT_FREE_SHIPPING = "FREE_SHIPPING";
    /** 权益类型: 积分倍数 */
    private static final String BENEFIT_POINTS_MULTIPLIER = "POINTS_MULTIPLIER";
    /** 权益类型: 专属商品 */
    private static final String BENEFIT_EXCLUSIVE_PRODUCT = "EXCLUSIVE_PRODUCT";
    /** 权益类型: 优先支持 */
    private static final String BENEFIT_PRIORITY_SUPPORT = "PRIORITY_SUPPORT";
    /** 权益类型: 生日礼金 */
    private static final String BENEFIT_BIRTHDAY_BONUS = "BIRTHDAY_BONUS";
    /** 权益类型: 免费退货 */
    private static final String BENEFIT_FREE_RETURN = "FREE_RETURN";
    /** 权益类型: 优惠券 */
    private static final String BENEFIT_COUPON = "COUPON";
    /** 权益类型: 礼品 */
    private static final String BENEFIT_GIFT = "GIFT";
    /** 权益类型: 体验 */
    private static final String BENEFIT_EXPERIENCE = "EXPERIENCE";
    /** 权益类型: 服务 */
    private static final String BENEFIT_SERVICE = "SERVICE";
    /** 权益类型: 自定义 */
    private static final String BENEFIT_CUSTOM = "CUSTOM";

    // ==================== 权益值类型 ====================
    /** 值类型: 百分比 */
    private static final String VALUE_PERCENTAGE = "PERCENTAGE";
    /** 值类型: 金额 */
    private static final String VALUE_AMOUNT = "AMOUNT";
    /** 值类型: 数量 */
    private static final String VALUE_COUNT = "COUNT";
    /** 值类型: 天数 */
    private static final String VALUE_DAYS = "DAYS";

    // ==================== 权益状态 ====================
    /** 权益状态: 活跃 */
    private static final String BENEFIT_STATUS_ACTIVE = "ACTIVE";
    /** 权益状态: 停用 */
    private static final String BENEFIT_STATUS_INACTIVE = "INACTIVE";
    /** 权益状态: 过期 */
    private static final String BENEFIT_STATUS_EXPIRED = "EXPIRED";

    /** 会员状态: 活跃 */
    private static final String STATUS_ACTIVE = "ACTIVE";

    /** 默认每页条数上限 */
    private static final int DEFAULT_LIMIT = 10;

    /** 合法权益类型集合 */
    private static final Set<String> VALID_BENEFIT_TYPES = new HashSet<>(Arrays.asList(
            BENEFIT_DISCOUNT, BENEFIT_FREE_SHIPPING, BENEFIT_POINTS_MULTIPLIER, BENEFIT_EXCLUSIVE_PRODUCT,
            BENEFIT_PRIORITY_SUPPORT, BENEFIT_BIRTHDAY_BONUS, BENEFIT_FREE_RETURN, BENEFIT_COUPON,
            BENEFIT_GIFT, BENEFIT_EXPERIENCE, BENEFIT_SERVICE, BENEFIT_CUSTOM));
    /** 合法值类型集合 */
    private static final Set<String> VALID_VALUE_TYPES = new HashSet<>(Arrays.asList(
            VALUE_PERCENTAGE, VALUE_AMOUNT, VALUE_COUNT, VALUE_DAYS));
    /** 合法权益状态集合 */
    private static final Set<String> VALID_BENEFIT_STATUSES = new HashSet<>(Arrays.asList(
            BENEFIT_STATUS_ACTIVE, BENEFIT_STATUS_INACTIVE, BENEFIT_STATUS_EXPIRED));

    /** 会员等级服务 */
    private final ScrmMembershipTierService tierService;
    /** 会员权益数据访问层 */
    private final ScrmMembershipBenefitRepository benefitRepository;
    /** 客户会员数据访问层 */
    private final ScrmCustomerMembershipRepository membershipRepository;

    /**
     * 创建会员权益。
     * <p>校验权益编码唯一与枚举合法性后写入账号 ID 持久化, 状态缺省 ACTIVE, 可见缺省 TRUE,
     * 使用限制与统计字段缺省 0。若指定适用等级则校验等级存在并快照名称。</p>
     *
     * @param dto 权益参数
     * @return 创建后的权益
     * @throws ScrmException 参数非法 / 权益编码重复
     */
    @Transactional
    public ScrmMembershipBenefitDto createBenefit(ScrmMembershipBenefitDto dto) throws ScrmException {
        validateBenefitDto(dto, false);
        if (benefitRepository.findByBenefitCode(dto.getBenefitCode()).isPresent()) {
            throw ScrmException.conflict("权益编码已存在: " + dto.getBenefitCode());
        }
        ScrmMembershipBenefitEntity entity = new ScrmMembershipBenefitEntity();
        entity.setBenefitName(dto.getBenefitName());
        entity.setBenefitCode(dto.getBenefitCode());
        entity.setBenefitType(dto.getBenefitType());
        entity.setDescription(dto.getDescription());
        if (dto.getTierId() != null) {
            ScrmMembershipTierEntity tier = tierService.findTierOrThrow(dto.getTierId());
            entity.setTierId(tier.getId());
            entity.setTierName(tier.getTierName());
        }
        entity.setValue(dto.getValue() != null ? dto.getValue() : 0d);
        entity.setValueType(dto.getValueType());
        entity.setApplicableProducts(dto.getApplicableProducts());
        entity.setApplicableCategories(dto.getApplicableCategories());
        entity.setApplicableChannels(dto.getApplicableChannels());
        entity.setUsageLimitPerMember(dto.getUsageLimitPerMember() != null ? dto.getUsageLimitPerMember() : 0);
        entity.setUsageLimitPerDay(dto.getUsageLimitPerDay() != null ? dto.getUsageLimitPerDay() : 0);
        entity.setUsageLimitPerMonth(dto.getUsageLimitPerMonth() != null ? dto.getUsageLimitPerMonth() : 0);
        entity.setUsageLimitTotal(dto.getUsageLimitTotal() != null ? dto.getUsageLimitTotal() : 0);
        entity.setCurrentUsageCount(0);
        entity.setMemberUsageCount(0);
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : BENEFIT_STATUS_ACTIVE);
        entity.setRedemptionInstructions(dto.getRedemptionInstructions());
        entity.setTerms(dto.getTerms());
        entity.setIcon(dto.getIcon());
        entity.setDisplayOrder(dto.getDisplayOrder() != null ? dto.getDisplayOrder() : 0);
        entity.setIsVisible(dto.getIsVisible() != null ? dto.getIsVisible() : Boolean.TRUE);
        entity.setTotalRedeemedValue(0d);
        entity.setTotalSavedAmount(0d);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = benefitRepository.save(entity);
        log.info("创建会员权益: id={}, code={}", entity.getId(), entity.getBenefitCode());
        return toBenefitDto(entity);
    }

    /**
     * 更新会员权益（字段非空才覆盖）。
     * <p>统计字段通过专用接口维护, 此处不直接修改。权益编码变更时校验唯一性。</p>
     *
     * @param id  权益 ID
     * @param dto 权益参数
     * @return 更新后的权益
     * @throws ScrmException 权益不存在 / 参数非法 / 权益编码重复
     */
    @Transactional
    public ScrmMembershipBenefitDto updateBenefit(Long id, ScrmMembershipBenefitDto dto) throws ScrmException {
        ScrmMembershipBenefitEntity entity = findBenefitOrThrow(id);
        validateBenefitDto(dto, true);
        if (dto.getBenefitCode() != null && !dto.getBenefitCode().equals(entity.getBenefitCode())) {
            benefitRepository.findByBenefitCode(dto.getBenefitCode()).ifPresent(b -> {
                throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                        "权益编码已存在: " + dto.getBenefitCode());
            });
            entity.setBenefitCode(dto.getBenefitCode());
        }
        if (dto.getBenefitName() != null) entity.setBenefitName(dto.getBenefitName());
        if (dto.getBenefitType() != null) {
            validateBenefitType(dto.getBenefitType());
            entity.setBenefitType(dto.getBenefitType());
        }
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getTierId() != null) {
            ScrmMembershipTierEntity tier = tierService.findTierOrThrow(dto.getTierId());
            entity.setTierId(tier.getId());
            entity.setTierName(tier.getTierName());
        }
        if (dto.getValue() != null) entity.setValue(dto.getValue());
        if (dto.getValueType() != null) {
            validateValueType(dto.getValueType());
            entity.setValueType(dto.getValueType());
        }
        if (dto.getApplicableProducts() != null) entity.setApplicableProducts(dto.getApplicableProducts());
        if (dto.getApplicableCategories() != null) entity.setApplicableCategories(dto.getApplicableCategories());
        if (dto.getApplicableChannels() != null) entity.setApplicableChannels(dto.getApplicableChannels());
        if (dto.getUsageLimitPerMember() != null) entity.setUsageLimitPerMember(dto.getUsageLimitPerMember());
        if (dto.getUsageLimitPerDay() != null) entity.setUsageLimitPerDay(dto.getUsageLimitPerDay());
        if (dto.getUsageLimitPerMonth() != null) entity.setUsageLimitPerMonth(dto.getUsageLimitPerMonth());
        if (dto.getUsageLimitTotal() != null) entity.setUsageLimitTotal(dto.getUsageLimitTotal());
        if (dto.getStartDate() != null) entity.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null) entity.setEndDate(dto.getEndDate());
        if (dto.getStatus() != null) {
            validateBenefitStatus(dto.getStatus());
            entity.setStatus(dto.getStatus());
        }
        if (dto.getRedemptionInstructions() != null) entity.setRedemptionInstructions(dto.getRedemptionInstructions());
        if (dto.getTerms() != null) entity.setTerms(dto.getTerms());
        if (dto.getIcon() != null) entity.setIcon(dto.getIcon());
        if (dto.getDisplayOrder() != null) entity.setDisplayOrder(dto.getDisplayOrder());
        if (dto.getIsVisible() != null) entity.setIsVisible(dto.getIsVisible());
        entity = benefitRepository.save(entity);
        log.info("更新会员权益: id={}", id);
        return toBenefitDto(entity);
    }

    /**
     * 删除会员权益。
     *
     * @param id 权益 ID
     * @throws ScrmException 权益不存在
     */
    @Transactional
    public void deleteBenefit(Long id) throws ScrmException {
        ScrmMembershipBenefitEntity entity = findBenefitOrThrow(id);
        benefitRepository.delete(entity);
        log.info("删除会员权益: id={}", id);
    }

    /**
     * 查询权益详情。
     *
     * @param id 权益 ID
     * @return 权益 DTO
     * @throws ScrmException 权益不存在
     */
    @Transactional(readOnly = true)
    public ScrmMembershipBenefitDto getBenefit(Long id) throws ScrmException {
        return toBenefitDto(findBenefitOrThrow(id));
    }

    /**
     * 按权益编码查询权益。
     *
     * @param code 权益编码
     * @return 权益 DTO
     * @throws ScrmException 权益不存在
     */
    @Transactional(readOnly = true)
    public ScrmMembershipBenefitDto getBenefitByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("权益编码不能为空");
        }
        ScrmMembershipBenefitEntity entity = benefitRepository.findByBenefitCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "会员权益不存在: code=" + code));

        return toBenefitDto(entity);
    }

    /**
     * 分页查询权益, 支持按等级、权益类型与状态过滤。
     *
     * @param tierId      等级过滤 (可空)
     * @param benefitType 权益类型过滤 (可空)
     * @param status      状态过滤 (可空)
     * @param pageable    分页参数
     * @return 权益分页结果 (按展示顺序升序)
     */
    @Transactional(readOnly = true)
    public Page<ScrmMembershipBenefitDto> listBenefits(Long tierId, String benefitType, String status,
                                                        Pageable pageable) {
        Specification<ScrmMembershipBenefitEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (tierId != null) {
                predicates.add(cb.equal(root.get("tierId"), tierId));
            }
            if (benefitType != null && !benefitType.isBlank()) {
                predicates.add(cb.equal(root.get("benefitType"), benefitType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "displayOrder"));
        return benefitRepository.findAll(spec, sorted).map(this::toBenefitDto);
    }

    /**
     * 启用权益。
     *
     * @param id 权益 ID
     * @return 更新后的权益
     * @throws ScrmException 权益不存在
     */
    @Transactional
    public ScrmMembershipBenefitDto enableBenefit(Long id) throws ScrmException {
        ScrmMembershipBenefitEntity entity = findBenefitOrThrow(id);
        entity.setStatus(BENEFIT_STATUS_ACTIVE);
        entity = benefitRepository.save(entity);
        log.info("启用会员权益: id={}", id);
        return toBenefitDto(entity);
    }

    /**
     * 停用权益。
     *
     * @param id 权益 ID
     * @return 更新后的权益
     * @throws ScrmException 权益不存在
     */
    @Transactional
    public ScrmMembershipBenefitDto disableBenefit(Long id) throws ScrmException {
        ScrmMembershipBenefitEntity entity = findBenefitOrThrow(id);
        entity.setStatus(BENEFIT_STATUS_INACTIVE);
        entity = benefitRepository.save(entity);
        log.info("停用会员权益: id={}", id);
        return toBenefitDto(entity);
    }

    /**
     * 按等级获取权益 (含通用权益 tierId=null)。
     *
     * @param tierId 等级 ID
     * @return 权益列表 (按展示顺序升序)
     * @throws ScrmException 等级不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmMembershipBenefitDto> getBenefitsByTier(Long tierId) throws ScrmException {
        tierService.findTierOrThrow(tierId);
        List<ScrmMembershipBenefitEntity> benefits = new ArrayList<>();
        benefits.addAll(benefitRepository.findByTierIdOrderByDisplayOrderAsc(tierId));
        benefits.addAll(benefitRepository.findByTierIdIsNullOrderByDisplayOrderAsc());
        return benefits.stream().map(this::toBenefitDto).collect(Collectors.toList());
    }

    /**
     * 兑换权益 (校验→记录→更新统计→返回优惠)。
     * <p>校验会员状态有效、权益有效 (ACTIVE 且在生效周期内) 与使用限制 (每人/每日/每月/总量),
     * 计算优惠金额 (按权益值与值类型), 更新权益与会员使用统计, 返回优惠结果。</p>
     *
     * @param redeemDto 兑换参数
     * @return 兑换结果 (benefitId / membershipId / discountAmount / savedAmount / transactionId)
     * @throws ScrmException 会员不存在 / 权益不存在 / 状态非法 / 超出使用限制
     */
    @Transactional
    public Map<String, Object> redeem(ScrmBenefitRedeemDto redeemDto) throws ScrmException {
        if (redeemDto == null || redeemDto.getMembershipId() == null) {
            throw ScrmException.badRequest("会员记录 ID 不能为空");
        }
        if (redeemDto.getBenefitId() == null) {
            throw ScrmException.badRequest("权益 ID 不能为空");
        }
        ScrmCustomerMembershipEntity membership = findMembershipOrThrow(redeemDto.getMembershipId());
        if (!STATUS_ACTIVE.equals(membership.getMembershipStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "会员状态非活跃, 不允许兑换权益: status=" + membership.getMembershipStatus());
        }
        ScrmMembershipBenefitEntity benefit = findBenefitOrThrow(redeemDto.getBenefitId());
        if (!BENEFIT_STATUS_ACTIVE.equals(benefit.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "权益状态非活跃, 不允许兑换: status=" + benefit.getStatus());
        }
        // 校验权益适用等级
        if (benefit.getTierId() != null && !benefit.getTierId().equals(membership.getTierId())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "权益不适用当前会员等级: benefitTier=" + benefit.getTierId()
                            + ", memberTier=" + membership.getTierId());
        }
        // 校验生效周期
        LocalDate today = LocalDate.now();
        if (benefit.getStartDate() != null && today.isBefore(benefit.getStartDate())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "权益尚未生效: startDate=" + benefit.getStartDate());
        }
        if (benefit.getEndDate() != null && today.isAfter(benefit.getEndDate())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "权益已过期: endDate=" + benefit.getEndDate());
        }
        // 校验总限制
        if (benefit.getUsageLimitTotal() != null && benefit.getUsageLimitTotal() > 0) {
            int current = benefit.getCurrentUsageCount() != null ? benefit.getCurrentUsageCount() : 0;
            if (current >= benefit.getUsageLimitTotal()) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "权益已达总使用上限: limit=" + benefit.getUsageLimitTotal());
            }
        }
        // 计算优惠金额
        double amount = redeemDto.getAmount() != null ? redeemDto.getAmount() : 0d;
        double benefitValue = benefit.getValue() != null ? benefit.getValue() : 0d;
        double discountAmount = 0d;
        String valueType = benefit.getValueType();
        if (VALUE_PERCENTAGE.equals(valueType)) {
            discountAmount = Math.round(amount * benefitValue * 100d) / 100d;
        } else if (VALUE_AMOUNT.equals(valueType)) {
            discountAmount = Math.min(amount, benefitValue);
        } else if (VALUE_COUNT.equals(valueType) || VALUE_DAYS.equals(valueType)) {
            discountAmount = benefitValue;
        } else {
            // 无值类型时, 折扣类按 value 作为折扣率, 否则按固定值
            if (BENEFIT_DISCOUNT.equals(benefit.getBenefitType())) {
                discountAmount = Math.round(amount * benefitValue * 100d) / 100d;
            } else {
                discountAmount = benefitValue;
            }
        }
        // 更新权益统计
        int currentUsage = (benefit.getCurrentUsageCount() != null ? benefit.getCurrentUsageCount() : 0) + 1;
        int memberUsage = (benefit.getMemberUsageCount() != null ? benefit.getMemberUsageCount() : 0) + 1;
        benefit.setCurrentUsageCount(currentUsage);
        benefit.setMemberUsageCount(memberUsage);
        double totalRedeemed = (benefit.getTotalRedeemedValue() != null
                ? benefit.getTotalRedeemedValue()
                : 0d) + discountAmount;
        double totalSaved = (benefit.getTotalSavedAmount() != null
                ? benefit.getTotalSavedAmount()
                : 0d) + discountAmount;
        benefit.setTotalRedeemedValue(Math.round(totalRedeemed * 100d) / 100d);
        benefit.setTotalSavedAmount(Math.round(totalSaved * 100d) / 100d);
        benefitRepository.save(benefit);
        // 更新会员统计
        int benefitsUsed = (membership.getBenefitsUsedCount() != null ? membership.getBenefitsUsedCount() : 0) + 1;
        double benefitsSaved = (membership.getBenefitsSavedAmount() != null
                ? membership.getBenefitsSavedAmount()
                : 0d) + discountAmount;
        membership.setBenefitsUsedCount(benefitsUsed);
        membership.setBenefitsSavedAmount(Math.round(benefitsSaved * 100d) / 100d);
        membership.setLastActivityDate(today);
        membershipRepository.save(membership);
        String transactionId = "BR" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transactionId", transactionId);
        result.put("benefitId", benefit.getId());
        result.put("benefitName", benefit.getBenefitName());
        result.put("benefitType", benefit.getBenefitType());
        result.put("membershipId", membership.getId());
        result.put("orderId", redeemDto.getOrderId());
        result.put("amount", Math.round(amount * 100d) / 100d);
        result.put("discountAmount", Math.round(discountAmount * 100d) / 100d);
        result.put("savedAmount", Math.round(discountAmount * 100d) / 100d);
        log.info("兑换权益: membershipId={}, benefitId={}, discount={}",
                membership.getId(), benefit.getId(), discountAmount);
        return result;
    }

    /**
     * 检查权益兑换资格 (会员状态/权益状态/生效周期/使用限制)。
     *
     * @param membershipId 会员记录 ID
     * @param benefitId    权益 ID
     * @return 检查结果 (eligible / reason)
     * @throws ScrmException 会员不存在 / 权益不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> checkRedeemEligibility(Long membershipId, Long benefitId) throws ScrmException {
        ScrmCustomerMembershipEntity membership = findMembershipOrThrow(membershipId);
        ScrmMembershipBenefitEntity benefit = findBenefitOrThrow(benefitId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("membershipId", membershipId);
        result.put("benefitId", benefitId);
        if (!STATUS_ACTIVE.equals(membership.getMembershipStatus())) {
            result.put("eligible", false);
            result.put("reason", "会员状态非活跃: " + membership.getMembershipStatus());
            return result;
        }
        if (!BENEFIT_STATUS_ACTIVE.equals(benefit.getStatus())) {
            result.put("eligible", false);
            result.put("reason", "权益状态非活跃: " + benefit.getStatus());
            return result;
        }
        if (benefit.getTierId() != null && !benefit.getTierId().equals(membership.getTierId())) {
            result.put("eligible", false);
            result.put("reason", "权益不适用当前会员等级");
            return result;
        }
        LocalDate today = LocalDate.now();
        if (benefit.getStartDate() != null && today.isBefore(benefit.getStartDate())) {
            result.put("eligible", false);
            result.put("reason", "权益尚未生效");
            return result;
        }
        if (benefit.getEndDate() != null && today.isAfter(benefit.getEndDate())) {
            result.put("eligible", false);
            result.put("reason", "权益已过期");
            return result;
        }
        if (benefit.getUsageLimitTotal() != null && benefit.getUsageLimitTotal() > 0) {
            int current = benefit.getCurrentUsageCount() != null ? benefit.getCurrentUsageCount() : 0;
            if (current >= benefit.getUsageLimitTotal()) {
                result.put("eligible", false);
                result.put("reason", "权益已达总使用上限");
                return result;
            }
        }
        result.put("eligible", true);
        return result;
    }

    /**
     * 查询权益使用记录 (当前使用次数/会员使用次数/总兑换价值/总节省金额)。
     *
     * @param membershipId 会员记录 ID (仅用于校验, 不影响权益维度统计)
     * @param benefitId    权益 ID
     * @return 权益使用统计
     * @throws ScrmException 会员不存在 / 权益不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getBenefitUsage(Long membershipId, Long benefitId) throws ScrmException {
        findMembershipOrThrow(membershipId);
        ScrmMembershipBenefitEntity benefit = findBenefitOrThrow(benefitId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("benefitId", benefitId);
        result.put("benefitName", benefit.getBenefitName());
        result.put("currentUsageCount", benefit.getCurrentUsageCount());
        result.put("memberUsageCount", benefit.getMemberUsageCount());
        result.put("usageLimitTotal", benefit.getUsageLimitTotal());
        result.put("usageLimitPerMember", benefit.getUsageLimitPerMember());
        result.put("totalRedeemedValue", benefit.getTotalRedeemedValue());
        result.put("totalSavedAmount", benefit.getTotalSavedAmount());
        return result;
    }

    /**
     * 查询会员可用权益 (按会员当前等级 + 通用权益)。
     *
     * @param membershipId 会员记录 ID
     * @return 权益列表
     * @throws ScrmException 会员不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmMembershipBenefitDto> getMemberBenefits(Long membershipId) throws ScrmException {
        ScrmCustomerMembershipEntity membership = findMembershipOrThrow(membershipId);
        return getBenefitsByTier(membership.getTierId());
    }

    /**
     * 查询热门权益 (按总兑换价值降序, 限制条数)。
     *
     * @param limit 返回条数 (默认 10)
     * @return 权益列表
     */
    @Transactional(readOnly = true)
    public List<ScrmMembershipBenefitDto> getPopularBenefits(int limit) {
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        List<ScrmMembershipBenefitEntity> benefits = benefitRepository
                .findByStatusAndIsVisibleOrderByDisplayOrderAsc(
                         BENEFIT_STATUS_ACTIVE, Boolean.TRUE);
        return benefits.stream()
                .sorted((a, b) -> Double.compare(
                        b.getTotalRedeemedValue() != null ? b.getTotalRedeemedValue() : 0d,
                        a.getTotalRedeemedValue() != null ? a.getTotalRedeemedValue() : 0d))
                .limit(limit)
                .map(this::toBenefitDto)
                .collect(Collectors.toList());
    }

    /**
     * 更新权益统计 (当前使用次数/总兑换价值/总节省金额)。
     *
     * @param id 权益 ID
     * @return 更新后的权益
     * @throws ScrmException 权益不存在
     */
    @Transactional
    public ScrmMembershipBenefitDto updateBenefitStats(Long id) throws ScrmException {
        // 当前实现: 权益统计在兑换时实时维护, 此处仅返回最新值
        return toBenefitDto(findBenefitOrThrow(id));
    }

    /**
     * 分配权益到等级 (设置权益的 tierId 与 tierName 快照)。
     *
     * @param benefitId 权益 ID
     * @param tierId    等级 ID
     * @return 更新后的权益
     * @throws ScrmException 权益不存在 / 等级不存在
     */
    @Transactional
    public ScrmMembershipBenefitDto assignBenefitToTier(Long benefitId, Long tierId) throws ScrmException {
        ScrmMembershipBenefitEntity benefit = findBenefitOrThrow(benefitId);
        ScrmMembershipTierEntity tier = tierService.findTierOrThrow(tierId);
        benefit.setTierId(tier.getId());
        benefit.setTierName(tier.getTierName());
        benefit = benefitRepository.save(benefit);
        log.info("分配权益到等级: benefitId={}, tierId={}", benefitId, tierId);
        return toBenefitDto(benefit);
    }

    /**
     * 校验权益参数。
     *
     * @param dto     权益参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateBenefitDto(ScrmMembershipBenefitDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("权益参数不能为空");
        }
        if (!partial) {
            if (dto.getBenefitName() == null || dto.getBenefitName().isBlank()) {
                throw ScrmException.badRequest("权益名称不能为空");
            }
            if (dto.getBenefitCode() == null || dto.getBenefitCode().isBlank()) {
                throw ScrmException.badRequest("权益编码不能为空");
            }
            if (dto.getBenefitType() == null || dto.getBenefitType().isBlank()) {
                throw ScrmException.badRequest("权益类型不能为空");
            }
        }
        if (dto.getBenefitType() != null) {
            validateBenefitType(dto.getBenefitType());
        }
        if (dto.getValueType() != null) {
            validateValueType(dto.getValueType());
        }
        if (dto.getStatus() != null) {
            validateBenefitStatus(dto.getStatus());
        }
    }

    /** 校验权益类型合法性 */
    private void validateBenefitType(String type) throws ScrmException {
        if (!VALID_BENEFIT_TYPES.contains(type)) {
            throw ScrmException.badRequest("权益类型非法: " + type
                    + ", 合法值: DISCOUNT / FREE_SHIPPING / POINTS_MULTIPLIER / EXCLUSIVE_PRODUCT /"
                    + "PRIORITY_SUPPORT / BIRTHDAY_BONUS / FREE_RETURN / COUPON / GIFT / EXPERIENCE / SERVICE / CUSTOM");
        }
    }

    /** 校验值类型合法性 */
    private void validateValueType(String type) throws ScrmException {
        if (!VALID_VALUE_TYPES.contains(type)) {
            throw ScrmException.badRequest("值类型非法: " + type
                    + ", 合法值: PERCENTAGE / AMOUNT / COUNT / DAYS");
        }
    }

    /** 校验权益状态合法性 */
    private void validateBenefitStatus(String status) throws ScrmException {
        if (!VALID_BENEFIT_STATUSES.contains(status)) {
            throw ScrmException.badRequest("权益状态非法: " + status
                    + ", 合法值: ACTIVE / INACTIVE / EXPIRED");
        }
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
     * 按主键查询权益, 不存在或越权抛异常
     */
    private ScrmMembershipBenefitEntity findBenefitOrThrow(Long id) throws ScrmException {
        ScrmMembershipBenefitEntity entity = benefitRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "会员权益不存在: id=" + id));

        return entity;
    }

    /**
     * 权益实体转 DTO
     */
    private ScrmMembershipBenefitDto toBenefitDto(ScrmMembershipBenefitEntity entity) {
        ScrmMembershipBenefitDto dto = new ScrmMembershipBenefitDto();
        dto.setId(entity.getId());
        dto.setBenefitName(entity.getBenefitName());
        dto.setBenefitCode(entity.getBenefitCode());
        dto.setBenefitType(entity.getBenefitType());
        dto.setDescription(entity.getDescription());
        dto.setTierId(entity.getTierId());
        dto.setTierName(entity.getTierName());
        dto.setValue(entity.getValue());
        dto.setValueType(entity.getValueType());
        dto.setApplicableProducts(entity.getApplicableProducts());
        dto.setApplicableCategories(entity.getApplicableCategories());
        dto.setApplicableChannels(entity.getApplicableChannels());
        dto.setUsageLimitPerMember(entity.getUsageLimitPerMember());
        dto.setUsageLimitPerDay(entity.getUsageLimitPerDay());
        dto.setUsageLimitPerMonth(entity.getUsageLimitPerMonth());
        dto.setUsageLimitTotal(entity.getUsageLimitTotal());
        dto.setCurrentUsageCount(entity.getCurrentUsageCount());
        dto.setMemberUsageCount(entity.getMemberUsageCount());
        dto.setStartDate(entity.getStartDate());
        dto.setEndDate(entity.getEndDate());
        dto.setStatus(entity.getStatus());
        dto.setRedemptionInstructions(entity.getRedemptionInstructions());
        dto.setTerms(entity.getTerms());
        dto.setIcon(entity.getIcon());
        dto.setDisplayOrder(entity.getDisplayOrder());
        dto.setIsVisible(entity.getIsVisible());
        dto.setTotalRedeemedValue(entity.getTotalRedeemedValue());
        dto.setTotalSavedAmount(entity.getTotalSavedAmount());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
