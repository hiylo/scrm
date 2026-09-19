/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMembershipMemberService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCustomerMembershipDto;
import org.hiylo.scrm.dto.ScrmMembershipEnrollDto;
import org.hiylo.scrm.dto.ScrmMembershipUpgradeDto;
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

import java.time.LocalDate;
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
 * SCRM 客户会员档案管理服务。
 * <p>
 * 承载会员生命周期管理: 注册 (生成卡号)、查询 (主键/客户/卡号/分页)、升级/降级、冻结/解冻、
 * 取消/续期、消费更新与升降级检查、到期扫描、进度查询、按等级/即将到期/可升级/降级风险会员查询、
 * 会员卡号生成等。升级/降级为完整实现: 校验阈值 → 更新等级 → 赠送积分 → 记录历史。
 * </p>
 * <p>
 * 校验失败抛出 {@link ScrmException} 携带通用错误码 (NOT_FOUND / BAD_REQUEST / CONFLICT)。
 * 等级相关能力委托 {@link ScrmMembershipTierService}。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmMembershipMemberService {

    // ==================== 会员卡类型 ====================
    /** 卡类型: 标准 */
    private static final String CARD_STANDARD = "STANDARD";
    /** 卡类型: VIP */
    private static final String CARD_VIP = "VIP";
    /** 卡类型: 黑金 */
    private static final String CARD_BLACK_GOLD = "BLACK_GOLD";
    /** 卡类型: 钻石 */
    private static final String CARD_DIAMOND = "DIAMOND";
    /** 卡类型: 自定义 */
    private static final String CARD_CUSTOM = "CUSTOM";

    // ==================== 会员状态 ====================
    /** 会员状态: 活跃 */
    private static final String STATUS_ACTIVE = "ACTIVE";
    /** 会员状态: 冻结 */
    private static final String STATUS_FROZEN = "FROZEN";
    /** 会员状态: 过期 */
    private static final String STATUS_EXPIRED = "EXPIRED";
    /** 会员状态: 取消 */
    private static final String STATUS_CANCELLED = "CANCELLED";
    /** 会员状态: 待定 */
    private static final String STATUS_PENDING = "PENDING";

    // ==================== 升降级类型 (历史记录) ====================
    /** 历史: 升级 */
    private static final String HISTORY_UPGRADE = "UPGRADE";
    /** 历史: 降级 */
    private static final String HISTORY_DOWNGRADE = "DOWNGRADE";

    /** 卡号前缀 */
    private static final String CARD_NO_PREFIX = "VM";
    /** 默认每页条数上限 */
    private static final int DEFAULT_LIMIT = 10;
    /** 默认有效期月 */
    private static final int DEFAULT_VALIDITY_MONTHS = 12;
    /** 默认积分倍数 */
    private static final double DEFAULT_POINT_MULTIPLIER = 1.0;
    /** 卡号日期格式 */
    private static final DateTimeFormatter CARD_NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    /** 合法卡类型集合 */
    private static final Set<String> VALID_CARD_TYPES = new HashSet<>(Arrays.asList(
            CARD_STANDARD, CARD_VIP, CARD_BLACK_GOLD, CARD_DIAMOND, CARD_CUSTOM));
    /** 合法会员状态集合 */
    private static final Set<String> VALID_MEMBERSHIP_STATUSES = new HashSet<>(Arrays.asList(
            STATUS_ACTIVE, STATUS_FROZEN, STATUS_EXPIRED, STATUS_CANCELLED, STATUS_PENDING));
    /** 会员终态状态集合 (不允许再变更) */
    private static final Set<String> MEMBERSHIP_TERMINAL_STATUSES = new HashSet<>(Arrays.asList(
            STATUS_EXPIRED, STATUS_CANCELLED));

    /** 会员等级服务 */
    private final ScrmMembershipTierService tierService;
    /** 客户会员数据访问层 */
    private final ScrmCustomerMembershipRepository membershipRepository;
    /** 会员等级数据访问层 */
    private final ScrmMembershipTierRepository tierRepository;

    /**
     * 注册会员 (生成卡号→设置初始等级→赠送注册积分)。
     * <p>校验客户未注册过会员, 等级有效 (未指定时取最低启用等级), 生成唯一会员卡号,
     * 设置初始等级快照, 赠送注册积分到可用与累计积分, 设置等级到期日 (按等级有效期月),
     * 状态缺省 ACTIVE, 卡类型缺省 STANDARD。</p>
     *
     * @param enrollDto 注册参数
     * @return 创建后的会员
     * @throws ScrmException 参数非法 / 客户已注册 / 等级不存在
     */
    @Transactional
    public ScrmCustomerMembershipDto enroll(ScrmMembershipEnrollDto enrollDto) throws ScrmException {
        if (enrollDto == null) {
            throw ScrmException.badRequest("注册参数不能为空");
        }
        if (enrollDto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (membershipRepository.findByCustomerId(enrollDto.getCustomerId()).isPresent()) {
            throw ScrmException.conflict("客户已注册会员: customerId=" + enrollDto.getCustomerId());
        }
        // 解析初始等级: 指定则校验, 未指定取最低启用等级
        ScrmMembershipTierEntity tier;
        if (enrollDto.getTierId() != null) {
            tier = tierService.findTierOrThrow(enrollDto.getTierId());
            if (!Boolean.TRUE.equals(tier.getEnabled())) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "等级未启用, 不允许注册: tierId=" + tier.getId());
            }
        } else {
            List<ScrmMembershipTierEntity> tiers = tierRepository
                    .findByEnabledOrderByTierLevelAsc(Boolean.TRUE);
            if (tiers.isEmpty()) {
                throw new ScrmException(ScrmExceptionConstants.NOT_FOUND, "无可用会员等级");
            }
            tier = tiers.get(0);
        }
        // 校验卡类型
        String cardType = enrollDto.getMemberCardType() != null ? enrollDto.getMemberCardType() : CARD_STANDARD;
        validateCardType(cardType);
        ScrmCustomerMembershipEntity entity = new ScrmCustomerMembershipEntity();
        entity.setCustomerId(enrollDto.getCustomerId());
        entity.setCustomerName(enrollDto.getCustomerName());
        entity.setTierId(tier.getId());
        entity.setTierName(tier.getTierName());
        entity.setTierLevel(tier.getTierLevel());
        entity.setTierCode(tier.getTierCode());
        entity.setMemberCardNo(generateCardNo());
        entity.setMemberCardType(cardType);
        entity.setMembershipStatus(STATUS_ACTIVE);
        LocalDate today = LocalDate.now();
        entity.setJoinDate(today);
        entity.setCurrentTierDate(today);
        // 等级到期日: 按等级有效期月
        Integer validityMonths = tier.getValidityPeriodMonths() != null
                ? tier.getValidityPeriodMonths() : DEFAULT_VALIDITY_MONTHS;
        entity.setTierExpiryDate(today.plusMonths(validityMonths));
        entity.setTotalSpend(0d);
        entity.setTotalOrders(0);
        // 注册赠送积分
        int signupBonus = tier.getSignupBonusPoints() != null ? tier.getSignupBonusPoints() : 0;
        entity.setTotalPoints(signupBonus);
        entity.setAvailablePoints(signupBonus);
        entity.setSpendInPeriod(0d);
        entity.setOrdersInPeriod(0);
        entity.setPeriodStartDate(today);
        entity.setPeriodEndDate(today.plusMonths(validityMonths));
        entity.setDowngradeRisk(Boolean.FALSE);
        entity.setBenefitsUsedCount(0);
        entity.setBenefitsSavedAmount(0d);
        entity.setLastActivityDate(today);
        entity.setReferralCode(enrollDto.getReferralCode());
        entity.setCreatedBy(enrollDto.getCreatedBy());
        // 计算下一等级进度信息
        populateNextTierInfo(entity, tier);
        entity = membershipRepository.save(entity);
        // 刷新等级统计
        tierService.updateTierStats(tier.getId());
        log.info("注册会员: id={}, customerId={}, cardNo={}", entity.getId(),
                entity.getCustomerId(), entity.getMemberCardNo());
        return toMembershipDto(entity);
    }

    /**
     * 查询会员详情。
     *
     * @param id 会员记录 ID
     * @return 会员 DTO
     * @throws ScrmException 会员不存在
     */
    @Transactional(readOnly = true)
    public ScrmCustomerMembershipDto getMembership(Long id) throws ScrmException {
        return toMembershipDto(findMembershipOrThrow(id));
    }

    /**
     * 按客户 ID 查询会员。
     *
     * @param customerId 客户 ID
     * @return 会员 DTO
     * @throws ScrmException 会员不存在
     */
    @Transactional(readOnly = true)
    public ScrmCustomerMembershipDto getMembershipByCustomer(Long customerId) throws ScrmException {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        ScrmCustomerMembershipEntity entity = membershipRepository
                .findByCustomerId(customerId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "会员不存在: customerId=" + customerId));
        return toMembershipDto(entity);
    }

    /**
     * 按会员卡号查询会员。
     *
     * @param cardNo 会员卡号
     * @return 会员 DTO
     * @throws ScrmException 会员不存在
     */
    @Transactional(readOnly = true)
    public ScrmCustomerMembershipDto getMembershipByCardNo(String cardNo) throws ScrmException {
        if (cardNo == null || cardNo.isBlank()) {
            throw ScrmException.badRequest("会员卡号不能为空");
        }
        ScrmCustomerMembershipEntity entity = membershipRepository.findByMemberCardNo(cardNo)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "会员不存在: cardNo=" + cardNo));

        return toMembershipDto(entity);
    }

    /**
     * 分页查询会员, 支持按等级、状态与关键词过滤。
     *
     * @param tierId  等级过滤 (可空)
     * @param status  状态过滤 (可空)
     * @param keyword 关键词过滤, 匹配客户名称/卡号 (可空)
     * @param pageable 分页参数
     * @return 会员分页结果 (按创建时间倒序)
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerMembershipDto> listMemberships(Long tierId, String status, String keyword,
                                                            Pageable pageable) {
        Specification<ScrmCustomerMembershipEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (tierId != null) {
                predicates.add(cb.equal(root.get("tierId"), tierId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("membershipStatus"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("customerName")), like),
                        cb.like(cb.lower(root.get("memberCardNo")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));
        return membershipRepository.findAll(spec, sorted).map(this::toMembershipDto);
    }

    /**
     * 升级会员 (校验目标等级高于当前等级→更新等级→赠送升级积分→记录历史)。
     * <p>校验会员状态非终态、目标等级有效且等级序号高于当前等级, 更新等级快照,
     * 赠送升级积分到可用与累计积分, 设置当前等级日期与最后升级日, 追加升降级历史,
     * 重新计算下一等级进度, 刷新新旧等级统计。</p>
     *
     * @param upgradeDto 升级参数
     * @return 更新后的会员
     * @throws ScrmException 会员不存在 / 状态非法 / 目标等级不高于当前等级
     */
    @Transactional
    public ScrmCustomerMembershipDto upgrade(ScrmMembershipUpgradeDto upgradeDto) throws ScrmException {
        if (upgradeDto == null || upgradeDto.getMembershipId() == null) {
            throw ScrmException.badRequest("会员记录 ID 不能为空");
        }
        if (upgradeDto.getTargetTierId() == null) {
            throw ScrmException.badRequest("目标等级 ID 不能为空");
        }
        ScrmCustomerMembershipEntity membership = findMembershipOrThrow(upgradeDto.getMembershipId());
        if (MEMBERSHIP_TERMINAL_STATUSES.contains(membership.getMembershipStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "会员已处于终态, 不允许升级: status=" + membership.getMembershipStatus());
        }
        ScrmMembershipTierEntity targetTier = tierService.findTierOrThrow(upgradeDto.getTargetTierId());
        if (!Boolean.TRUE.equals(targetTier.getEnabled())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "目标等级未启用: tierId=" + targetTier.getId());
        }
        if (targetTier.getTierLevel() <= membership.getTierLevel()) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "目标等级序号不高于当前等级, 不允许升级: target=" + targetTier.getTierLevel()
                            + ", current=" + membership.getTierLevel());
        }
        Long fromTierId = membership.getTierId();
        Integer fromTierLevel = membership.getTierLevel();
        String fromTierName = membership.getTierName();
        // 更新等级快照
        membership.setTierId(targetTier.getId());
        membership.setTierName(targetTier.getTierName());
        membership.setTierLevel(targetTier.getTierLevel());
        membership.setTierCode(targetTier.getTierCode());
        LocalDate today = LocalDate.now();
        membership.setCurrentTierDate(today);
        membership.setLastUpgradeDate(today);
        membership.setLastActivityDate(today);
        // 赠送升级积分
        int tierUpBonus = targetTier.getTierUpBonusPoints() != null ? targetTier.getTierUpBonusPoints() : 0;
        if (tierUpBonus > 0) {
            int totalPoints = (membership.getTotalPoints() != null ? membership.getTotalPoints() : 0) + tierUpBonus;
            int availablePoints = (membership.getAvailablePoints() != null ? membership.getAvailablePoints() : 0)
                    + tierUpBonus;
            membership.setTotalPoints(totalPoints);
            membership.setAvailablePoints(availablePoints);
        }
        // 等级到期日刷新
        Integer validityMonths = targetTier.getValidityPeriodMonths() != null
                ? targetTier.getValidityPeriodMonths() : DEFAULT_VALIDITY_MONTHS;
        membership.setTierExpiryDate(today.plusMonths(validityMonths));
        membership.setPeriodEndDate(today.plusMonths(validityMonths));
        // 重置周期内消费与下一等级进度
        membership.setSpendInPeriod(0d);
        membership.setOrdersInPeriod(0);
        membership.setPeriodStartDate(today);
        populateNextTierInfo(membership, targetTier);
        membership.setUpgradeProgress(0d);
        membership.setDowngradeRisk(Boolean.FALSE);
        // 记录升降级历史
        membership.setUpgradeHistory(appendHistoryEntry(membership.getUpgradeHistory(), today,
                fromTierName, targetTier.getTierName(), upgradeDto.getReason(), HISTORY_UPGRADE,
                fromTierId, targetTier.getId(), fromTierLevel, targetTier.getTierLevel()));
        membership = membershipRepository.save(membership);
        // 刷新等级统计
        tierService.updateTierStats(fromTierId);
        tierService.updateTierStats(targetTier.getId());
        log.info("升级会员: id={}, from={}, to={}", membership.getId(), fromTierLevel, targetTier.getTierLevel());
        return toMembershipDto(membership);
    }

    /**
     * 降级会员 (校验目标等级低于当前等级→更新等级→记录历史)。
     * <p>校验会员状态非终态、目标等级有效且等级序号低于当前等级, 更新等级快照,
     * 设置当前等级日期与最后降级日, 追加升降级历史, 重新计算下一等级进度, 刷新新旧等级统计。
     * 降级不扣减已获积分。</p>
     *
     * @param membershipId 会员记录 ID
     * @param reason       降级原因 (可空)
     * @return 更新后的会员
     * @throws ScrmException 会员不存在 / 状态非法 / 目标等级不低于当前等级
     */
    @Transactional
    public ScrmCustomerMembershipDto downgrade(Long membershipId, String reason) throws ScrmException {
        ScrmCustomerMembershipEntity membership = findMembershipOrThrow(membershipId);
        if (MEMBERSHIP_TERMINAL_STATUSES.contains(membership.getMembershipStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "会员已处于终态, 不允许降级: status=" + membership.getMembershipStatus());
        }
        // 取下一更低等级
        List<ScrmMembershipTierEntity> lowers = tierRepository
                .findByTierLevelLessThanOrderByTierLevelDesc(membership.getTierLevel());
        if (lowers.isEmpty()) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "已是最低等级, 不允许降级: tierLevel=" + membership.getTierLevel());
        }
        ScrmMembershipTierEntity targetTier = lowers.get(0);
        Long fromTierId = membership.getTierId();
        Integer fromTierLevel = membership.getTierLevel();
        String fromTierName = membership.getTierName();
        membership.setTierId(targetTier.getId());
        membership.setTierName(targetTier.getTierName());
        membership.setTierLevel(targetTier.getTierLevel());
        membership.setTierCode(targetTier.getTierCode());
        LocalDate today = LocalDate.now();
        membership.setCurrentTierDate(today);
        membership.setLastDowngradeDate(today);
        membership.setLastActivityDate(today);
        Integer validityMonths = targetTier.getValidityPeriodMonths() != null
                ? targetTier.getValidityPeriodMonths() : DEFAULT_VALIDITY_MONTHS;
        membership.setTierExpiryDate(today.plusMonths(validityMonths));
        membership.setPeriodEndDate(today.plusMonths(validityMonths));
        membership.setSpendInPeriod(0d);
        membership.setOrdersInPeriod(0);
        membership.setPeriodStartDate(today);
        populateNextTierInfo(membership, targetTier);
        membership.setDowngradeRisk(Boolean.FALSE);
        membership.setUpgradeHistory(appendHistoryEntry(membership.getUpgradeHistory(), today,
                fromTierName, targetTier.getTierName(), reason, HISTORY_DOWNGRADE,
                fromTierId, targetTier.getId(), fromTierLevel, targetTier.getTierLevel()));
        membership = membershipRepository.save(membership);
        tierService.updateTierStats(fromTierId);
        tierService.updateTierStats(targetTier.getId());
        log.info("降级会员: id={}, from={}, to={}", membership.getId(), fromTierLevel, targetTier.getTierLevel());
        return toMembershipDto(membership);
    }

    /**
     * 冻结会员。
     *
     * @param membershipId 会员记录 ID
     * @param reason       冻结原因 (可空)
     * @return 更新后的会员
     * @throws ScrmException 会员不存在 / 状态非法
     */
    @Transactional
    public ScrmCustomerMembershipDto freeze(Long membershipId, String reason) throws ScrmException {
        ScrmCustomerMembershipEntity membership = findMembershipOrThrow(membershipId);
        if (MEMBERSHIP_TERMINAL_STATUSES.contains(membership.getMembershipStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "会员已处于终态, 不允许冻结: status=" + membership.getMembershipStatus());
        }
        if (STATUS_FROZEN.equals(membership.getMembershipStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "会员已冻结, 不允许重复冻结");
        }
        membership.setMembershipStatus(STATUS_FROZEN);
        if (reason != null && !reason.isBlank()) {
            membership.setNotes(reason);
        }
        membership = membershipRepository.save(membership);
        log.info("冻结会员: id={}, reason={}", membershipId, reason);
        return toMembershipDto(membership);
    }

    /**
     * 解冻会员。
     *
     * @param membershipId 会员记录 ID
     * @return 更新后的会员
     * @throws ScrmException 会员不存在 / 状态非冻结
     */
    @Transactional
    public ScrmCustomerMembershipDto unfreeze(Long membershipId) throws ScrmException {
        ScrmCustomerMembershipEntity membership = findMembershipOrThrow(membershipId);
        if (!STATUS_FROZEN.equals(membership.getMembershipStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "会员状态非冻结, 不允许解冻: status=" + membership.getMembershipStatus());
        }
        membership.setMembershipStatus(STATUS_ACTIVE);
        membership = membershipRepository.save(membership);
        log.info("解冻会员: id={}", membershipId);
        return toMembershipDto(membership);
    }

    /**
     * 取消会员 (置终态)。
     *
     * @param membershipId 会员记录 ID
     * @param reason       取消原因 (可空)
     * @return 更新后的会员
     * @throws ScrmException 会员不存在 / 状态非法
     */
    @Transactional
    public ScrmCustomerMembershipDto cancel(Long membershipId, String reason) throws ScrmException {
        ScrmCustomerMembershipEntity membership = findMembershipOrThrow(membershipId);
        if (STATUS_CANCELLED.equals(membership.getMembershipStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "会员已取消, 不允许重复取消");
        }
        Long fromTierId = membership.getTierId();
        membership.setMembershipStatus(STATUS_CANCELLED);
        if (reason != null && !reason.isBlank()) {
            membership.setNotes(reason);
        }
        membership = membershipRepository.save(membership);
        tierService.updateTierStats(fromTierId);
        log.info("取消会员: id={}, reason={}", membershipId, reason);
        return toMembershipDto(membership);
    }

    /**
     * 续期会员 (按当前等级有效期月延长到期日)。
     *
     * @param membershipId 会员记录 ID
     * @return 更新后的会员
     * @throws ScrmException 会员不存在 / 状态非法
     */
    @Transactional
    public ScrmCustomerMembershipDto renewMembership(Long membershipId) throws ScrmException {
        ScrmCustomerMembershipEntity membership = findMembershipOrThrow(membershipId);
        if (MEMBERSHIP_TERMINAL_STATUSES.contains(membership.getMembershipStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "会员已处于终态, 不允许续期: status=" + membership.getMembershipStatus());
        }
        ScrmMembershipTierEntity tier = tierService.findTierOrThrow(membership.getTierId());
        Integer validityMonths = tier.getValidityPeriodMonths() != null
                ? tier.getValidityPeriodMonths() : DEFAULT_VALIDITY_MONTHS;
        LocalDate base = membership.getTierExpiryDate() != null && membership.getTierExpiryDate().isAfter(LocalDate.now())
                ? membership.getTierExpiryDate() : LocalDate.now();
        LocalDate newExpiry = base.plusMonths(validityMonths);
        membership.setTierExpiryDate(newExpiry);
        membership.setPeriodEndDate(newExpiry);
        if (STATUS_EXPIRED.equals(membership.getMembershipStatus())) {
            membership.setMembershipStatus(STATUS_ACTIVE);
        }
        membership.setLastActivityDate(LocalDate.now());
        membership = membershipRepository.save(membership);
        log.info("续期会员: id={}, expiry={}", membershipId, newExpiry);
        return toMembershipDto(membership);
    }

    /**
     * 更新会员消费 (累计消费/订单/周期内消费/积分)。
     * <p>累加累计消费与订单数, 周期内消费与订单数 (周期过期则重置), 按等级积分倍数累计积分,
     * 刷新最后活动日与下一等级进度, 检查升级与降级风险。</p>
     *
     * @param membershipId 会员记录 ID
     * @param amount       消费金额
     * @param orderId      订单 ID (可空, 仅用于日志)
     * @return 更新后的会员
     * @throws ScrmException 会员不存在 / 状态非法 / 金额非法
     */
    @Transactional
    public ScrmCustomerMembershipDto updateSpend(Long membershipId, Double amount, Long orderId)
            throws ScrmException {
        if (amount == null || amount < 0) {
            throw ScrmException.badRequest("消费金额不能为空且不能为负");
        }
        ScrmCustomerMembershipEntity membership = findMembershipOrThrow(membershipId);
        if (!STATUS_ACTIVE.equals(membership.getMembershipStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "会员状态非活跃, 不允许更新消费: status=" + membership.getMembershipStatus());
        }
        ScrmMembershipTierEntity tier = tierService.findTierOrThrow(membership.getTierId());
        // 累计消费与订单
        double totalSpend = (membership.getTotalSpend() != null ? membership.getTotalSpend() : 0d) + amount;
        int totalOrders = (membership.getTotalOrders() != null ? membership.getTotalOrders() : 0) + 1;
        membership.setTotalSpend(Math.round(totalSpend * 100d) / 100d);
        membership.setTotalOrders(totalOrders);
        // 周期内消费: 周期过期则重置
        LocalDate today = LocalDate.now();
        if (membership.getPeriodEndDate() == null || membership.getPeriodEndDate().isBefore(today)) {
            membership.setSpendInPeriod(amount);
            membership.setOrdersInPeriod(1);
            membership.setPeriodStartDate(today);
            Integer validityMonths = tier.getValidityPeriodMonths() != null
                    ? tier.getValidityPeriodMonths() : DEFAULT_VALIDITY_MONTHS;
            membership.setPeriodEndDate(today.plusMonths(validityMonths));
        } else {
            double periodSpend = (membership.getSpendInPeriod() != null ? membership.getSpendInPeriod() : 0d) + amount;
            int periodOrders = (membership.getOrdersInPeriod() != null ? membership.getOrdersInPeriod() : 0) + 1;
            membership.setSpendInPeriod(Math.round(periodSpend * 100d) / 100d);
            membership.setOrdersInPeriod(periodOrders);
        }
        // 累计积分 (按等级积分倍数)
        double multiplier = tier.getPointMultiplier() != null ? tier.getPointMultiplier() : DEFAULT_POINT_MULTIPLIER;
        int earnedPoints = (int) Math.round(amount * multiplier);
        if (earnedPoints > 0) {
            int totalPoints = (membership.getTotalPoints() != null ? membership.getTotalPoints() : 0) + earnedPoints;
            int availablePoints = (membership.getAvailablePoints() != null ? membership.getAvailablePoints() : 0)
                    + earnedPoints;
            membership.setTotalPoints(totalPoints);
            membership.setAvailablePoints(availablePoints);
        }
        membership.setLastActivityDate(today);
        // 重新计算下一等级进度
        populateNextTierInfo(membership, tier);
        // 检查升级与降级风险
        checkUpgradeInternal(membership, tier);
        checkDowngradeInternal(membership, tier);
        membership = membershipRepository.save(membership);
        log.info("更新会员消费: id={}, amount={}, orderId={}", membershipId, amount, orderId);
        return toMembershipDto(membership);
    }

    /**
     * 检查会员是否可升级 (周期内消费是否达到下一等级升级阈值)。
     *
     * @param membershipId 会员记录 ID
     * @return 检查结果 (canUpgrade / nextTier / spendInPeriod / threshold / progress)
     * @throws ScrmException 会员不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> checkUpgrade(Long membershipId) throws ScrmException {
        ScrmCustomerMembershipEntity membership = findMembershipOrThrow(membershipId);
        ScrmMembershipTierEntity nextTier = tierRepository
                .findByTierLevelGreaterThanOrderByTierLevelAsc(
                        membership.getTierLevel()).stream().findFirst().orElse(null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("membershipId", membershipId);
        result.put("currentTierLevel", membership.getTierLevel());
        if (nextTier == null) {
            result.put("canUpgrade", false);
            result.put("reason", "已是最高等级");
            return result;
        }
        double threshold = nextTier.getUpgradeThreshold() != null ? nextTier.getUpgradeThreshold() : 0d;
        double spend = membership.getSpendInPeriod() != null ? membership.getSpendInPeriod() : 0d;
        double progress = threshold > 0 ? Math.min(1.0d, spend / threshold) : 1.0d;
        result.put("canUpgrade", spend >= threshold && threshold > 0);
        result.put("nextTier", tierService.toTierDto(nextTier));
        result.put("spendInPeriod", Math.round(spend * 100d) / 100d);
        result.put("threshold", threshold);
        result.put("nextTierSpendNeeded", Math.max(0, threshold - spend));
        result.put("progress", Math.round(progress * 10000d) / 10000d);
        return result;
    }

    /**
     * 检查会员降级风险 (周期内消费是否低于当前等级降级阈值)。
     *
     * @param membershipId 会员记录 ID
     * @return 检查结果 (downgradeRisk / threshold / spendInPeriod / periodEndDate)
     * @throws ScrmException 会员不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> checkDowngrade(Long membershipId) throws ScrmException {
        ScrmCustomerMembershipEntity membership = findMembershipOrThrow(membershipId);
        ScrmMembershipTierEntity tier = tierService.findTierOrThrow(membership.getTierId());
        double threshold = tier.getDowngradeThreshold() != null ? tier.getDowngradeThreshold() : 0d;
        double spend = membership.getSpendInPeriod() != null ? membership.getSpendInPeriod() : 0d;
        boolean risk = threshold > 0 && spend < threshold;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("membershipId", membershipId);
        result.put("downgradeRisk", risk);
        result.put("threshold", threshold);
        result.put("spendInPeriod", Math.round(spend * 100d) / 100d);
        result.put("periodEndDate", membership.getPeriodEndDate());
        return result;
    }

    /**
     * 批量检查到期会员 (扫描已过期会员置终态)。
     *
     * @return 处理结果 (expiredCount / membershipIds)
     */
    @Transactional
    public Map<String, Object> checkExpiry() {
        LocalDate today = LocalDate.now();
        Specification<ScrmCustomerMembershipEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.notEqual(root.get("membershipStatus"), STATUS_CANCELLED));
            predicates.add(cb.notEqual(root.get("membershipStatus"), STATUS_EXPIRED));
            predicates.add(cb.isNotNull(root.get("tierExpiryDate")));
            predicates.add(cb.lessThan(root.get("tierExpiryDate"), today));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCustomerMembershipEntity> expired = membershipRepository.findAll(spec);
        Set<Long> tierIds = new HashSet<>();
        for (ScrmCustomerMembershipEntity m : expired) {
            m.setMembershipStatus(STATUS_EXPIRED);
            if (m.getTierId() != null) {
                tierIds.add(m.getTierId());
            }
        }
        membershipRepository.saveAll(expired);
        for (Long tierId : tierIds) {
            try {
                tierService.updateTierStats(tierId);
            } catch (ScrmException e) {
                log.warn("刷新等级统计失败, 跳过: tierId={}", tierId);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("expiredCount", expired.size());
        result.put("membershipIds", expired.stream()
                .map(ScrmCustomerMembershipEntity::getId).collect(Collectors.toList()));
        log.info("批量检查到期会员: expired={}", expired.size());
        return result;
    }

    /**
     * 查询会员消费进度 (周期内消费 / 下一等级阈值 / 进度)。
     *
     * @param membershipId 会员记录 ID
     * @return 消费进度
     * @throws ScrmException 会员不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSpendProgress(Long membershipId) throws ScrmException {
        return checkUpgrade(membershipId);
    }

    /**
     * 按等级分页查询会员。
     *
     * @param tierId   等级 ID
     * @param pageable 分页参数
     * @return 会员分页结果 (按创建时间倒序)
     * @throws ScrmException 等级不存在
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerMembershipDto> getMembersByTier(Long tierId, Pageable pageable) throws ScrmException {
        tierService.findTierOrThrow(tierId);
        return listMemberships(tierId, null, null, pageable);
    }

    /**
     * 查询即将到期的会员 (到期日在未来 days 天内)。
     *
     * @param days 天数
     * @return 会员列表 (按到期日升序)
     */
    @Transactional(readOnly = true)
    public List<ScrmCustomerMembershipDto> getExpiringMemberships(int days) {
        if (days <= 0) {
            days = 30;
        }
        LocalDate today = LocalDate.now();
        LocalDate end = today.plusDays(days);
        Specification<ScrmCustomerMembershipEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.notEqual(root.get("membershipStatus"), STATUS_CANCELLED));
            predicates.add(cb.notEqual(root.get("membershipStatus"), STATUS_EXPIRED));
            predicates.add(cb.isNotNull(root.get("tierExpiryDate")));
            predicates.add(cb.greaterThanOrEqualTo(root.get("tierExpiryDate"), today));
            predicates.add(cb.lessThanOrEqualTo(root.get("tierExpiryDate"), end));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(0, DEFAULT_LIMIT, Sort.by(Sort.Direction.ASC, "tierExpiryDate"));
        return membershipRepository.findAll(spec, sorted).stream().map(this::toMembershipDto)
                .collect(Collectors.toList());
    }

    /**
     * 查询可升级会员 (周期内消费达到或超过下一等级升级阈值, 限制条数)。
     *
     * @param limit 返回条数 (默认 10)
     * @return 会员列表
     */
    @Transactional(readOnly = true)
    public List<ScrmCustomerMembershipDto> getUpgradeCandidates(int limit) {
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        Specification<ScrmCustomerMembershipEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("membershipStatus"), STATUS_ACTIVE));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCustomerMembershipEntity> members = membershipRepository.findAll(spec);
        List<ScrmCustomerMembershipDto> candidates = new ArrayList<>();
        for (ScrmCustomerMembershipEntity m : members) {
            ScrmMembershipTierEntity nextTier = tierRepository
                    .findByTierLevelGreaterThanOrderByTierLevelAsc(m.getTierLevel())
                    .stream().findFirst().orElse(null);
            if (nextTier == null) {
                continue;
            }
            double threshold = nextTier.getUpgradeThreshold() != null ? nextTier.getUpgradeThreshold() : 0d;
            double spend = m.getSpendInPeriod() != null ? m.getSpendInPeriod() : 0d;
            if (threshold > 0 && spend >= threshold) {
                candidates.add(toMembershipDto(m));
                if (candidates.size() >= limit) {
                    break;
                }
            }
        }
        return candidates;
    }

    /**
     * 查询降级风险会员 (downgradeRisk 标记为 TRUE, 限制条数)。
     *
     * @param limit 返回条数 (默认 10)
     * @return 会员列表
     */
    @Transactional(readOnly = true)
    public List<ScrmCustomerMembershipDto> getDowngradeRiskMembers(int limit) {
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        Specification<ScrmCustomerMembershipEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("membershipStatus"), STATUS_ACTIVE));
            predicates.add(cb.equal(root.get("downgradeRisk"), Boolean.TRUE));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "tierExpiryDate"));
        return membershipRepository.findAll(spec, sorted).stream().map(this::toMembershipDto)
                .collect(Collectors.toList());
    }

    /**
     * 生成会员卡号 (VM + 年月 + 6 位序号, 保证唯一)。
     *
     * @return 会员卡号
     */
    @Transactional
    public String generateCardNo() {
        String cardNo;
        int retry = 0;
        String datePart = LocalDate.now().format(CARD_NO_DATE_FORMAT);
        do {
            String seqPart = String.format("%06d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1000000));
            cardNo = CARD_NO_PREFIX + datePart + seqPart;
            retry++;
            if (retry > 10) {
                cardNo = CARD_NO_PREFIX + datePart
                        + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
                break;
            }
        } while (membershipRepository.findByMemberCardNo(cardNo).isPresent());
        return cardNo;
    }

    /**
     * 填充会员的下一等级信息 (nextTierId/nextTierName/nextTierSpendNeeded/upgradeProgress)。
     *
     * @param membership 会员实体
     * @param currentTier 当前等级
     */
    private void populateNextTierInfo(ScrmCustomerMembershipEntity membership, ScrmMembershipTierEntity currentTier) {
        ScrmMembershipTierEntity nextTier = tierRepository
                .findByTierLevelGreaterThanOrderByTierLevelAsc(
                        currentTier.getTierLevel()).stream().findFirst().orElse(null);
        if (nextTier == null) {
            membership.setNextTierId(null);
            membership.setNextTierName(null);
            membership.setNextTierSpendNeeded(0d);
            membership.setUpgradeProgress(1.0d);
        } else {
            membership.setNextTierId(nextTier.getId());
            membership.setNextTierName(nextTier.getTierName());
            double threshold = nextTier.getUpgradeThreshold() != null ? nextTier.getUpgradeThreshold() : 0d;
            double spend = membership.getSpendInPeriod() != null ? membership.getSpendInPeriod() : 0d;
            membership.setNextTierSpendNeeded(Math.max(0, threshold - spend));
            membership.setUpgradeProgress(threshold > 0 ? Math.min(1.0d, spend / threshold) : 1.0d);
        }
    }

    /**
     * 检查并标记升级状态 (内部, 仅更新 upgradeProgress, 不自动升级)。
     *
     * @param membership 会员实体
     * @param tier       当前等级
     */
    private void checkUpgradeInternal(ScrmCustomerMembershipEntity membership, ScrmMembershipTierEntity tier) {
        // 仅刷新进度, 实际升级由 upgrade 接口显式触发
        populateNextTierInfo(membership, tier);
    }

    /**
     * 检查并标记降级风险 (内部, 周期内消费低于降级阈值时标记)。
     *
     * @param membership 会员实体
     * @param tier       当前等级
     */
    private void checkDowngradeInternal(ScrmCustomerMembershipEntity membership, ScrmMembershipTierEntity tier) {
        double threshold = tier.getDowngradeThreshold() != null ? tier.getDowngradeThreshold() : 0d;
        if (threshold <= 0) {
            membership.setDowngradeRisk(Boolean.FALSE);
            return;
        }
        double spend = membership.getSpendInPeriod() != null ? membership.getSpendInPeriod() : 0d;
        membership.setDowngradeRisk(spend < threshold);
    }

    /**
     * 追加升降级历史条目到 JSON 历史数组。
     * <p>历史格式: [{date,fromTier,toTier,reason,type,fromTierId,toTierId,fromLevel,toLevel}]</p>
     *
     * @param existingHistory 现有历史 JSON (可空)
     * @param date            发生日期
     * @param fromTierName    原等级名称
     * @param toTierName      新等级名称
     * @param reason          原因 (可空)
     * @param type            类型: UPGRADE / DOWNGRADE
     * @param fromTierId      原等级 ID
     * @param toTierId        新等级 ID
     * @param fromLevel       原等级序号
     * @param toLevel         新等级序号
     * @return 更新后的历史 JSON
     */
    private String appendHistoryEntry(String existingHistory, LocalDate date, String fromTierName,
                                       String toTierName, String reason, String type, Long fromTierId,
                                       Long toTierId, Integer fromLevel, Integer toLevel) {
        String escapedReason = reason != null ? reason.replace("\"", "\\\"") : "";
        String entry = String.format(
                "{\"date\":\"%s\",\"fromTier\":\"%s\",\"toTier\":\"%s\",\"reason\":\"%s\",\"type\":\"%s\","
                        + "\"fromTierId\":%s,\"toTierId\":%s,\"fromLevel\":%s,\"toLevel\":%s}",
                date.toString(),
                fromTierName != null ? fromTierName : "",
                toTierName != null ? toTierName : "",
                escapedReason,
                type,
                fromTierId != null ? fromTierId : "null",
                toTierId != null ? toTierId : "null",
                fromLevel != null ? fromLevel : "null",
                toLevel != null ? toLevel : "null");
        if (existingHistory == null || existingHistory.isBlank() || "[]".equals(existingHistory.trim())) {
            return "[" + entry + "]";
        }
        String trimmed = existingHistory.trim();
        if (trimmed.endsWith("]")) {
            return trimmed.substring(0, trimmed.length() - 1) + (trimmed.length() > 2 ? "," : "") + entry + "]";
        }
        return "[" + entry + "]";
    }

    /** 校验会员卡类型合法性 */
    private void validateCardType(String type) throws ScrmException {
        if (!VALID_CARD_TYPES.contains(type)) {
            throw ScrmException.badRequest("会员卡类型非法: " + type
                    + ", 合法值: STANDARD / VIP / BLACK_GOLD / DIAMOND / CUSTOM");
        }
    }

    /** 校验会员状态合法性 */
    @SuppressWarnings("unused")
    private void validateMembershipStatus(String status) throws ScrmException {
        if (!VALID_MEMBERSHIP_STATUSES.contains(status)) {
            throw ScrmException.badRequest("会员状态非法: " + status
                    + ", 合法值: ACTIVE / FROZEN / EXPIRED / CANCELLED / PENDING");
        }
    }

    /**
     * 按主键查询会员, 不存在或越权抛异常
     */
    ScrmCustomerMembershipEntity findMembershipOrThrow(Long id) throws ScrmException {
        ScrmCustomerMembershipEntity entity = membershipRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "会员不存在: id=" + id));

        return entity;
    }

    /**
     * 会员实体转 DTO
     */
    ScrmCustomerMembershipDto toMembershipDto(ScrmCustomerMembershipEntity entity) {
        ScrmCustomerMembershipDto dto = new ScrmCustomerMembershipDto();
        dto.setId(entity.getId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setTierId(entity.getTierId());
        dto.setTierName(entity.getTierName());
        dto.setTierLevel(entity.getTierLevel());
        dto.setTierCode(entity.getTierCode());
        dto.setMemberCardNo(entity.getMemberCardNo());
        dto.setMemberCardType(entity.getMemberCardType());
        dto.setMembershipStatus(entity.getMembershipStatus());
        dto.setJoinDate(entity.getJoinDate());
        dto.setCurrentTierDate(entity.getCurrentTierDate());
        dto.setTierExpiryDate(entity.getTierExpiryDate());
        dto.setTotalSpend(entity.getTotalSpend());
        dto.setTotalOrders(entity.getTotalOrders());
        dto.setTotalPoints(entity.getTotalPoints());
        dto.setAvailablePoints(entity.getAvailablePoints());
        dto.setSpendInPeriod(entity.getSpendInPeriod());
        dto.setOrdersInPeriod(entity.getOrdersInPeriod());
        dto.setPeriodStartDate(entity.getPeriodStartDate());
        dto.setPeriodEndDate(entity.getPeriodEndDate());
        dto.setNextTierSpendNeeded(entity.getNextTierSpendNeeded());
        dto.setNextTierId(entity.getNextTierId());
        dto.setNextTierName(entity.getNextTierName());
        dto.setUpgradeProgress(entity.getUpgradeProgress());
        dto.setDowngradeRisk(entity.getDowngradeRisk());
        dto.setPointsToExpire(entity.getPointsToExpire());
        dto.setPointsExpiryDate(entity.getPointsExpiryDate());
        dto.setBenefitsUsedCount(entity.getBenefitsUsedCount());
        dto.setBenefitsSavedAmount(entity.getBenefitsSavedAmount());
        dto.setLastActivityDate(entity.getLastActivityDate());
        dto.setLastUpgradeDate(entity.getLastUpgradeDate());
        dto.setLastDowngradeDate(entity.getLastDowngradeDate());
        dto.setUpgradeHistory(entity.getUpgradeHistory());
        dto.setReferralCode(entity.getReferralCode());
        dto.setReferredBy(entity.getReferredBy());
        dto.setNotes(entity.getNotes());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
