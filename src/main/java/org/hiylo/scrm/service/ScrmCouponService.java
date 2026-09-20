/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCouponService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCouponDto;
import org.hiylo.scrm.dto.ScrmCouponIssueDto;
import org.hiylo.scrm.dto.ScrmCouponTemplateDto;
import org.hiylo.scrm.dto.ScrmCouponUsageLogDto;
import org.hiylo.scrm.dto.ScrmCouponUseDto;
import org.hiylo.scrm.entity.ScrmCouponEntity;
import org.hiylo.scrm.entity.ScrmCouponTemplateEntity;
import org.hiylo.scrm.entity.ScrmCouponUsageLogEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCouponRepository;
import org.hiylo.scrm.repository.ScrmCouponTemplateRepository;
import org.hiylo.scrm.repository.ScrmCouponUsageLogRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * SCRM 优惠券/卡券管理服务。
 * <p>
 * 承载营销优惠券的核心能力: 优惠券模板增删改查与启停、模板统计, 批量发券与单客户发券
 * (生成券码 → 分配客户 → 记录日志), 领取与核销 (校验 → 计算抵扣 → 标记已用 → 记录日志),
 * 退还与过期处理, 优惠券与使用日志查询, 以及优惠券统计与客户优惠券统计。
 * </p>
 * <p>
 * 所有写操作写入归属账号实现数据隔离, 读操作通过
 * JPA Specification 始终按当前用户可见账号范围过滤。校验失败抛出 {@link ScrmException} 携带
 * 通用错误码 (NOT_FOUND / BAD_REQUEST / CONFLICT)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCouponService {

    // ==================== 模板状态 ====================
    /** 模板状态: 启用 */
    private static final String TEMPLATE_STATUS_ACTIVE = "ACTIVE";
    /** 模板状态: 禁用 */
    private static final String TEMPLATE_STATUS_INACTIVE = "INACTIVE";
    /** 模板状态: 已过期 */
    private static final String TEMPLATE_STATUS_EXPIRED = "EXPIRED";
    /** 模板状态: 已发完 */
    private static final String TEMPLATE_STATUS_SOLD_OUT = "SOLD_OUT";

    // ==================== 优惠券状态 ====================
    /** 优惠券状态: 未使用 */
    private static final String COUPON_STATUS_UNUSED = "UNUSED";
    /** 优惠券状态: 已使用 */
    private static final String COUPON_STATUS_USED = "USED";
    /** 优惠券状态: 已过期 */
    private static final String COUPON_STATUS_EXPIRED = "EXPIRED";
    /** 优惠券状态: 已退还 */
    private static final String COUPON_STATUS_RETURNED = "RETURNED";

    // ==================== 优惠券类型 ====================
    /** 优惠券类型: 折扣 */
    private static final String COUPON_TYPE_DISCOUNT = "DISCOUNT";
    /** 优惠券类型: 满减 */
    private static final String COUPON_TYPE_FIXED_AMOUNT = "FIXED_AMOUNT";
    /** 优惠券类型: 兑换 */
    private static final String COUPON_TYPE_EXCHANGE = "EXCHANGE";
    /** 优惠券类型: 赠品 */
    private static final String COUPON_TYPE_GIFT = "GIFT";
    /** 优惠券类型: 代金券 */
    private static final String COUPON_TYPE_CASH_VOUCHER = "CASH_VOUCHER";

    // ==================== 有效期类型 ====================
    /** 有效期类型: 固定日期 */
    private static final String VALID_TYPE_FIXED = "FIXED";
    /** 有效期类型: 领取后 N 天 */
    private static final String VALID_TYPE_RELATIVE = "RELATIVE";

    // ==================== 领取来源 ====================
    /** 领取来源: 手动 */
    private static final String SOURCE_MANUAL = "MANUAL";
    /** 领取来源: 批量发放 */
    private static final String SOURCE_MASS_SEND = "MASS_SEND";

    // ==================== 日志动作类型 ====================
    /** 日志动作: 发放 */
    private static final String ACTION_ISSUE = "ISSUE";
    /** 日志动作: 领取 */
    private static final String ACTION_CLAIM = "CLAIM";
    /** 日志动作: 使用 */
    private static final String ACTION_USE = "USE";
    /** 日志动作: 退还 */
    private static final String ACTION_RETURN = "RETURN";
    /** 日志动作: 过期 */
    private static final String ACTION_EXPIRE = "EXPIRE";

    /** 默认每人限领 */
    private static final int DEFAULT_PER_USER_LIMIT = 1;
    /** 默认使用门槛 */
    private static final double DEFAULT_THRESHOLD_AMOUNT = 0d;
    /** 券码长度 (去掉横线的 UUID + 随机后缀) */
    private static final int CODE_RANDOM_SUFFIX_BOUND = 10000;

    /** 优惠券模板数据访问层 */
    private final ScrmCouponTemplateRepository templateRepository;
    /** 优惠券实例数据访问层 */
    private final ScrmCouponRepository couponRepository;
    /** 优惠券使用日志数据访问层 */
    private final ScrmCouponUsageLogRepository usageLogRepository;
    /** 客户数据访问层 (解析客户名称) */
    private final ScrmCustomerRepository customerRepository;

    // ============================================================
    // 模板管理
    // ============================================================

    /**
     * 创建优惠券模板。
     * <p>校验参数合法性后写入归属账号 ID 持久化, 状态缺省 ACTIVE, 计数字段缺省 0。</p>
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmCouponTemplateDto createTemplate(ScrmCouponTemplateDto dto) throws ScrmException {
        validateTemplateDto(dto, false);
        ScrmCouponTemplateEntity entity = new ScrmCouponTemplateEntity();
        entity.setTemplateName(dto.getTemplateName());
        entity.setCouponType(dto.getCouponType());
        entity.setFaceValue(dto.getFaceValue());
        entity.setThresholdAmount(
                dto.getThresholdAmount() != null ? dto.getThresholdAmount() : DEFAULT_THRESHOLD_AMOUNT);
        entity.setDiscountLimit(dto.getDiscountLimit());
        entity.setValidType(dto.getValidType());
        entity.setValidStart(dto.getValidStart());
        entity.setValidEnd(dto.getValidEnd());
        entity.setValidDays(dto.getValidDays());
        entity.setTotalQuantity(dto.getTotalQuantity());
        entity.setIssuedQuantity(0);
        entity.setUsedQuantity(0);
        entity.setClaimedQuantity(0);
        entity.setPerUserLimit(dto.getPerUserLimit() != null ? dto.getPerUserLimit() : DEFAULT_PER_USER_LIMIT);
        entity.setApplicableProducts(dto.getApplicableProducts());
        entity.setApplicableScenes(dto.getApplicableScenes());
        entity.setDescription(dto.getDescription());
        entity.setRules(dto.getRules());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : TEMPLATE_STATUS_ACTIVE);
        entity.setCreatedBy(dto.getCreatedBy());
        // 固定有效期模板: 若结束日期早于今天, 直接置为已过期
        if (VALID_TYPE_FIXED.equals(entity.getValidType()) && entity.getValidEnd() != null && entity.getValidEnd().isBefore(java.time.LocalDate.now())) {
            entity.setStatus(TEMPLATE_STATUS_EXPIRED);
        }
        entity = templateRepository.save(entity);
        log.info("创建优惠券模板: id={}, templateName={}, couponType={}",
                entity.getId(), entity.getTemplateName(), entity.getCouponType());
        return toTemplateDto(entity);
    }

    /**
     * 更新优惠券模板（字段非空才覆盖）。
     * <p>已发放量大于 0 时不允许修改优惠券类型与面值, 避免与已发行券实例不一致。</p>
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 参数非法 / 已发行券不允许修改关键参数
     */
    @Transactional
    public ScrmCouponTemplateDto updateTemplate(Long id, ScrmCouponTemplateDto dto) throws ScrmException {
        ScrmCouponTemplateEntity entity = findTemplateOrThrow(id);
        validateTemplateDto(dto, true);
        boolean hasIssued = (entity.getIssuedQuantity() != null && entity.getIssuedQuantity() > 0);
        if (hasIssued) {
            if (dto.getCouponType() != null && !dto.getCouponType().equals(entity.getCouponType())) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "模板已发行券, 不允许修改优惠券类型: id=" + id);
            }
            if (dto.getFaceValue() != null && !dto.getFaceValue().equals(entity.getFaceValue())) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "模板已发行券, 不允许修改面值: id=" + id);
            }
        }
        if (dto.getTemplateName() != null) entity.setTemplateName(dto.getTemplateName());
        if (dto.getCouponType() != null) entity.setCouponType(dto.getCouponType());
        if (dto.getFaceValue() != null) entity.setFaceValue(dto.getFaceValue());
        if (dto.getThresholdAmount() != null) entity.setThresholdAmount(dto.getThresholdAmount());
        if (dto.getDiscountLimit() != null) entity.setDiscountLimit(dto.getDiscountLimit());
        if (dto.getValidType() != null) entity.setValidType(dto.getValidType());
        if (dto.getValidStart() != null) entity.setValidStart(dto.getValidStart());
        if (dto.getValidEnd() != null) entity.setValidEnd(dto.getValidEnd());
        if (dto.getValidDays() != null) entity.setValidDays(dto.getValidDays());
        if (dto.getTotalQuantity() != null) entity.setTotalQuantity(dto.getTotalQuantity());
        if (dto.getPerUserLimit() != null) entity.setPerUserLimit(dto.getPerUserLimit());
        if (dto.getApplicableProducts() != null) entity.setApplicableProducts(dto.getApplicableProducts());
        if (dto.getApplicableScenes() != null) entity.setApplicableScenes(dto.getApplicableScenes());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getRules() != null) entity.setRules(dto.getRules());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = templateRepository.save(entity);
        log.info("更新优惠券模板: id={}", id);
        return toTemplateDto(entity);
    }

    /**
     * 删除优惠券模板。
     * <p>已有券实例的模板不允许删除 (避免券实例悬空引用), 请先停用模板。</p>
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在 / 已发行券不允许删除
     */
    @Transactional
    public void deleteTemplate(Long id) throws ScrmException {
        ScrmCouponTemplateEntity entity = findTemplateOrThrow(id);
        long couponCount = couponRepository.countByTemplateIdAndStatus(
                 id, COUPON_STATUS_UNUSED);
        long usedCount = couponRepository.countByTemplateIdAndStatus(
                 id, COUPON_STATUS_USED);
        if (couponCount > 0 || usedCount > 0) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "模板已发行优惠券, 不允许删除, 请先停用: id=" + id);
        }
        templateRepository.delete(entity);
        log.info("删除优惠券模板: id={}", id);
    }

    /**
     * 查询优惠券模板详情。
     *
     * @param id 模板 ID
     * @return 模板 DTO
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public ScrmCouponTemplateDto getTemplate(Long id) throws ScrmException {
        return toTemplateDto(findTemplateOrThrow(id));
    }

    /**
     * 分页查询优惠券模板, 支持按优惠券类型、状态与关键词过滤。
     *
     * @param couponType 优惠券类型过滤 (可空)
     * @param status     状态过滤 (可空)
     * @param keyword    关键词过滤, 匹配模板名称 (可空)
     * @param pageable   分页参数
     * @return 模板分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCouponTemplateDto> listTemplates(String couponType, String status, String keyword,
                                                      Pageable pageable) {
        Specification<ScrmCouponTemplateEntity> spec = buildTemplateSpec(couponType, status, keyword);
        return templateRepository.findAll(spec, pageable).map(this::toTemplateDto);
    }

    /**
     * 启用优惠券模板 (状态置 ACTIVE)。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public ScrmCouponTemplateDto activateTemplate(Long id) throws ScrmException {
        ScrmCouponTemplateEntity entity = findTemplateOrThrow(id);
        entity.setStatus(TEMPLATE_STATUS_ACTIVE);
        entity = templateRepository.save(entity);
        log.info("启用优惠券模板: id={}", id);
        return toTemplateDto(entity);
    }

    /**
     * 停用优惠券模板 (状态置 INACTIVE), 已发放券不受影响。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public ScrmCouponTemplateDto deactivateTemplate(Long id) throws ScrmException {
        ScrmCouponTemplateEntity entity = findTemplateOrThrow(id);
        entity.setStatus(TEMPLATE_STATUS_INACTIVE);
        entity = templateRepository.save(entity);
        log.info("停用优惠券模板: id={}", id);
        return toTemplateDto(entity);
    }

    /**
     * 模板统计: 发放/领取/使用/过期数。
     *
     * @param id 模板 ID
     * @return 统计结果
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTemplateStats(Long id) throws ScrmException {
        ScrmCouponTemplateEntity entity = findTemplateOrThrow(id);
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("templateId", entity.getId());
        stats.put("templateName", entity.getTemplateName());
        stats.put("totalQuantity", entity.getTotalQuantity());
        stats.put("issuedQuantity", entity.getIssuedQuantity());
        stats.put("claimedQuantity", entity.getClaimedQuantity());
        stats.put("usedQuantity", entity.getUsedQuantity());
        stats.put("unusedCount",
                couponRepository.countByTemplateIdAndStatus(id, COUPON_STATUS_UNUSED));
        stats.put("usedCount",
                couponRepository.countByTemplateIdAndStatus(id, COUPON_STATUS_USED));
        stats.put("expiredCount",
                couponRepository.countByTemplateIdAndStatus(id, COUPON_STATUS_EXPIRED));
        stats.put("returnedCount",
                couponRepository.countByTemplateIdAndStatus(id, COUPON_STATUS_RETURNED));
        stats.put("totalDiscountAmount", couponRepository.sumUsedAmountByTemplate(id, null, null));
        return stats;
    }

    // ============================================================
    // 发券
    // ============================================================

    /**
     * 批量发券: 生成券码 → 分配客户 → 记录日志。
     * <p>
     * customerIds 非空时, 为每个客户发放 issueCount 张券 (claimSource=MASS_SEND, 直接归属客户);
     * customerIds 为空时, 发放 issueCount 张未归属券 (customer_id 为空, 待领取)。
     * 发放后更新模板已发放量, 若达到总发行量则置 SOLD_OUT。
     * </p>
     *
     * @param issueDto 发券请求
     * @return 发放的优惠券列表
     * @throws ScrmException 模板不存在 / 模板未启用 / 库存不足
     */
    @Transactional
    public List<ScrmCouponDto> issueCoupons(ScrmCouponIssueDto issueDto) throws ScrmException {
        return doIssue(issueDto.getTemplateId(), issueDto.getCustomerIds(),
                issueDto.getIssueCount(), SOURCE_MASS_SEND, issueDto.getIssuedBy(), issueDto.getNotes());
    }

    /**
     * 发券给单客户 (claimSource=MANUAL)。
     *
     * @param templateId 模板 ID
     * @param customerId 客户 ID
     * @param count      发放数量
     * @return 发放的优惠券列表
     * @throws ScrmException 模板不存在 / 模板未启用 / 库存不足
     */
    @Transactional
    public List<ScrmCouponDto> issueToCustomer(Long templateId, Long customerId, int count)
            throws ScrmException {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (count <= 0) {
            throw ScrmException.badRequest("发放数量必须大于 0");
        }
        return doIssue(templateId, java.util.Collections.singletonList(customerId),
                count, SOURCE_MANUAL, null, null);
    }

    /**
     * 生成唯一优惠券码 (UUID 去横线 + 时间戳后 4 位 + 随机数, 大写)。
     *
     * @return 优惠券码
     */
    public String generateCouponCode() {
        String uuid = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        long suffix = System.currentTimeMillis() % 10000L * 100L
                + ThreadLocalRandom.current().nextInt(CODE_RANDOM_SUFFIX_BOUND);
        return uuid + String.format("%08d", suffix);
    }

    // ============================================================
    // 领取 / 使用 / 退还 / 过期
    // ============================================================

    /**
     * 领取优惠券: 将未归属券 (customer_id 为空) 分配给客户。
     * <p>校验券状态为 UNUSED 且未归属, 校验客户每人限领数量, 写入领取时间与客户信息,
     * 累加模板已领取量并记录 CLAIM 日志。</p>
     *
     * @param couponCode 优惠券码
     * @param customerId 客户 ID
     * @return 更新后的优惠券
     * @throws ScrmException 优惠券不存在 / 状态非法 / 已被领取 / 超出每人限领
     */
    @Transactional
    public ScrmCouponDto claimCoupon(String couponCode, Long customerId) throws ScrmException {
        if (couponCode == null || couponCode.isBlank()) {
            throw ScrmException.badRequest("优惠券码不能为空");
        }
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        ScrmCouponEntity coupon = findCouponByCodeOrThrow(couponCode);
        if (!COUPON_STATUS_UNUSED.equals(coupon.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "优惠券状态非法, 仅 UNUSED 可领取: currentStatus=" + coupon.getStatus());
        }
        if (coupon.getCustomerId() != null) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "优惠券已被领取: couponCode=" + couponCode);
        }
        ScrmCouponTemplateEntity template = findTemplateOrThrow(coupon.getTemplateId());
        // 每人限领校验: 统计客户已持有的未使用与已使用券数量
        long held = couponRepository.findByTemplateIdAndCustomerIdAndStatusIn(
                 template.getId(), customerId,
                java.util.List.of(COUPON_STATUS_UNUSED, COUPON_STATUS_USED)).size();
        int limit = template.getPerUserLimit() != null ? template.getPerUserLimit() : DEFAULT_PER_USER_LIMIT;
        if (held >= limit) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "超出每人限领数量: limit=" + limit);
        }
        String customerName = resolveCustomerName(customerId);
        LocalDateTime now = LocalDateTime.now();
        coupon.setCustomerId(customerId);
        coupon.setCustomerName(customerName);
        coupon.setClaimedAt(now);
        // RELATIVE 有效期: 领取后重新计算过期时间
        if (VALID_TYPE_RELATIVE.equals(template.getValidType()) && template.getValidDays() != null) {
            coupon.setExpiresAt(now.plusDays(template.getValidDays()));
        }
        coupon = couponRepository.save(coupon);
        template.setClaimedQuantity((template.getClaimedQuantity() == null ? 0 : template.getClaimedQuantity()) + 1);
        templateRepository.save(template);
        recordLog(coupon, template, ACTION_CLAIM, now, null, null, null, null,
                "客户领取优惠券: customerId=" + customerId);
        log.info("领取优惠券: couponId={}, customerId={}", coupon.getId(), customerId);
        return toCouponDto(coupon);
    }

    /**
     * 使用优惠券: 校验 → 计算抵扣 → 标记已用 → 记录日志。
     * <p>校验券状态为 UNUSED、未过期、已达使用门槛且已归属客户, 按模板类型计算实际抵扣金额,
     * 标记 USED 并写入使用订单号与抵扣金额, 累加模板已使用量并记录 USE 日志。</p>
     *
     * @param useDto 使用请求 (券码 + 订单号 + 订单金额)
     * @return 更新后的优惠券
     * @throws ScrmException 优惠券不存在 / 状态非法 / 已过期 / 未达使用门槛 / 未领取
     */
    @Transactional
    public ScrmCouponDto useCoupon(ScrmCouponUseDto useDto) throws ScrmException {
        if (useDto == null || useDto.getCouponCode() == null || useDto.getCouponCode().isBlank()) {
            throw ScrmException.badRequest("优惠券码不能为空");
        }
        if (useDto.getOrderId() == null || useDto.getOrderId().isBlank()) {
            throw ScrmException.badRequest("订单号不能为空");
        }
        if (useDto.getOrderAmount() == null) {
            throw ScrmException.badRequest("订单金额不能为空");
        }
        ScrmCouponEntity coupon = findCouponByCodeOrThrow(useDto.getCouponCode());
        if (!COUPON_STATUS_UNUSED.equals(coupon.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "优惠券状态非法, 仅 UNUSED 可使用: currentStatus=" + coupon.getStatus());
        }
        if (coupon.getCustomerId() == null) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "优惠券尚未被领取, 无法使用: couponCode=" + useDto.getCouponCode());
        }
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getExpiresAt() != null && coupon.getExpiresAt().isBefore(now)) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "优惠券已过期: expiresAt=" + coupon.getExpiresAt());
        }
        ScrmCouponTemplateEntity template = findTemplateOrThrow(coupon.getTemplateId());
        double threshold = template.getThresholdAmount() != null ? template.getThresholdAmount()
                : DEFAULT_THRESHOLD_AMOUNT;
        if (useDto.getOrderAmount() < threshold) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "订单金额未达使用门槛: orderAmount=" + useDto.getOrderAmount() + ", threshold=" + threshold);
        }
        double discount = calculateDiscount(template, useDto.getOrderAmount());
        coupon.setStatus(COUPON_STATUS_USED);
        coupon.setUsedAt(now);
        coupon.setUsedOrder(useDto.getOrderId());
        coupon.setUsedAmount(discount);
        coupon = couponRepository.save(coupon);
        template.setUsedQuantity((template.getUsedQuantity() == null ? 0 : template.getUsedQuantity()) + 1);
        templateRepository.save(template);
        recordLog(coupon, template, ACTION_USE, now, useDto.getOperatorId(), useDto.getOperatorName(),
                useDto.getOrderAmount(), discount, "核销优惠券: orderId=" + useDto.getOrderId());
        log.info("使用优惠券: couponId={}, orderId={}, discount={}", coupon.getId(), useDto.getOrderId(), discount);
        return toCouponDto(coupon);
    }

    /**
     * 退还优惠券 (状态置 RETURNED)。
     * <p>仅 USED 状态可退还, 退还后扣减模板已使用量并记录 RETURN 日志, 券可重新核销。</p>
     *
     * @param couponId 优惠券 ID
     * @param reason   退还原因 (可空)
     * @return 更新后的优惠券
     * @throws ScrmException 优惠券不存在 / 状态非法
     */
    @Transactional
    public ScrmCouponDto returnCoupon(Long couponId, String reason) throws ScrmException {
        ScrmCouponEntity coupon = findCouponOrThrow(couponId);
        if (!COUPON_STATUS_USED.equals(coupon.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "优惠券状态非法, 仅 USED 可退还: currentStatus=" + coupon.getStatus());
        }
        ScrmCouponTemplateEntity template = findTemplateOrThrow(coupon.getTemplateId());
        LocalDateTime now = LocalDateTime.now();
        Double refundAmount = coupon.getUsedAmount();
        coupon.setStatus(COUPON_STATUS_RETURNED);
        coupon = couponRepository.save(coupon);
        // 扣减已使用量
        int used = (template.getUsedQuantity() == null ? 0 : template.getUsedQuantity());
        if (used > 0) {
            template.setUsedQuantity(used - 1);
            templateRepository.save(template);
        }
        recordLog(coupon, template, ACTION_RETURN, now, null, null,
                null, refundAmount, "退还优惠券: reason=" + reason);
        log.info("退还优惠券: couponId={}, reason={}", couponId, reason);
        return toCouponDto(coupon);
    }

    /**
     * 过期处理 (定时任务): 将过期未使用券置为 EXPIRED。
     * <p>扫描当前账号下 status=UNUSED 且 expiresAt 早于当前时间的优惠券, 批量置为 EXPIRED
     * 并记录 EXPIRE 日志。</p>
     *
     * @return 过期处理的优惠券数量
     */
    @Transactional
    public int expireCoupons() {
        LocalDateTime now = LocalDateTime.now();
        List<ScrmCouponEntity> expired = couponRepository.findByStatusAndExpiresAtBefore(
                 COUPON_STATUS_UNUSED, now);
        if (expired.isEmpty()) {
            return 0;
        }
        // 按模板分组累加过期数, 减少模板更新次数
        Map<Long, Integer> templateExpiredMap = new java.util.HashMap<>();
        for (ScrmCouponEntity coupon : expired) {
            coupon.setStatus(COUPON_STATUS_EXPIRED);
            couponRepository.save(coupon);
            templateExpiredMap.merge(coupon.getTemplateId(), 1, Integer::sum);
            recordLog(coupon, null, ACTION_EXPIRE, now, null, null,
                    null, null, "优惠券过期处理");
        }
        // 模板已领取量扣减过期数 (过期券不再计入有效领取)
        for (Map.Entry<Long, Integer> entry : templateExpiredMap.entrySet()) {
            templateRepository.findById(entry.getKey()).ifPresent(t -> {
                int claimed = (t.getClaimedQuantity() == null ? 0 : t.getClaimedQuantity());
                t.setClaimedQuantity(Math.max(0, claimed - entry.getValue()));
                templateRepository.save(t);
            });
        }
        log.info("优惠券过期处理:, expiredCount={}", expired.size());
        return expired.size();
    }

    // ============================================================
    // 优惠券查询
    // ============================================================

    /**
     * 查询优惠券详情。
     *
     * @param id 优惠券 ID
     * @return 优惠券 DTO
     * @throws ScrmException 优惠券不存在
     */
    @Transactional(readOnly = true)
    public ScrmCouponDto getCoupon(Long id) throws ScrmException {
        return toCouponDto(findCouponOrThrow(id));
    }

    /**
     * 按券码查询优惠券。
     *
     * @param code 优惠券码
     * @return 优惠券 DTO
     * @throws ScrmException 优惠券不存在
     */
    @Transactional(readOnly = true)
    public ScrmCouponDto getCouponByCode(String code) throws ScrmException {
        return toCouponDto(findCouponByCodeOrThrow(code));
    }

    /**
     * 分页查询优惠券, 支持按模板、客户、状态与时间范围过滤。
     *
     * @param templateId 模板 ID 过滤 (可空)
     * @param customerId 客户 ID 过滤 (可空)
     * @param status     状态过滤 (可空)
     * @param startTime  起始时间 (按创建时间, 可空)
     * @param endTime    截止时间 (按创建时间, 可空)
     * @param pageable   分页参数
     * @return 优惠券分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCouponDto> listCoupons(Long templateId, Long customerId, String status,
                                            LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        Specification<ScrmCouponEntity> spec = buildCouponSpec(templateId, customerId, status, startTime, endTime);
        return couponRepository.findAll(spec, pageable).map(this::toCouponDto);
    }

    /**
     * 查询客户优惠券列表, 支持按状态过滤。
     *
     * @param customerId 客户 ID
     * @param status     状态过滤 (可空)
     * @return 优惠券列表
     */
    @Transactional(readOnly = true)
    public List<ScrmCouponDto> getCustomerCoupons(Long customerId, String status) {
        Specification<ScrmCouponEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("customerId"), customerId));
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return couponRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createTime")).stream()
                .map(this::toCouponDto)
                .collect(Collectors.toList());
    }

    /**
     * 分页查询优惠券使用日志, 支持按券 ID、模板 ID 与动作类型过滤。
     *
     * @param couponId   优惠券 ID 过滤 (可空)
     * @param templateId 模板 ID 过滤 (可空)
     * @param actionType 动作类型过滤 (可空)
     * @param pageable   分页参数
     * @return 使用日志分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCouponUsageLogDto> getUsageLogs(Long couponId, Long templateId, String actionType,
                                                     Pageable pageable) {
        Specification<ScrmCouponUsageLogEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (couponId != null) {
                predicates.add(cb.equal(root.get("couponId"), couponId));
            }
            if (templateId != null) {
                predicates.add(cb.equal(root.get("templateId"), templateId));
            }
            if (actionType != null && !actionType.isBlank()) {
                predicates.add(cb.equal(root.get("actionType"), actionType));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return usageLogRepository.findAll(spec, pageable).map(this::toUsageLogDto);
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 优惠券统计: 发放率 / 使用率 / 核销率 / 总抵扣金额。
     * <p>时间范围按优惠券创建时间过滤, 为空时统计全量。</p>
     *
     * @param templateId 模板 ID
     * @param startTime  起始时间 (可空)
     * @param endTime    截止时间 (可空)
     * @return 统计结果
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCouponStats(Long templateId, LocalDateTime startTime, LocalDateTime endTime)
            throws ScrmException {
        ScrmCouponTemplateEntity template = findTemplateOrThrow(templateId);
        long issuedCount = countCoupon(templateId, null, startTime, endTime);
        long usedCount = countCoupon(templateId, COUPON_STATUS_USED, startTime, endTime);
        long claimedCount = countCoupon(templateId, null, startTime, endTime);
        long expiredCount = countCoupon(templateId, COUPON_STATUS_EXPIRED, startTime, endTime);
        double totalDiscount = couponRepository.sumUsedAmountByTemplate(
                 templateId, startTime, endTime);
        int totalQuantity = template.getTotalQuantity() != null ? template.getTotalQuantity() : 0;
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("templateId", template.getId());
        stats.put("templateName", template.getTemplateName());
        stats.put("totalQuantity", totalQuantity);
        stats.put("issuedCount", issuedCount);
        stats.put("claimedCount", claimedCount);
        stats.put("usedCount", usedCount);
        stats.put("expiredCount", expiredCount);
        stats.put("totalDiscountAmount", totalDiscount);
        stats.put("issueRate", totalQuantity > 0 ? round(issuedCount * 100d / totalQuantity) : 0d);
        stats.put("usageRate", issuedCount > 0 ? round(usedCount * 100d / issuedCount) : 0d);
        stats.put("verificationRate", claimedCount > 0 ? round(usedCount * 100d / claimedCount) : 0d);
        return stats;
    }

    /**
     * 客户优惠券统计: 各状态持有数量。
     *
     * @param customerId 客户 ID
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCustomerCouponStats(Long customerId) {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("customerId", customerId);
        stats.put("unusedCount",
                couponRepository.countByCustomerIdAndStatus(customerId, COUPON_STATUS_UNUSED));
        stats.put("usedCount",
                couponRepository.countByCustomerIdAndStatus(customerId, COUPON_STATUS_USED));
        stats.put("expiredCount",
                couponRepository.countByCustomerIdAndStatus(customerId, COUPON_STATUS_EXPIRED));
        stats.put("returnedCount",
                couponRepository.countByCustomerIdAndStatus(customerId, COUPON_STATUS_RETURNED));
        return stats;
    }

    /**
     * 计算抵扣金额。
     * <p>按优惠券模板类型计算:
     * <ul>
     *   <li>DISCOUNT: 订单金额 × (1 - 折扣率), 受折扣上限约束</li>
     *   <li>FIXED_AMOUNT / CASH_VOUCHER: 面值, 不超过订单金额</li>
     *   <li>EXCHANGE: 全额抵扣 (订单金额)</li>
     *   <li>GIFT: 0 (赠品无金额抵扣)</li>
     * </ul>
     * 调用方需自行校验使用门槛, 本方法仅计算抵扣金额。</p>
     *
     * @param coupon      优惠券实例
     * @param orderAmount 订单金额
     * @return 抵扣金额
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public double calculateDiscount(ScrmCouponEntity coupon, Double orderAmount) throws ScrmException {
        if (coupon == null || orderAmount == null) {
            return 0d;
        }
        ScrmCouponTemplateEntity template = findTemplateOrThrow(coupon.getTemplateId());
        return calculateDiscount(template, orderAmount);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 发券内部实现。
     *
     * @param templateId  模板 ID
     * @param customerIds 客户 ID 列表 (可空, 为空发放未归属券)
     * @param issueCount  每客户发放数 (customerIds 非空) 或总发放数 (customerIds 为空)
     * @param source      领取来源
     * @param issuedBy    发放人
     * @param notes       备注
     * @return 发放的优惠券列表
     * @throws ScrmException 模板不存在 / 模板未启用 / 库存不足
     */
    private List<ScrmCouponDto> doIssue(Long templateId, List<Long> customerIds, int issueCount,
                                         String source, String issuedBy, String notes) throws ScrmException {
        if (templateId == null) {
            throw ScrmException.badRequest("模板 ID 不能为空");
        }
        if (issueCount <= 0) {
            throw ScrmException.badRequest("发放数量必须大于 0");
        }
        ScrmCouponTemplateEntity template = findTemplateOrThrow(templateId);
        if (!TEMPLATE_STATUS_ACTIVE.equals(template.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "模板未启用, 不允许发券: status=" + template.getStatus());
        }
        boolean hasCustomers = customerIds != null && !customerIds.isEmpty();
        int total = hasCustomers ? customerIds.size() * issueCount : issueCount;
        int issued = template.getIssuedQuantity() != null ? template.getIssuedQuantity() : 0;
        int totalQuantity = template.getTotalQuantity() != null ? template.getTotalQuantity() : 0;
        if (issued + total > totalQuantity) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "库存不足: remaining=" + (totalQuantity - issued) + ", requested=" + total);
        }
        LocalDateTime now = LocalDateTime.now();
        List<ScrmCouponEntity> coupons = new ArrayList<>();
        if (hasCustomers) {
            for (Long customerId : customerIds) {
                if (customerId == null) {
                    continue;
                }
                String customerName = resolveCustomerName(customerId);
                for (int i = 0; i < issueCount; i++) {
                    coupons.add(buildCouponEntity(template, customerId, customerName, source, issuedBy, notes, now));
                }
            }
        } else {
            for (int i = 0; i < issueCount; i++) {
                coupons.add(buildCouponEntity(template, null, null, source, issuedBy, notes, now));
            }
        }
        List<ScrmCouponEntity> saved = couponRepository.saveAll(coupons);
        // 累加模板已发放量与已领取量 (直接归属客户的券计入已领取)
        template.setIssuedQuantity(issued + total);
        if (hasCustomers) {
            int claimed = (template.getClaimedQuantity() == null ? 0 : template.getClaimedQuantity());
            template.setClaimedQuantity(claimed + total);
        }
        // 达到总发行量则置 SOLD_OUT
        if (template.getIssuedQuantity() >= totalQuantity) {
            template.setStatus(TEMPLATE_STATUS_SOLD_OUT);
        }
        templateRepository.save(template);
        // 记录发放日志
        for (ScrmCouponEntity c : saved) {
            recordLog(c, template, ACTION_ISSUE, now, issuedBy, issuedBy,
                    null, null, "批量发券: source=" + source + (notes == null ? "" : ", notes=" + notes));
        }
        log.info("批量发券: templateId={}, count={}, hasCustomers={}", templateId, total, hasCustomers);
        return saved.stream().map(this::toCouponDto).collect(Collectors.toList());
    }

    /**
     * 构建优惠券实例实体 (生成唯一券码, 计算过期时间)。
     */
    private ScrmCouponEntity buildCouponEntity(ScrmCouponTemplateEntity template, Long customerId,
                                                String customerName, String source, String issuedBy,
                                                String notes, LocalDateTime now) {
        ScrmCouponEntity coupon = new ScrmCouponEntity();
        coupon.setTemplateId(template.getId());
        coupon.setCouponCode(generateUniqueCouponCode());
        coupon.setCustomerId(customerId);
        coupon.setCustomerName(customerName);
        coupon.setClaimSource(source);
        coupon.setClaimedAt(customerId != null ? now : null);
        coupon.setExpiresAt(calcExpiresAt(template, now));
        coupon.setStatus(COUPON_STATUS_UNUSED);
        coupon.setIssuedBy(issuedBy);
        coupon.setNotes(notes);
        return coupon;
    }

    /**
     * 生成唯一券码 (冲突重试)。
     */
    private String generateUniqueCouponCode() {
        for (int i = 0; i < 5; i++) {
            String code = generateCouponCode();
            if (couponRepository.findByCouponCode(code).isEmpty()) {
                return code;
            }
        }
        // 兜底: UUID + 当前纳秒
        return UUID.randomUUID().toString().replace("-", "").toUpperCase()
                + Long.toHexString(System.nanoTime());
    }

    /**
     * 计算优惠券过期时间。
     * <p>FIXED: 固定结束日期的当天 23:59:59; RELATIVE: 领取/发放时间 + validDays 天。</p>
     */
    private LocalDateTime calcExpiresAt(ScrmCouponTemplateEntity template, LocalDateTime base) {
        if (VALID_TYPE_FIXED.equals(template.getValidType())) {
            if (template.getValidEnd() != null) {
                return template.getValidEnd().atTime(23, 59, 59);
            }
            // 固定有效期缺省结束日期时, 默认 30 天后过期
            return base.plusDays(30);
        }
        // RELATIVE
        int days = template.getValidDays() != null ? template.getValidDays() : 30;
        return base.plusDays(days);
    }

    /**
     * 计算抵扣金额 (基于模板)。
     */
    private double calculateDiscount(ScrmCouponTemplateEntity template, double orderAmount) {
        double faceValue = template.getFaceValue() != null ? template.getFaceValue() : 0d;
        switch (template.getCouponType()) {
            case COUPON_TYPE_DISCOUNT: {
                // faceValue 为折扣率 0~1, 抵扣 = 订单金额 × (1 - 折扣率)
                double discount = orderAmount * (1 - faceValue);
                if (template.getDiscountLimit() != null && discount > template.getDiscountLimit()) {
                    discount = template.getDiscountLimit();
                }
                return round(discount);
            }
            case COUPON_TYPE_FIXED_AMOUNT:
            case COUPON_TYPE_CASH_VOUCHER: {
                // 面值抵扣, 不超过订单金额
                return round(Math.min(faceValue, orderAmount));
            }
            case COUPON_TYPE_EXCHANGE: {
                // 兑换券全额抵扣
                return round(orderAmount);
            }
            case COUPON_TYPE_GIFT:
            default:
                // 赠品券无金额抵扣
                return 0d;
        }
    }

    /**
     * 按模板 + 状态 + 时间范围统计优惠券数量 (claimedOnly=true 时统计已领取即 UNUSED+USED)。
     */
    private long countCoupon(Long templateId, String status, LocalDateTime startTime, LocalDateTime endTime) {
        return countCoupon(templateId, status, startTime, endTime, false);
    }

    /**
     * 按模板 + 状态 + 时间范围统计优惠券数量。
     *
     * @param claimedOnly true 时统计已领取 (状态为 UNUSED 或 USED), 此时 status 参数忽略
     */
    private long countCoupon(Long templateId, String status, LocalDateTime startTime, LocalDateTime endTime,
                             boolean claimedOnly) {
        Specification<ScrmCouponEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("templateId"), templateId));
            if (claimedOnly) {
                predicates.add(root.get("status").in(java.util.List.of(COUPON_STATUS_UNUSED, COUPON_STATUS_USED)));
            } else if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return couponRepository.count(spec);
    }

    /**
     * 记录使用日志。
     */
    private void recordLog(ScrmCouponEntity coupon, ScrmCouponTemplateEntity template, String action,
                           LocalDateTime actionTime, String operatorId, String operatorName,
                           Double orderAmount, Double discountAmount, String detail) {
        ScrmCouponUsageLogEntity logEntity = new ScrmCouponUsageLogEntity();
        logEntity.setCouponId(coupon.getId());
        logEntity.setTemplateId(coupon.getTemplateId());
        logEntity.setCustomerId(coupon.getCustomerId());
        logEntity.setCustomerName(coupon.getCustomerName());
        logEntity.setActionType(action);
        logEntity.setActionTime(actionTime);
        logEntity.setOperatorId(operatorId);
        logEntity.setOperatorName(operatorName);
        logEntity.setOrderAmount(orderAmount);
        logEntity.setDiscountAmount(discountAmount);
        logEntity.setDetail(detail);
        usageLogRepository.save(logEntity);
    }

    /**
     * 解析客户名称 (从客户实体 nickname 字段), 客户不存在时返回 null。
     */
    private String resolveCustomerName(Long customerId) {
        if (customerId == null) {
            return null;
        }
        return customerRepository.findById(customerId)

                .map(ScrmCustomerEntity::getNickname)
                .orElse(null);
    }

    /**
     * 构建模板查询条件 Specification。
     */
    private Specification<ScrmCouponTemplateEntity> buildTemplateSpec(
            String couponType, String status, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (couponType != null && !couponType.isBlank()) {
                predicates.add(cb.equal(root.get("couponType"), couponType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("templateName")),
                        "%" + keyword.toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 构建优惠券查询条件 Specification。
     */
    private Specification<ScrmCouponEntity> buildCouponSpec(Long templateId, Long customerId, String status,
                                                             LocalDateTime startTime, LocalDateTime endTime) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (templateId != null) {
                predicates.add(cb.equal(root.get("templateId"), templateId));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 校验模板参数。
     *
     * @param dto     模板参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateTemplateDto(ScrmCouponTemplateDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("模板参数不能为空");
        }
        if (!partial) {
            if (dto.getTemplateName() == null || dto.getTemplateName().isBlank()) {
                throw ScrmException.badRequest("模板名称不能为空");
            }
            if (dto.getCouponType() == null || dto.getCouponType().isBlank()) {
                throw ScrmException.badRequest("优惠券类型不能为空");
            }
            if (dto.getFaceValue() == null) {
                throw ScrmException.badRequest("面值不能为空");
            }
            if (dto.getValidType() == null || dto.getValidType().isBlank()) {
                throw ScrmException.badRequest("有效期类型不能为空");
            }
            if (dto.getTotalQuantity() == null || dto.getTotalQuantity() <= 0) {
                throw ScrmException.badRequest("总发行量必须为正数");
            }
        }
        // FIXED 有效期需提供结束日期
        if (VALID_TYPE_FIXED.equals(dto.getValidType()) && dto.getValidEnd() == null && !partial) {
            throw ScrmException.badRequest("固定有效期类型需提供结束日期");
        }
        // RELATIVE 有效期需提供有效天数
        if (VALID_TYPE_RELATIVE.equals(dto.getValidType()) && (dto.getValidDays() == null || dto.getValidDays() <= 0) && !partial) {
            throw ScrmException.badRequest("相对有效期类型需提供有效天数");
        }
        // DISCOUNT 折扣率范围校验
        if (COUPON_TYPE_DISCOUNT.equals(dto.getCouponType()) && dto.getFaceValue() != null && (dto.getFaceValue() < 0 || dto.getFaceValue() > 1)) {
            throw ScrmException.badRequest("折扣类型面值需为 0~1 的折扣率");
        }
    }

    /**
     * 按主键查询模板, 不存在抛异常
     */
    private ScrmCouponTemplateEntity findTemplateOrThrow(Long id) throws ScrmException {
        ScrmCouponTemplateEntity entity = templateRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "优惠券模板不存在: id=" + id));

        return entity;
    }

    /**
     * 按主键查询优惠券, 不存在抛异常
     */
    private ScrmCouponEntity findCouponOrThrow(Long id) throws ScrmException {
        ScrmCouponEntity entity = couponRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "优惠券不存在: id=" + id));

        return entity;
    }

    /**
     * 按券码查询优惠券, 不存在抛异常
     */
    private ScrmCouponEntity findCouponByCodeOrThrow(String code) throws ScrmException {
        ScrmCouponEntity entity = couponRepository.findByCouponCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "优惠券不存在: couponCode=" + code));

        return entity;
    }

    /**
     * 保留两位小数。
     */
    private double round(double value) {
        return Math.round(value * 100d) / 100d;
    }

    /**
     * 模板实体转 DTO
     */
    private ScrmCouponTemplateDto toTemplateDto(ScrmCouponTemplateEntity entity) {
        ScrmCouponTemplateDto dto = new ScrmCouponTemplateDto();
        dto.setId(entity.getId());
        dto.setTemplateName(entity.getTemplateName());
        dto.setCouponType(entity.getCouponType());
        dto.setFaceValue(entity.getFaceValue());
        dto.setThresholdAmount(entity.getThresholdAmount());
        dto.setDiscountLimit(entity.getDiscountLimit());
        dto.setValidType(entity.getValidType());
        dto.setValidStart(entity.getValidStart());
        dto.setValidEnd(entity.getValidEnd());
        dto.setValidDays(entity.getValidDays());
        dto.setTotalQuantity(entity.getTotalQuantity());
        dto.setIssuedQuantity(entity.getIssuedQuantity());
        dto.setUsedQuantity(entity.getUsedQuantity());
        dto.setClaimedQuantity(entity.getClaimedQuantity());
        dto.setPerUserLimit(entity.getPerUserLimit());
        dto.setApplicableProducts(entity.getApplicableProducts());
        dto.setApplicableScenes(entity.getApplicableScenes());
        dto.setDescription(entity.getDescription());
        dto.setRules(entity.getRules());
        dto.setStatus(entity.getStatus());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 优惠券实体转 DTO
     */
    private ScrmCouponDto toCouponDto(ScrmCouponEntity entity) {
        ScrmCouponDto dto = new ScrmCouponDto();
        dto.setId(entity.getId());
        dto.setTemplateId(entity.getTemplateId());
        dto.setCouponCode(entity.getCouponCode());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setClaimSource(entity.getClaimSource());
        dto.setClaimedAt(entity.getClaimedAt());
        dto.setExpiresAt(entity.getExpiresAt());
        dto.setStatus(entity.getStatus());
        dto.setUsedAt(entity.getUsedAt());
        dto.setUsedOrder(entity.getUsedOrder());
        dto.setUsedAmount(entity.getUsedAmount());
        dto.setIssuedBy(entity.getIssuedBy());
        dto.setNotes(entity.getNotes());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 使用日志实体转 DTO
     */
    private ScrmCouponUsageLogDto toUsageLogDto(ScrmCouponUsageLogEntity entity) {
        ScrmCouponUsageLogDto dto = new ScrmCouponUsageLogDto();
        dto.setId(entity.getId());
        dto.setCouponId(entity.getCouponId());
        dto.setTemplateId(entity.getTemplateId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setActionType(entity.getActionType());
        dto.setActionTime(entity.getActionTime());
        dto.setOperatorId(entity.getOperatorId());
        dto.setOperatorName(entity.getOperatorName());
        dto.setOrderAmount(entity.getOrderAmount());
        dto.setDiscountAmount(entity.getDiscountAmount());
        dto.setDetail(entity.getDetail());
        dto.setCreateTime(entity.getCreateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
