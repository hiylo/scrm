/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNpsSurveyResponseService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmSurveySubmitDto;
import org.hiylo.scrm.entity.ScrmSurveyEntity;
import org.hiylo.scrm.entity.ScrmSurveyInvitationEntity;
import org.hiylo.scrm.entity.ScrmSurveyResponseEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmSurveyInvitationRepository;
import org.hiylo.scrm.repository.ScrmSurveyRepository;
import org.hiylo.scrm.repository.ScrmSurveyResponseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户满意度 NPS 调查 - 回复管理子域服务。
 * <p>
 * 承载调查回复收集能力: 提交回答 (计算 NPS / CSAT / CES 分数 → 情感分析 → 保存 → 更新
 * 邀请与问卷统计)、回复查询与跟进状态 / 标签管理。共享问卷管理子域的问卷与邀请查询、
 * JSON 解析能力。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmNpsSurveyResponseService {

    /** NPS 推荐者下限 (9-10) */
    private static final int NPS_PROMOTER_MIN = 9;
    /** NPS 被动者下限 (7-8) */
    private static final int NPS_PASSIVE_MIN = 7;

    /** CSAT 正向下限 (4-5) */
    private static final int CSAT_POSITIVE_MIN = 4;
    /** CSAT 中性值 (3) */
    private static final int CSAT_NEUTRAL = 3;

    /** CES 正向上限 (1-2, 低费力 = 正向) */
    private static final int CES_POSITIVE_MAX = 2;
    /** CES 中性上限 (3-5) */
    private static final int CES_NEUTRAL_MAX = 5;

    /** 量表类型: NPS_0_10 */
    private static final String SCALE_NPS = "NPS_0_10";
    /** 量表类型: CSAT_1_5 */
    private static final String SCALE_CSAT = "CSAT_1_5";
    /** 量表类型: CES_1_7 */
    private static final String SCALE_CES = "CES_1_7";

    /** 情感: 正向 */
    private static final String SENTIMENT_POSITIVE = "POSITIVE";
    /** 情感: 中性 */
    private static final String SENTIMENT_NEUTRAL = "NEUTRAL";
    /** 情感: 负向 */
    private static final String SENTIMENT_NEGATIVE = "NEGATIVE";

    /** 问卷状态: 进行中 */
    private static final String STATUS_ACTIVE = "ACTIVE";

    /** 邀请状态: 已完成 */
    private static final String INVITATION_COMPLETED = "COMPLETED";
    /** 邀请状态: 已过期 */
    private static final String INVITATION_EXPIRED = "EXPIRED";

    /** 跟进状态: 待跟进 */
    private static final String FOLLOW_UP_PENDING = "PENDING";
    /** 跟进状态: 进行中 */
    private static final String FOLLOW_UP_IN_PROGRESS = "IN_PROGRESS";
    /** 跟进状态: 已完成 */
    private static final String FOLLOW_UP_COMPLETED = "COMPLETED";

    /** 合法的跟进状态 */
    private static final List<String> VALID_FOLLOW_UP_STATUS = List.of(
            FOLLOW_UP_PENDING, FOLLOW_UP_IN_PROGRESS, FOLLOW_UP_COMPLETED);

    /** 调查回复数据访问层 */
    private final ScrmSurveyResponseRepository responseRepository;

    /** 调查邀请数据访问层 */
    private final ScrmSurveyInvitationRepository invitationRepository;

    /** 调查问卷数据访问层 */
    private final ScrmSurveyRepository surveyRepository;

    /** JSON 解析器 (解析回答) */
    private final ObjectMapper objectMapper;

    /** 问卷管理子域服务 (共享问卷与邀请查询、JSON 解析) */
    private final ScrmNpsSurveyManageService manageService;

    /**
     * 提交调查回答。
     * <p>
     * 流程: 校验邀请码 → 加载邀请与问卷 (问卷须 ACTIVE) → 校验邀请状态 (SENT/OPENED/IN_PROGRESS) →
     * 解析回答 JSON → 计算 NPS / CSAT / CES 分数 → 判定情感倾向 → 标记是否需要跟进 →
     * 保存回复 → 更新邀请状态为 COMPLETED → 增量更新问卷统计 (回复数 / 完成率)。
     * </p>
     * <p>NPS / CSAT / CES 分数由回答中匹配问卷题目的量表类型自动计算; 情感判定优先级
     * NPS &gt; CSAT &gt; CES, 负面情感自动标记需要跟进。</p>
     *
     * @param submitDto 提交参数 (invitationCode + responses + customerId)
     * @return 创建后的回复
     * @throws ScrmException 邀请不存在 / 问卷不存在 / 状态非法 / 回答 JSON 解析失败
     */
    @Transactional
    public ScrmSurveyResponseEntity submitResponse(ScrmSurveySubmitDto submitDto) throws ScrmException {
        if (submitDto == null) {
            throw ScrmException.badRequest("提交参数不能为空");
        }
        if (submitDto.getInvitationCode() == null || submitDto.getInvitationCode().isBlank()) {
            throw ScrmException.badRequest("邀请码不能为空");
        }
        if (submitDto.getResponses() == null || submitDto.getResponses().isBlank()) {
            throw ScrmException.badRequest("回答列表不能为空");
        }
        ScrmSurveyInvitationEntity invitation = manageService.findInvitationByCodeOrThrow(submitDto.getInvitationCode());
        if (INVITATION_COMPLETED.equals(invitation.getStatus())) {
            throw ScrmException.conflict(
                    "邀请已完成, 不允许重复提交: code=" + submitDto.getInvitationCode());
        }
        if (INVITATION_EXPIRED.equals(invitation.getStatus()) || "BOUNCED".equals(invitation.getStatus())) {
            throw ScrmException.conflict("邀请已失效, 不允许提交: code=" + submitDto.getInvitationCode()
                    + ", status=" + invitation.getStatus());
        }
        ScrmSurveyEntity survey = manageService.findSurveyOrThrow(invitation.getSurveyId());
        if (!STATUS_ACTIVE.equals(survey.getStatus())) {
            throw ScrmException.conflict("问卷未激活, 不允许提交: surveyId=" + survey.getId()
                    + ", status=" + survey.getStatus());
        }
        // 校验回答 JSON 可解析
        List<Map<String, Object>> responseList = manageService.parseJsonList(submitDto.getResponses(), "回答");
        // 客户 ID: 入参优先, 缺省取邀请记录
        Long customerId = submitDto.getCustomerId() != null ? submitDto.getCustomerId() : invitation.getCustomerId();
        if (!customerId.equals(invitation.getCustomerId())) {
            throw ScrmException.badRequest("客户 ID 与邀请归属不一致: customerId=" + customerId
                    + ", invitationCustomerId=" + invitation.getCustomerId());
        }
        // 计算 NPS / CSAT / CES 分数
        ScoreResult scores = computeScores(survey, responseList);
        // 判定情感倾向
        String sentiment = determineSentiment(scores);
        // 是否需要跟进: 负面情感
        boolean followUpRequired = SENTIMENT_NEGATIVE.equals(sentiment);
        // 保存回复
        ScrmSurveyResponseEntity entity = new ScrmSurveyResponseEntity();
        entity.setSurveyId(survey.getId());
        entity.setInvitationId(invitation.getId());
        entity.setCustomerId(customerId);
        entity.setCustomerName(invitation.getCustomerName());
        entity.setResponses(submitDto.getResponses());
        entity.setNpsScore(scores.nps);
        entity.setCsatScore(scores.csat);
        entity.setCesScore(scores.ces);
        entity.setOverallScore(scores.overall);
        entity.setSentiment(sentiment);
        entity.setFeedbackText(submitDto.getFeedbackText());
        entity.setFollowUpRequired(followUpRequired);
        if (followUpRequired) {
            entity.setFollowUpStatus(FOLLOW_UP_PENDING);
        }
        entity.setSubmittedAt(LocalDateTime.now());
        entity.setDurationSeconds(submitDto.getDurationSeconds());
        entity.setClientIp(submitDto.getClientIp());
        entity.setUserAgent(submitDto.getUserAgent());
        entity = responseRepository.save(entity);
        // 更新邀请状态为已完成
        invitation.setStatus(INVITATION_COMPLETED);
        invitation.setCompletedAt(LocalDateTime.now());
        invitationRepository.save(invitation);
        // 增量更新问卷统计 (回复数 +1, 完成率重算)
        long totalInvitations = invitationRepository.countBySurveyId(
                survey.getId());
        long completedInvitations = invitationRepository.countBySurveyIdAndStatus(
                survey.getId(), INVITATION_COMPLETED);
        double completionRate = totalInvitations == 0 ? 0.0 : (double) completedInvitations / totalInvitations;
        try {
            surveyRepository.incrementResponseCount(survey.getId(), 1, completionRate, LocalDateTime.now());
        } catch (Exception e) {
            log.warn("更新问卷回复计数失败, 忽略: surveyId={}, err={}", survey.getId(), e.getMessage());
        }
        log.info("提交调查回答: responseId={}, surveyId={}, customerId={}, nps={}, csat={}, ces={}, sentiment={}",
                entity.getId(), survey.getId(), customerId, scores.nps, scores.csat, scores.ces, sentiment);
        return entity;
    }

    /**
     * 查询回复详情。
     *
     * @param id 回复 ID
     * @return 回复实体
     * @throws ScrmException 回复不存在
     */
    @Transactional(readOnly = true)
    public ScrmSurveyResponseEntity getResponse(Long id) throws ScrmException {
        return findResponseOrThrow(id);
    }

    /**
     * 分页查询回复, 支持按问卷、客户、NPS 分数、情感、是否需要跟进与时间范围过滤。
     *
     * @param surveyId         问卷 ID 过滤（可空）
     * @param customerId       客户 ID 过滤（可空）
     * @param npsScore         NPS 分数过滤（可空, 精确匹配）
     * @param sentiment        情感过滤（可空）: POSITIVE/NEUTRAL/NEGATIVE
     * @param followUpRequired 是否需要跟进过滤（可空）
     * @param startTime        提交时间起始 (含, 可空)
     * @param endTime          提交时间截止 (含, 可空)
     * @param pageable         分页参数
     * @return 回复分页结果 (按 submittedAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmSurveyResponseEntity> listResponses(Long surveyId, Long customerId, Integer npsScore,
                                                          String sentiment, Boolean followUpRequired,
                                                          LocalDateTime startTime, LocalDateTime endTime,
                                                          Pageable pageable) {
        Specification<ScrmSurveyResponseEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (surveyId != null) {
                predicates.add(cb.equal(root.get("surveyId"), surveyId));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (npsScore != null) {
                predicates.add(cb.equal(root.get("npsScore"), npsScore));
            }
            if (sentiment != null && !sentiment.isBlank()) {
                predicates.add(cb.equal(root.get("sentiment"), sentiment));
            }
            if (followUpRequired != null) {
                predicates.add(cb.equal(root.get("followUpRequired"), followUpRequired));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("submittedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("submittedAt"), endTime));
            }
            query.orderBy(cb.desc(root.get("submittedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return responseRepository.findAll(spec, pageable);
    }

    /**
     * 更新回复跟进状态。
     *
     * @param id          回复 ID
     * @param status      跟进状态: PENDING / IN_PROGRESS / COMPLETED
     * @param assigneeId  跟进人 ID (可空)
     * @return 更新后的回复
     * @throws ScrmException 回复不存在 / 跟进状态非法
     */
    @Transactional
    public ScrmSurveyResponseEntity updateFollowUpStatus(Long id, String status, String assigneeId)
            throws ScrmException {
        if (status == null || !VALID_FOLLOW_UP_STATUS.contains(status)) {
            throw ScrmException.badRequest(
                    "跟进状态非法: " + status + ", 仅支持 " + VALID_FOLLOW_UP_STATUS);
        }
        ScrmSurveyResponseEntity entity = findResponseOrThrow(id);
        entity.setFollowUpStatus(status);
        if (assigneeId != null) {
            entity.setAssigneeId(assigneeId);
        }
        if (FOLLOW_UP_COMPLETED.equals(status) && !Boolean.TRUE.equals(entity.getFollowUpRequired())) {
            entity.setFollowUpRequired(true);
        }
        entity = responseRepository.save(entity);
        log.info("更新回复跟进状态: id={}, status={}, assigneeId={}", id, status, assigneeId);
        return entity;
    }

    /**
     * 添加标签 (追加, 不覆盖已有标签)。
     *
     * @param id    回复 ID
     * @param tags  待添加标签 (逗号分隔)
     * @return 更新后的回复
     * @throws ScrmException 回复不存在
     */
    @Transactional
    public ScrmSurveyResponseEntity addTags(Long id, String tags) throws ScrmException {
        if (tags == null || tags.isBlank()) {
            throw ScrmException.badRequest("标签不能为空");
        }
        ScrmSurveyResponseEntity entity = findResponseOrThrow(id);
        String existing = entity.getTags();
        String merged;
        if (existing == null || existing.isBlank()) {
            merged = tags;
        } else {
            // 去重合并
            List<String> existingList = new ArrayList<>(List.of(existing.split(",")));
            for (String t : tags.split(",")) {
                String trimmed = t.trim();
                if (!trimmed.isEmpty() && !existingList.contains(trimmed)) {
                    existingList.add(trimmed);
                }
            }
            merged = String.join(",", existingList);
        }
        entity.setTags(merged);
        entity = responseRepository.save(entity);
        log.info("添加回复标签: id={}, tags={}", id, merged);
        return entity;
    }

    /**
     * 根据问卷题目量表类型计算 NPS / CSAT / CES 分数与综合得分。
     * <p>遍历回答, 匹配问卷题目 (按 questionId) 的 scale 字段:
     * NPS_0_10 → npsScore, CSAT_1_5 → csatScore, CES_1_7 → cesScore。
     * overallScore = 各量表归一化 (NPS/10, CSAT/5, CES/7) 后的加权平均 (CES 反向, 1 为最佳)。</p>
     *
     * @param survey       问卷实体
     * @param responseList 回答列表
     * @return 分数结果
     */
    private ScoreResult computeScores(ScrmSurveyEntity survey, List<Map<String, Object>> responseList) {
        ScoreResult result = new ScoreResult();
        if (responseList.isEmpty()) {
            return result;
        }
        // 解析问卷题目, 构建 questionId → scale 映射
        Map<String, String> questionScales = new HashMap<>();
        try {
            List<Map<String, Object>> questions = manageService.parseJsonList(survey.getQuestions(), "问题列表");
            for (Map<String, Object> q : questions) {
                Object id = q.get("id");
                Object scale = q.get("scale");
                if (id != null && scale != null) {
                    questionScales.put(id.toString(), scale.toString());
                }
            }
        } catch (ScrmException e) {
            // 题目解析失败, 退化为按问卷类型推断
            log.warn("问卷题目解析失败, 退化为按问卷类型推断: surveyId={}, err={}",
                    survey.getId(), e.getMessage());
        }
        // 问卷级别量表 (无题目级 scale 时使用)
        String surveyScale = survey.getScaleType();
        String surveyType = survey.getSurveyType();
        for (Map<String, Object> resp : responseList) {
            Object qidObj = resp.get("questionId");
            Object valueObj = resp.get("value");
            if (valueObj == null) {
                continue;
            }
            Integer value = toInt(valueObj);
            if (value == null) {
                continue;
            }
            String scale = null;
            if (qidObj != null) {
                scale = questionScales.get(qidObj.toString());
            }
            if (scale == null) {
                scale = surveyScale;
            }
            if (scale == null) {
                // 按问卷类型推断
                if ("NPS".equals(surveyType)) {
                    scale = SCALE_NPS;
                } else if ("CSAT".equals(surveyType)) {
                    scale = SCALE_CSAT;
                } else if ("CES".equals(surveyType)) {
                    scale = SCALE_CES;
                }
            }
            if (SCALE_NPS.equals(scale) && result.nps == null) {
                result.nps = Math.max(0, Math.min(10, value));
            } else if (SCALE_CSAT.equals(scale) && result.csat == null) {
                result.csat = Math.max(1, Math.min(5, value));
            } else if (SCALE_CES.equals(scale) && result.ces == null) {
                result.ces = Math.max(1, Math.min(7, value));
            }
        }
        // 综合得分: 各量表归一化加权平均 (CES 反向, 1 为最佳 → (7 - ces + 1) / 7)
        List<Double> normalized = new ArrayList<>();
        if (result.nps != null) {
            normalized.add(result.nps / 10.0);
        }
        if (result.csat != null) {
            normalized.add(result.csat / 5.0);
        }
        if (result.ces != null) {
            normalized.add((7.0 - result.ces + 1.0) / 7.0);
        }
        if (!normalized.isEmpty()) {
            result.overall = normalized.stream().mapToDouble(d -> d).average().orElse(0.0);
        }
        return result;
    }

    /**
     * 判定情感倾向 (NPS &gt; CSAT &gt; CES 优先级)。
     *
     * @param scores 分数结果
     * @return 情感: POSITIVE / NEUTRAL / NEGATIVE
     */
    private String determineSentiment(ScoreResult scores) {
        if (scores.nps != null) {
            if (scores.nps >= NPS_PROMOTER_MIN) {
                return SENTIMENT_POSITIVE;
            } else if (scores.nps >= NPS_PASSIVE_MIN) {
                return SENTIMENT_NEUTRAL;
            } else {
                return SENTIMENT_NEGATIVE;
            }
        }
        if (scores.csat != null) {
            if (scores.csat >= CSAT_POSITIVE_MIN) {
                return SENTIMENT_POSITIVE;
            } else if (scores.csat == CSAT_NEUTRAL) {
                return SENTIMENT_NEUTRAL;
            } else {
                return SENTIMENT_NEGATIVE;
            }
        }
        if (scores.ces != null) {
            if (scores.ces <= CES_POSITIVE_MAX) {
                return SENTIMENT_POSITIVE;
            } else if (scores.ces <= CES_NEUTRAL_MAX) {
                return SENTIMENT_NEUTRAL;
            } else {
                return SENTIMENT_NEGATIVE;
            }
        }
        return SENTIMENT_NEUTRAL;
    }

    /**
     * 将对象转为 Integer, 失败返回 null。
     *
     * @param value 对象值
     * @return Integer 值
     */
    private Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 按主键查询回复, 不存在抛异常, 并校验归属账号。
     *
     * @param id 回复 ID
     * @return 回复实体
     * @throws ScrmException 回复不存在
     */
    private ScrmSurveyResponseEntity findResponseOrThrow(Long id) throws ScrmException {
        ScrmSurveyResponseEntity entity = responseRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "调查回复不存在: id=" + id));
        return entity;
    }

    /**
     * 分数计算中间结果。
     *
     * @author Hsi Chu
     * @since V1.0
     */
    private static class ScoreResult {
        /** NPS 得分 0-10 (可空) */
        Integer nps;
        /** CSAT 得分 1-5 (可空) */
        Integer csat;
        /** CES 得分 1-7 (可空) */
        Integer ces;
        /** 综合得分 0-1 (可空) */
        Double overall;
    }
}