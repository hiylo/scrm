/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReferralRewardService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmReferralRewardDto;
import org.hiylo.scrm.entity.ScrmReferralEntity;
import org.hiylo.scrm.entity.ScrmReferralProgramEntity;
import org.hiylo.scrm.entity.ScrmReferralRewardEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmReferralRewardRepository;
import org.hiylo.scrm.repository.ScrmReferralRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * SCRM 客户推荐管理 - 奖励管理子域服务。
 * <p>
 * 承载奖励的发放 / 兑换 / 过期 / 查询 / 批量发放能力, 并回写推荐记录对应方向的
 * 奖励状态。同时托管奖励终态集合与接收者类型校验等工具。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmReferralRewardService {

    // ==================== 接收者类型 ====================

    /** 接收者类型: 推荐人 */
    private static final String RECIPIENT_REFERRER = "REFERRER";
    /** 接收者类型: 被推荐人 */
    private static final String RECIPIENT_REFEREE = "REFEREE";

    /** 合法接收者类型集合 */
    private static final Set<String> VALID_RECIPIENT_TYPES = new HashSet<>(Arrays.asList(
            RECIPIENT_REFERRER, RECIPIENT_REFEREE));

    /** 奖励终态状态集合 (不允许再变更) */
    private static final Set<String> REWARD_TERMINAL_STATUSES = new HashSet<>(Arrays.asList(
            ScrmReferralProgramService.REWARD_STATUS_REDEEMED,
            ScrmReferralProgramService.REWARD_STATUS_EXPIRED,
            ScrmReferralProgramService.REWARD_STATUS_CANCELLED));

    /** 推荐奖励数据访问层 */
    private final ScrmReferralRewardRepository rewardRepository;

    /** 推荐关系数据访问层 (奖励状态回写) */
    private final ScrmReferralRepository referralRepository;

    /** 推荐活动子域服务 (活动校验与统计刷新) */
    private final ScrmReferralProgramService programService;

    /** 推荐关系子域服务 (推荐查询校验) */
    private final ScrmReferralRelationshipService referralService;

    /**
     * 发放奖励 (模拟实现: 创建奖励记录→更新状态)。
     * <p>按接收者类型从活动读取奖励配置创建奖励记录, 状态置 ISSUED, 设置 issuedAt 与交易 ID。
     * 同时更新推荐记录对应方向的奖励状态, 双方均已发放时推荐状态置 REWARDED 并设置 rewardedAt。
     * 最终刷新活动统计 (累计奖励价值)。</p>
     *
     * @param referralId    推荐记录 ID
     * @param recipientType 接收者类型: REFERRER / REFEREE
     * @return 创建后的奖励
     * @throws ScrmException 推荐不存在 / 接收者类型非法 / 奖励已发放
     */
    @Transactional
    public ScrmReferralRewardDto issueReward(Long referralId, String recipientType) throws ScrmException {
        validateRecipientType(recipientType);
        ScrmReferralEntity referral = referralService.findReferralOrThrow(referralId);
        ScrmReferralProgramEntity program = programService.findProgramOrThrow(referral.getProgramId());
        // 校验推荐状态: 需已达标或已奖励
        if (!ScrmReferralProgramService.REFERRAL_STATUS_QUALIFIED.equals(referral.getStatus())
                && !ScrmReferralProgramService.REFERRAL_STATUS_REWARDED.equals(referral.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "推荐尚未达标, 不允许发放奖励: status=" + referral.getStatus());
        }
        boolean isReferrer = RECIPIENT_REFERRER.equals(recipientType);
        String currentRewardStatus = isReferrer ? referral.getReferrerRewardStatus()
                : referral.getRefereeRewardStatus();
        if (ScrmReferralProgramService.REWARD_STATUS_ISSUED.equals(currentRewardStatus)
                || ScrmReferralProgramService.REWARD_STATUS_REDEEMED.equals(currentRewardStatus)) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "奖励已发放, 不允许重复发放: recipientType=" + recipientType);
        }
        // 创建奖励记录
        ScrmReferralRewardEntity reward = new ScrmReferralRewardEntity();
        reward.setReferralId(referral.getId());
        reward.setProgramId(program.getId());
        reward.setRecipientType(recipientType);
        if (isReferrer) {
            reward.setRecipientCustomerId(referral.getReferrerCustomerId());
            reward.setRecipientName(referral.getReferrerName());
            reward.setRewardType(program.getReferrerRewardType());
            reward.setRewardValue(program.getReferrerRewardValue());
            reward.setRewardConfig(program.getReferrerRewardConfig());
        } else {
            reward.setRecipientCustomerId(referral.getRefereeCustomerId());
            reward.setRecipientName(referral.getRefereeName());
            reward.setRewardType(program.getRefereeRewardType());
            reward.setRewardValue(program.getRefereeRewardValue());
            reward.setRewardConfig(program.getRefereeRewardConfig());
        }
        reward.setStatus(ScrmReferralProgramService.REWARD_STATUS_ISSUED);
        LocalDateTime now = LocalDateTime.now();
        reward.setIssuedAt(now);
        reward.setTransactionId("TX" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        reward = rewardRepository.save(reward);
        // 更新推荐记录对应方向的奖励状态
        String details = buildRewardDetails(program, isReferrer);
        if (isReferrer) {
            referral.setReferrerRewardStatus(ScrmReferralProgramService.REWARD_STATUS_ISSUED);
            referral.setReferrerRewardDetails(details);
        } else {
            referral.setRefereeRewardStatus(ScrmReferralProgramService.REWARD_STATUS_ISSUED);
            referral.setRefereeRewardDetails(details);
        }
        // 双方均已发放 (或单向奖励) 时推荐状态置 REWARDED
        boolean referrerDone = ScrmReferralProgramService.REWARD_STATUS_ISSUED.equals(
                        referral.getReferrerRewardStatus())
                || ScrmReferralProgramService.REWARD_STATUS_REDEEMED.equals(
                        referral.getReferrerRewardStatus());
        boolean refereeDone = ScrmReferralProgramService.REWARD_STATUS_ISSUED.equals(
                        referral.getRefereeRewardStatus())
                || ScrmReferralProgramService.REWARD_STATUS_REDEEMED.equals(
                        referral.getRefereeRewardStatus());
        boolean doubleSided = Boolean.TRUE.equals(program.getDoubleSidedReward());
        boolean rewardComplete = doubleSided ? (referrerDone && refereeDone) : (referrerDone || refereeDone);
        if (rewardComplete) {
            referral.setStatus(ScrmReferralProgramService.REFERRAL_STATUS_REWARDED);
            referral.setRewardedAt(now);
        }
        referralRepository.save(referral);
        programService.updateProgramStats(program.getId());
        log.info("发放推荐奖励: rewardId={}, referralId={}, recipientType={}",
                reward.getId(), referralId, recipientType);
        return toRewardDto(reward);
    }

    /**
     * 兑换奖励。
     *
     * @param rewardId 奖励 ID
     * @return 更新后的奖励
     * @throws ScrmException 奖励不存在 / 状态非法
     */
    @Transactional
    public ScrmReferralRewardDto redeemReward(Long rewardId) throws ScrmException {
        ScrmReferralRewardEntity reward = findRewardOrThrow(rewardId);
        if (REWARD_TERMINAL_STATUSES.contains(reward.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "奖励已处于终态, 不允许兑换: status=" + reward.getStatus());
        }
        if (!ScrmReferralProgramService.REWARD_STATUS_ISSUED.equals(reward.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "奖励状态非 ISSUED, 不允许兑换: status=" + reward.getStatus());
        }
        reward.setStatus(ScrmReferralProgramService.REWARD_STATUS_REDEEMED);
        reward.setRedeemedAt(LocalDateTime.now());
        reward = rewardRepository.save(reward);
        // 同步推荐记录对应方向的奖励状态
        updateReferralRewardStatus(reward);
        log.info("兑换推荐奖励: id={}", rewardId);
        return toRewardDto(reward);
    }

    /**
     * 过期奖励。
     *
     * @param rewardId 奖励 ID
     * @return 更新后的奖励
     * @throws ScrmException 奖励不存在 / 状态非法
     */
    @Transactional
    public ScrmReferralRewardDto expireReward(Long rewardId) throws ScrmException {
        ScrmReferralRewardEntity reward = findRewardOrThrow(rewardId);
        if (REWARD_TERMINAL_STATUSES.contains(reward.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "奖励已处于终态, 不允许过期: status=" + reward.getStatus());
        }
        reward.setStatus(ScrmReferralProgramService.REWARD_STATUS_EXPIRED);
        reward.setExpiredAt(LocalDateTime.now());
        reward = rewardRepository.save(reward);
        updateReferralRewardStatus(reward);
        log.info("过期推荐奖励: id={}", rewardId);
        return toRewardDto(reward);
    }

    /**
     * 查询奖励详情。
     *
     * @param id 奖励 ID
     * @return 奖励 DTO
     * @throws ScrmException 奖励不存在
     */
    @Transactional(readOnly = true)
    public ScrmReferralRewardDto getReward(Long id) throws ScrmException {
        return toRewardDto(findRewardOrThrow(id));
    }

    /**
     * 分页查询奖励, 支持按推荐记录、接收者类型与状态过滤。
     *
     * @param referralId    推荐记录过滤 (可空)
     * @param recipientType 接收者类型过滤 (可空)
     * @param status        状态过滤 (可空)
     * @param pageable      分页参数
     * @return 奖励分页结果 (按创建时间倒序)
     */
    @Transactional(readOnly = true)
    public Page<ScrmReferralRewardDto> listRewards(Long referralId, String recipientType, String status,
                                                    Pageable pageable) {
        Specification<ScrmReferralRewardEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (referralId != null) {
                predicates.add(cb.equal(root.get("referralId"), referralId));
            }
            if (recipientType != null && !recipientType.isBlank()) {
                predicates.add(cb.equal(root.get("recipientType"), recipientType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));
        return rewardRepository.findAll(spec, sorted).map(this::toRewardDto);
    }

    /**
     * 查询客户奖励列表。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 奖励分页结果 (按创建时间倒序)
     */
    @Transactional(readOnly = true)
    public Page<ScrmReferralRewardDto> getCustomerRewards(Long customerId, Pageable pageable) {
        Specification<ScrmReferralRewardEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("recipientCustomerId"), customerId));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));
        return rewardRepository.findAll(spec, sorted).map(this::toRewardDto);
    }

    /**
     * 批量发放奖励。
     * <p>按推荐记录 ID 列表逐个发放推荐人方向奖励, 跳过失败记录 (记录日志), 返回成功发放的奖励列表。</p>
     *
     * @param referralIds 推荐记录 ID 列表
     * @return 成功发放的奖励列表
     */
    @Transactional
    public List<ScrmReferralRewardDto> batchIssueRewards(List<Long> referralIds) {
        if (referralIds == null || referralIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<ScrmReferralRewardDto> result = new ArrayList<>();
        for (Long referralId : referralIds) {
            try {
                result.add(issueReward(referralId, RECIPIENT_REFERRER));
            } catch (ScrmException e) {
                log.warn("批量发放奖励失败, 跳过: referralId={}, reason={}", referralId, e.getMessage());
            }
        }
        log.info("批量发放推荐奖励: total={}, success={}", referralIds.size(), result.size());
        return result;
    }

    /**
     * 校验接收者类型合法性
     */
    private void validateRecipientType(String type) throws ScrmException {
        if (type == null || type.isBlank()) {
            throw ScrmException.badRequest("接收者类型不能为空");
        }
        if (!VALID_RECIPIENT_TYPES.contains(type)) {
            throw ScrmException.badRequest("接收者类型非法: " + type
                    + ", 合法值: REFERRER / REFEREE");
        }
    }

    /** 校验奖励状态合法性 */
    @SuppressWarnings("unused")
    private void validateRewardStatus(String status) throws ScrmException {
        if (!ScrmReferralProgramService.VALID_REWARD_STATUSES.contains(status)) {
            throw ScrmException.badRequest("奖励状态非法: " + status
                    + ", 合法值: PENDING / ISSUED / REDEEMED / EXPIRED / CANCELLED");
        }
    }

    /**
     * 构建奖励详情描述。
     *
     * @param program    活动
     * @param isReferrer 是否为推荐人
     * @return 奖励详情
     */
    private String buildRewardDetails(ScrmReferralProgramEntity program, boolean isReferrer) {
        String type = isReferrer ? program.getReferrerRewardType() : program.getRefereeRewardType();
        Double value = isReferrer ? program.getReferrerRewardValue() : program.getRefereeRewardValue();
        return type + ":" + (value != null ? value : 0);
    }

    /**
     * 同步推荐记录对应方向的奖励状态 (奖励兑换/过期后回写)。
     *
     * @param reward 奖励实体
     */
    private void updateReferralRewardStatus(ScrmReferralRewardEntity reward) {
        referralRepository.findById(reward.getReferralId()).ifPresent(referral -> {
            if (RECIPIENT_REFERRER.equals(reward.getRecipientType())) {
                referral.setReferrerRewardStatus(reward.getStatus());
            } else {
                referral.setRefereeRewardStatus(reward.getStatus());
            }
            referralRepository.save(referral);
        });
    }

    /**
     * 按主键查询奖励, 不存在或越权抛异常
     */
    private ScrmReferralRewardEntity findRewardOrThrow(Long id) throws ScrmException {
        ScrmReferralRewardEntity entity = rewardRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "推荐奖励不存在: id=" + id));

        return entity;
    }

    /**
     * 奖励实体转 DTO
     */
    private ScrmReferralRewardDto toRewardDto(ScrmReferralRewardEntity entity) {
        ScrmReferralRewardDto dto = new ScrmReferralRewardDto();
        dto.setId(entity.getId());
        dto.setReferralId(entity.getReferralId());
        dto.setProgramId(entity.getProgramId());
        dto.setRecipientType(entity.getRecipientType());
        dto.setRecipientCustomerId(entity.getRecipientCustomerId());
        dto.setRecipientName(entity.getRecipientName());
        dto.setRewardType(entity.getRewardType());
        dto.setRewardValue(entity.getRewardValue());
        dto.setRewardConfig(entity.getRewardConfig());
        dto.setStatus(entity.getStatus());
        dto.setIssuedAt(entity.getIssuedAt());
        dto.setRedeemedAt(entity.getRedeemedAt());
        dto.setExpiredAt(entity.getExpiredAt());
        dto.setCouponCode(entity.getCouponCode());
        dto.setPointsAccount(entity.getPointsAccount());
        dto.setTransactionId(entity.getTransactionId());
        dto.setNotes(entity.getNotes());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}