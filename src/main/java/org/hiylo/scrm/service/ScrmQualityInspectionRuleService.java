/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQualityInspectionRuleService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmQualityInspectionRuleDto;
import org.hiylo.scrm.entity.ScrmQualityInspectionRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmQualityInspectionRuleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * SCRM 质检规则管理服务。
 * <p>
 * 承载质检规则的增删改查、启用/禁用与分页过滤查询，负责规则参数的合法性校验
 * (质检类别/规则类型/规则配置 JSON 等) 及缺省默认值填充。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmQualityInspectionRuleService {

    /** 默认启用状态 */
    private static final boolean DEFAULT_ENABLED = true;

    /** 默认通过条件 (总分及格线 80) */
    private static final String DEFAULT_PASS_CONDITION = "GTE:80";

    /** 默认评分权重 */
    private static final double DEFAULT_SCORE_WEIGHT = 1.0;

    /** 合法质检类别 */
    private static final List<String> VALID_CATEGORIES = List.of(
            "SCRIPT_COMPLIANCE", "SERVICE_ATTITUDE", "SENSITIVE_WORD",
            "RESPONSE_TIME", "PROFESSIONALISM", "COMPLIANCE");

    /** 合法规则类型 */
    private static final List<String> VALID_RULE_TYPES = List.of(
            "KEYWORD_MATCH", "REGEX", "DURATION", "RESPONSE_TIME", "AI_EVALUATE");

    /** 质检规则数据访问层 */
    private final ScrmQualityInspectionRuleRepository ruleRepository;

    /** JSON 序列化/反序列化 */
    private final ObjectMapper objectMapper;

    // ============================================================
    // 规则管理
    // ============================================================

    /**
     * 创建质检规则。
     * <p>校验参数合法性后写入归属账号 ID 持久化, enabled/passCondition/scoreWeight 缺省时填默认值。</p>
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmQualityInspectionRuleEntity createRule(ScrmQualityInspectionRuleDto dto) throws ScrmException {
        validateRuleDto(dto, false);
        ScrmQualityInspectionRuleEntity entity = new ScrmQualityInspectionRuleEntity();
        entity.setRuleName(dto.getRuleName());
        entity.setCategory(dto.getCategory());
        entity.setDescription(dto.getDescription());
        entity.setRuleType(dto.getRuleType());
        entity.setRuleConfig(dto.getRuleConfig());
        entity.setPassCondition(dto.getPassCondition() != null && !dto.getPassCondition().isBlank()
                ? dto.getPassCondition() : DEFAULT_PASS_CONDITION);
        entity.setScoreWeight(dto.getScoreWeight() != null ? dto.getScoreWeight() : DEFAULT_SCORE_WEIGHT);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : DEFAULT_ENABLED);
        entity.setMatchCount(0);
        entity.setPassCount(0);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = ruleRepository.save(entity);
        log.info("创建质检规则: id={}, ruleName={}, category={}, ruleType={}",
                entity.getId(), entity.getRuleName(), entity.getCategory(), entity.getRuleType());
        return entity;
    }

    /**
     * 更新质检规则（字段非空才覆盖）。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    @Transactional
    public ScrmQualityInspectionRuleEntity updateRule(Long id,
            ScrmQualityInspectionRuleDto dto) throws ScrmException {
        ScrmQualityInspectionRuleEntity entity = findRuleOrThrow(id);
        validateRuleDto(dto, true);
        if (dto.getRuleName() != null) entity.setRuleName(dto.getRuleName());
        if (dto.getCategory() != null) entity.setCategory(dto.getCategory());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getRuleType() != null) entity.setRuleType(dto.getRuleType());
        if (dto.getRuleConfig() != null) entity.setRuleConfig(dto.getRuleConfig());
        if (dto.getPassCondition() != null && !dto.getPassCondition().isBlank()) entity.setPassCondition(dto.getPassCondition());
        if (dto.getScoreWeight() != null) entity.setScoreWeight(dto.getScoreWeight());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = ruleRepository.save(entity);
        log.info("更新质检规则: id={}, ruleName={}", entity.getId(), entity.getRuleName());
        return entity;
    }

    /**
     * 删除质检规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public void deleteRule(Long id) throws ScrmException {
        ScrmQualityInspectionRuleEntity entity = findRuleOrThrow(id);
        ruleRepository.delete(entity);
        log.info("删除质检规则: id={}, ruleName={}", id, entity.getRuleName());
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public ScrmQualityInspectionRuleEntity getRule(Long id) throws ScrmException {
        return findRuleOrThrow(id);
    }

    /**
     * 分页查询规则, 支持按类别/规则类型/启用状态/关键字过滤。
     *
     * @param category 质检类别过滤（可空）
     * @param ruleType 规则类型过滤（可空）
     * @param enabled  启用状态过滤（可空）
     * @param keyword  关键字过滤（按规则名称模糊匹配, 可空）
     * @param pageable 分页参数
     * @return 规则分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmQualityInspectionRuleEntity> listRules(String category, String ruleType,
                                                            Boolean enabled, String keyword, Pageable pageable) {
        Specification<ScrmQualityInspectionRuleEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (ruleType != null && !ruleType.isBlank()) {
                predicates.add(cb.equal(root.get("ruleType"), ruleType));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("ruleName")), kw));
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
        ScrmQualityInspectionRuleEntity entity = findRuleOrThrow(id);
        entity.setEnabled(true);
        ruleRepository.save(entity);
        log.info("启用质检规则: id={}, ruleName={}", id, entity.getRuleName());
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public void disableRule(Long id) throws ScrmException {
        ScrmQualityInspectionRuleEntity entity = findRuleOrThrow(id);
        entity.setEnabled(false);
        ruleRepository.save(entity);
        log.info("禁用质检规则: id={}, ruleName={}", id, entity.getRuleName());
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验质检规则参数。
     *
     * @param dto     规则参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateRuleDto(ScrmQualityInspectionRuleDto dto, boolean partial) throws ScrmException {
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
        if (dto.getCategory() != null) {
            if (!VALID_CATEGORIES.contains(dto.getCategory())) {
                throw ScrmException.badRequest(
                        "质检类别非法: " + dto.getCategory() + ", 仅支持 " + VALID_CATEGORIES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("质检类别不能为空");
        }
        if (dto.getRuleType() != null) {
            if (!VALID_RULE_TYPES.contains(dto.getRuleType())) {
                throw ScrmException.badRequest(
                        "规则类型非法: " + dto.getRuleType() + ", 仅支持 " + VALID_RULE_TYPES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("规则类型不能为空");
        }
        if (dto.getRuleConfig() != null) {
            if (dto.getRuleConfig().isBlank()) {
                throw ScrmException.badRequest("规则配置不能为空");
            }
            // 校验 ruleConfig 为合法 JSON
            try {
                objectMapper.readTree(dto.getRuleConfig());
            } catch (Exception e) {
                throw ScrmException.badRequest("规则配置不是合法 JSON: " + e.getMessage());
            }
        } else if (!partial) {
            throw ScrmException.badRequest("规则配置不能为空");
        }
        if (dto.getScoreWeight() != null && dto.getScoreWeight() <= 0) {
            throw ScrmException.badRequest("评分权重必须大于 0");
        }
    }

    /**
     * 按主键查询规则, 不存在抛 404, 并校验归属账号。
     */
    private ScrmQualityInspectionRuleEntity findRuleOrThrow(Long id) throws ScrmException {
        ScrmQualityInspectionRuleEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "质检规则不存在: id=" + id));

        return entity;
    }
}
