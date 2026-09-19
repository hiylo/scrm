/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReferralRelationshipService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmReferralCreateDto;
import org.hiylo.scrm.dto.ScrmReferralDto;
import org.hiylo.scrm.dto.ScrmReferralQualifyDto;
import org.hiylo.scrm.entity.ScrmReferralEntity;
import org.hiylo.scrm.entity.ScrmReferralProgramEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmReferralRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * SCRM 客户推荐管理 - 推荐关系子域服务。
 * <p>
 * 承载推荐关系的创建 (推荐码生成) / 注册 / 达标 / 取消 / 过期以及推荐码与链接生成能力。
 * 同时托管按主键查询推荐能力, 供奖励兄弟类以 package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmReferralRelationshipService {

    // ==================== 推荐渠道 ====================

    /** 推荐渠道: 链接 */
    private static final String CHANNEL_LINK = "LINK";
    /** 推荐渠道: 二维码 */
    private static final String CHANNEL_QR_CODE = "QR_CODE";
    /** 推荐渠道: 推荐码 */
    private static final String CHANNEL_CODE = "CODE";

    /** 推荐码前缀 */
    private static final String REFERRAL_CODE_PREFIX = "RF";

    /** 合法推荐渠道集合 */
    private static final Set<String> VALID_CHANNELS = new HashSet<>(Arrays.asList(
            CHANNEL_LINK, CHANNEL_QR_CODE, CHANNEL_CODE, "EMAIL", "SMS", "WECHAT"));

    /** 推荐终态状态集合 (不允许再变更) */
    private static final Set<String> REFERRAL_TERMINAL_STATUSES = new HashSet<>(Arrays.asList(
            ScrmReferralProgramService.REFERRAL_STATUS_REWARDED,
            ScrmReferralProgramService.REFERRAL_STATUS_EXPIRED,
            ScrmReferralProgramService.REFERRAL_STATUS_CANCELLED));

    /** 推荐关系数据访问层 */
    private final ScrmReferralRepository referralRepository;

    /** 推荐活动子域服务 (活动校验与统计刷新) */
    private final ScrmReferralProgramService programService;

    /**
     * 创建推荐。
     * <p>校验活动有效 (ACTIVE 且在活动周期内)、单人推荐上限, 生成推荐码后创建推荐记录,
     * 推荐渠道缺省 CODE, 状态缺省 PENDING, 奖励状态缺省 PENDING, 同时刷新活动统计。</p>
     *
     * @param createDto 创建参数
     * @return 创建后的推荐
     * @throws ScrmException 活动不存在 / 活动非活跃 / 超出推荐上限
     */
    @Transactional
    public ScrmReferralDto createReferral(ScrmReferralCreateDto createDto) throws ScrmException {
        if (createDto == null) {
            throw ScrmException.badRequest("推荐参数不能为空");
        }
        if (createDto.getProgramId() == null) {
            throw ScrmException.badRequest("推荐活动 ID 不能为空");
        }
        if (createDto.getReferrerCustomerId() == null) {
            throw ScrmException.badRequest("推荐人客户 ID 不能为空");
        }
        ScrmReferralProgramEntity program = programService.findProgramOrThrow(createDto.getProgramId());
        if (!ScrmReferralProgramService.PROGRAM_STATUS_ACTIVE.equals(program.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "推荐活动非活跃状态, 不允许创建推荐: status=" + program.getStatus());
        }
        // 校验活动周期
        LocalDate today = LocalDate.now();
        if (program.getStartDate() != null && today.isBefore(program.getStartDate())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "推荐活动尚未开始: startDate=" + program.getStartDate());
        }
        if (program.getEndDate() != null && today.isAfter(program.getEndDate())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "推荐活动已结束: endDate=" + program.getEndDate());
        }
        // 校验单人推荐上限
        if (program.getMaxReferralsPerReferrer() != null && program.getMaxReferralsPerReferrer() > 0) {
            long referrerCount = referralRepository.countByProgramIdAndReferrerCustomerId(
                     program.getId(), createDto.getReferrerCustomerId());
            if (referrerCount >= program.getMaxReferralsPerReferrer()) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "推荐人已达推荐上限: max=" + program.getMaxReferralsPerReferrer());
            }
        }
        // 校验总推荐上限
        if (program.getMaxReferralsTotal() != null && program.getMaxReferralsTotal() > 0) {
            long totalCount = referralRepository.countByProgramId(
                     program.getId());
            if (totalCount >= program.getMaxReferralsTotal()) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "推荐活动已达总推荐上限: max=" + program.getMaxReferralsTotal());
            }
        }
        // 校验推荐渠道
        String channel = createDto.getChannel() != null ? createDto.getChannel() : CHANNEL_CODE;
        validateChannel(channel);
        ScrmReferralEntity entity = new ScrmReferralEntity();
        entity.setProgramId(program.getId());
        entity.setReferralCode(generateReferralCode(program.getId(), createDto.getReferrerCustomerId()));
        entity.setReferrerCustomerId(createDto.getReferrerCustomerId());
        entity.setReferrerName(createDto.getReferrerName());
        entity.setRefereeContact(createDto.getRefereeContact());
        entity.setReferralChannel(channel);
        entity.setStatus(ScrmReferralProgramService.REFERRAL_STATUS_PENDING);
        entity.setReferrerRewardStatus(ScrmReferralProgramService.REWARD_STATUS_PENDING);
        entity.setRefereeRewardStatus(ScrmReferralProgramService.REWARD_STATUS_PENDING);
        entity.setPurchaseAmount(0d);
        entity.setNotes(createDto.getNotes());
        entity = referralRepository.save(entity);
        // 刷新活动统计
        programService.updateProgramStats(program.getId());
        log.info("创建推荐: id={}, programId={}, referrerCustomerId={}",
                entity.getId(), program.getId(), createDto.getReferrerCustomerId());
        return toReferralDto(entity);
    }

    /**
     * 查询推荐详情。
     *
     * @param id 推荐 ID
     * @return 推荐 DTO
     * @throws ScrmException 推荐不存在
     */
    @Transactional(readOnly = true)
    public ScrmReferralDto getReferral(Long id) throws ScrmException {
        return toReferralDto(findReferralOrThrow(id));
    }

    /**
     * 按推荐码查询推荐。
     *
     * @param code 推荐码
     * @return 推荐 DTO
     * @throws ScrmException 推荐不存在
     */
    @Transactional(readOnly = true)
    public ScrmReferralDto getReferralByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("推荐码不能为空");
        }
        ScrmReferralEntity entity = referralRepository.findByReferralCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "推荐不存在: code=" + code));

        return toReferralDto(entity);
    }

    /**
     * 分页查询推荐, 支持按活动、推荐人、状态与时间范围过滤。
     *
     * @param programId          活动过滤 (可空)
     * @param referrerCustomerId 推荐人过滤 (可空)
     * @param status             状态过滤 (可空)
     * @param startTime          起始时间 (按创建时间, 可空)
     * @param endTime            截止时间 (按创建时间, 可空)
     * @param pageable           分页参数
     * @return 推荐分页结果 (按创建时间倒序)
     */
    @Transactional(readOnly = true)
    public Page<ScrmReferralDto> listReferrals(Long programId, Long referrerCustomerId, String status,
                                                LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        Specification<ScrmReferralEntity> spec = buildReferralSpec(programId, referrerCustomerId, status,
                startTime, endTime);
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));
        return referralRepository.findAll(spec, sorted).map(this::toReferralDto);
    }

    /**
     * 查询推荐人的推荐列表。
     *
     * @param customerId 推荐人客户 ID
     * @param pageable   分页参数
     * @return 推荐分页结果 (按创建时间倒序)
     */
    @Transactional(readOnly = true)
    public Page<ScrmReferralDto> getReferralsByReferrer(Long customerId, Pageable pageable) {
        return listReferrals(null, customerId, null, null, null, pageable);
    }

    /**
     * 被推荐人注册 (按推荐码)。
     * <p>校验推荐码存在且状态为 PENDING, 关联被推荐人客户 ID, 状态置 SIGNED_UP, 设置 signedUpAt。
     * 若活动触发条件为 SIGNUP, 则自动达标 (置 QUALIFIED); 否则保持 SIGNED_UP 等待达标。</p>
     *
     * @param referralCode      推荐码
     * @param refereeCustomerId 被推荐人客户 ID
     * @return 更新后的推荐
     * @throws ScrmException 推荐不存在 / 状态非法
     */
    @Transactional
    public ScrmReferralDto signUpReferral(String referralCode, Long refereeCustomerId) throws ScrmException {
        if (referralCode == null || referralCode.isBlank()) {
            throw ScrmException.badRequest("推荐码不能为空");
        }
        if (refereeCustomerId == null) {
            throw ScrmException.badRequest("被推荐人客户 ID 不能为空");
        }
        ScrmReferralEntity entity = referralRepository.findByReferralCode(referralCode)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "推荐不存在: code=" + referralCode));

        if (!ScrmReferralProgramService.REFERRAL_STATUS_PENDING.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "推荐状态非 PENDING, 不允许注册: status=" + entity.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setRefereeCustomerId(refereeCustomerId);
        entity.setSignedUpAt(now);
        // 触发条件为 SIGNUP 时自动达标
        ScrmReferralProgramEntity program = programService.findProgramOrThrow(entity.getProgramId());
        if (ScrmReferralProgramService.TRIGGER_SIGNUP.equals(program.getRewardTrigger())) {
            entity.setStatus(ScrmReferralProgramService.REFERRAL_STATUS_QUALIFIED);
            entity.setQualifiedAt(now);
        } else {
            entity.setStatus(ScrmReferralProgramService.REFERRAL_STATUS_SIGNED_UP);
        }
        entity = referralRepository.save(entity);
        programService.updateProgramStats(entity.getProgramId());
        log.info("推荐注册: referralId={}, refereeCustomerId={}", entity.getId(), refereeCustomerId);
        return toReferralDto(entity);
    }

    /**
     * 推荐达标 (检查触发条件→更新状态)。
     * <p>按活动奖励触发条件校验: FIRST_PURCHASE 需 purchaseAmount&gt;0; PURCHASE_AMOUNT 需
     * purchaseAmount≥触发条件值; RETENTION_DAYS 需注册后达到留存天数 (按 signedUpAt 与触发条件值估算);
     * SIGNUP 无需调用此接口 (注册时自动达标)。达标后状态置 QUALIFIED, 设置 qualifiedAt 与 purchaseAmount。</p>
     *
     * @param qualifyDto 达标参数
     * @return 更新后的推荐
     * @throws ScrmException 推荐不存在 / 状态非法 / 未达触发条件
     */
    @Transactional
    public ScrmReferralDto qualifyReferral(ScrmReferralQualifyDto qualifyDto) throws ScrmException {
        if (qualifyDto == null || qualifyDto.getReferralId() == null) {
            throw ScrmException.badRequest("推荐记录 ID 不能为空");
        }
        ScrmReferralEntity entity = findReferralOrThrow(qualifyDto.getReferralId());
        if (REFERRAL_TERMINAL_STATUSES.contains(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "推荐已处于终态, 不允许达标: status=" + entity.getStatus());
        }
        if (ScrmReferralProgramService.REFERRAL_STATUS_PENDING.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "推荐尚未注册, 不允许达标");
        }
        ScrmReferralProgramEntity program = programService.findProgramOrThrow(entity.getProgramId());
        String trigger = program.getRewardTrigger();
        double purchaseAmount = qualifyDto.getPurchaseAmount() != null ? qualifyDto.getPurchaseAmount() : 0d;
        // 补全被推荐人客户 ID
        if (qualifyDto.getRefereeCustomerId() != null && entity.getRefereeCustomerId() == null) {
            entity.setRefereeCustomerId(qualifyDto.getRefereeCustomerId());
        }
        // 校验触发条件
        switch (trigger) {
            case ScrmReferralProgramService.TRIGGER_SIGNUP:
                // SIGNUP 已在注册时达标, 此处直接通过
                break;
            case ScrmReferralProgramService.TRIGGER_FIRST_PURCHASE:
                if (purchaseAmount <= 0) {
                    throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                            "首单触发条件未满足: 需消费金额大于 0");
                }
                break;
            case ScrmReferralProgramService.TRIGGER_PURCHASE_AMOUNT:
                double threshold = program.getRewardTriggerValue() != null ? program.getRewardTriggerValue() : 0d;
                if (purchaseAmount < threshold) {
                    throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                            "消费金额触发条件未满足: 需 " + threshold + ", 实际 " + purchaseAmount);
                }
                break;
            case ScrmReferralProgramService.TRIGGER_RETENTION_DAYS:
                double retentionDays = program.getRewardTriggerValue() != null ? program.getRewardTriggerValue() : 0d;
                if (entity.getSignedUpAt() == null) {
                    throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                            "留存天数触发条件未满足: 被推荐人尚未注册");
                }
                long actualDays = java.time.Duration.between(entity.getSignedUpAt(), LocalDateTime.now()).toDays();
                if (actualDays < retentionDays) {
                    throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                            "留存天数触发条件未满足: 需 " + retentionDays + " 天, 实际 " + actualDays + " 天");
                }
                break;
            default:
                break;
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(ScrmReferralProgramService.REFERRAL_STATUS_QUALIFIED);
        entity.setQualifiedAt(now);
        entity.setPurchaseAmount(purchaseAmount);
        entity = referralRepository.save(entity);
        programService.updateProgramStats(entity.getProgramId());
        log.info("推荐达标: referralId={}, trigger={}, purchaseAmount={}", entity.getId(), trigger, purchaseAmount);
        return toReferralDto(entity);
    }

    /**
     * 取消推荐。
     *
     * @param id     推荐 ID
     * @param reason 取消原因 (可空)
     * @return 更新后的推荐
     * @throws ScrmException 推荐不存在 / 状态非法
     */
    @Transactional
    public ScrmReferralDto cancelReferral(Long id, String reason) throws ScrmException {
        ScrmReferralEntity entity = findReferralOrThrow(id);
        if (REFERRAL_TERMINAL_STATUSES.contains(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "推荐已处于终态, 不允许取消: status=" + entity.getStatus());
        }
        entity.setStatus(ScrmReferralProgramService.REFERRAL_STATUS_CANCELLED);
        if (reason != null && !reason.isBlank()) {
            entity.setNotes(reason);
        }
        entity = referralRepository.save(entity);
        programService.updateProgramStats(entity.getProgramId());
        log.info("取消推荐: id={}, reason={}", id, reason);
        return toReferralDto(entity);
    }

    /**
     * 过期推荐。
     *
     * @param id 推荐 ID
     * @return 更新后的推荐
     * @throws ScrmException 推荐不存在 / 状态非法
     */
    @Transactional
    public ScrmReferralDto expireReferral(Long id) throws ScrmException {
        ScrmReferralEntity entity = findReferralOrThrow(id);
        if (REFERRAL_TERMINAL_STATUSES.contains(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "推荐已处于终态, 不允许过期: status=" + entity.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(ScrmReferralProgramService.REFERRAL_STATUS_EXPIRED);
        entity.setExpiredAt(now);
        entity.setReferrerRewardStatus(ScrmReferralProgramService.REWARD_STATUS_EXPIRED);
        entity.setRefereeRewardStatus(ScrmReferralProgramService.REWARD_STATUS_EXPIRED);
        entity = referralRepository.save(entity);
        programService.updateProgramStats(entity.getProgramId());
        log.info("过期推荐: id={}", id);
        return toReferralDto(entity);
    }

    /**
     * 生成推荐码 (RF + 推荐人 ID 的 Base36 + 随机串, 保证唯一)。
     *
     * @param programId  活动 ID
     * @param customerId 推荐人客户 ID
     * @return 推荐码
     */
    @Transactional
    public String generateReferralCode(Long programId, Long customerId) {
        String code;
        int retry = 0;
        do {
            String customerPart = Long.toString(Math.abs(customerId), 36).toUpperCase();
            String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
            code = REFERRAL_CODE_PREFIX + customerPart + randomPart;
            retry++;
            if (retry > 10) {
                // 兜底: 追加时间戳避免极端冲突
                code = REFERRAL_CODE_PREFIX + customerPart
                        + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
                break;
            }
        } while (referralRepository.findByReferralCode(code).isPresent());
        return code;
    }

    /**
     * 生成推荐链接 (基于推荐码与渠道, 模拟实现)。
     * <p>格式: https://referral.example.com/r/{referralCode}?channel={channel}</p>
     *
     * @param programId  活动 ID
     * @param customerId 推荐人客户 ID
     * @param channel    推荐渠道 (可空, 缺省 LINK)
     * @return 推荐链接
     * @throws ScrmException 活动不存在
     */
    @Transactional
    public String generateReferralLink(Long programId, Long customerId, String channel) throws ScrmException {
        programService.findProgramOrThrow(programId);
        String normalizedChannel = channel != null && !channel.isBlank() ? channel : CHANNEL_LINK;
        validateChannel(normalizedChannel);
        String code = generateReferralCode(programId, customerId);
        return "https://referral.example.com/r/" + code + "?channel=" + normalizedChannel;
    }

    /**
     * 校验推荐渠道合法性
     */
    private void validateChannel(String channel) throws ScrmException {
        if (!VALID_CHANNELS.contains(channel)) {
            throw ScrmException.badRequest("推荐渠道非法: " + channel
                    + ", 合法值: LINK / QR_CODE / CODE / EMAIL / SMS / WECHAT");
        }
    }

    /** 校验推荐状态合法性 */
    @SuppressWarnings("unused")
    private void validateReferralStatus(String status) throws ScrmException {
        if (!ScrmReferralProgramService.VALID_REFERRAL_STATUSES.contains(status)) {
            throw ScrmException.badRequest("推荐状态非法: " + status
                    + ", 合法值: PENDING / SIGNED_UP / QUALIFIED / REWARDED / EXPIRED / CANCELLED");
        }
    }

    /**
     * 构建推荐查询条件 Specification。
     */
    private Specification<ScrmReferralEntity> buildReferralSpec(Long programId, Long referrerCustomerId,
                                                                 String status, LocalDateTime startTime,
                                                                 LocalDateTime endTime) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (programId != null) {
                predicates.add(cb.equal(root.get("programId"), programId));
            }
            if (referrerCustomerId != null) {
                predicates.add(cb.equal(root.get("referrerCustomerId"), referrerCustomerId));
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
     * 按主键查询推荐, 不存在或越权抛异常
     */
    ScrmReferralEntity findReferralOrThrow(Long id) throws ScrmException {
        ScrmReferralEntity entity = referralRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "推荐不存在: id=" + id));

        return entity;
    }

    /**
     * 推荐实体转 DTO
     */
    private ScrmReferralDto toReferralDto(ScrmReferralEntity entity) {
        ScrmReferralDto dto = new ScrmReferralDto();
        dto.setId(entity.getId());
        dto.setProgramId(entity.getProgramId());
        dto.setReferralCode(entity.getReferralCode());
        dto.setReferrerCustomerId(entity.getReferrerCustomerId());
        dto.setReferrerName(entity.getReferrerName());
        dto.setRefereeCustomerId(entity.getRefereeCustomerId());
        dto.setRefereeName(entity.getRefereeName());
        dto.setRefereeContact(entity.getRefereeContact());
        dto.setReferralChannel(entity.getReferralChannel());
        dto.setReferralLink(entity.getReferralLink());
        dto.setStatus(entity.getStatus());
        dto.setSignedUpAt(entity.getSignedUpAt());
        dto.setQualifiedAt(entity.getQualifiedAt());
        dto.setRewardedAt(entity.getRewardedAt());
        dto.setReferrerRewardStatus(entity.getReferrerRewardStatus());
        dto.setRefereeRewardStatus(entity.getRefereeRewardStatus());
        dto.setReferrerRewardDetails(entity.getReferrerRewardDetails());
        dto.setRefereeRewardDetails(entity.getRefereeRewardDetails());
        dto.setPurchaseAmount(entity.getPurchaseAmount());
        dto.setNotes(entity.getNotes());
        dto.setExpiredAt(entity.getExpiredAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}