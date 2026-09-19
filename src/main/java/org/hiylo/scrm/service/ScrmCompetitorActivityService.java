/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCompetitorActivityService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmCompetitorActivityDto;
import org.hiylo.scrm.entity.ScrmCompetitorActivityEntity;
import org.hiylo.scrm.entity.ScrmCompetitorEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCompetitorActivityRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SCRM 竞品动态追踪服务。
 * <p>
 * 承载竞品动态子域全部能力: 动态录入、更新、删除、查询、多维度分页过滤、重大动态、
 * 待响应动态、应对流程 (制定/执行/完成)、验证、重要性评分与时间线。
 * </p>
 * <p>
 * 校验失败抛出 {@link ScrmException} 携带通用错误码 (NOT_FOUND / BAD_REQUEST / CONFLICT)。
 * 竞品校验委托 {@link ScrmCompetitorManagementService}。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCompetitorActivityService {

    // ==================== 默认值常量 ====================

    /** 默认动态影响等级 */
    private static final String DEFAULT_IMPACT_LEVEL = "MEDIUM";
    /** 默认应对状态 */
    private static final String DEFAULT_RESPONSE_STATUS = "PENDING";
    /** 默认重要性评分 */
    private static final int DEFAULT_IMPORTANCE_SCORE = 50;
    /** 默认是否已验证 */
    private static final boolean DEFAULT_VERIFIED = false;
    /** 默认操作人 */
    private static final String DEFAULT_OPERATOR = "scrm-system";
    /** 重要性评分上限 */
    private static final int MAX_IMPORTANCE_SCORE = 100;

    // ==================== 合法枚举值 ====================

    /** 合法的动态类型 */
    private static final List<String> VALID_ACTIVITY_TYPES = List.of(
            "PRODUCT_LAUNCH", "PRICE_CHANGE", "MARKETING_CAMPAIGN", "FUNDING", "PARTNERSHIP", "HIRING",
            "EXPANSION", "CONTENT", "PR_EVENT", "ACQUISITION", "PROMOTION", "FEATURE_UPDATE", "CRISIS", "OTHER");
    /** 合法的影响等级 */
    private static final List<String> VALID_IMPACT_LEVELS = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    /** 合法的应对状态 */
    private static final List<String> VALID_RESPONSE_STATUSES =
            List.of("PENDING", "PLANNING", "EXECUTING", "COMPLETED", "NO_ACTION");
    /** 合法的发现方式 */
    private static final List<String> VALID_DETECTION_METHODS = List.of("MANUAL", "AUTOMATED", "ALERT", "INTEL");
    /** 重大动态影响等级 */
    private static final List<String> CRITICAL_IMPACT_LEVELS = List.of("HIGH", "CRITICAL");

    // ==================== 依赖注入 ====================

    /** 竞品管理服务 */
    private final ScrmCompetitorManagementService competitorService;
    /** 竞品动态数据访问层 */
    private final ScrmCompetitorActivityRepository activityRepository;

    /**
     * 创建竞品动态。
     * <p>校验竞品存在, competitorName 缺省时从竞品实体填充, impactLevel / responseStatus /
     * importanceScore / isVerified 缺省时填默认值。</p>
     *
     * @param dto 动态参数
     * @return 创建后的动态
     * @throws ScrmException 参数非法 / 竞品不存在
     */
    @Transactional
    public ScrmCompetitorActivityEntity createActivity(ScrmCompetitorActivityDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("动态参数不能为空");
        }
        validateActivityEnums(dto, false);
        ScrmCompetitorEntity competitor = competitorService.findCompetitorOrThrow(dto.getCompetitorId());
        ScrmCompetitorActivityEntity entity = new ScrmCompetitorActivityEntity();
        entity.setCompetitorId(dto.getCompetitorId());
        entity.setCompetitorName(dto.getCompetitorName() != null
                ? dto.getCompetitorName()
                : competitor.getCompetitorName());
        entity.setActivityType(dto.getActivityType());
        entity.setTitle(dto.getTitle());
        entity.setSummary(dto.getSummary());
        entity.setDescription(dto.getDescription());
        entity.setActivityDate(dto.getActivityDate());
        entity.setSource(dto.getSource());
        entity.setSourceUrl(dto.getSourceUrl());
        entity.setImpactLevel(dto.getImpactLevel() != null ? dto.getImpactLevel() : DEFAULT_IMPACT_LEVEL);
        entity.setImpactAnalysis(dto.getImpactAnalysis());
        entity.setAffectedProducts(dto.getAffectedProducts());
        entity.setAffectedSegments(dto.getAffectedSegments());
        entity.setOurResponse(dto.getOurResponse());
        entity.setResponseStatus(dto.getResponseStatus() != null ? dto.getResponseStatus() : DEFAULT_RESPONSE_STATUS);
        entity.setResponseOwner(dto.getResponseOwner());
        entity.setResponseDueDate(dto.getResponseDueDate());
        entity.setDetectedBy(dto.getDetectedBy());
        entity.setDetectionMethod(dto.getDetectionMethod());
        entity.setImportanceScore(dto.getImportanceScore() != null
                ? dto.getImportanceScore()
                : DEFAULT_IMPORTANCE_SCORE);
        entity.setIsVerified(dto.getIsVerified() != null ? dto.getIsVerified() : DEFAULT_VERIFIED);
        entity.setVerifiedBy(dto.getVerifiedBy());
        entity.setVerifiedAt(dto.getVerifiedAt());
        entity.setTags(dto.getTags());
        entity.setAttachments(dto.getAttachments());
        entity.setRelatedActivityIds(dto.getRelatedActivityIds());
        entity.setCreatedBy(dto.getCreatedBy());
        entity = activityRepository.save(entity);
        log.info("创建竞品动态: id={}, competitorId={}, type={}, title={}",
                entity.getId(), entity.getCompetitorId(), entity.getActivityType(), entity.getTitle());
        return entity;
    }

    /**
     * 更新竞品动态（字段非空才覆盖）。
     *
     * @param id  动态 ID
     * @param dto 动态参数
     * @return 更新后的动态
     * @throws ScrmException 动态不存在 / 参数非法
     */
    @Transactional
    public ScrmCompetitorActivityEntity updateActivity(Long id, ScrmCompetitorActivityDto dto)
            throws ScrmException {
        ScrmCompetitorActivityEntity entity = findActivityOrThrow(id);
        if (dto == null) {
            throw ScrmException.badRequest("动态参数不能为空");
        }
        validateActivityEnums(dto, true);
        if (dto.getCompetitorId() != null) {
            competitorService.findCompetitorOrThrow(dto.getCompetitorId());
            entity.setCompetitorId(dto.getCompetitorId());
        }
        if (dto.getCompetitorName() != null) entity.setCompetitorName(dto.getCompetitorName());
        if (dto.getActivityType() != null) entity.setActivityType(dto.getActivityType());
        if (dto.getTitle() != null) entity.setTitle(dto.getTitle());
        if (dto.getSummary() != null) entity.setSummary(dto.getSummary());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getActivityDate() != null) entity.setActivityDate(dto.getActivityDate());
        if (dto.getSource() != null) entity.setSource(dto.getSource());
        if (dto.getSourceUrl() != null) entity.setSourceUrl(dto.getSourceUrl());
        if (dto.getImpactLevel() != null) entity.setImpactLevel(dto.getImpactLevel());
        if (dto.getImpactAnalysis() != null) entity.setImpactAnalysis(dto.getImpactAnalysis());
        if (dto.getAffectedProducts() != null) entity.setAffectedProducts(dto.getAffectedProducts());
        if (dto.getAffectedSegments() != null) entity.setAffectedSegments(dto.getAffectedSegments());
        if (dto.getOurResponse() != null) entity.setOurResponse(dto.getOurResponse());
        if (dto.getResponseStatus() != null) entity.setResponseStatus(dto.getResponseStatus());
        if (dto.getResponseOwner() != null) entity.setResponseOwner(dto.getResponseOwner());
        if (dto.getResponseDueDate() != null) entity.setResponseDueDate(dto.getResponseDueDate());
        if (dto.getDetectedBy() != null) entity.setDetectedBy(dto.getDetectedBy());
        if (dto.getDetectionMethod() != null) entity.setDetectionMethod(dto.getDetectionMethod());
        if (dto.getImportanceScore() != null) entity.setImportanceScore(dto.getImportanceScore());
        if (dto.getIsVerified() != null) entity.setIsVerified(dto.getIsVerified());
        if (dto.getVerifiedBy() != null) entity.setVerifiedBy(dto.getVerifiedBy());
        if (dto.getVerifiedAt() != null) entity.setVerifiedAt(dto.getVerifiedAt());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getAttachments() != null) entity.setAttachments(dto.getAttachments());
        if (dto.getRelatedActivityIds() != null) entity.setRelatedActivityIds(dto.getRelatedActivityIds());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = activityRepository.save(entity);
        log.info("更新竞品动态: id={}, title={}", entity.getId(), entity.getTitle());
        return entity;
    }

    /**
     * 删除竞品动态。
     *
     * @param id 动态 ID
     * @throws ScrmException 动态不存在
     */
    @Transactional
    public void deleteActivity(Long id) throws ScrmException {
        ScrmCompetitorActivityEntity entity = findActivityOrThrow(id);
        activityRepository.delete(entity);
        log.info("删除竞品动态: id={}, title={}", id, entity.getTitle());
    }

    /**
     * 查询动态详情。
     *
     * @param id 动态 ID
     * @return 动态实体
     * @throws ScrmException 动态不存在
     */
    @Transactional(readOnly = true)
    public ScrmCompetitorActivityEntity getActivity(Long id) throws ScrmException {
        return findActivityOrThrow(id);
    }

    /**
     * 分页查询动态, 支持按竞品、类型、影响等级、应对状态与时间范围过滤。
     *
     * @param competitorId   竞品 ID 过滤（可空）
     * @param activityType   动态类型过滤（可空）
     * @param impactLevel    影响等级过滤（可空）
     * @param responseStatus 应对状态过滤（可空）
     * @param startTime      动态日期起始 (含, 可空)
     * @param endTime        动态日期截止 (含, 可空)
     * @param pageable       分页参数
     * @return 动态分页结果 (按 activityDate DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmCompetitorActivityEntity> listActivities(Long competitorId, String activityType,
            String impactLevel, String responseStatus, LocalDate startTime, LocalDate endTime,
            Pageable pageable) {
        Specification<ScrmCompetitorActivityEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (competitorId != null) {
                predicates.add(cb.equal(root.get("competitorId"), competitorId));
            }
            if (activityType != null && !activityType.isBlank()) {
                predicates.add(cb.equal(root.get("activityType"), activityType));
            }
            if (impactLevel != null && !impactLevel.isBlank()) {
                predicates.add(cb.equal(root.get("impactLevel"), impactLevel));
            }
            if (responseStatus != null && !responseStatus.isBlank()) {
                predicates.add(cb.equal(root.get("responseStatus"), responseStatus));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("activityDate"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("activityDate"), endTime));
            }
            query.orderBy(cb.desc(root.get("activityDate")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return activityRepository.findAll(spec, pageable);
    }

    /**
     * 按竞品分页查询动态。
     *
     * @param competitorId 竞品 ID
     * @param pageable     分页参数
     * @return 动态分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCompetitorActivityEntity> getActivitiesByCompetitor(Long competitorId, Pageable pageable) {
        return activityRepository.findByCompetitorId(competitorId, pageable);
    }

    /**
     * 按动态类型分页查询。
     *
     * @param activityType 动态类型
     * @param pageable     分页参数
     * @return 动态分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCompetitorActivityEntity> getActivitiesByType(String activityType, Pageable pageable) {
        return activityRepository.findByActivityType(activityType, pageable);
    }

    /**
     * 近期动态查询。
     *
     * @param days     回溯天数
     * @param pageable 分页参数
     * @return 动态分页结果 (按 activityDate DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmCompetitorActivityEntity> getRecentActivities(int days, Pageable pageable) {
        LocalDate since = LocalDate.now().minusDays(Math.max(days, 0));
        return activityRepository.findByActivityDateAfter(since, pageable);
    }

    /**
     * 重大动态查询 (影响等级 HIGH / CRITICAL, 按重要性评分倒序)。
     *
     * @param limit 返回条数
     * @return 动态列表
     */
    @Transactional(readOnly = true)
    public List<ScrmCompetitorActivityEntity> getCriticalActivities(int limit) {
        int pageSize = Math.max(limit, 1);
        return activityRepository.findCritical(CRITICAL_IMPACT_LEVELS,
                PageRequest.of(0, pageSize)).getContent();
    }

    /**
     * 待响应动态查询 (responseStatus = PENDING)。
     *
     * @param pageable 分页参数
     * @return 动态分页结果 (按重要性评分倒序, 再按动态日期倒序)
     */
    @Transactional(readOnly = true)
    public Page<ScrmCompetitorActivityEntity> getPendingResponses(Pageable pageable) {
        Specification<ScrmCompetitorActivityEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("responseStatus"), "PENDING"));
            query.orderBy(cb.desc(root.get("importanceScore")), cb.desc(root.get("activityDate")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return activityRepository.findAll(spec, pageable);
    }

    /**
     * 制定应对策略。
     * <p>状态须为 PENDING, 置为 PLANNING 并记录策略、负责人与截止日期。</p>
     *
     * @param id       动态 ID
     * @param response 应对策略
     * @param owner    应对负责人
     * @param dueDate  应对截止日期
     * @return 更新后的动态
     * @throws ScrmException 动态不存在 / 状态非法
     */
    @Transactional
    public ScrmCompetitorActivityEntity planResponse(Long id, String response, String owner, LocalDate dueDate)
            throws ScrmException {
        ScrmCompetitorActivityEntity entity = findActivityOrThrow(id);
        if (!"PENDING".equals(entity.getResponseStatus())) {
            throw ScrmException.conflict("动态应对状态非法, 仅 PENDING 可制定应对: id=" + id
                    + ", status=" + entity.getResponseStatus());
        }
        entity.setOurResponse(response);
        entity.setResponseOwner(owner);
        entity.setResponseDueDate(dueDate);
        entity.setResponseStatus("PLANNING");
        entity = activityRepository.save(entity);
        log.info("制定竞品动态应对: id={}, owner={}, dueDate={}", id, owner, dueDate);
        return entity;
    }

    /**
     * 执行应对。
     * <p>状态须为 PLANNING, 置为 EXECUTING。</p>
     *
     * @param id 动态 ID
     * @return 更新后的动态
     * @throws ScrmException 动态不存在 / 状态非法
     */
    @Transactional
    public ScrmCompetitorActivityEntity executeResponse(Long id) throws ScrmException {
        ScrmCompetitorActivityEntity entity = findActivityOrThrow(id);
        if (!"PLANNING".equals(entity.getResponseStatus())) {
            throw ScrmException.conflict("动态应对状态非法, 仅 PLANNING 可执行应对: id=" + id
                    + ", status=" + entity.getResponseStatus());
        }
        entity.setResponseStatus("EXECUTING");
        entity = activityRepository.save(entity);
        log.info("执行竞品动态应对: id={}", id);
        return entity;
    }

    /**
     * 完成应对。
     * <p>状态须为 EXECUTING, 置为 COMPLETED 并记录应对结果。</p>
     *
     * @param id      动态 ID
     * @param outcome 应对结果 (追加到 ourResponse)
     * @return 更新后的动态
     * @throws ScrmException 动态不存在 / 状态非法
     */
    @Transactional
    public ScrmCompetitorActivityEntity completeResponse(Long id, String outcome) throws ScrmException {
        ScrmCompetitorActivityEntity entity = findActivityOrThrow(id);
        if (!"EXECUTING".equals(entity.getResponseStatus())) {
            throw ScrmException.conflict("动态应对状态非法, 仅 EXECUTING 可完成应对: id=" + id
                    + ", status=" + entity.getResponseStatus());
        }
        entity.setResponseStatus("COMPLETED");
        if (outcome != null && !outcome.isBlank()) {
            String existing = entity.getOurResponse() != null ? entity.getOurResponse() : "";
            entity.setOurResponse(existing.isBlank() ? outcome : existing + " | 完成: " + outcome);
        }
        entity = activityRepository.save(entity);
        log.info("完成竞品动态应对: id={}", id);
        return entity;
    }

    /**
     * 验证动态。
     *
     * @param id          动态 ID
     * @param verifierId 验证人 ID
     * @return 更新后的动态
     * @throws ScrmException 动态不存在
     */
    @Transactional
    public ScrmCompetitorActivityEntity verifyActivity(Long id, String verifierId) throws ScrmException {
        ScrmCompetitorActivityEntity entity = findActivityOrThrow(id);
        entity.setIsVerified(true);
        entity.setVerifiedBy(verifierId != null ? verifierId : currentOperator());
        entity.setVerifiedAt(LocalDateTime.now());
        entity = activityRepository.save(entity);
        log.info("验证竞品动态: id={}, verifier={}", id, entity.getVerifiedBy());
        return entity;
    }

    /**
     * 更新动态重要性评分。
     *
     * @param id    动态 ID
     * @param score 重要性评分 (0-100)
     * @return 更新后的动态
     * @throws ScrmException 动态不存在 / 评分越界
     */
    @Transactional
    public ScrmCompetitorActivityEntity updateImportance(Long id, int score) throws ScrmException {
        if (score < 0 || score > MAX_IMPORTANCE_SCORE) {
            throw ScrmException.badRequest("重要性评分越界 (0-100): " + score);
        }
        ScrmCompetitorActivityEntity entity = findActivityOrThrow(id);
        entity.setImportanceScore(score);
        entity = activityRepository.save(entity);
        log.info("更新竞品动态重要性: id={}, score={}", id, score);
        return entity;
    }

    /**
     * 动态时间线: 按竞品聚合最近 months 个月的动态, 按动态日期倒序。
     *
     * @param competitorId 竞品 ID
     * @param months       回溯月数
     * @return 动态列表
     * @throws ScrmException 竞品不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmCompetitorActivityEntity> getActivityTimeline(Long competitorId, int months)
            throws ScrmException {
        competitorService.findCompetitorOrThrow(competitorId);
        LocalDate since = LocalDate.now().minusMonths(Math.max(months, 0));
        Specification<ScrmCompetitorActivityEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("competitorId"), competitorId));
            predicates.add(cb.greaterThanOrEqualTo(root.get("activityDate"), since));
            query.orderBy(cb.desc(root.get("activityDate")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return activityRepository.findAll(spec, PageRequest.of(0, Integer.MAX_VALUE)).getContent();
    }

    /**
     * 校验动态枚举字段合法性。
     *
     * @param dto     动态参数
     * @param partial 是否为部分更新
     * @throws ScrmException 参数非法
     */
    private void validateActivityEnums(ScrmCompetitorActivityDto dto, boolean partial) throws ScrmException {
        if (dto.getActivityType() != null && !VALID_ACTIVITY_TYPES.contains(dto.getActivityType())) {
            throw ScrmException.badRequest("动态类型非法: " + dto.getActivityType() + ", 仅支持 " + VALID_ACTIVITY_TYPES);
        }
        if (dto.getImpactLevel() != null && !VALID_IMPACT_LEVELS.contains(dto.getImpactLevel())) {
            throw ScrmException.badRequest("影响等级非法: " + dto.getImpactLevel() + ", 仅支持 " + VALID_IMPACT_LEVELS);
        }
        if (dto.getResponseStatus() != null && !VALID_RESPONSE_STATUSES.contains(dto.getResponseStatus())) {
            throw ScrmException.badRequest("应对状态非法: " + dto.getResponseStatus()
                    + ", 仅支持 " + VALID_RESPONSE_STATUSES);
        }
        if (dto.getDetectionMethod() != null && !VALID_DETECTION_METHODS.contains(dto.getDetectionMethod())) {
            throw ScrmException.badRequest("发现方式非法: " + dto.getDetectionMethod()
                    + ", 仅支持 " + VALID_DETECTION_METHODS);
        }
        if (dto.getImportanceScore() != null && (dto.getImportanceScore() < 0 || dto.getImportanceScore() > MAX_IMPORTANCE_SCORE)) {
            throw ScrmException.badRequest("重要性评分越界 (0-100): " + dto.getImportanceScore());
        }
    }

    /**
     * 获取当前操作人 (优先从 UserContext 获取)。
     *
     * @return 操作人用户名
     */
    private String currentOperator() {
        String username = UserContext.getUsername();
        return username != null ? username : DEFAULT_OPERATOR;
    }

    /**
     * 按主键查询动态, 不存在抛异常, 并校验账号归属。
     *
     * @param id 动态 ID
     * @return 动态实体
     * @throws ScrmException 动态不存在
     */
    private ScrmCompetitorActivityEntity findActivityOrThrow(Long id) throws ScrmException {
        ScrmCompetitorActivityEntity entity = activityRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "竞品动态不存在: id=" + id));
        return entity;
    }
}