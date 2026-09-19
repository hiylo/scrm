/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechScenarioService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

package org.hiylo.scrm.service;


import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.hiylo.scrm.dto.ScrmSpeechScenarioDto;
import org.hiylo.scrm.entity.ScrmSalesSpeechEntity;
import org.hiylo.scrm.entity.ScrmSpeechRecommendationEntity;
import org.hiylo.scrm.entity.ScrmSpeechScenarioEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmSalesSpeechRepository;
import org.hiylo.scrm.repository.ScrmSpeechRecommendationRepository;
import org.hiylo.scrm.repository.ScrmSpeechScenarioRepository;

/**
 * 话术场景管理兄弟服务。
 * <p>
 * 承载话术场景的增删改查、启停管理、编码查询与场景统计重算。
 * 作为 {@link ScrmSpeechRecommendService} 的场景子域拆分产物, 由门面与话术/推荐兄弟服务注入。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmSpeechScenarioService {

    // ==================== 默认值常量 ====================

    /** 默认优先级 */
    private static final int DEFAULT_PRIORITY = 0;

    // ==================== 枚举值常量 ====================

    /** 合法的场景类别 */
    private static final List<String> VALID_SCENARIO_CATEGORIES = List.of(
            "GREETING", "INQUIRY", "PITCH", "OBJECTION", "CLOSING", "FOLLOW_UP",
            "CROSS_SELL", "UP_SELL", "RETENTION", "RECOVERY", "APPOINTMENT",
            "REFERRAL", "THANK_YOU", "APOLOGY");

    /** 合法的客户阶段 */
    private static final List<String> VALID_CUSTOMER_STAGES = List.of(
            "NEW", "ACTIVE", "AT_RISK", "CHURNED", "VIP", "PROSPECT");

    // ==================== 依赖注入 ====================

    /** 场景数据访问层 */
    private final ScrmSpeechScenarioRepository scenarioRepository;

    /** 话术数据访问层 (删除场景/统计联动) */
    private final ScrmSalesSpeechRepository speechRepository;

    /** 推荐记录数据访问层 (删除场景联动) */
    private final ScrmSpeechRecommendationRepository recommendationRepository;

    /**
     * 创建话术场景。
     * <p>校验 scenarioCategory 合法性与 scenarioCode 唯一性后写入归属账号 ID 持久化,
     * priority / enabled 缺省时填默认值, 统计字段初始化为 0。</p>
     *
     * @param dto 场景参数
     * @return 创建后的场景
     * @throws ScrmException 参数非法 / 编码重复
     */
    @Transactional
    public ScrmSpeechScenarioEntity createScenario(ScrmSpeechScenarioDto dto) throws ScrmException {
        validateScenarioDto(dto, false);
        if (scenarioRepository.findByScenarioCode(dto.getScenarioCode()).isPresent()) {
            throw ScrmException.conflict("场景编码已存在: " + dto.getScenarioCode());
        }
        ScrmSpeechScenarioEntity entity = new ScrmSpeechScenarioEntity();
        entity.setScenarioName(dto.getScenarioName());
        entity.setScenarioCode(dto.getScenarioCode());
        entity.setScenarioCategory(dto.getScenarioCategory());
        entity.setDescription(dto.getDescription());
        entity.setTriggerConditions(dto.getTriggerConditions());
        entity.setApplicableProducts(dto.getApplicableProducts());
        entity.setApplicableChannels(dto.getApplicableChannels());
        entity.setCustomerStage(dto.getCustomerStage());
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : DEFAULT_PRIORITY);
        entity.setSpeechCount(0);
        entity.setAvgRating(0.0);
        entity.setUsageCount(0);
        entity.setSuccessRate(0.0);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = scenarioRepository.save(entity);
        log.info("创建话术场景: id={}, scenarioName={}, scenarioCode={}, category={}",
                entity.getId(), entity.getScenarioName(), entity.getScenarioCode(), entity.getScenarioCategory());
        return entity;
    }

    /**
     * 更新话术场景（字段非空才覆盖）。
     *
     * @param id  场景 ID
     * @param dto 场景参数
     * @return 更新后的场景
     * @throws ScrmException 场景不存在 / 参数非法 / 编码重复
     */
    @Transactional
    public ScrmSpeechScenarioEntity updateScenario(Long id, ScrmSpeechScenarioDto dto) throws ScrmException {
        ScrmSpeechScenarioEntity entity = findScenarioOrThrow(id);
        validateScenarioDto(dto, true);
        if (dto.getScenarioCode() != null && !dto.getScenarioCode().equals(entity.getScenarioCode())) {
            if (scenarioRepository.findByScenarioCode(dto.getScenarioCode()).isPresent()) {
                throw ScrmException.conflict("场景编码已存在: " + dto.getScenarioCode());
            }
        }
        if (dto.getScenarioName() != null) entity.setScenarioName(dto.getScenarioName());
        if (dto.getScenarioCode() != null) entity.setScenarioCode(dto.getScenarioCode());
        if (dto.getScenarioCategory() != null) entity.setScenarioCategory(dto.getScenarioCategory());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getTriggerConditions() != null) entity.setTriggerConditions(dto.getTriggerConditions());
        if (dto.getApplicableProducts() != null) entity.setApplicableProducts(dto.getApplicableProducts());
        if (dto.getApplicableChannels() != null) entity.setApplicableChannels(dto.getApplicableChannels());
        if (dto.getCustomerStage() != null) entity.setCustomerStage(dto.getCustomerStage());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = scenarioRepository.save(entity);
        log.info("更新话术场景: id={}, scenarioName={}", entity.getId(), entity.getScenarioName());
        return entity;
    }

    /**
     * 删除话术场景 (同时清理场景下话术与推荐记录)。
     *
     * @param id 场景 ID
     * @throws ScrmException 场景不存在
     */
    @Transactional
    public void deleteScenario(Long id) throws ScrmException {
        ScrmSpeechScenarioEntity entity = findScenarioOrThrow(id);
        List<ScrmSalesSpeechEntity> speeches = speechRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("scenarioId"), id)));
        if (!speeches.isEmpty()) {
            speechRepository.deleteAll(speeches);
        }
        List<ScrmSpeechRecommendationEntity> recommendations = recommendationRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("scenarioId"), id)));
        if (!recommendations.isEmpty()) {
            recommendationRepository.deleteAll(recommendations);
        }
        scenarioRepository.delete(entity);
        log.info("删除话术场景: id={}, scenarioName={}, speeches={}, recommendations={}",
                id, entity.getScenarioName(), speeches.size(), recommendations.size());
    }

    /**
     * 查询场景详情。
     *
     * @param id 场景 ID
     * @return 场景实体
     * @throws ScrmException 场景不存在
     */
    @Transactional(readOnly = true)
    public ScrmSpeechScenarioEntity getScenario(Long id) throws ScrmException {
        return findScenarioOrThrow(id);
    }

    /**
     * 按编码查询场景。
     *
     * @param code 场景编码
     * @return 场景实体
     * @throws ScrmException 场景不存在
     */
    @Transactional(readOnly = true)
    public ScrmSpeechScenarioEntity getScenarioByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("场景编码不能为空");
        }
        return scenarioRepository.findByScenarioCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "话术场景不存在: code=" + code));
    }

    /**
     * 分页查询场景, 支持按场景类别 / 客户阶段 / 启用状态 / 关键字过滤。
     *
     * @param scenarioCategory 场景类别过滤（可空）
     * @param customerStage    客户阶段过滤（可空）
     * @param enabled          启用状态过滤（可空）
     * @param keyword          场景名称关键字模糊匹配（可空）
     * @param pageable         分页参数
     * @return 场景分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmSpeechScenarioEntity> listScenarios(String scenarioCategory, String customerStage,
                                                         Boolean enabled, String keyword, Pageable pageable) {
        Specification<ScrmSpeechScenarioEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (scenarioCategory != null && !scenarioCategory.isBlank()) {
                predicates.add(cb.equal(root.get("scenarioCategory"), scenarioCategory));
            }
            if (customerStage != null && !customerStage.isBlank()) {
                predicates.add(cb.equal(root.get("customerStage"), customerStage));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("scenarioName"), "%" + keyword + "%"));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return scenarioRepository.findAll(spec, pageable);
    }

    /**
     * 启用场景。
     *
     * @param id 场景 ID
     * @return 更新后的场景
     * @throws ScrmException 场景不存在
     */
    @Transactional
    public ScrmSpeechScenarioEntity enableScenario(Long id) throws ScrmException {
        ScrmSpeechScenarioEntity entity = findScenarioOrThrow(id);
        entity.setEnabled(true);
        entity = scenarioRepository.save(entity);
        log.info("启用话术场景: id={}, scenarioName={}", id, entity.getScenarioName());
        return entity;
    }

    /**
     * 禁用场景。
     *
     * @param id 场景 ID
     * @return 更新后的场景
     * @throws ScrmException 场景不存在
     */
    @Transactional
    public ScrmSpeechScenarioEntity disableScenario(Long id) throws ScrmException {
        ScrmSpeechScenarioEntity entity = findScenarioOrThrow(id);
        entity.setEnabled(false);
        entity = scenarioRepository.save(entity);
        log.info("禁用话术场景: id={}, scenarioName={}", id, entity.getScenarioName());
        return entity;
    }

    /**
     * 更新场景统计 (话术数 / 平均评分 / 使用次数 / 成功率)。
     *
     * @param id 场景 ID
     * @return 更新后的场景
     * @throws ScrmException 场景不存在
     */
    @Transactional
    public ScrmSpeechScenarioEntity updateScenarioStats(Long id) throws ScrmException {
        ScrmSpeechScenarioEntity entity = findScenarioOrThrow(id);
        List<ScrmSalesSpeechEntity> speeches = speechRepository
                .findByScenarioIdAndEnabledOrderByRatingDesc(id, Boolean.TRUE);
        int speechCount = speeches.size();
        double ratingSum = 0.0;
        int ratingCount = 0;
        int usageSum = 0;
        int successSum = 0;
        for (ScrmSalesSpeechEntity s : speeches) {
            if (s.getRating() != null) {
                ratingSum += s.getRating();
                ratingCount++;
            }
            if (s.getUsageCount() != null) {
                usageSum += s.getUsageCount();
            }
            if (s.getSuccessCount() != null) {
                successSum += s.getSuccessCount();
            }
        }
        entity.setSpeechCount(speechCount);
        entity.setAvgRating(ratingCount > 0 ? Math.round(ratingSum / ratingCount * 100d) / 100d : 0.0);
        entity.setUsageCount(usageSum);
        entity.setSuccessRate(usageSum > 0 ? Math.round(successSum * 100.0 / usageSum * 100d) / 100d : 0.0);
        entity = scenarioRepository.save(entity);
        log.info("更新话术场景统计: id={}, speechCount={}, avgRating={}, usageCount={}, successRate={}",
                id, speechCount, entity.getAvgRating(), usageSum, entity.getSuccessRate());
        return entity;
    }

    /**
     * 校验场景参数。
     *
     * @param dto     场景参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateScenarioDto(ScrmSpeechScenarioDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("场景参数不能为空");
        }
        if (dto.getScenarioName() != null) {
            if (dto.getScenarioName().isBlank()) {
                throw ScrmException.badRequest("场景名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("场景名称不能为空");
        }
        if (dto.getScenarioCode() != null) {
            if (dto.getScenarioCode().isBlank()) {
                throw ScrmException.badRequest("场景编码不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("场景编码不能为空");
        }
        if (dto.getScenarioCategory() != null) {
            if (!VALID_SCENARIO_CATEGORIES.contains(dto.getScenarioCategory())) {
                throw ScrmException.badRequest(
                        "场景类别非法: " + dto.getScenarioCategory() + ", 仅支持 " + VALID_SCENARIO_CATEGORIES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("场景类别不能为空");
        }
        if (dto.getCustomerStage() != null && !dto.getCustomerStage().isBlank() && !VALID_CUSTOMER_STAGES.contains(dto.getCustomerStage())) {
            throw ScrmException.badRequest(
                    "客户阶段非法: " + dto.getCustomerStage() + ", 仅支持 " + VALID_CUSTOMER_STAGES);
        }
    }

    /**
     * 按主键查询场景, 不存在抛异常, 并校验归属账号。
     *
     * @param id 场景 ID
     * @return 场景实体
     * @throws ScrmException 场景不存在
     */
    ScrmSpeechScenarioEntity findScenarioOrThrow(Long id) throws ScrmException {
        ScrmSpeechScenarioEntity entity = scenarioRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "话术场景不存在: id=" + id));
        return entity;
    }

}
