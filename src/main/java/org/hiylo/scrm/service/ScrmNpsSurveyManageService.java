/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNpsSurveyManageService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmSurveyDto;
import org.hiylo.scrm.entity.ScrmSurveyEntity;
import org.hiylo.scrm.entity.ScrmSurveyInvitationEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmSurveyInvitationRepository;
import org.hiylo.scrm.repository.ScrmSurveyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户满意度 NPS 调查 - 问卷管理子域服务。
 * <p>
 * 承载调查问卷管理能力: 问卷 CRUD、状态机 (草稿/进行中/暂停/完成/归档)、复制,
 * 以及通过邀请码查询问卷。共享 {@link #findSurveyOrThrow} / {@link #findInvitationByCodeOrThrow}
 * / {@link #parseJsonList} 供邀请、回复、基准统计子域复用。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmNpsSurveyManageService {

    /** 默认触发延迟小时 */
    private static final int DEFAULT_TRIGGER_DELAY_HOURS = 0;

    /** 默认预计完成时间分钟 */
    private static final int DEFAULT_ESTIMATED_TIME_MINUTES = 2;

    /** 默认回复数初值 */
    private static final int DEFAULT_RESPONSE_COUNT = 0;

    /** 默认完成率初值 */
    private static final double DEFAULT_COMPLETION_RATE = 0.0;

    /** 默认操作人 (请求头未透传时使用) */
    private static final String DEFAULT_OPERATOR = "scrm-system";

    /** 问卷状态: 草稿 */
    private static final String STATUS_DRAFT = "DRAFT";
    /** 问卷状态: 进行中 */
    private static final String STATUS_ACTIVE = "ACTIVE";
    /** 问卷状态: 暂停 */
    private static final String STATUS_PAUSED = "PAUSED";
    /** 问卷状态: 已完成 */
    private static final String STATUS_COMPLETED = "COMPLETED";
    /** 问卷状态: 已归档 */
    private static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 合法的调查类型 */
    private static final List<String> VALID_SURVEY_TYPES = List.of("NPS", "CSAT", "CES", "CUSTOM");

    /** 合法的问卷状态 */
    private static final List<String> VALID_SURVEY_STATUS = List.of(
            STATUS_DRAFT, STATUS_ACTIVE, STATUS_PAUSED, STATUS_COMPLETED, STATUS_ARCHIVED);

    /** 合法的量表类型 */
    private static final List<String> VALID_SCALE_TYPES = List.of("NPS_0_10", "CSAT_1_5", "CES_1_7");

    /** 调查问卷数据访问层 */
    private final ScrmSurveyRepository surveyRepository;

    /** 调查邀请数据访问层 */
    private final ScrmSurveyInvitationRepository invitationRepository;

    /** JSON 解析器 (解析 questions) */
    private final ObjectMapper objectMapper;

    /**
     * 创建调查问卷。
     * <p>校验 surveyType / scaleType / questions JSON 合法性后写入归属账号 ID 持久化,
     * triggerDelayHours / estimatedTimeMinutes / responseCount / completionRate 缺省时填默认值,
     * status 缺省 DRAFT。</p>
     *
     * @param dto 问卷参数
     * @return 创建后的问卷
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmSurveyEntity createSurvey(ScrmSurveyDto dto) throws ScrmException {
        validateSurveyDto(dto, false);
        ScrmSurveyEntity entity = new ScrmSurveyEntity();
        entity.setSurveyName(dto.getSurveyName());
        entity.setSurveyType(dto.getSurveyType());
        entity.setDescription(dto.getDescription());
        entity.setTitle(dto.getTitle());
        entity.setIntroText(dto.getIntroText());
        entity.setOutroText(dto.getOutroText());
        entity.setQuestions(dto.getQuestions());
        entity.setScaleType(dto.getScaleType());
        entity.setTriggerEvent(dto.getTriggerEvent());
        entity.setTriggerDelayHours(dto.getTriggerDelayHours() != null
                ? dto.getTriggerDelayHours() : DEFAULT_TRIGGER_DELAY_HOURS);
        entity.setTargetSegment(dto.getTargetSegment());
        entity.setChannels(dto.getChannels());
        entity.setEstimatedTimeMinutes(dto.getEstimatedTimeMinutes() != null
                ? dto.getEstimatedTimeMinutes() : DEFAULT_ESTIMATED_TIME_MINUTES);
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : STATUS_DRAFT);
        entity.setResponseCount(DEFAULT_RESPONSE_COUNT);
        entity.setCompletionRate(DEFAULT_COMPLETION_RATE);
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : currentOperator());
        entity = surveyRepository.save(entity);
        log.info("创建调查问卷: id={}, surveyName={}, surveyType={}",
                entity.getId(), entity.getSurveyName(), entity.getSurveyType());
        return entity;
    }

    /**
     * 更新调查问卷（字段非空才覆盖）。
     * <p>已归档问卷不允许更新。</p>
     *
     * @param id  问卷 ID
     * @param dto 问卷参数
     * @return 更新后的问卷
     * @throws ScrmException 问卷不存在 / 已归档 / 参数非法
     */
    @Transactional
    public ScrmSurveyEntity updateSurvey(Long id, ScrmSurveyDto dto) throws ScrmException {
        ScrmSurveyEntity entity = findSurveyOrThrow(id);
        if (STATUS_ARCHIVED.equals(entity.getStatus())) {
            throw ScrmException.conflict("问卷已归档, 不允许更新: id=" + id);
        }
        validateSurveyDto(dto, true);
        if (dto.getSurveyName() != null) entity.setSurveyName(dto.getSurveyName());
        if (dto.getSurveyType() != null) entity.setSurveyType(dto.getSurveyType());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getTitle() != null) entity.setTitle(dto.getTitle());
        if (dto.getIntroText() != null) entity.setIntroText(dto.getIntroText());
        if (dto.getOutroText() != null) entity.setOutroText(dto.getOutroText());
        if (dto.getQuestions() != null) entity.setQuestions(dto.getQuestions());
        if (dto.getScaleType() != null) entity.setScaleType(dto.getScaleType());
        if (dto.getTriggerEvent() != null) entity.setTriggerEvent(dto.getTriggerEvent());
        if (dto.getTriggerDelayHours() != null) entity.setTriggerDelayHours(dto.getTriggerDelayHours());
        if (dto.getTargetSegment() != null) entity.setTargetSegment(dto.getTargetSegment());
        if (dto.getChannels() != null) entity.setChannels(dto.getChannels());
        if (dto.getEstimatedTimeMinutes() != null) entity.setEstimatedTimeMinutes(dto.getEstimatedTimeMinutes());
        if (dto.getStartDate() != null) entity.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null) entity.setEndDate(dto.getEndDate());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = surveyRepository.save(entity);
        log.info("更新调查问卷: id={}, surveyName={}", entity.getId(), entity.getSurveyName());
        return entity;
    }

    /**
     * 删除调查问卷。
     * <p>已激活问卷不允许删除, 需先归档。</p>
     *
     * @param id 问卷 ID
     * @throws ScrmException 问卷不存在 / 状态非法
     */
    @Transactional
    public void deleteSurvey(Long id) throws ScrmException {
        ScrmSurveyEntity entity = findSurveyOrThrow(id);
        if (STATUS_ACTIVE.equals(entity.getStatus())) {
            throw ScrmException.conflict("问卷进行中, 不允许删除, 请先暂停或归档: id=" + id);
        }
        surveyRepository.delete(entity);
        log.info("删除调查问卷: id={}, surveyName={}", id, entity.getSurveyName());
    }

    /**
     * 查询问卷详情。
     *
     * @param id 问卷 ID
     * @return 问卷实体
     * @throws ScrmException 问卷不存在
     */
    @Transactional(readOnly = true)
    public ScrmSurveyEntity getSurvey(Long id) throws ScrmException {
        return findSurveyOrThrow(id);
    }

    /**
     * 分页查询问卷, 支持按调查类型、状态与关键字过滤。
     *
     * @param surveyType 调查类型过滤（可空）: NPS/CSAT/CES/CUSTOM
     * @param status     状态过滤（可空）: DRAFT/ACTIVE/PAUSED/COMPLETED/ARCHIVED
     * @param keyword    问卷名称关键字模糊匹配（可空）
     * @param pageable   分页参数
     * @return 问卷分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmSurveyEntity> listSurveys(String surveyType, String status, String keyword, Pageable pageable) {
        Specification<ScrmSurveyEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (surveyType != null && !surveyType.isBlank()) {
                predicates.add(cb.equal(root.get("surveyType"), surveyType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword + "%";
                predicates.add(cb.or(cb.like(root.get("surveyName"), like), cb.like(root.get("title"), like)));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return surveyRepository.findAll(spec, pageable);
    }

    /**
     * 激活问卷 (DRAFT/PAUSED → ACTIVE)。
     *
     * @param id 问卷 ID
     * @return 更新后的问卷
     * @throws ScrmException 问卷不存在 / 状态非法
     */
    @Transactional
    public ScrmSurveyEntity activateSurvey(Long id) throws ScrmException {
        ScrmSurveyEntity entity = findSurveyOrThrow(id);
        if (!STATUS_DRAFT.equals(entity.getStatus()) && !STATUS_PAUSED.equals(entity.getStatus())) {
            throw ScrmException.conflict(
                    "问卷状态不允许激活: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(STATUS_ACTIVE);
        entity = surveyRepository.save(entity);
        log.info("激活调查问卷: id={}, surveyName={}", id, entity.getSurveyName());
        return entity;
    }

    /**
     * 暂停问卷 (ACTIVE → PAUSED)。
     *
     * @param id 问卷 ID
     * @return 更新后的问卷
     * @throws ScrmException 问卷不存在 / 状态非法
     */
    @Transactional
    public ScrmSurveyEntity pauseSurvey(Long id) throws ScrmException {
        ScrmSurveyEntity entity = findSurveyOrThrow(id);
        if (!STATUS_ACTIVE.equals(entity.getStatus())) {
            throw ScrmException.conflict(
                    "问卷状态不允许暂停: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(STATUS_PAUSED);
        entity = surveyRepository.save(entity);
        log.info("暂停调查问卷: id={}, surveyName={}", id, entity.getSurveyName());
        return entity;
    }

    /**
     * 完成问卷 (ACTIVE/PAUSED → COMPLETED)。
     *
     * @param id 问卷 ID
     * @return 更新后的问卷
     * @throws ScrmException 问卷不存在 / 状态非法
     */
    @Transactional
    public ScrmSurveyEntity completeSurvey(Long id) throws ScrmException {
        ScrmSurveyEntity entity = findSurveyOrThrow(id);
        if (STATUS_COMPLETED.equals(entity.getStatus()) || STATUS_ARCHIVED.equals(entity.getStatus())) {
            throw ScrmException.conflict(
                    "问卷状态不允许完成: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(STATUS_COMPLETED);
        entity = surveyRepository.save(entity);
        log.info("完成调查问卷: id={}, surveyName={}", id, entity.getSurveyName());
        return entity;
    }

    /**
     * 归档问卷 (任意状态 → ARCHIVED)。
     *
     * @param id 问卷 ID
     * @return 更新后的问卷
     * @throws ScrmException 问卷不存在
     */
    @Transactional
    public ScrmSurveyEntity archiveSurvey(Long id) throws ScrmException {
        ScrmSurveyEntity entity = findSurveyOrThrow(id);
        if (STATUS_ARCHIVED.equals(entity.getStatus())) {
            throw ScrmException.conflict("问卷已归档: id=" + id);
        }
        entity.setStatus(STATUS_ARCHIVED);
        entity = surveyRepository.save(entity);
        log.info("归档调查问卷: id={}, surveyName={}", id, entity.getSurveyName());
        return entity;
    }

    /**
     * 复制问卷 (创建副本, 状态重置为 DRAFT, 统计清零)。
     *
     * @param id 源问卷 ID
     * @return 复制后的新问卷
     * @throws ScrmException 源问卷不存在
     */
    @Transactional
    public ScrmSurveyEntity copySurvey(Long id) throws ScrmException {
        ScrmSurveyEntity source = findSurveyOrThrow(id);
        ScrmSurveyEntity copy = new ScrmSurveyEntity();
        copy.setSurveyName(source.getSurveyName() + " (副本)");
        copy.setSurveyType(source.getSurveyType());
        copy.setDescription(source.getDescription());
        copy.setTitle(source.getTitle());
        copy.setIntroText(source.getIntroText());
        copy.setOutroText(source.getOutroText());
        copy.setQuestions(source.getQuestions());
        copy.setScaleType(source.getScaleType());
        copy.setTriggerEvent(source.getTriggerEvent());
        copy.setTriggerDelayHours(source.getTriggerDelayHours());
        copy.setTargetSegment(source.getTargetSegment());
        copy.setChannels(source.getChannels());
        copy.setEstimatedTimeMinutes(source.getEstimatedTimeMinutes());
        copy.setStartDate(source.getStartDate());
        copy.setEndDate(source.getEndDate());
        copy.setStatus(STATUS_DRAFT);
        copy.setResponseCount(DEFAULT_RESPONSE_COUNT);
        copy.setCompletionRate(DEFAULT_COMPLETION_RATE);
        copy.setCreatedBy(currentOperator());
        copy = surveyRepository.save(copy);
        log.info("复制调查问卷: sourceId={}, newId={}, surveyName={}",
                id, copy.getId(), copy.getSurveyName());
        return copy;
    }

    /**
     * 通过邀请码获取问卷 (客户凭码填写场景)。
     *
     * @param code 邀请码
     * @return 问卷实体
     * @throws ScrmException 邀请不存在 / 问卷不存在
     */
    @Transactional(readOnly = true)
    public ScrmSurveyEntity getSurveyByInvitationCode(String code) throws ScrmException {
        ScrmSurveyInvitationEntity invitation = findInvitationByCodeOrThrow(code);
        return findSurveyOrThrow(invitation.getSurveyId());
    }

    /**
     * 校验问卷参数。
     * <p>创建场景 (partial=false): surveyName / surveyType / title / questions 必填;
     * 更新场景 (partial=true): 允许字段为空, 仅校验非空字段的合法性。questions 非空时校验 JSON 可解析。</p>
     *
     * @param dto     问卷参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateSurveyDto(ScrmSurveyDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("问卷参数不能为空");
        }
        if (dto.getSurveyName() != null) {
            if (dto.getSurveyName().isBlank()) {
                throw ScrmException.badRequest("调查名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("调查名称不能为空");
        }
        if (dto.getSurveyType() != null) {
            if (!VALID_SURVEY_TYPES.contains(dto.getSurveyType())) {
                throw ScrmException.badRequest(
                        "调查类型非法: " + dto.getSurveyType() + ", 仅支持 " + VALID_SURVEY_TYPES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("调查类型不能为空");
        }
        if (dto.getTitle() != null) {
            if (dto.getTitle().isBlank()) {
                throw ScrmException.badRequest("调查标题不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("调查标题不能为空");
        }
        if (dto.getQuestions() != null) {
            if (dto.getQuestions().isBlank()) {
                throw ScrmException.badRequest("问题列表不能为空");
            }
            parseJsonList(dto.getQuestions(), "问题列表");
        } else if (!partial) {
            throw ScrmException.badRequest("问题列表不能为空");
        }
        if (dto.getScaleType() != null && !VALID_SCALE_TYPES.contains(dto.getScaleType())) {
            throw ScrmException.badRequest(
                    "量表类型非法: " + dto.getScaleType() + ", 仅支持 " + VALID_SCALE_TYPES);
        }
        if (dto.getStatus() != null && !VALID_SURVEY_STATUS.contains(dto.getStatus())) {
            throw ScrmException.badRequest(
                    "问卷状态非法: " + dto.getStatus() + ", 仅支持 " + VALID_SURVEY_STATUS);
        }
    }

    /**
     * 解析 JSON 数组字符串为 List Map。
     *
     * @param json       JSON 字符串
     * @param fieldName  字段名 (错误消息用)
     * @return List Map
     * @throws ScrmException JSON 解析失败
     */
    public List<Map<String, Object>> parseJsonList(String json, String fieldName) throws ScrmException {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw ScrmException.badRequest(fieldName + " JSON 解析失败: " + e.getMessage());
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
     * 按主键查询问卷, 不存在抛异常, 并校验归属账号。
     *
     * @param id 问卷 ID
     * @return 问卷实体
     * @throws ScrmException 问卷不存在
     */
    public ScrmSurveyEntity findSurveyOrThrow(Long id) throws ScrmException {
        ScrmSurveyEntity entity = surveyRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "调查问卷不存在: id=" + id));
        return entity;
    }

    /**
     * 按邀请码查询邀请, 不存在抛异常, 并校验归属账号。
     *
     * @param code 邀请码
     * @return 邀请实体
     * @throws ScrmException 邀请不存在
     */
    public ScrmSurveyInvitationEntity findInvitationByCodeOrThrow(String code) throws ScrmException {
        ScrmSurveyInvitationEntity entity = invitationRepository.findByInvitationCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "调查邀请不存在: code=" + code));
        return entity;
    }
}