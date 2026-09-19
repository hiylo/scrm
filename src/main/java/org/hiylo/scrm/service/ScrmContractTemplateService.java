/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractTemplateService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmContractTemplateDto;
import org.hiylo.scrm.entity.ScrmContractEntity;
import org.hiylo.scrm.entity.ScrmContractTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmContractRepository;
import org.hiylo.scrm.repository.ScrmContractTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SCRM 合同模板管理服务 (模板子域)。
 * <p>
 * 承载合同模板增删改查 / 按编码查询 / 启停 / 渲染 / 复制 / 使用统计 / 变量定义解析。
 * 同时托管模板共享常量 (模板状态 / 合法合同类型 / 变量占位符正则) 与变量解析、内容渲染、
 * 按主键查询模板能力, 供合同管理兄弟类以 package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmContractTemplateService {

    // ==================== 模板状态 (共享) ====================
    /** 模板状态: 启用 */
    static final String TEMPLATE_STATUS_ACTIVE = "ACTIVE";
    /** 模板状态: 禁用 */
    static final String TEMPLATE_STATUS_INACTIVE = "INACTIVE";
    /** 模板状态: 草稿 */
    static final String TEMPLATE_STATUS_DRAFT = "DRAFT";

    // ==================== 合同类型 (共享) ====================
    /** 合同类型合法集合 */
    private static final Set<String> VALID_CONTRACT_TYPES = new HashSet<>(Arrays.asList(
            "SALES", "SERVICE", "PARTNERSHIP", "NDA", "RESELLER",
            "AGENCY", "MAINTENANCE", "RENTAL", "PURCHASE", "CUSTOM"));

    // ==================== 渲染常量 ====================
    /** 变量占位符正则: {{variableName}} */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*(\\w+)\\s*\\}\\}");

    /** JSON 解析器 (解析模板变量与渲染) */
    private final ObjectMapper objectMapper;

    /** 合同模板数据访问层 */
    private final ScrmContractTemplateRepository templateRepository;

    /** 合同实例数据访问层 (删除模板时校验引用) */
    private final ScrmContractRepository contractRepository;

    // ============================================================
    // 模板管理
    // ============================================================

    /**
     * 创建合同模板。
     * <p>校验参数合法性后写入归属账号 ID 持久化, 状态缺省 ACTIVE, 版本与使用次数缺省 0。</p>
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 参数非法 / 模板编码重复
     */
    @Transactional
    public ScrmContractTemplateDto createTemplate(ScrmContractTemplateDto dto) throws ScrmException {
        validateTemplateDto(dto, false);
        if (templateRepository.findByTemplateCode(dto.getTemplateCode()).isPresent()) {
            throw ScrmException.conflict("模板编码已存在: code=" + dto.getTemplateCode());
        }
        ScrmContractTemplateEntity entity = new ScrmContractTemplateEntity();
        entity.setTemplateName(dto.getTemplateName());
        entity.setTemplateCode(dto.getTemplateCode());
        entity.setDescription(dto.getDescription());
        entity.setContractType(dto.getContractType());
        entity.setTemplateContent(dto.getTemplateContent());
        entity.setVariables(dto.getVariables());
        entity.setApplicableProducts(dto.getApplicableProducts());
        entity.setClauses(dto.getClauses());
        entity.setTemplateVersion(dto.getTemplateVersion() != null ? dto.getTemplateVersion() : 1);
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : TEMPLATE_STATUS_ACTIVE);
        entity.setUsageCount(0);
        entity.setReviewedBy(dto.getReviewedBy());
        entity.setApprovedAt(dto.getApprovedAt());
        entity.setCreatedBy(dto.getCreatedBy());
        entity = templateRepository.save(entity);
        log.info("创建合同模板: id={}, templateName={}, contractType={}",
                entity.getId(), entity.getTemplateName(), entity.getContractType());
        return toTemplateDto(entity);
    }

    /**
     * 更新合同模板 (字段非空才覆盖)。
     * <p>更新模板内容时自动递增业务版本号。</p>
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 参数非法 / 模板编码重复
     */
    @Transactional
    public ScrmContractTemplateDto updateTemplate(Long id, ScrmContractTemplateDto dto) throws ScrmException {
        ScrmContractTemplateEntity entity = findTemplateOrThrow(id);
        validateTemplateDto(dto, true);
        if (dto.getTemplateCode() != null && !dto.getTemplateCode().equals(entity.getTemplateCode())) {
            if (templateRepository.findByTemplateCode(dto.getTemplateCode()).isPresent()) {
                throw ScrmException.conflict("模板编码已存在: code=" + dto.getTemplateCode());
            }
            entity.setTemplateCode(dto.getTemplateCode());
        }
        boolean contentChanged = dto.getTemplateContent() != null && !dto.getTemplateContent().equals(entity.getTemplateContent());
        if (dto.getTemplateName() != null) entity.setTemplateName(dto.getTemplateName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getContractType() != null) entity.setContractType(dto.getContractType());
        if (dto.getTemplateContent() != null) entity.setTemplateContent(dto.getTemplateContent());
        if (dto.getVariables() != null) entity.setVariables(dto.getVariables());
        if (dto.getApplicableProducts() != null) entity.setApplicableProducts(dto.getApplicableProducts());
        if (dto.getClauses() != null) entity.setClauses(dto.getClauses());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getReviewedBy() != null) entity.setReviewedBy(dto.getReviewedBy());
        if (dto.getApprovedAt() != null) entity.setApprovedAt(dto.getApprovedAt());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        // 模板内容变更时递增业务版本号
        if (contentChanged) {
            int currentVersion = entity.getTemplateVersion() != null ? entity.getTemplateVersion() : 0;
            entity.setTemplateVersion(currentVersion + 1);
        }
        entity = templateRepository.save(entity);
        log.info("更新合同模板: id={}, version={}", id, entity.getTemplateVersion());
        return toTemplateDto(entity);
    }

    /**
     * 删除合同模板。
     * <p>已被合同引用的模板不允许删除 (避免合同悬空引用), 请先停用模板。</p>
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在 / 已被合同引用不允许删除
     */
    @Transactional
    public void deleteTemplate(Long id) throws ScrmException {
        ScrmContractTemplateEntity entity = findTemplateOrThrow(id);
        Specification<ScrmContractEntity> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("templateId"), id));
        long refCount = contractRepository.count(spec);
        if (refCount > 0) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "模板已被合同引用, 不允许删除, 请先停用: id=" + id + ", refCount=" + refCount);
        }
        templateRepository.delete(entity);
        log.info("删除合同模板: id={}", id);
    }

    /**
     * 查询合同模板详情。
     *
     * @param id 模板 ID
     * @return 模板 DTO
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public ScrmContractTemplateDto getTemplate(Long id) throws ScrmException {
        return toTemplateDto(findTemplateOrThrow(id));
    }

    /**
     * 按模板编码查询合同模板。
     *
     * @param code 模板编码
     * @return 模板 DTO
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public ScrmContractTemplateDto getTemplateByCode(String code) throws ScrmException {
        ScrmContractTemplateEntity entity = templateRepository.findByTemplateCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "合同模板不存在: code=" + code));

        return toTemplateDto(entity);
    }

    /**
     * 分页查询合同模板, 支持按合同类型、状态与关键词过滤。
     *
     * @param contractType 合同类型过滤 (可空)
     * @param status       状态过滤 (可空)
     * @param keyword      关键词过滤, 匹配模板名称或编码 (可空)
     * @param pageable     分页参数
     * @return 模板分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmContractTemplateDto> listTemplates(String contractType, String status, String keyword,
                                                        Pageable pageable) {
        Specification<ScrmContractTemplateEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (contractType != null && !contractType.isBlank()) {
                predicates.add(cb.equal(root.get("contractType"), contractType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("templateName")), like),
                        cb.like(cb.lower(root.get("templateCode")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return templateRepository.findAll(spec, pageable).map(this::toTemplateDto);
    }

    /**
     * 启用合同模板 (状态置 ACTIVE)。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public ScrmContractTemplateDto activateTemplate(Long id) throws ScrmException {
        ScrmContractTemplateEntity entity = findTemplateOrThrow(id);
        entity.setStatus(TEMPLATE_STATUS_ACTIVE);
        entity = templateRepository.save(entity);
        log.info("启用合同模板: id={}", id);
        return toTemplateDto(entity);
    }

    /**
     * 停用合同模板 (状态置 INACTIVE), 已创建合同不受影响。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public ScrmContractTemplateDto deactivateTemplate(Long id) throws ScrmException {
        ScrmContractTemplateEntity entity = findTemplateOrThrow(id);
        entity.setStatus(TEMPLATE_STATUS_INACTIVE);
        entity = templateRepository.save(entity);
        log.info("停用合同模板: id={}", id);
        return toTemplateDto(entity);
    }

    /**
     * 渲染模板: 将模板内容中的 {{variableName}} 占位符替换为 variables 中的值。
     * <p>variables 为 JSON 字符串 (键值对), 未提供的变量保留占位符原样。
     * 渲染后递增模板使用次数并刷新最后使用时间。</p>
     *
     * @param templateId 模板 ID
     * @param variables  变量值 JSON 字符串 (可空)
     * @return 渲染后的合同内容
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public String renderTemplate(Long templateId, String variables) throws ScrmException {
        ScrmContractTemplateEntity entity = findTemplateOrThrow(templateId);
        Map<String, Object> varMap = parseVariables(variables);
        String rendered = renderContent(entity.getTemplateContent(), varMap);
        log.info("渲染合同模板: id={}, variables={}", templateId, varMap.size());
        return rendered;
    }

    /**
     * 复制合同模板 (基于已有模板创建新模板, 新模板编码由参数指定)。
     * <p>复制后的模板状态为 DRAFT, 使用次数为 0, 业务版本号重置为 1。</p>
     *
     * @param id      源模板 ID
     * @param newCode 新模板编码
     * @return 新模板
     * @throws ScrmException 模板不存在 / 新编码重复
     */
    @Transactional
    public ScrmContractTemplateDto copyTemplate(Long id, String newCode) throws ScrmException {
        if (newCode == null || newCode.isBlank()) {
            throw ScrmException.badRequest("新模板编码不能为空");
        }
        ScrmContractTemplateEntity source = findTemplateOrThrow(id);
        if (templateRepository.findByTemplateCode(newCode).isPresent()) {
            throw ScrmException.conflict("模板编码已存在: code=" + newCode);
        }
        ScrmContractTemplateEntity entity = new ScrmContractTemplateEntity();
        entity.setTemplateName(source.getTemplateName() + "_copy");
        entity.setTemplateCode(newCode);
        entity.setDescription(source.getDescription());
        entity.setContractType(source.getContractType());
        entity.setTemplateContent(source.getTemplateContent());
        entity.setVariables(source.getVariables());
        entity.setApplicableProducts(source.getApplicableProducts());
        entity.setClauses(source.getClauses());
        entity.setTemplateVersion(1);
        entity.setStatus(TEMPLATE_STATUS_DRAFT);
        entity.setUsageCount(0);
        entity.setCreatedBy(source.getCreatedBy());
        entity = templateRepository.save(entity);
        log.info("复制合同模板: sourceId={}, newId={}, newCode={}", id, entity.getId(), newCode);
        return toTemplateDto(entity);
    }

    /**
     * 递增模板使用次数并刷新最后使用时间。
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public void incrementUsage(Long id) throws ScrmException {
        ScrmContractTemplateEntity entity = findTemplateOrThrow(id);
        int usage = entity.getUsageCount() != null ? entity.getUsageCount() : 0;
        entity.setUsageCount(usage + 1);
        entity.setLastUsedAt(LocalDateTime.now());
        templateRepository.save(entity);
    }

    /**
     * 获取模板变量定义 (解析 variables JSON 并返回变量列表)。
     *
     * @param id 模板 ID
     * @return 变量定义列表
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTemplateVariables(Long id) throws ScrmException {
        ScrmContractTemplateEntity entity = findTemplateOrThrow(id);
        if (entity.getVariables() == null || entity.getVariables().isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(entity.getVariables(),
                    new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("模板变量 JSON 解析失败: id={}, error={}", id, e.getMessage());
            return new ArrayList<>();
        }
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
    private void validateTemplateDto(ScrmContractTemplateDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("模板参数不能为空");
        }
        if (!partial) {
            if (dto.getTemplateName() == null || dto.getTemplateName().isBlank()) {
                throw ScrmException.badRequest("模板名称不能为空");
            }
            if (dto.getTemplateCode() == null || dto.getTemplateCode().isBlank()) {
                throw ScrmException.badRequest("模板编码不能为空");
            }
            if (dto.getContractType() == null || dto.getContractType().isBlank()) {
                throw ScrmException.badRequest("合同类型不能为空");
            }
            if (dto.getTemplateContent() == null || dto.getTemplateContent().isBlank()) {
                throw ScrmException.badRequest("模板内容不能为空");
            }
        }
        if (dto.getContractType() != null && !VALID_CONTRACT_TYPES.contains(dto.getContractType())) {
            throw ScrmException.badRequest("合同类型非法: " + dto.getContractType());
        }
    }

    /**
     * 按主键查询模板, 不存在抛异常
     */
    ScrmContractTemplateEntity findTemplateOrThrow(Long id) throws ScrmException {
        ScrmContractTemplateEntity entity = templateRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "合同模板不存在: id=" + id));

        return entity;
    }

    /**
     * 模板实体转 DTO
     */
    private ScrmContractTemplateDto toTemplateDto(ScrmContractTemplateEntity entity) {
        ScrmContractTemplateDto dto = new ScrmContractTemplateDto();
        dto.setId(entity.getId());
        dto.setTemplateName(entity.getTemplateName());
        dto.setTemplateCode(entity.getTemplateCode());
        dto.setDescription(entity.getDescription());
        dto.setContractType(entity.getContractType());
        dto.setTemplateContent(entity.getTemplateContent());
        dto.setVariables(entity.getVariables());
        dto.setApplicableProducts(entity.getApplicableProducts());
        dto.setClauses(entity.getClauses());
        dto.setTemplateVersion(entity.getTemplateVersion());
        dto.setStatus(entity.getStatus());
        dto.setUsageCount(entity.getUsageCount());
        dto.setLastUsedAt(entity.getLastUsedAt());
        dto.setReviewedBy(entity.getReviewedBy());
        dto.setApprovedAt(entity.getApprovedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 解析变量 JSON 字符串为 Map (支持对象或键值对格式)。
     * <p>variables 可能为 {"key":"value"} 或 [{name, defaultValue}] 格式,
     * 后者取 defaultValue 作为变量值。</p>
     *
     * @param variables JSON 字符串 (可空)
     * @return 变量键值对
     */
    Map<String, Object> parseVariables(String variables) {
        if (variables == null || variables.isBlank()) {
            return new HashMap<>();
        }
        try {
            // 尝试解析为对象
            Map<String, Object> map = objectMapper.readValue(variables,
                    new TypeReference<Map<String, Object>>() {});
            return map != null ? map : new HashMap<>();
        } catch (JsonProcessingException e1) {
            // 尝试解析为变量定义列表 [{name, defaultValue}]
            try {
                List<Map<String, Object>> list = objectMapper.readValue(variables,
                        new TypeReference<List<Map<String, Object>>>() {});
                Map<String, Object> map = new HashMap<>();
                for (Map<String, Object> item : list) {
                    Object name = item.get("name");
                    Object value = item.get("defaultValue");
                    if (name != null) {
                        map.put(name.toString(), value != null ? value : "");
                    }
                }
                return map;
            } catch (Exception e2) {
                log.warn("变量 JSON 解析失败: variables={}, error={}", variables, e2.getMessage());
                return new HashMap<>();
            }
        }
    }

    /**
     * 渲染合同内容: 将 {{variableName}} 占位符替换为变量值。
     * <p>未提供的变量保留占位符原样, 变量值统一转为字符串。</p>
     *
     * @param content  原始内容
     * @param varMap   变量键值对
     * @return 渲染后的内容
     */
    String renderContent(String content, Map<String, Object> varMap) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        if (varMap == null || varMap.isEmpty()) {
            return content;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = varMap.get(varName);
            String replacement = value != null ? String.valueOf(value) : matcher.group(0);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}