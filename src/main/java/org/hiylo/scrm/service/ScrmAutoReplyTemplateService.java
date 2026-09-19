/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoReplyTemplateService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmAutoReplyTemplateDto;
import org.hiylo.scrm.entity.ScrmAutoReplyTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmAutoReplyRuleRepository;
import org.hiylo.scrm.repository.ScrmAutoReplyTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 自动回复话术模板服务。
 * <p>
 * 承载回复模板的增删改查、启用/禁用、使用次数累计与模板渲染 (变量替换)。
 * 删除模板前检查是否有规则引用该模板 (replyTemplateId)。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmAutoReplyTemplateService {

    /** 默认启用状态 */
    private static final boolean DEFAULT_ENABLED = true;

    /** 默认模板类型 */
    private static final String DEFAULT_TEMPLATE_TYPE = "TEXT";

    /** 模板变量: 客户名称 */
    private static final String VAR_CUSTOMER_NAME = "customerName";

    /** 模板变量: 昵称 */
    private static final String VAR_NICKNAME = "nickname";

    /** 模板变量: 时间 */
    private static final String VAR_TIME = "time";

    /** 合法的模板类型 */
    private static final List<String> VALID_TEMPLATE_TYPES = List.of("TEXT", "RICH_TEXT", "HTML");

    /** 自动回复模板数据访问层 */
    private final ScrmAutoReplyTemplateRepository templateRepository;

    /** 自动回复规则数据访问层 (删除模板前检查规则引用) */
    private final ScrmAutoReplyRuleRepository ruleRepository;

    /**
     * 创建回复模板。
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmAutoReplyTemplateEntity createTemplate(ScrmAutoReplyTemplateDto dto) throws ScrmException {
        validateTemplateDto(dto, false);
        ScrmAutoReplyTemplateEntity entity = new ScrmAutoReplyTemplateEntity();
        entity.setTemplateName(dto.getTemplateName());
        entity.setTemplateType(dto.getTemplateType() != null && !dto.getTemplateType().isBlank()
                ? dto.getTemplateType() : DEFAULT_TEMPLATE_TYPE);
        entity.setCategory(dto.getCategory());
        entity.setContent(dto.getContent());
        entity.setVariables(dto.getVariables());
        entity.setApplicableScenes(dto.getApplicableScenes());
        entity.setThumbnailUrl(dto.getThumbnailUrl());
        entity.setUsageCount(0);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : DEFAULT_ENABLED);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = templateRepository.save(entity);
        log.info("创建回复模板: id={}, templateName={}", entity.getId(), entity.getTemplateName());
        return entity;
    }

    /**
     * 更新回复模板（字段非空才覆盖）。
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 参数非法
     */
    @Transactional
    public ScrmAutoReplyTemplateEntity updateTemplate(Long id, ScrmAutoReplyTemplateDto dto)
            throws ScrmException {
        ScrmAutoReplyTemplateEntity entity = findTemplateOrThrow(id);
        validateTemplateDto(dto, true);
        if (dto.getTemplateName() != null) entity.setTemplateName(dto.getTemplateName());
        if (dto.getTemplateType() != null) entity.setTemplateType(dto.getTemplateType());
        if (dto.getCategory() != null) entity.setCategory(dto.getCategory());
        if (dto.getContent() != null) entity.setContent(dto.getContent());
        if (dto.getVariables() != null) entity.setVariables(dto.getVariables());
        if (dto.getApplicableScenes() != null) entity.setApplicableScenes(dto.getApplicableScenes());
        if (dto.getThumbnailUrl() != null) entity.setThumbnailUrl(dto.getThumbnailUrl());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = templateRepository.save(entity);
        log.info("更新回复模板: id={}, templateName={}", entity.getId(), entity.getTemplateName());
        return entity;
    }

    /**
     * 删除回复模板。
     * <p>删除前检查是否有规则引用该模板 (replyTemplateId), 若有则阻止删除。</p>
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在 / 仍有规则引用
     */
    @Transactional
    public void deleteTemplate(Long id) throws ScrmException {
        ScrmAutoReplyTemplateEntity entity = findTemplateOrThrow(id);
        long refCount = ruleRepository.count((root, query, cb) -> cb.equal(root.get("replyTemplateId"), id));
        if (refCount > 0) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    String.format("无法删除模板: 仍有 %d 条规则引用该模板", refCount));
        }
        templateRepository.delete(entity);
        log.info("删除回复模板: id={}, templateName={}", id, entity.getTemplateName());
    }

    /**
     * 查询模板详情。
     *
     * @param id 模板 ID
     * @return 模板实体
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public ScrmAutoReplyTemplateEntity getTemplate(Long id) throws ScrmException {
        return findTemplateOrThrow(id);
    }

    /**
     * 分页查询模板, 支持按模板类型、分类、启用状态与关键字过滤。
     *
     * @param templateType 模板类型过滤（可空）
     * @param category     分类过滤（可空）
     * @param enabled      启用状态过滤（可空）
     * @param keyword      关键字过滤（按模板名称模糊匹配, 可空）
     * @param pageable     分页参数
     * @return 模板分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmAutoReplyTemplateEntity> listTemplates(String templateType, String category,
                                                            Boolean enabled, String keyword, Pageable pageable) {
        Specification<ScrmAutoReplyTemplateEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (templateType != null && !templateType.isBlank()) {
                predicates.add(cb.equal(root.get("templateType"), templateType));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("templateName")), kw));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return templateRepository.findAll(spec, pageable);
    }

    /**
     * 启用模板。
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public void enableTemplate(Long id) throws ScrmException {
        ScrmAutoReplyTemplateEntity entity = findTemplateOrThrow(id);
        entity.setEnabled(true);
        templateRepository.save(entity);
        log.info("启用回复模板: id={}, templateName={}", id, entity.getTemplateName());
    }

    /**
     * 禁用模板。
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public void disableTemplate(Long id) throws ScrmException {
        ScrmAutoReplyTemplateEntity entity = findTemplateOrThrow(id);
        entity.setEnabled(false);
        templateRepository.save(entity);
        log.info("禁用回复模板: id={}, templateName={}", id, entity.getTemplateName());
    }

    /**
     * 渲染模板 (变量替换)。
     * <p>支持变量: {customerName}/{nickname}/{time}。variables 中提供的额外变量也会替换。</p>
     *
     * @param id       模板 ID
     * @param variables 变量 Map (key 为变量名, value 为变量值)
     * @return 渲染后的内容
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public String renderTemplate(Long id, Map<String, String> variables) throws ScrmException {
        ScrmAutoReplyTemplateEntity template = findTemplateOrThrow(id);
        String content = template.getContent();
        if (content == null || content.isBlank()) {
            return "";
        }
        Map<String, String> merged = new HashMap<>();
        // 默认变量
        merged.put(VAR_TIME, LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        if (variables != null) {
            merged.putAll(variables);
        }
        // customerName 与 nickname 互为别名
        String customerName = merged.get(VAR_CUSTOMER_NAME);
        if (customerName == null) {
            customerName = merged.get(VAR_NICKNAME);
        }
        if (customerName != null) {
            merged.put(VAR_CUSTOMER_NAME, customerName);
            merged.put(VAR_NICKNAME, customerName);
        }
        String rendered = content;
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            if (entry.getValue() != null) {
                rendered = rendered.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return rendered;
    }

    /**
     * 增加模板使用次数（直接 SQL 更新, 避免乐观锁冲突）。
     *
     * @param id 模板 ID
     */
    @Transactional
    public void incrementUsage(Long id) {
        templateRepository.incrementUsageCount(id);
    }

    /**
     * 按主键查询模板, 不存在抛异常, 并校验账号归属。
     *
     * @param id 模板 ID
     * @return 模板实体
     * @throws ScrmException 模板不存在
     */
    private ScrmAutoReplyTemplateEntity findTemplateOrThrow(Long id) throws ScrmException {
        ScrmAutoReplyTemplateEntity entity = templateRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "回复模板不存在: id=" + id));

        return entity;
    }

    /**
     * 校验模板参数。
     *
     * @param dto     模板参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateTemplateDto(ScrmAutoReplyTemplateDto dto, boolean partial) throws ScrmException {
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
        if (dto.getTemplateType() != null && !dto.getTemplateType().isBlank() && !VALID_TEMPLATE_TYPES.contains(dto.getTemplateType())) {
            throw ScrmException.badRequest(
                    "模板类型非法: " + dto.getTemplateType() + ", 仅支持 " + VALID_TEMPLATE_TYPES);
        }
        if (dto.getContent() != null) {
            if (dto.getContent().isBlank()) {
                throw ScrmException.badRequest("模板内容不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("模板内容不能为空");
        }
    }
}
