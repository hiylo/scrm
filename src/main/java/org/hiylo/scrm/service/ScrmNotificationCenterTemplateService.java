/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationCenterTemplateService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmNotificationTemplateDto;
import org.hiylo.scrm.entity.ScrmNotificationTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmNotificationTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 通知中心模板管理服务。
 * <p>
 * 承载通知模板管理的全部能力: 模板增删改查 (创建 / 更新 / 删除 / 详情 / 按编码查询 / 分页查询),
 * 模板启用与禁用, 以及模板渲染 (变量占位符替换)。
 * </p>
 * <p>
 * 拆自 {@link ScrmNotificationCenterService} 门面, 供通知中心各能力类共享的模板实体查找、
 * 模板渲染与分页排序工具也一并归属本类。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmNotificationCenterTemplateService {

    // ==================== 默认值常量 ====================

    /** 默认启用状态 (供偏好服务等兄弟类复用) */
    static final boolean DEFAULT_ENABLED = true;
    /** 默认 HTML 内容 */
    private static final boolean DEFAULT_HTML = false;

    // ==================== 数据访问层 ====================

    /** 通知模板数据访问层 */
    private final ScrmNotificationTemplateRepository templateRepository;

    /**
     * 创建通知模板。
     * <p>参数校验: templateName / templateCode / category / channel / title / content 必填,
     * templateCode 唯一; 默认 isHtml=false, enabled=true, usageCount=0。</p>
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 参数非法 / 模板编码重复
     */
    @Transactional
    public ScrmNotificationTemplateDto createTemplate(ScrmNotificationTemplateDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("模板参数不能为空");
        }
        if (templateRepository.countByTemplateCode(dto.getTemplateCode()) > 0) {
            throw ScrmException.conflict("模板编码已存在: " + dto.getTemplateCode());
        }
        ScrmNotificationTemplateEntity entity = new ScrmNotificationTemplateEntity();
        entity.setTemplateName(dto.getTemplateName());
        entity.setTemplateCode(dto.getTemplateCode());
        entity.setCategory(dto.getCategory());
        entity.setChannel(dto.getChannel());
        entity.setTitle(dto.getTitle());
        entity.setContent(dto.getContent());
        entity.setVariables(dto.getVariables());
        entity.setSenderName(dto.getSenderName());
        entity.setSenderEmail(dto.getSenderEmail());
        entity.setSmsSignName(dto.getSmsSignName());
        entity.setIsHtml(dto.getIsHtml() != null ? dto.getIsHtml() : DEFAULT_HTML);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : DEFAULT_ENABLED);
        entity.setUsageCount(0);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = templateRepository.save(entity);
        log.info("创建通知模板: id={}, templateCode={}, channel={}, category={}",
                entity.getId(), entity.getTemplateCode(), entity.getChannel(), entity.getCategory());
        return toTemplateDto(entity);
    }

    /**
     * 更新模板 (部分更新, 仅非空字段生效)。
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 模板编码重复
     */
    @Transactional
    public ScrmNotificationTemplateDto updateTemplate(
            Long id, ScrmNotificationTemplateDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("模板参数不能为空");
        }
        ScrmNotificationTemplateEntity entity = findTemplateOrThrow(id);
        if (dto.getTemplateName() != null) entity.setTemplateName(dto.getTemplateName());
        if (dto.getTemplateCode() != null) {
            if (!entity.getTemplateCode().equals(dto.getTemplateCode()) && templateRepository.countByTemplateCode(dto.getTemplateCode()) > 0) {
                throw ScrmException.conflict("模板编码已存在: " + dto.getTemplateCode());
            }
            entity.setTemplateCode(dto.getTemplateCode());
        }
        if (dto.getCategory() != null) entity.setCategory(dto.getCategory());
        if (dto.getChannel() != null) entity.setChannel(dto.getChannel());
        if (dto.getTitle() != null) entity.setTitle(dto.getTitle());
        if (dto.getContent() != null) entity.setContent(dto.getContent());
        if (dto.getVariables() != null) entity.setVariables(dto.getVariables());
        if (dto.getSenderName() != null) entity.setSenderName(dto.getSenderName());
        if (dto.getSenderEmail() != null) entity.setSenderEmail(dto.getSenderEmail());
        if (dto.getSmsSignName() != null) entity.setSmsSignName(dto.getSmsSignName());
        if (dto.getIsHtml() != null) entity.setIsHtml(dto.getIsHtml());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = templateRepository.save(entity);
        log.info("更新通知模板: id={}, templateCode={}", id, entity.getTemplateCode());
        return toTemplateDto(entity);
    }

    /**
     * 删除模板。
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public void deleteTemplate(Long id) throws ScrmException {
        ScrmNotificationTemplateEntity entity = findTemplateOrThrow(id);
        templateRepository.delete(entity);
        log.info("删除通知模板: id={}, templateCode={}", id, entity.getTemplateCode());
    }

    /**
     * 查询模板详情。
     *
     * @param id 模板 ID
     * @return 模板 DTO
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public ScrmNotificationTemplateDto getTemplate(Long id) throws ScrmException {
        return toTemplateDto(findTemplateOrThrow(id));
    }

    /**
     * 按模板编码查询模板。
     *
     * @param code 模板编码
     * @return 模板 DTO
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public ScrmNotificationTemplateDto getTemplateByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("模板编码不能为空");
        }
        ScrmNotificationTemplateEntity entity = templateRepository
                .findByTemplateCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "通知模板不存在: code=" + code));
        return toTemplateDto(entity);
    }

    /**
     * 分页查询模板, 支持按渠道 / 分类 / 启用状态 / 关键词过滤。
     *
     * @param channel  渠道过滤 (可空)
     * @param category 分类过滤 (可空)
     * @param enabled  启用状态过滤 (可空)
     * @param keyword  关键词过滤, 匹配模板名称 / 模板编码 (可空)
     * @param pageable 分页参数
     * @return 模板分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmNotificationTemplateDto> listTemplates(String channel, String category, Boolean enabled,
                                                            String keyword, Pageable pageable) {
        Specification<ScrmNotificationTemplateEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (channel != null && !channel.isBlank()) {
                predicates.add(cb.equal(root.get("channel"), channel));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("templateName")), kw),
                        cb.like(cb.lower(root.get("templateCode")), kw)
                ));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return templateRepository.findAll(spec, ensureSort(pageable, "createTime")).map(this::toTemplateDto);
    }

    /**
     * 启用模板。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public ScrmNotificationTemplateDto enableTemplate(Long id) throws ScrmException {
        ScrmNotificationTemplateEntity entity = findTemplateOrThrow(id);
        entity.setEnabled(true);
        entity = templateRepository.save(entity);
        log.info("启用通知模板: id={}", id);
        return toTemplateDto(entity);
    }

    /**
     * 禁用模板。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public ScrmNotificationTemplateDto disableTemplate(Long id) throws ScrmException {
        ScrmNotificationTemplateEntity entity = findTemplateOrThrow(id);
        entity.setEnabled(false);
        entity = templateRepository.save(entity);
        log.info("禁用通知模板: id={}", id);
        return toTemplateDto(entity);
    }

    /**
     * 渲染模板 (变量替换)。
     * <p>按模板编码加载模板并替换标题与内容中的 {varName} 占位符, 返回渲染结果。</p>
     *
     * @param code      模板编码
     * @param variables 变量映射 (可空)
     * @return 渲染结果 (title / content)
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public Map<String, String> renderTemplate(String code, Map<String, String> variables) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("模板编码不能为空");
        }
        ScrmNotificationTemplateEntity entity = templateRepository
                .findByTemplateCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "通知模板不存在: code=" + code));
        Map<String, String> rendered = new LinkedHashMap<>();
        rendered.put("title", render(entity.getTitle(), variables));
        rendered.put("content", render(entity.getContent(), variables));
        return rendered;
    }

    /**
     * 按主键查询模板并校验账号归属, 不存在或越权抛异常。
     *
     * @param id 模板 ID
     * @return 模板实体
     * @throws ScrmException 模板不存在
     */
    private ScrmNotificationTemplateEntity findTemplateOrThrow(Long id) throws ScrmException {
        ScrmNotificationTemplateEntity entity = templateRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "通知模板不存在: id=" + id));
        return entity;
    }

    /**
     * 确保分页参数带默认排序 (按指定字段倒序)。
     * <p>供通知中心多个兄弟服务 (发送 / 查询) 复用。</p>
     *
     * @param pageable 分页参数
     * @param field    默认排序字段
     * @return 处理后的分页参数
     */
    public Pageable ensureSort(Pageable pageable, String field) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, field));
    }

    /**
     * 渲染模板字符串: 将 {varName} 占位符替换为变量值。
     * <p>供通知中心通知发送服务复用。</p>
     *
     * @param template  模板字符串
     * @param variables 变量映射 (可空)
     * @return 渲染后的字符串
     */
    public String render(String template, Map<String, String> variables) {
        if (template == null) {
            return null;
        }
        String result = template;
        if (variables != null) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}",
                        entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return result;
    }

    /**
     * 模板实体转 DTO。
     */
    private ScrmNotificationTemplateDto toTemplateDto(ScrmNotificationTemplateEntity entity) {
        ScrmNotificationTemplateDto dto = new ScrmNotificationTemplateDto();
        dto.setId(entity.getId());
        dto.setTemplateName(entity.getTemplateName());
        dto.setTemplateCode(entity.getTemplateCode());
        dto.setCategory(entity.getCategory());
        dto.setChannel(entity.getChannel());
        dto.setTitle(entity.getTitle());
        dto.setContent(entity.getContent());
        dto.setVariables(entity.getVariables());
        dto.setSenderName(entity.getSenderName());
        dto.setSenderEmail(entity.getSenderEmail());
        dto.setSmsSignName(entity.getSmsSignName());
        dto.setIsHtml(entity.getIsHtml());
        dto.setEnabled(entity.getEnabled());
        dto.setUsageCount(entity.getUsageCount());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}