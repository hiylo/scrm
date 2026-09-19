/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVisitTemplateService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmVisitTemplateDto;
import org.hiylo.scrm.entity.ScrmVisitTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmVisitTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * SCRM 客户回访模板子域服务
 * <p>
 * 负责回访模板管理 (CRUD + 启停 + 复制 + 使用计数与平均满意度)。
 * 操作人获取复用 {@link ScrmVisitPlanService}。本服务为 {@link ScrmVisitService}
 * 门面的子域拆分, 不反向依赖门面。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmVisitTemplateService {

    /** 默认任务统计初值 */
    private static final int DEFAULT_COUNT = 0;

    /** 默认完成率 */
    private static final double DEFAULT_RATE = 0.0;

    /** 默认预计时长 */
    private static final int DEFAULT_DURATION_MINUTES = 15;

    /** 复制模板名称后缀 */
    private static final String COPY_SUFFIX = "-副本";

    /** 合法的回访类型 */
    private static final List<String> VALID_VISIT_TYPES = List.of(
            "REGULAR", "FOLLOW_UP", "SATISFACTION", "RENEWAL", "UPSELL",
            "CROSS_SELL", "CARE", "COMPLAINT_FOLLOWUP", "CUSTOM");

    /** 合法的回访方式 */
    private static final List<String> VALID_VISIT_METHODS = List.of(
            "PHONE", "ON_SITE", "VIDEO", "WECHAT", "EMAIL", "SMS", "MIXED");

    /** 回访模板数据访问层 */
    private final ScrmVisitTemplateRepository templateRepository;

    /** 回访计划子域服务 (操作人获取) */
    private final ScrmVisitPlanService planService;

    /**
     * 创建回访模板。
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmVisitTemplateEntity createTemplate(ScrmVisitTemplateDto dto) throws ScrmException {
        validateTemplateDto(dto, false);
        if (templateRepository.findByTemplateCode(dto.getTemplateCode()).isPresent()) {
            throw ScrmException.conflict("模板编码已存在: " + dto.getTemplateCode());
        }
        ScrmVisitTemplateEntity entity = new ScrmVisitTemplateEntity();
        entity.setTemplateName(dto.getTemplateName());
        entity.setTemplateCode(dto.getTemplateCode());
        entity.setDescription(dto.getDescription());
        entity.setVisitType(dto.getVisitType());
        entity.setVisitMethod(dto.getVisitMethod());
        entity.setQuestions(dto.getQuestions());
        entity.setIntroduction(dto.getIntroduction());
        entity.setClosing(dto.getClosing());
        entity.setSuccessCriteria(dto.getSuccessCriteria());
        entity.setEstimatedDurationMinutes(dto.getEstimatedDurationMinutes() != null
                ? dto.getEstimatedDurationMinutes() : DEFAULT_DURATION_MINUTES);
        entity.setApplicableProducts(dto.getApplicableProducts());
        entity.setTags(dto.getTags());
        entity.setUsageCount(DEFAULT_COUNT);
        entity.setAvgSatisfactionScore(DEFAULT_RATE);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : planService.currentOperator());
        entity = templateRepository.save(entity);
        log.info("创建回访模板: id={}, templateName={}, templateCode={}",
                entity.getId(), entity.getTemplateName(), entity.getTemplateCode());
        return entity;
    }

    /**
     * 更新回访模板 (字段非空才覆盖)。
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 参数非法
     */
    @Transactional
    public ScrmVisitTemplateEntity updateTemplate(Long id, ScrmVisitTemplateDto dto) throws ScrmException {
        ScrmVisitTemplateEntity entity = findTemplateOrThrow(id);
        validateTemplateDto(dto, true);
        if (dto.getTemplateName() != null) entity.setTemplateName(dto.getTemplateName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getVisitType() != null) entity.setVisitType(dto.getVisitType());
        if (dto.getVisitMethod() != null) entity.setVisitMethod(dto.getVisitMethod());
        if (dto.getQuestions() != null) entity.setQuestions(dto.getQuestions());
        if (dto.getIntroduction() != null) entity.setIntroduction(dto.getIntroduction());
        if (dto.getClosing() != null) entity.setClosing(dto.getClosing());
        if (dto.getSuccessCriteria() != null) entity.setSuccessCriteria(dto.getSuccessCriteria());
        if (dto.getEstimatedDurationMinutes() != null) entity.setEstimatedDurationMinutes(
                dto.getEstimatedDurationMinutes());
        if (dto.getApplicableProducts() != null) entity.setApplicableProducts(dto.getApplicableProducts());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = templateRepository.save(entity);
        log.info("更新回访模板: id={}, templateName={}", entity.getId(), entity.getTemplateName());
        return entity;
    }

    /**
     * 删除回访模板。
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public void deleteTemplate(Long id) throws ScrmException {
        ScrmVisitTemplateEntity entity = findTemplateOrThrow(id);
        templateRepository.delete(entity);
        log.info("删除回访模板: id={}, templateName={}", id, entity.getTemplateName());
    }

    /**
     * 查询模板详情。
     *
     * @param id 模板 ID
     * @return 模板实体
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public ScrmVisitTemplateEntity getTemplate(Long id) throws ScrmException {
        return findTemplateOrThrow(id);
    }

    /**
     * 按模板编码查询。
     *
     * @param code 模板编码
     * @return 模板实体
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public ScrmVisitTemplateEntity getTemplateByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("模板编码不能为空");
        }
        return templateRepository.findByTemplateCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "回访模板不存在: code=" + code));
    }

    /**
     * 分页查询模板列表。
     *
     * @param visitType   回访类型过滤（可空）
     * @param visitMethod 回访方式过滤（可空）
     * @param enabled     启用状态过滤（可空）
     * @param pageable    分页参数
     * @return 模板分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmVisitTemplateEntity> listTemplates(String visitType, String visitMethod,
                                                       Boolean enabled, Pageable pageable) {
        Specification<ScrmVisitTemplateEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (visitType != null && !visitType.isBlank()) {
                predicates.add(cb.equal(root.get("visitType"), visitType));
            }
            if (visitMethod != null && !visitMethod.isBlank()) {
                predicates.add(cb.equal(root.get("visitMethod"), visitMethod));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return templateRepository.findAll(spec, pageable);
    }

    /**
     * 启用模板。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public ScrmVisitTemplateEntity enableTemplate(Long id) throws ScrmException {
        ScrmVisitTemplateEntity entity = findTemplateOrThrow(id);
        entity.setEnabled(Boolean.TRUE);
        entity = templateRepository.save(entity);
        log.info("启用回访模板: id={}, templateName={}", id, entity.getTemplateName());
        return entity;
    }

    /**
     * 停用模板。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public ScrmVisitTemplateEntity disableTemplate(Long id) throws ScrmException {
        ScrmVisitTemplateEntity entity = findTemplateOrThrow(id);
        entity.setEnabled(Boolean.FALSE);
        entity = templateRepository.save(entity);
        log.info("停用回访模板: id={}, templateName={}", id, entity.getTemplateName());
        return entity;
    }

    /**
     * 复制模板 (创建副本, 编码使用 newCode)。
     *
     * @param id      源模板 ID
     * @param newCode 新模板编码
     * @return 复制后的模板
     * @throws ScrmException 源模板不存在 / 编码已存在
     */
    @Transactional
    public ScrmVisitTemplateEntity copyTemplate(Long id, String newCode) throws ScrmException {
        if (newCode == null || newCode.isBlank()) {
            throw ScrmException.badRequest("新模板编码不能为空");
        }
        ScrmVisitTemplateEntity source = findTemplateOrThrow(id);
        if (templateRepository.findByTemplateCode(newCode).isPresent()) {
            throw ScrmException.conflict("模板编码已存在: " + newCode);
        }
        ScrmVisitTemplateEntity copy = new ScrmVisitTemplateEntity();
        copy.setTemplateName(source.getTemplateName() + COPY_SUFFIX);
        copy.setTemplateCode(newCode);
        copy.setDescription(source.getDescription());
        copy.setVisitType(source.getVisitType());
        copy.setVisitMethod(source.getVisitMethod());
        copy.setQuestions(source.getQuestions());
        copy.setIntroduction(source.getIntroduction());
        copy.setClosing(source.getClosing());
        copy.setSuccessCriteria(source.getSuccessCriteria());
        copy.setEstimatedDurationMinutes(source.getEstimatedDurationMinutes());
        copy.setApplicableProducts(source.getApplicableProducts());
        copy.setTags(source.getTags());
        copy.setUsageCount(DEFAULT_COUNT);
        copy.setAvgSatisfactionScore(DEFAULT_RATE);
        copy.setEnabled(Boolean.TRUE);
        copy.setCreatedBy(planService.currentOperator());
        copy = templateRepository.save(copy);
        log.info("复制回访模板: sourceId={}, copyId={}, newCode={}", id, copy.getId(), newCode);
        return copy;
    }

    /**
     * 增长模板使用计数并更新平均满意度。
     *
     * @param id                模板 ID
     * @param satisfactionScore 本次满意度评分 (可空)
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public ScrmVisitTemplateEntity incrementUsage(Long id, Integer satisfactionScore) throws ScrmException {
        ScrmVisitTemplateEntity entity = findTemplateOrThrow(id);
        int count = entity.getUsageCount() == null ? 0 : entity.getUsageCount();
        double prevAvg = entity.getAvgSatisfactionScore() == null ? 0.0 : entity.getAvgSatisfactionScore();
        int newCount = count + 1;
        double newAvg = prevAvg;
        if (satisfactionScore != null) {
            newAvg = (prevAvg * count + satisfactionScore) / newCount;
        }
        entity.setUsageCount(newCount);
        entity.setAvgSatisfactionScore(newAvg);
        entity = templateRepository.save(entity);
        log.info("更新模板使用计数: id={}, usageCount={}, avgScore={}",
                id, entity.getUsageCount(), entity.getAvgSatisfactionScore());
        return entity;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验模板参数。
     *
     * @param dto     模板参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateTemplateDto(ScrmVisitTemplateDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("模板参数不能为空");
        }
        if (dto.getTemplateName() != null) {
            if (dto.getTemplateName().isBlank()) {
                throw ScrmException.badRequest("模板名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("模板名称不能为空");
        }
        if (dto.getTemplateCode() != null) {
            if (dto.getTemplateCode().isBlank()) {
                throw ScrmException.badRequest("模板编码不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("模板编码不能为空");
        }
        if (dto.getVisitType() != null) {
            if (!VALID_VISIT_TYPES.contains(dto.getVisitType())) {
                throw ScrmException.badRequest(
                        "回访类型非法: " + dto.getVisitType() + ", 仅支持 " + VALID_VISIT_TYPES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("回访类型不能为空");
        }
        if (dto.getVisitMethod() != null && !VALID_VISIT_METHODS.contains(dto.getVisitMethod())) {
            throw ScrmException.badRequest(
                    "回访方式非法: " + dto.getVisitMethod() + ", 仅支持 " + VALID_VISIT_METHODS);
        }
        if (!partial && (dto.getQuestions() == null || dto.getQuestions().isBlank())) {
            throw ScrmException.badRequest("问题列表不能为空");
        }
    }

    /**
     * 按主键查询模板, 不存在抛异常, 并校验归属账号。
     *
     * @param id 模板 ID
     * @return 模板实体
     * @throws ScrmException 模板不存在
     */
    private ScrmVisitTemplateEntity findTemplateOrThrow(Long id) throws ScrmException {
        ScrmVisitTemplateEntity entity = templateRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "回访模板不存在: id=" + id));
        return entity;
    }
}
