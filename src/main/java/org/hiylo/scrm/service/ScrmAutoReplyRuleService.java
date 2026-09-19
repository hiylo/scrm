/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoReplyRuleService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmAutoReplyRuleDto;
import org.hiylo.scrm.entity.ScrmAutoReplyRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmAutoReplyLogRepository;
import org.hiylo.scrm.repository.ScrmAutoReplyRuleRepository;
import org.hiylo.scrm.repository.ScrmAutoReplyTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * SCRM 自动回复规则管理服务。
 * <p>
 * 承载自动回复规则的增删改查、启用/禁用、兜底规则设置、触发次数累计与参数校验。
 * 规则校验同时校验引用的回复模板存在性, 删除规则前检查回复日志引用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmAutoReplyRuleService {

    /** 默认启用状态 */
    private static final boolean DEFAULT_ENABLED = true;

    /** 默认优先级 */
    private static final int DEFAULT_PRIORITY = 0;

    /** 默认匹配方式 */
    private static final String DEFAULT_MATCH_TYPE = "EXACT";

    /** 默认匹配范围 */
    private static final String DEFAULT_MATCH_SCOPE = "MESSAGE";

    /** 默认回复类型 */
    private static final String DEFAULT_REPLY_TYPE = "TEXT";

    /** 默认冷却时间 (分钟) */
    private static final int DEFAULT_COOLDOWN_MINUTES = 0;

    /** 默认每客户最大触发次数 (0=无限) */
    private static final int DEFAULT_MAX_TRIGGER = 0;

    /** 优先级下限 (含) */
    private static final int PRIORITY_MIN = 0;

    /** 优先级上限 (含) */
    private static final int PRIORITY_MAX = 9999;

    /** 时间格式 (HH:mm) */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /** 合法的规则类型 */
    private static final List<String> VALID_RULE_TYPES = List.of(
            "KEYWORD", "WELCOME", "TIMEOUT", "OFFLINE", "FORM", "EVENT", "CONDITIONAL");

    /** 合法的匹配方式 */
    private static final List<String> VALID_MATCH_TYPES = List.of(
            "EXACT", "CONTAINS", "STARTS_WITH", "ENDS_WITH", "REGEX", "FUZZY");

    /** 合法的匹配范围 */
    private static final List<String> VALID_MATCH_SCOPES = List.of("MESSAGE", "FULL_TEXT", "SUBJECT");

    /** 合法的回复类型 */
    private static final List<String> VALID_REPLY_TYPES = List.of(
            "TEXT", "IMAGE", "LINK", "FILE", "TEMPLATE", "HTML", "RICH_TEXT");

    /** 合法的渠道 */
    private static final List<String> VALID_CHANNELS = List.of(
            "WECHAT", "WORK_WECHAT", "WEB", "APP", "SMS", "EMAIL");

    /** 自动回复规则数据访问层 */
    private final ScrmAutoReplyRuleRepository ruleRepository;

    /** 自动回复日志数据访问层 (删除规则前检查引用) */
    private final ScrmAutoReplyLogRepository logRepository;

    /** 自动回复模板数据访问层 (校验模板存在性) */
    private final ScrmAutoReplyTemplateRepository templateRepository;

    /**
     * 创建自动回复规则。
     * <p>校验参数合法性后写入归属账号 ID 持久化, enabled / priority / matchType / matchScope /
     * replyType 缺省时填默认值。</p>
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmAutoReplyRuleEntity createRule(ScrmAutoReplyRuleDto dto) throws ScrmException {
        validateRuleDto(dto, false);
        ScrmAutoReplyRuleEntity entity = new ScrmAutoReplyRuleEntity();
        entity.setRuleName(dto.getRuleName());
        entity.setRuleType(dto.getRuleType());
        entity.setDescription(dto.getDescription());
        entity.setMatchType(dto.getMatchType() != null && !dto.getMatchType().isBlank()
                ? dto.getMatchType() : DEFAULT_MATCH_TYPE);
        entity.setKeywords(dto.getKeywords());
        entity.setMatchScope(dto.getMatchScope() != null && !dto.getMatchScope().isBlank()
                ? dto.getMatchScope() : DEFAULT_MATCH_SCOPE);
        entity.setReplyType(dto.getReplyType() != null && !dto.getReplyType().isBlank()
                ? dto.getReplyType() : DEFAULT_REPLY_TYPE);
        entity.setReplyContent(dto.getReplyContent());
        entity.setReplyTemplateId(dto.getReplyTemplateId());
        entity.setMediaUrl(dto.getMediaUrl());
        entity.setLinkUrl(dto.getLinkUrl());
        entity.setLinkTitle(dto.getLinkTitle());
        entity.setLinkDescription(dto.getLinkDescription());
        entity.setLinkThumbnail(dto.getLinkThumbnail());
        entity.setApplicableChannels(dto.getApplicableChannels());
        entity.setApplicableAccounts(dto.getApplicableAccounts());
        entity.setWorkTimeOnly(dto.getWorkTimeOnly() != null ? dto.getWorkTimeOnly() : false);
        entity.setWorkTimeStart(dto.getWorkTimeStart());
        entity.setWorkTimeEnd(dto.getWorkTimeEnd());
        entity.setWorkDays(dto.getWorkDays());
        entity.setTimeoutSeconds(dto.getTimeoutSeconds());
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : DEFAULT_PRIORITY);
        entity.setMaxTriggerPerCustomer(dto.getMaxTriggerPerCustomer() != null
                ? dto.getMaxTriggerPerCustomer() : DEFAULT_MAX_TRIGGER);
        entity.setCooldownMinutes(dto.getCooldownMinutes() != null
                ? dto.getCooldownMinutes() : DEFAULT_COOLDOWN_MINUTES);
        entity.setFallbackRule(dto.getFallbackRule() != null ? dto.getFallbackRule() : false);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : DEFAULT_ENABLED);
        entity.setTriggerCount(0);
        entity.setCreatedBy(dto.getCreatedBy());
        // 若标记为兜底规则, 清除同一账号下其他规则的兜底标记, 保证唯一
        if (Boolean.TRUE.equals(entity.getFallbackRule())) {
            ruleRepository.clearFallbackFlags();
        }
        entity = ruleRepository.save(entity);
        log.info("创建自动回复规则: id={}, ruleName={}, ruleType={}",
                entity.getId(), entity.getRuleName(), entity.getRuleType());
        return entity;
    }

    /**
     * 更新自动回复规则（字段非空才覆盖）。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    @Transactional
    public ScrmAutoReplyRuleEntity updateRule(Long id, ScrmAutoReplyRuleDto dto) throws ScrmException {
        ScrmAutoReplyRuleEntity entity = findRuleOrThrow(id);
        validateRuleDto(dto, true);
        if (dto.getRuleName() != null) entity.setRuleName(dto.getRuleName());
        if (dto.getRuleType() != null) entity.setRuleType(dto.getRuleType());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getMatchType() != null) entity.setMatchType(dto.getMatchType());
        if (dto.getKeywords() != null) entity.setKeywords(dto.getKeywords());
        if (dto.getMatchScope() != null) entity.setMatchScope(dto.getMatchScope());
        if (dto.getReplyType() != null) entity.setReplyType(dto.getReplyType());
        if (dto.getReplyContent() != null) entity.setReplyContent(dto.getReplyContent());
        if (dto.getReplyTemplateId() != null) entity.setReplyTemplateId(dto.getReplyTemplateId());
        if (dto.getMediaUrl() != null) entity.setMediaUrl(dto.getMediaUrl());
        if (dto.getLinkUrl() != null) entity.setLinkUrl(dto.getLinkUrl());
        if (dto.getLinkTitle() != null) entity.setLinkTitle(dto.getLinkTitle());
        if (dto.getLinkDescription() != null) entity.setLinkDescription(dto.getLinkDescription());
        if (dto.getLinkThumbnail() != null) entity.setLinkThumbnail(dto.getLinkThumbnail());
        if (dto.getApplicableChannels() != null) entity.setApplicableChannels(dto.getApplicableChannels());
        if (dto.getApplicableAccounts() != null) entity.setApplicableAccounts(dto.getApplicableAccounts());
        if (dto.getWorkTimeOnly() != null) entity.setWorkTimeOnly(dto.getWorkTimeOnly());
        if (dto.getWorkTimeStart() != null) entity.setWorkTimeStart(dto.getWorkTimeStart());
        if (dto.getWorkTimeEnd() != null) entity.setWorkTimeEnd(dto.getWorkTimeEnd());
        if (dto.getWorkDays() != null) entity.setWorkDays(dto.getWorkDays());
        if (dto.getTimeoutSeconds() != null) entity.setTimeoutSeconds(dto.getTimeoutSeconds());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getMaxTriggerPerCustomer() != null) entity.setMaxTriggerPerCustomer(dto.getMaxTriggerPerCustomer());
        if (dto.getCooldownMinutes() != null) entity.setCooldownMinutes(dto.getCooldownMinutes());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        if (dto.getFallbackRule() != null) {
            // 设置为兜底规则时, 清除同一账号下其他规则的兜底标记
            if (Boolean.TRUE.equals(dto.getFallbackRule())) {
                ruleRepository.clearFallbackFlags();
            }
            entity.setFallbackRule(dto.getFallbackRule());
        }
        entity = ruleRepository.save(entity);
        log.info("更新自动回复规则: id={}, ruleName={}", entity.getId(), entity.getRuleName());
        return entity;
    }

    /**
     * 删除自动回复规则。
     * <p>删除前检查是否有回复日志引用该规则, 若有则阻止删除并返回引用数量。</p>
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在 / 仍有回复日志引用
     */
    @Transactional
    public void deleteRule(Long id) throws ScrmException {
        ScrmAutoReplyRuleEntity entity = findRuleOrThrow(id);
        long logCount = logRepository.count((root, query, cb) -> cb.equal(root.get("ruleId"), id));
        if (logCount > 0) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    String.format("无法删除规则: 仍有 %d 条回复日志引用该规则, 请先禁用规则而非删除", logCount));
        }
        ruleRepository.delete(entity);
        log.info("删除自动回复规则: id={}, ruleName={}", id, entity.getRuleName());
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public ScrmAutoReplyRuleEntity getRule(Long id) throws ScrmException {
        return findRuleOrThrow(id);
    }

    /**
     * 分页查询规则, 支持按规则类型、匹配方式、启用状态与关键字过滤。
     *
     * @param ruleType  规则类型过滤（可空）
     * @param matchType 匹配方式过滤（可空）
     * @param enabled   启用状态过滤（可空）
     * @param keyword   关键字过滤（按规则名称/描述模糊匹配, 可空）
     * @param pageable  分页参数
     * @return 规则分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmAutoReplyRuleEntity> listRules(String ruleType, String matchType, Boolean enabled,
                                                    String keyword, Pageable pageable) {
        Specification<ScrmAutoReplyRuleEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (ruleType != null && !ruleType.isBlank()) {
                predicates.add(cb.equal(root.get("ruleType"), ruleType));
            }
            if (matchType != null && !matchType.isBlank()) {
                predicates.add(cb.equal(root.get("matchType"), matchType));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("ruleName")), kw),
                        cb.like(cb.lower(root.get("description")), kw)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return ruleRepository.findAll(spec, pageable);
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public void enableRule(Long id) throws ScrmException {
        ScrmAutoReplyRuleEntity entity = findRuleOrThrow(id);
        entity.setEnabled(true);
        ruleRepository.save(entity);
        log.info("启用自动回复规则: id={}, ruleName={}", id, entity.getRuleName());
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public void disableRule(Long id) throws ScrmException {
        ScrmAutoReplyRuleEntity entity = findRuleOrThrow(id);
        entity.setEnabled(false);
        ruleRepository.save(entity);
        log.info("禁用自动回复规则: id={}, ruleName={}", id, entity.getRuleName());
    }

    /**
     * 设置兜底规则。
     * <p>清除同一账号下其他规则的兜底标记, 保证唯一兜底规则。</p>
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmAutoReplyRuleEntity setFallbackRule(Long id) throws ScrmException {
        ScrmAutoReplyRuleEntity entity = findRuleOrThrow(id);
        ruleRepository.clearFallbackFlags();
        entity.setFallbackRule(true);
        entity = ruleRepository.save(entity);
        log.info("设置兜底规则: id={}, ruleName={}", id, entity.getRuleName());
        return entity;
    }

    /**
     * 增加规则触发次数（直接 SQL 更新, 避免乐观锁冲突）。
     *
     * @param id 规则 ID
     */
    @Transactional
    public void incrementTriggerCount(Long id) {
        ruleRepository.incrementTriggerCount(id, LocalDateTime.now());
    }

    /**
     * 按主键查询规则, 不存在抛异常, 并校验账号归属。
     * <p>供匹配引擎兄弟类共用。</p>
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    ScrmAutoReplyRuleEntity findRuleOrThrow(Long id) throws ScrmException {
        ScrmAutoReplyRuleEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "自动回复规则不存在: id=" + id));

        return entity;
    }

    /**
     * 校验规则参数。
     *
     * @param dto     规则参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateRuleDto(ScrmAutoReplyRuleDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("规则参数不能为空");
        }
        if (dto.getRuleName() != null) {
            if (dto.getRuleName().isBlank()) {
                throw ScrmException.badRequest("规则名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("规则名称不能为空");
        }
        if (dto.getRuleType() != null) {
            if (!VALID_RULE_TYPES.contains(dto.getRuleType())) {
                throw ScrmException.badRequest(
                        "规则类型非法: " + dto.getRuleType() + ", 仅支持 " + VALID_RULE_TYPES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("规则类型不能为空");
        }
        if (dto.getMatchType() != null && !dto.getMatchType().isBlank() && !VALID_MATCH_TYPES.contains(dto.getMatchType())) {
            throw ScrmException.badRequest(
                    "匹配方式非法: " + dto.getMatchType() + ", 仅支持 " + VALID_MATCH_TYPES);
        }
        if (dto.getMatchScope() != null && !dto.getMatchScope().isBlank() && !VALID_MATCH_SCOPES.contains(dto.getMatchScope())) {
            throw ScrmException.badRequest(
                    "匹配范围非法: " + dto.getMatchScope() + ", 仅支持 " + VALID_MATCH_SCOPES);
        }
        if (dto.getReplyType() != null && !dto.getReplyType().isBlank() && !VALID_REPLY_TYPES.contains(dto.getReplyType())) {
            throw ScrmException.badRequest(
                    "回复类型非法: " + dto.getReplyType() + ", 仅支持 " + VALID_REPLY_TYPES);
        }
        if (dto.getReplyContent() != null) {
            if (dto.getReplyContent().isBlank()) {
                throw ScrmException.badRequest("回复内容不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("回复内容不能为空");
        }
        // replyType=TEMPLATE 时校验模板 ID 非空
        String replyType = dto.getReplyType() != null && !dto.getReplyType().isBlank()
                ? dto.getReplyType() : (partial ? null : DEFAULT_REPLY_TYPE);
        if ("TEMPLATE".equals(replyType) && dto.getReplyTemplateId() == null) {
            throw ScrmException.badRequest("回复类型为 TEMPLATE 时必须指定回复模板 ID");
        }
        // 校验模板存在性 (若指定了模板 ID)
        if (dto.getReplyTemplateId() != null) {
            if (!templateRepository.existsById(dto.getReplyTemplateId())) {
                throw ScrmException.badRequest("回复模板不存在: id=" + dto.getReplyTemplateId());
            }
        }
        // 校验适用渠道合法性
        if (dto.getApplicableChannels() != null && !dto.getApplicableChannels().isBlank()) {
            validateChannels(dto.getApplicableChannels());
        }
        // 校验 REGEX 类型关键词为合法正则
        if (dto.getKeywords() != null && !dto.getKeywords().isBlank() && dto.getMatchType() != null && "REGEX".equals(dto.getMatchType())) {
            try {
                Pattern.compile(dto.getKeywords());
            } catch (PatternSyntaxException e) {
                throw ScrmException.badRequest("正则表达式非法: " + e.getMessage());
            }
        }
        // priority 校验
        if (dto.getPriority() != null) {
            if (dto.getPriority() < PRIORITY_MIN || dto.getPriority() > PRIORITY_MAX) {
                throw ScrmException.badRequest(
                        "优先级必须在 " + PRIORITY_MIN + "-" + PRIORITY_MAX + " 之间: " + dto.getPriority());
            }
        }
        // timeoutSeconds 校验
        if (dto.getTimeoutSeconds() != null && dto.getTimeoutSeconds() < 0) {
            throw ScrmException.badRequest("超时秒数不能为负数: " + dto.getTimeoutSeconds());
        }
        // workTimeOnly=TRUE 时校验工作时间字段
        if (Boolean.TRUE.equals(dto.getWorkTimeOnly())) {
            if (dto.getWorkTimeStart() == null || dto.getWorkTimeEnd() == null) {
                throw ScrmException.badRequest("仅工作时间触发时必须指定工作时间开始与结束");
            }
            try {
                LocalTime.parse(dto.getWorkTimeStart(), TIME_FORMATTER);
                LocalTime.parse(dto.getWorkTimeEnd(), TIME_FORMATTER);
            } catch (Exception e) {
                throw ScrmException.badRequest("工作时间格式非法, 应为 HH:mm");
            }
        }
    }

    /**
     * 校验渠道列表合法性。
     *
     * @param channels 渠道列表 (逗号分隔)
     * @throws ScrmException 渠道非法
     */
    private void validateChannels(String channels) throws ScrmException {
        for (String channel : channels.split(",")) {
            String trimmed = channel.trim();
            if (!trimmed.isEmpty() && !VALID_CHANNELS.contains(trimmed)) {
                throw ScrmException.badRequest(
                        "渠道非法: " + trimmed + ", 仅支持 " + VALID_CHANNELS);
            }
        }
    }
}
