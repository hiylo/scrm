/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReferralProgramService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmReferralProgramDto;
import org.hiylo.scrm.entity.ScrmReferralProgramEntity;
import org.hiylo.scrm.entity.ScrmReferralRewardEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmReferralProgramRepository;
import org.hiylo.scrm.repository.ScrmReferralRepository;
import org.hiylo.scrm.repository.ScrmReferralRewardRepository;
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
import java.util.List;
import java.util.Set;

/**
 * SCRM 客户推荐管理 - 推荐活动子域服务。
 * <p>
 * 承载推荐活动的增删改查、启停 / 完成状态流转与活动统计刷新能力。同时托管推荐模块
 * 共享常量 (活动 / 奖励 / 触发条件 / 状态集合) 与按主键查询活动能力, 供推荐关系、
 * 奖励与统计兄弟类以 package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmReferralProgramService {

    // ==================== 活动类型 ====================

    /** 活动类型: 推荐 */
    static final String TYPE_REFERRAL = "REFERRAL";
    /** 活动类型: 邀请 */
    static final String TYPE_INVITE = "INVITE";
    /** 活动类型: 分享 */
    static final String TYPE_SHARE = "SHARE";
    /** 活动类型: 大使 */
    static final String TYPE_AMBASSADOR = "AMBASSADOR";
    /** 活动类型: 联盟 */
    static final String TYPE_AFFILIATE = "AFFILIATE";

    // ==================== 奖励类型 ====================

    /** 奖励类型: 积分 */
    static final String REWARD_POINTS = "POINTS";
    /** 奖励类型: 优惠券 */
    static final String REWARD_COUPON = "COUPON";
    /** 奖励类型: 现金 */
    static final String REWARD_CASH = "CASH";
    /** 奖励类型: 折扣 */
    static final String REWARD_DISCOUNT = "DISCOUNT";
    /** 奖励类型: 礼品 */
    static final String REWARD_GIFT = "GIFT";
    /** 奖励类型: 会员 */
    static final String REWARD_MEMBERSHIP = "MEMBERSHIP";

    // ==================== 触发条件 ====================

    /** 触发条件: 注册 */
    static final String TRIGGER_SIGNUP = "SIGNUP";
    /** 触发条件: 首单 */
    static final String TRIGGER_FIRST_PURCHASE = "FIRST_PURCHASE";
    /** 触发条件: 消费金额 */
    static final String TRIGGER_PURCHASE_AMOUNT = "PURCHASE_AMOUNT";
    /** 触发条件: 留存天数 */
    static final String TRIGGER_RETENTION_DAYS = "RETENTION_DAYS";

    // ==================== 活动状态 ====================

    /** 活动状态: 活跃 */
    static final String PROGRAM_STATUS_ACTIVE = "ACTIVE";
    /** 活动状态: 暂停 */
    static final String PROGRAM_STATUS_PAUSED = "PAUSED";
    /** 活动状态: 过期 */
    static final String PROGRAM_STATUS_EXPIRED = "EXPIRED";
    /** 活动状态: 完成 */
    static final String PROGRAM_STATUS_COMPLETED = "COMPLETED";

    // ==================== 推荐状态 ====================

    /** 推荐状态: 待处理 */
    static final String REFERRAL_STATUS_PENDING = "PENDING";
    /** 推荐状态: 已注册 */
    static final String REFERRAL_STATUS_SIGNED_UP = "SIGNED_UP";
    /** 推荐状态: 已达标 */
    static final String REFERRAL_STATUS_QUALIFIED = "QUALIFIED";
    /** 推荐状态: 已奖励 */
    static final String REFERRAL_STATUS_REWARDED = "REWARDED";
    /** 推荐状态: 已过期 */
    static final String REFERRAL_STATUS_EXPIRED = "EXPIRED";
    /** 推荐状态: 已取消 */
    static final String REFERRAL_STATUS_CANCELLED = "CANCELLED";

    // ==================== 奖励状态 ====================

    /** 奖励状态: 待发放 */
    static final String REWARD_STATUS_PENDING = "PENDING";
    /** 奖励状态: 已发放 */
    static final String REWARD_STATUS_ISSUED = "ISSUED";
    /** 奖励状态: 已兑换 */
    static final String REWARD_STATUS_REDEEMED = "REDEEMED";
    /** 奖励状态: 已过期 */
    static final String REWARD_STATUS_EXPIRED = "EXPIRED";
    /** 奖励状态: 已取消 */
    static final String REWARD_STATUS_CANCELLED = "CANCELLED";

    // ==================== 合法值集合 ====================

    /** 合法活动类型集合 */
    static final Set<String> VALID_PROGRAM_TYPES = new HashSet<>(Arrays.asList(
            TYPE_REFERRAL, TYPE_INVITE, TYPE_SHARE, TYPE_AMBASSADOR, TYPE_AFFILIATE));
    /** 合法奖励类型集合 */
    static final Set<String> VALID_REWARD_TYPES = new HashSet<>(Arrays.asList(
            REWARD_POINTS, REWARD_COUPON, REWARD_CASH, REWARD_DISCOUNT, REWARD_GIFT, REWARD_MEMBERSHIP));
    /** 合法触发条件集合 */
    static final Set<String> VALID_TRIGGERS = new HashSet<>(Arrays.asList(
            TRIGGER_SIGNUP, TRIGGER_FIRST_PURCHASE, TRIGGER_PURCHASE_AMOUNT, TRIGGER_RETENTION_DAYS));
    /** 合法活动状态集合 */
    static final Set<String> VALID_PROGRAM_STATUSES = new HashSet<>(Arrays.asList(
            PROGRAM_STATUS_ACTIVE, PROGRAM_STATUS_PAUSED, PROGRAM_STATUS_EXPIRED, PROGRAM_STATUS_COMPLETED));
    /** 合法推荐状态集合 */
    static final Set<String> VALID_REFERRAL_STATUSES = new HashSet<>(Arrays.asList(
            REFERRAL_STATUS_PENDING, REFERRAL_STATUS_SIGNED_UP, REFERRAL_STATUS_QUALIFIED,
            REFERRAL_STATUS_REWARDED, REFERRAL_STATUS_EXPIRED, REFERRAL_STATUS_CANCELLED));
    /** 合法奖励状态集合 */
    static final Set<String> VALID_REWARD_STATUSES = new HashSet<>(Arrays.asList(
            REWARD_STATUS_PENDING, REWARD_STATUS_ISSUED, REWARD_STATUS_REDEEMED,
            REWARD_STATUS_EXPIRED, REWARD_STATUS_CANCELLED));
    /** 成功推荐状态集合 (已达标/已奖励) */
    static final Set<String> SUCCESSFUL_STATUSES = new HashSet<>(Arrays.asList(
            REFERRAL_STATUS_QUALIFIED, REFERRAL_STATUS_REWARDED));

    /** 推荐活动数据访问层 */
    private final ScrmReferralProgramRepository programRepository;
    /** 推荐关系数据访问层 (活动统计刷新用) */
    private final ScrmReferralRepository referralRepository;
    /** 推荐奖励数据访问层 (活动统计刷新用) */
    private final ScrmReferralRewardRepository rewardRepository;

    /**
     * 创建推荐活动。
     * <p>校验活动编码唯一与枚举合法性后写入归属账号 ID 持久化, 奖励触发条件缺省 SIGNUP,
     * 双向奖励缺省 TRUE, 推荐上限缺省 0 (无限), 状态缺省 ACTIVE, 统计字段缺省 0。</p>
     *
     * @param dto 活动参数
     * @return 创建后的活动
     * @throws ScrmException 参数非法 / 活动编码重复
     */
    @Transactional
    public ScrmReferralProgramDto createProgram(ScrmReferralProgramDto dto) throws ScrmException {
        validateProgramDto(dto, false);
        if (programRepository.findByProgramCode(dto.getProgramCode()).isPresent()) {
            throw ScrmException.conflict("活动编码已存在: " + dto.getProgramCode());
        }
        ScrmReferralProgramEntity entity = new ScrmReferralProgramEntity();
        entity.setProgramName(dto.getProgramName());
        entity.setProgramCode(dto.getProgramCode());
        entity.setDescription(dto.getDescription());
        entity.setProgramType(dto.getProgramType());
        entity.setReferrerRewardType(dto.getReferrerRewardType());
        entity.setReferrerRewardValue(dto.getReferrerRewardValue());
        entity.setReferrerRewardConfig(dto.getReferrerRewardConfig());
        entity.setRefereeRewardType(dto.getRefereeRewardType());
        entity.setRefereeRewardValue(dto.getRefereeRewardValue());
        entity.setRefereeRewardConfig(dto.getRefereeRewardConfig());
        entity.setRewardTrigger(dto.getRewardTrigger() != null ? dto.getRewardTrigger() : TRIGGER_SIGNUP);
        entity.setRewardTriggerValue(dto.getRewardTriggerValue());
        entity.setMaxReferralsPerReferrer(dto.getMaxReferralsPerReferrer() != null
                ? dto.getMaxReferralsPerReferrer() : 0);
        entity.setMaxReferralsTotal(dto.getMaxReferralsTotal() != null ? dto.getMaxReferralsTotal() : 0);
        entity.setDoubleSidedReward(dto.getDoubleSidedReward() != null ? dto.getDoubleSidedReward() : Boolean.TRUE);
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : PROGRAM_STATUS_ACTIVE);
        entity.setTermsConditions(dto.getTermsConditions());
        entity.setTotalReferrals(0);
        entity.setSuccessfulReferrals(0);
        entity.setTotalRewardValue(0d);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = programRepository.save(entity);
        log.info("创建推荐活动: id={}, code={}", entity.getId(), entity.getProgramCode());
        return toProgramDto(entity);
    }

    /**
     * 更新推荐活动（字段非空才覆盖）。
     * <p>状态、统计字段通过专用接口维护, 此处不直接修改。活动编码变更时校验唯一性。</p>
     *
     * @param id  活动 ID
     * @param dto 活动参数
     * @return 更新后的活动
     * @throws ScrmException 活动不存在 / 参数非法 / 活动编码重复
     */
    @Transactional
    public ScrmReferralProgramDto updateProgram(Long id, ScrmReferralProgramDto dto) throws ScrmException {
        ScrmReferralProgramEntity entity = findProgramOrThrow(id);
        validateProgramDto(dto, true);
        if (dto.getProgramCode() != null && !dto.getProgramCode().equals(entity.getProgramCode())) {
            programRepository.findByProgramCode(dto.getProgramCode()).ifPresent(p -> {
                throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                        "活动编码已存在: " + dto.getProgramCode());
            });
            entity.setProgramCode(dto.getProgramCode());
        }
        if (dto.getProgramName() != null) entity.setProgramName(dto.getProgramName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getProgramType() != null) {
            validateProgramType(dto.getProgramType());
            entity.setProgramType(dto.getProgramType());
        }
        if (dto.getReferrerRewardType() != null) {
            validateRewardType(dto.getReferrerRewardType());
            entity.setReferrerRewardType(dto.getReferrerRewardType());
        }
        if (dto.getReferrerRewardValue() != null) entity.setReferrerRewardValue(dto.getReferrerRewardValue());
        if (dto.getReferrerRewardConfig() != null) entity.setReferrerRewardConfig(dto.getReferrerRewardConfig());
        if (dto.getRefereeRewardType() != null) {
            validateRewardType(dto.getRefereeRewardType());
            entity.setRefereeRewardType(dto.getRefereeRewardType());
        }
        if (dto.getRefereeRewardValue() != null) entity.setRefereeRewardValue(dto.getRefereeRewardValue());
        if (dto.getRefereeRewardConfig() != null) entity.setRefereeRewardConfig(dto.getRefereeRewardConfig());
        if (dto.getRewardTrigger() != null) {
            validateTrigger(dto.getRewardTrigger());
            entity.setRewardTrigger(dto.getRewardTrigger());
        }
        if (dto.getRewardTriggerValue() != null) entity.setRewardTriggerValue(dto.getRewardTriggerValue());
        if (dto.getMaxReferralsPerReferrer() != null) entity.setMaxReferralsPerReferrer(
                dto.getMaxReferralsPerReferrer());
        if (dto.getMaxReferralsTotal() != null) entity.setMaxReferralsTotal(dto.getMaxReferralsTotal());
        if (dto.getDoubleSidedReward() != null) entity.setDoubleSidedReward(dto.getDoubleSidedReward());
        if (dto.getStartDate() != null) entity.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null) entity.setEndDate(dto.getEndDate());
        if (dto.getTermsConditions() != null) entity.setTermsConditions(dto.getTermsConditions());
        entity = programRepository.save(entity);
        log.info("更新推荐活动: id={}", id);
        return toProgramDto(entity);
    }

    /**
     * 删除推荐活动。
     *
     * @param id 活动 ID
     * @throws ScrmException 活动不存在
     */
    @Transactional
    public void deleteProgram(Long id) throws ScrmException {
        ScrmReferralProgramEntity entity = findProgramOrThrow(id);
        programRepository.delete(entity);
        log.info("删除推荐活动: id={}", id);
    }

    /**
     * 查询推荐活动详情。
     *
     * @param id 活动 ID
     * @return 活动 DTO
     * @throws ScrmException 活动不存在
     */
    @Transactional(readOnly = true)
    public ScrmReferralProgramDto getProgram(Long id) throws ScrmException {
        return toProgramDto(findProgramOrThrow(id));
    }

    /**
     * 按活动编码查询推荐活动。
     *
     * @param code 活动编码
     * @return 活动 DTO
     * @throws ScrmException 活动不存在
     */
    @Transactional(readOnly = true)
    public ScrmReferralProgramDto getProgramByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("活动编码不能为空");
        }
        ScrmReferralProgramEntity entity = programRepository.findByProgramCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "推荐活动不存在: code=" + code));

        return toProgramDto(entity);
    }

    /**
     * 分页查询推荐活动, 支持按活动类型、状态与关键词过滤。
     *
     * @param programType 活动类型过滤 (可空)
     * @param status      状态过滤 (可空)
     * @param keyword     关键词过滤, 匹配活动名称/编码/描述 (可空)
     * @param pageable    分页参数
     * @return 活动分页结果 (按创建时间倒序)
     */
    @Transactional(readOnly = true)
    public Page<ScrmReferralProgramDto> listPrograms(String programType, String status, String keyword,
                                                       Pageable pageable) {
        Specification<ScrmReferralProgramEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (programType != null && !programType.isBlank()) {
                predicates.add(cb.equal(root.get("programType"), programType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("programName")), like),
                        cb.like(cb.lower(root.get("programCode")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));
        return programRepository.findAll(spec, sorted).map(this::toProgramDto);
    }

    /**
     * 激活推荐活动。
     *
     * @param id 活动 ID
     * @return 更新后的活动
     * @throws ScrmException 活动不存在
     */
    @Transactional
    public ScrmReferralProgramDto activateProgram(Long id) throws ScrmException {
        ScrmReferralProgramEntity entity = findProgramOrThrow(id);
        entity.setStatus(PROGRAM_STATUS_ACTIVE);
        entity = programRepository.save(entity);
        log.info("激活推荐活动: id={}", id);
        return toProgramDto(entity);
    }

    /**
     * 暂停推荐活动。
     *
     * @param id 活动 ID
     * @return 更新后的活动
     * @throws ScrmException 活动不存在
     */
    @Transactional
    public ScrmReferralProgramDto pauseProgram(Long id) throws ScrmException {
        ScrmReferralProgramEntity entity = findProgramOrThrow(id);
        entity.setStatus(PROGRAM_STATUS_PAUSED);
        entity = programRepository.save(entity);
        log.info("暂停推荐活动: id={}", id);
        return toProgramDto(entity);
    }

    /**
     * 完成推荐活动。
     *
     * @param id 活动 ID
     * @return 更新后的活动
     * @throws ScrmException 活动不存在
     */
    @Transactional
    public ScrmReferralProgramDto completeProgram(Long id) throws ScrmException {
        ScrmReferralProgramEntity entity = findProgramOrThrow(id);
        entity.setStatus(PROGRAM_STATUS_COMPLETED);
        entity = programRepository.save(entity);
        log.info("完成推荐活动: id={}", id);
        return toProgramDto(entity);
    }

    /**
     * 更新活动统计 (总推荐数/成功推荐数/累计奖励价值)。
     *
     * @param id 活动 ID
     * @return 更新后的活动
     * @throws ScrmException 活动不存在
     */
    @Transactional
    public ScrmReferralProgramDto updateProgramStats(Long id) throws ScrmException {
        ScrmReferralProgramEntity entity = findProgramOrThrow(id);
        long total = referralRepository.countByProgramId(id);
        long successful = referralRepository
                .countByProgramIdAndStatusIn(id, new ArrayList<>(SUCCESSFUL_STATUSES));
        Specification<ScrmReferralRewardEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("programId"), id));
            predicates.add(cb.notEqual(root.get("status"), REWARD_STATUS_CANCELLED));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmReferralRewardEntity> rewards = rewardRepository.findAll(spec);
        double totalReward = rewards.stream()
                .filter(r -> r.getRewardValue() != null)
                .mapToDouble(ScrmReferralRewardEntity::getRewardValue)
                .sum();
        entity.setTotalReferrals((int) total);
        entity.setSuccessfulReferrals((int) successful);
        entity.setTotalRewardValue(Math.round(totalReward * 100d) / 100d);
        entity = programRepository.save(entity);
        log.info("更新推荐活动统计: id={}, total={}, successful={}, reward={}",
                id, total, successful, totalReward);
        return toProgramDto(entity);
    }

    /**
     * 校验推荐活动参数。
     *
     * @param dto     活动参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateProgramDto(ScrmReferralProgramDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("活动参数不能为空");
        }
        if (!partial) {
            if (dto.getProgramName() == null || dto.getProgramName().isBlank()) {
                throw ScrmException.badRequest("活动名称不能为空");
            }
            if (dto.getProgramCode() == null || dto.getProgramCode().isBlank()) {
                throw ScrmException.badRequest("活动编码不能为空");
            }
            if (dto.getProgramType() == null || dto.getProgramType().isBlank()) {
                throw ScrmException.badRequest("活动类型不能为空");
            }
            if (dto.getReferrerRewardType() == null || dto.getReferrerRewardType().isBlank()) {
                throw ScrmException.badRequest("推荐人奖励类型不能为空");
            }
            if (dto.getRefereeRewardType() == null || dto.getRefereeRewardType().isBlank()) {
                throw ScrmException.badRequest("被推荐人奖励类型不能为空");
            }
            if (dto.getStartDate() == null) {
                throw ScrmException.badRequest("活动开始日期不能为空");
            }
        }
        if (dto.getProgramType() != null) {
            validateProgramType(dto.getProgramType());
        }
        if (dto.getReferrerRewardType() != null) {
            validateRewardType(dto.getReferrerRewardType());
        }
        if (dto.getRefereeRewardType() != null) {
            validateRewardType(dto.getRefereeRewardType());
        }
        if (dto.getRewardTrigger() != null) {
            validateTrigger(dto.getRewardTrigger());
        }
        if (dto.getStatus() != null) {
            validateProgramStatus(dto.getStatus());
        }
    }

    /** 校验活动类型合法性 */
    private void validateProgramType(String type) throws ScrmException {
        if (!VALID_PROGRAM_TYPES.contains(type)) {
            throw ScrmException.badRequest("活动类型非法: " + type
                    + ", 合法值: REFERRAL / INVITE / SHARE / AMBASSADOR / AFFILIATE");
        }
    }

    /** 校验奖励类型合法性 */
    private void validateRewardType(String type) throws ScrmException {
        if (!VALID_REWARD_TYPES.contains(type)) {
            throw ScrmException.badRequest("奖励类型非法: " + type
                    + ", 合法值: POINTS / COUPON / CASH / DISCOUNT / GIFT / MEMBERSHIP");
        }
    }

    /** 校验触发条件合法性 */
    private void validateTrigger(String trigger) throws ScrmException {
        if (!VALID_TRIGGERS.contains(trigger)) {
            throw ScrmException.badRequest("触发条件非法: " + trigger
                    + ", 合法值: SIGNUP / FIRST_PURCHASE / PURCHASE_AMOUNT / RETENTION_DAYS");
        }
    }

    /** 校验活动状态合法性 */
    private void validateProgramStatus(String status) throws ScrmException {
        if (!VALID_PROGRAM_STATUSES.contains(status)) {
            throw ScrmException.badRequest("活动状态非法: " + status
                    + ", 合法值: ACTIVE / PAUSED / EXPIRED / COMPLETED");
        }
    }

    /**
     * 按主键查询推荐活动, 不存在或越权抛异常
     */
    ScrmReferralProgramEntity findProgramOrThrow(Long id) throws ScrmException {
        ScrmReferralProgramEntity entity = programRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "推荐活动不存在: id=" + id));

        return entity;
    }

    /**
     * 活动实体转 DTO
     */
    private ScrmReferralProgramDto toProgramDto(ScrmReferralProgramEntity entity) {
        ScrmReferralProgramDto dto = new ScrmReferralProgramDto();
        dto.setId(entity.getId());
        dto.setProgramName(entity.getProgramName());
        dto.setProgramCode(entity.getProgramCode());
        dto.setDescription(entity.getDescription());
        dto.setProgramType(entity.getProgramType());
        dto.setReferrerRewardType(entity.getReferrerRewardType());
        dto.setReferrerRewardValue(entity.getReferrerRewardValue());
        dto.setReferrerRewardConfig(entity.getReferrerRewardConfig());
        dto.setRefereeRewardType(entity.getRefereeRewardType());
        dto.setRefereeRewardValue(entity.getRefereeRewardValue());
        dto.setRefereeRewardConfig(entity.getRefereeRewardConfig());
        dto.setRewardTrigger(entity.getRewardTrigger());
        dto.setRewardTriggerValue(entity.getRewardTriggerValue());
        dto.setMaxReferralsPerReferrer(entity.getMaxReferralsPerReferrer());
        dto.setMaxReferralsTotal(entity.getMaxReferralsTotal());
        dto.setDoubleSidedReward(entity.getDoubleSidedReward());
        dto.setStartDate(entity.getStartDate());
        dto.setEndDate(entity.getEndDate());
        dto.setStatus(entity.getStatus());
        dto.setTermsConditions(entity.getTermsConditions());
        dto.setTotalReferrals(entity.getTotalReferrals());
        dto.setSuccessfulReferrals(entity.getSuccessfulReferrals());
        dto.setTotalRewardValue(entity.getTotalRewardValue());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}