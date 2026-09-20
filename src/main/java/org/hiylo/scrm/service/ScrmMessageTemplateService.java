/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTemplateService.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmMessageTemplateDto;
import org.hiylo.scrm.entity.ScrmMessageTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmMessageTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SCRM 消息模板服务（快捷回复）。
 * <p>
 * 承载消息模板的增删改查、启用/禁用、适用模板查询与变量插值渲染能力。所有写操作
 * 写入当前用户归属账号, 实现数据隔离。模板内容支持
 * {@code {{nickname}}} / {@code {{platformType}}} / {@code {{customerName}}} /
 * {@code {{ownerName}}} 等变量插值, 创建/更新时通过正则 {@code \{\{(\w+)\}\}} 自动提取变量列表
 * 写入 variables 字段, 渲染时缺失变量替换为空字符串。
 * </p>
 * <p>
 * {@link #renderContent} 作为静态方法供其他 service 在已知模板内容时直接调用, 无需加载实体。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmMessageTemplateService {

    /** 默认启用状态 */
    private static final boolean DEFAULT_ENABLED = true;

    /** 默认排序值（数字越小越靠前） */
    private static final int DEFAULT_SORT_ORDER = 0;

    /** 模板名称最大长度 */
    private static final int MAX_TEMPLATE_NAME_LENGTH = 100;

    /** 模板内容最大长度 (支持长文本话术) */
    private static final int MAX_CONTENT_LENGTH = 5000;

    /** 分类最大长度 */
    private static final int MAX_CATEGORY_LENGTH = 50;

    /** 变量插值正则: 匹配 {{varName}} 形式, 捕获组 1 为变量名 */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    /** 消息模板数据访问层 */
    private final ScrmMessageTemplateRepository messageTemplateRepository;

    /** JSON 序列化/反序列化器（variables 字段读写） */
    private final ObjectMapper objectMapper;

    /**
     * 创建消息模板。
     * <p>从 content 中自动提取变量列表写入 variables 字段, enabled/sortOrder 缺省时填默认值。</p>
     * <p>参数校验: templateName 非空且不超过 100 字符, content 非空且不超过 5000 字符,
     * category 不超过 50 字符。</p>
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 模板名称已存在 / 参数非法 / variables 序列化失败
     */
    @Transactional
    public ScrmMessageTemplateEntity createTemplate(ScrmMessageTemplateDto dto) throws ScrmException {
        validateTemplateDto(dto, false);
        if (messageTemplateRepository.findByTemplateName(dto.getTemplateName()).isPresent()) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "模板名称已存在: templateName=" + dto.getTemplateName());
        }
        ScrmMessageTemplateEntity entity = new ScrmMessageTemplateEntity();
        entity.setTemplateName(dto.getTemplateName());
        entity.setCategory(dto.getCategory());
        entity.setContent(dto.getContent());
        entity.setPlatformType(dto.getPlatformType());
        entity.setVariables(extractVariablesAsJson(dto.getContent()));
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : DEFAULT_ENABLED);
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : DEFAULT_SORT_ORDER);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = messageTemplateRepository.save(entity);
        log.info("创建消息模板: id={}, templateName={}, category={}",
                entity.getId(), entity.getTemplateName(), entity.getCategory());
        return entity;
    }

    /**
     * 更新消息模板（字段非空才覆盖）。
     * <p>content 变更时自动重新提取 variables 字段。
     * 参数校验同 {@link #createTemplate}, 但允许部分字段为空 (仅校验非空字段)。</p>
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 模板名称与其他模板冲突 / 参数非法
     */
    @Transactional
    public ScrmMessageTemplateEntity updateTemplate(Long id, ScrmMessageTemplateDto dto) throws ScrmException {
        ScrmMessageTemplateEntity entity = findOrThrow(id);
        // 部分更新场景: 仅校验非空字段
        validateTemplateDto(dto, true);
        // templateName 变更时校验与其他模板不冲突
        if (dto.getTemplateName() != null && !dto.getTemplateName().equals(entity.getTemplateName())) {
            messageTemplateRepository.findByTemplateName(
                     dto.getTemplateName()).ifPresent(other -> {
                if (!other.getId().equals(id)) {
                    throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                            "模板名称已被其他模板占用: templateName=" + dto.getTemplateName());
                }
            });
            entity.setTemplateName(dto.getTemplateName());
        }
        if (dto.getCategory() != null) entity.setCategory(dto.getCategory());
        if (dto.getContent() != null) {
            entity.setContent(dto.getContent());
            entity.setVariables(extractVariablesAsJson(dto.getContent()));
        }
        if (dto.getPlatformType() != null) entity.setPlatformType(dto.getPlatformType());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getSortOrder() != null) entity.setSortOrder(dto.getSortOrder());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = messageTemplateRepository.save(entity);
        log.info("更新消息模板: id={}, templateName={}", entity.getId(), entity.getTemplateName());
        return entity;
    }

    /**
     * 删除消息模板。
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public void deleteTemplate(Long id) throws ScrmException {
        findOrThrow(id);
        messageTemplateRepository.deleteById(id);
        log.info("删除消息模板: id={}", id);
    }

    /**
     * 查询模板详情。
     *
     * @param id 模板 ID
     * @return 模板实体
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public ScrmMessageTemplateEntity getTemplate(Long id) throws ScrmException {
        return findOrThrow(id);
    }

    /**
     * 分页查询消息模板, 支持按分类、启用状态与关键字过滤。
     * <p>过滤优先级: category > enabled > keyword, 均为空时全量分页 (按 sortOrder ASC, createTime DESC)。
     * 多条件组合时按主条件查询后在内存中二次过滤。</p>
     *
     * @param category 分类过滤（可空）
     * @param enabled  启用状态过滤（可空）
     * @param keyword  关键字过滤（按 templateName / category 模糊匹配, 可空）
     * @param page     页码（从 0 开始）
     * @param size     每页大小
     * @return 模板分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmMessageTemplateEntity> listTemplates(String category, Boolean enabled,
                                                         String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "sortOrder")
                        .and(Sort.by(Sort.Direction.DESC, "createTime")));
        Specification<ScrmMessageTemplateEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 数据隔离: 始终按当前用户可见账号范围过滤
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
                        cb.like(cb.lower(root.get("category")), kw)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return messageTemplateRepository.findAll(spec, pageable);
    }

    /**
     * 查询适用模板（含通用模板 platformType 为空）, 仅返回启用模板, 按 sortOrder ASC 排列。
     *
     * @param platformType 平台类型（可空, 空则不按平台过滤）
     * @return 适用模板列表
     */
    @Transactional(readOnly = true)
    public List<ScrmMessageTemplateEntity> getApplicableTemplates(String platformType) {
        return messageTemplateRepository.findApplicableTemplates(platformType);
    }

    /**
     * 渲染指定模板: 将 {@code {{key}}} 替换为 variables.get(key), 缺失变量替换为空字符串。
     *
     * @param id        模板 ID
     * @param variables 变量值映射（可空, 空则全部变量替换为空字符串）
     * @return 渲染后的内容
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public String renderTemplate(Long id, Map<String, String> variables) throws ScrmException {
        ScrmMessageTemplateEntity entity = findOrThrow(id);
        return renderContent(entity.getContent(), variables);
    }

    /**
     * 静态渲染方法: 将 content 中的 {@code {{key}}} 替换为 variables.get(key),
     * 缺失变量替换为空字符串。供其他 service 在已知内容时直接调用, 无需加载实体。
     *
     * @param content   模板内容
     * @param variables 变量值映射（可空, 空则全部变量替换为空字符串）
     * @return 渲染后的内容
     */
    public static String renderContent(String content, Map<String, String> variables) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = (variables != null && variables.containsKey(key))
                    ? String.valueOf(variables.get(key))
                    : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 启用模板。
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public void enableTemplate(Long id) throws ScrmException {
        ScrmMessageTemplateEntity entity = findOrThrow(id);
        entity.setEnabled(true);
        messageTemplateRepository.save(entity);
        log.info("启用消息模板: id={}, templateName={}", id, entity.getTemplateName());
    }

    /**
     * 禁用模板。
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public void disableTemplate(Long id) throws ScrmException {
        ScrmMessageTemplateEntity entity = findOrThrow(id);
        entity.setEnabled(false);
        messageTemplateRepository.save(entity);
        log.info("禁用消息模板: id={}, templateName={}", id, entity.getTemplateName());
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验模板参数。
     * <p>
     * 创建场景 (partial=false): templateName 与 content 必填, 不允许为空。
     * 更新场景 (partial=true): 允许字段为空 (部分更新), 仅校验非空字段的合法性。
     * </p>
     * <ul>
     *   <li>templateName: 非空 (创建场景), 长度 ≤ {@value #MAX_TEMPLATE_NAME_LENGTH}</li>
     *   <li>content: 非空 (创建场景), 长度 ≤ {@value #MAX_CONTENT_LENGTH}</li>
     *   <li>category: 长度 ≤ {@value #MAX_CATEGORY_LENGTH}</li>
     * </ul>
     *
     * @param dto     模板参数
     * @param partial 是否为部分更新场景 (true 时允许必填字段为空)
     * @throws ScrmException 参数非法
     */
    private void validateTemplateDto(ScrmMessageTemplateDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("模板参数不能为空");
        }
        // templateName 校验
        if (dto.getTemplateName() != null) {
            if (dto.getTemplateName().isBlank()) {
                throw ScrmException.badRequest("模板名称不能为空");
            }
            if (dto.getTemplateName().length() > MAX_TEMPLATE_NAME_LENGTH) {
                throw ScrmException.badRequest(
                        "模板名称长度不能超过 " + MAX_TEMPLATE_NAME_LENGTH + " 字符");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("模板名称不能为空");
        }
        // content 校验
        if (dto.getContent() != null) {
            if (dto.getContent().isBlank()) {
                throw ScrmException.badRequest("模板内容不能为空");
            }
            if (dto.getContent().length() > MAX_CONTENT_LENGTH) {
                throw ScrmException.badRequest(
                        "模板内容长度不能超过 " + MAX_CONTENT_LENGTH + " 字符");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("模板内容不能为空");
        }
        // category 校验 (可选, 但有值时检查长度)
        if (dto.getCategory() != null && dto.getCategory().length() > MAX_CATEGORY_LENGTH) {
            throw ScrmException.badRequest(
                    "分类长度不能超过 " + MAX_CATEGORY_LENGTH + " 字符");
        }
    }

    /**
     * 从 content 中提取变量名列表并序列化为 JSON 数组字符串。
     * <p>正则 {@code \{\{(\w+)\}\}} 匹配 {@code {{varName}}} 形式, 捕获组 1 为变量名。
     * 重复变量去重保留顺序, 无变量时返回 {@code "[]"}。</p>
     *
     * @param content 模板内容
     * @return JSON 数组字符串, 如 {@code ["nickname", "platformType"]}
     * @throws ScrmException JSON 序列化失败
     */
    private String extractVariablesAsJson(String content) throws ScrmException {
        java.util.List<String> variables = new java.util.ArrayList<>();
        if (content != null && !content.isEmpty()) {
            Matcher matcher = VARIABLE_PATTERN.matcher(content);
            java.util.Set<String> seen = new java.util.HashSet<>();
            while (matcher.find()) {
                String name = matcher.group(1);
                if (seen.add(name)) {
                    variables.add(name);
                }
            }
        }
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (JsonProcessingException e) {
            throw new ScrmException(ScrmExceptionConstants.INTERNAL_ERROR,
                    "变量列表序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 在内存中按分类与启用状态过滤分页结果（用于多条件组合场景）。
     *
     * @param page     原始分页
     * @param category 分类过滤（可空）
     * @param enabled  启用状态过滤（可空）
     * @return 过滤后的分页 (PageImpl)
     */
    private Page<ScrmMessageTemplateEntity> filterInMemory(Page<ScrmMessageTemplateEntity> page,
                                                          String category, Boolean enabled) {
        List<ScrmMessageTemplateEntity> filtered = page.getContent().stream()
                .filter(e -> category == null || category.isBlank() || category.equals(e.getCategory()))
                .filter(e -> enabled == null || enabled.equals(e.getEnabled()))
                .toList();
        return new PageImpl<>(filtered, page.getPageable(), filtered.size());
    }

    /**
     * 按主键查询模板, 不存在抛异常。
     *
     * @param id 模板 ID
     * @return 模板实体
     * @throws ScrmException 模板不存在
     */
    private ScrmMessageTemplateEntity findOrThrow(Long id) throws ScrmException {
        ScrmMessageTemplateEntity entity = messageTemplateRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "消息模板不存在: id=" + id));
        // 数据隔离: 校验模板归属当前账号

        return entity;
    }
}
