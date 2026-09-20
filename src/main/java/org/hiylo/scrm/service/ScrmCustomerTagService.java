/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerTagService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCustomerTagDto;
import org.hiylo.scrm.dto.ScrmTagCustomerDto;
import org.hiylo.scrm.dto.ScrmTagRuleDto;
import org.hiylo.scrm.dto.ScrmTagRuleEvaluateDto;
import org.hiylo.scrm.dto.ScrmTagRuleTestDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerTagEntity;
import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.hiylo.scrm.entity.ScrmTagRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmCustomerTagRepository;
import org.hiylo.scrm.repository.ScrmTagCustomerRepository;
import org.hiylo.scrm.repository.ScrmTagRuleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * SCRM 客户标签画像管理服务。
 * <p>
 * 承载客户标签画像模块的核心能力: 标签定义增删改查 / 启用禁用, 客户打标 (单条 / 批量) /
 * 去标 / 按标签查客户 / 查客户标签, 标签自动规则 (ScrmTagRuleEntity) 的增删改查 / 启用禁用 /
 * 实现数据隔离, findOrThrow 校验资源存在性并核对账号归属。
 * </p>
 * <p>
 * 标签定义 ({@link ScrmCustomerTagEntity}) 与客户-标签关联 ({@link ScrmTagCustomerEntity})
 * 分离: 同一客户同一标签默认仅保留一条关联, 重复打标按 tagValue 覆盖更新。规则
 * ({@link ScrmTagRuleEntity}) 由 conditions JSON 数组 + conditionType (ALL/ANY/NONE) 组成,
 * 评估时按 conditions 中的 field 从客户上下文取值, 按 operator (eq/ne/gt/lt/contains/between)
 * 进行条件匹配, 命中则打标 (tag_source=AUTO, is_auto=true)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCustomerTagService {

    /** 默认值类型 */
    private static final String DEFAULT_VALUE_TYPE = "BOOLEAN";

    /** 默认规则逻辑 */
    private static final String DEFAULT_RULE_LOGIC = "AND";

    /** 默认评估频率 */
    private static final String DEFAULT_EVALUATION_FREQUENCY = "DAILY";

    /** 默认标签来源 */
    private static final String DEFAULT_TAG_SOURCE = "MANUAL";

    /** 标签来源: 自动 */
    private static final String TAG_SOURCE_AUTO = "AUTO";

    /** 默认置信度 */
    private static final double DEFAULT_CONFIDENCE = 1.0;

    /** 规则状态: 活跃 */
    private static final String RULE_STATUS_ACTIVE = "ACTIVE";
    /** 规则状态: 停用 */
    private static final String RULE_STATUS_INACTIVE = "INACTIVE";

    /** 条件组合类型: 全部满足 */
    private static final String CONDITION_TYPE_ALL = "ALL";
    /** 条件组合类型: 任一满足 */
    private static final String CONDITION_TYPE_ANY = "ANY";
    /** 条件组合类型: 全部不满足 */
    private static final String CONDITION_TYPE_NONE = "NONE";

    /** 默认操作人 (请求头未透传时使用) */
    private static final String DEFAULT_OPERATOR = "scrm-system";

    /** 客户标签定义数据访问层 */
    private final ScrmCustomerTagRepository tagRepository;

    /** 标签规则数据访问层 */
    private final ScrmTagRuleRepository ruleRepository;

    /** 客户标签关联数据访问层 */
    private final ScrmTagCustomerRepository tagCustomerRepository;

    /** 客户数据访问层 (规则执行时遍历客户) */
    private final ScrmCustomerRepository customerRepository;

    /** JSON 解析器 (解析 conditions) */
    private final ObjectMapper objectMapper;

    /** JPA 实体管理器 (标签定义动态查询, ScrmCustomerTagRepository 未扩展 Specification) */
    private final EntityManager entityManager;

    // ============================================================
    // 标签管理 Tag CRUD
    // ============================================================

    /**
     * 创建客户标签定义。
     * <p>校验标签编码在唯一后写入账号 ID 持久化, 可空字段缺省时填默认值。</p>
     *
     * @param dto 标签参数
     * @return 创建后的标签
     * @throws ScrmException 参数非法 / 标签编码重复
     */
    @Transactional
    public ScrmCustomerTagEntity createTag(ScrmCustomerTagDto dto) throws ScrmException {
        validateTagDto(dto, false);
        if (tagRepository.findByTagCode(dto.getTagCode()).isPresent()) {
            throw ScrmException.conflict("标签编码已存在: " + dto.getTagCode());
        }
        ScrmCustomerTagEntity entity = new ScrmCustomerTagEntity();
        entity.setTagName(dto.getTagName());
        entity.setTagCode(dto.getTagCode());
        entity.setDescription(dto.getDescription());
        entity.setGroupId(dto.getGroupId());
        entity.setGroupName(dto.getGroupName());
        entity.setTagType(dto.getTagType());
        entity.setValueType(dto.getValueType() != null ? dto.getValueType() : DEFAULT_VALUE_TYPE);
        entity.setEnumOptions(dto.getEnumOptions());
        entity.setDefaultValue(dto.getDefaultValue());
        entity.setCategory(dto.getCategory());
        entity.setSubCategory(dto.getSubCategory());
        entity.setIsSystem(dto.getIsSystem());
        entity.setIsRequired(dto.getIsRequired());
        entity.setIsVisible(dto.getIsVisible());
        entity.setIsSearchable(dto.getIsSearchable());
        entity.setIsMultiple(dto.getIsMultiple());
        entity.setColor(dto.getColor());
        entity.setIcon(dto.getIcon());
        entity.setDisplayOrder(dto.getDisplayOrder());
        entity.setHelpText(dto.getHelpText());
        entity.setApplicableSegments(dto.getApplicableSegments());
        entity.setRuleExpression(dto.getRuleExpression());
        entity.setRuleConditions(dto.getRuleConditions());
        entity.setRuleLogic(dto.getRuleLogic() != null ? dto.getRuleLogic() : DEFAULT_RULE_LOGIC);
        entity.setAutoApply(dto.getAutoApply());
        entity.setEvaluationFrequency(dto.getEvaluationFrequency() != null
                ? dto.getEvaluationFrequency() : DEFAULT_EVALUATION_FREQUENCY);
        entity.setPriority(dto.getPriority());
        entity.setEnabled(dto.getEnabled());
        entity.setTags(dto.getTags());
        entity.setCreatedBy(dto.getCreatedBy());
        entity = tagRepository.save(entity);
        log.info("创建客户标签: id={}, tagName={}, tagCode={}, tagType={}",
                entity.getId(), entity.getTagName(), entity.getTagCode(), entity.getTagType());
        return entity;
    }

    /**
     * 更新客户标签定义（字段非空才覆盖）。
     *
     * @param id  标签 ID
     * @param dto 标签参数
     * @return 更新后的标签
     * @throws ScrmException 标签不存在 / 参数非法 / 标签编码重复
     */
    @Transactional
    public ScrmCustomerTagEntity updateTag(Long id, ScrmCustomerTagDto dto) throws ScrmException {
        ScrmCustomerTagEntity entity = findTagOrThrow(id);
        validateTagDto(dto, true);
        if (dto.getTagCode() != null && !dto.getTagCode().equals(entity.getTagCode())) {
            Optional<ScrmCustomerTagEntity> existing = tagRepository
                    .findByTagCode(dto.getTagCode());
            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                throw ScrmException.conflict("标签编码已存在: " + dto.getTagCode());
            }
            entity.setTagCode(dto.getTagCode());
        }
        if (dto.getTagName() != null) entity.setTagName(dto.getTagName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getGroupId() != null) entity.setGroupId(dto.getGroupId());
        if (dto.getGroupName() != null) entity.setGroupName(dto.getGroupName());
        if (dto.getTagType() != null) entity.setTagType(dto.getTagType());
        if (dto.getValueType() != null) entity.setValueType(dto.getValueType());
        if (dto.getEnumOptions() != null) entity.setEnumOptions(dto.getEnumOptions());
        if (dto.getDefaultValue() != null) entity.setDefaultValue(dto.getDefaultValue());
        if (dto.getCategory() != null) entity.setCategory(dto.getCategory());
        if (dto.getSubCategory() != null) entity.setSubCategory(dto.getSubCategory());
        if (dto.getIsSystem() != null) entity.setIsSystem(dto.getIsSystem());
        if (dto.getIsRequired() != null) entity.setIsRequired(dto.getIsRequired());
        if (dto.getIsVisible() != null) entity.setIsVisible(dto.getIsVisible());
        if (dto.getIsSearchable() != null) entity.setIsSearchable(dto.getIsSearchable());
        if (dto.getIsMultiple() != null) entity.setIsMultiple(dto.getIsMultiple());
        if (dto.getColor() != null) entity.setColor(dto.getColor());
        if (dto.getIcon() != null) entity.setIcon(dto.getIcon());
        if (dto.getDisplayOrder() != null) entity.setDisplayOrder(dto.getDisplayOrder());
        if (dto.getHelpText() != null) entity.setHelpText(dto.getHelpText());
        if (dto.getApplicableSegments() != null) entity.setApplicableSegments(dto.getApplicableSegments());
        if (dto.getRuleExpression() != null) entity.setRuleExpression(dto.getRuleExpression());
        if (dto.getRuleConditions() != null) entity.setRuleConditions(dto.getRuleConditions());
        if (dto.getRuleLogic() != null) entity.setRuleLogic(dto.getRuleLogic());
        if (dto.getAutoApply() != null) entity.setAutoApply(dto.getAutoApply());
        if (dto.getEvaluationFrequency() != null) entity.setEvaluationFrequency(dto.getEvaluationFrequency());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = tagRepository.save(entity);
        log.info("更新客户标签: id={}, tagName={}", entity.getId(), entity.getTagName());
        return entity;
    }

    /**
     * 删除客户标签定义。
     * <p>删除前检查是否有规则引用, 若有则阻止删除。删除时级联清理客户-标签关联。</p>
     *
     * @param id 标签 ID
     * @throws ScrmException 标签不存在 / 仍有规则引用
     */
    @Transactional
    public void deleteTag(Long id) throws ScrmException {
        ScrmCustomerTagEntity entity = findTagOrThrow(id);
        long ruleCount = ruleRepository.findByTagId(id).size();
        if (ruleCount > 0) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    String.format("无法删除标签: 仍有 %d 条规则引用该标签, 请先调整规则", ruleCount));
        }
        tagCustomerRepository.deleteByTagId(id);
        tagRepository.delete(entity);
        log.info("删除客户标签: id={}, tagName={}", id, entity.getTagName());
    }

    /**
     * 查询标签详情。
     *
     * @param id 标签 ID
     * @return 标签实体
     * @throws ScrmException 标签不存在
     */
    @Transactional(readOnly = true)
    public ScrmCustomerTagEntity getTag(Long id) throws ScrmException {
        return findTagOrThrow(id);
    }

    /**
     * 按标签编码查询标签。
     *
     * @param code 标签编码
     * @return 标签实体
     * @throws ScrmException 标签不存在
     */
    @Transactional(readOnly = true)
    public ScrmCustomerTagEntity getTagByCode(String code) throws ScrmException {
        ScrmCustomerTagEntity entity = tagRepository
                .findByTagCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "标签不存在: tagCode=" + code));
        return entity;
    }

    /**
     * 分页查询标签定义, 支持按分组 / 类型 / 分类 / 启用状态 / 关键字过滤。
     *
     * @param groupId  分组 ID 过滤（可空）
     * @param tagType  标签类型过滤（可空）
     * @param category 标签分类过滤（可空）
     * @param enabled  启用状态过滤（可空）
     * @param keyword  关键字过滤（按 tagName / tagCode / description 模糊匹配, 可空）
     * @param pageable 分页参数
     * @return 标签分页结果 (按 displayOrder ASC, createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerTagEntity> listTags(Long groupId, String tagType, String category,
                                                  Boolean enabled, String keyword, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // 计数查询
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<ScrmCustomerTagEntity> countRoot = countQuery.from(ScrmCustomerTagEntity.class);
        List<Predicate> countPredicates = buildTagPredicates(cb, countRoot,
                groupId, tagType, category, enabled, keyword);
        countQuery.select(cb.count(countRoot)).where(countPredicates.toArray(new Predicate[0]));
        Long total = entityManager.createQuery(countQuery).getSingleResult();

        // 数据查询
        CriteriaQuery<ScrmCustomerTagEntity> dataQuery = cb.createQuery(ScrmCustomerTagEntity.class);
        Root<ScrmCustomerTagEntity> dataRoot = dataQuery.from(ScrmCustomerTagEntity.class);
        List<Predicate> dataPredicates = buildTagPredicates(cb, dataRoot,
                groupId, tagType, category, enabled, keyword);
        dataQuery.select(dataRoot).where(dataPredicates.toArray(new Predicate[0]));
        dataQuery.orderBy(cb.asc(dataRoot.get("displayOrder")), cb.desc(dataRoot.get("createTime")));

        List<ScrmCustomerTagEntity> content = entityManager.createQuery(dataQuery)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    /**
     * 启用标签。
     *
     * @param id 标签 ID
     * @return 更新后的标签
     * @throws ScrmException 标签不存在
     */
    @Transactional
    public ScrmCustomerTagEntity enableTag(Long id) throws ScrmException {
        ScrmCustomerTagEntity entity = findTagOrThrow(id);
        entity.setEnabled(true);
        entity = tagRepository.save(entity);
        log.info("启用客户标签: id={}, tagName={}", id, entity.getTagName());
        return entity;
    }

    /**
     * 禁用标签。
     *
     * @param id 标签 ID
     * @return 更新后的标签
     * @throws ScrmException 标签不存在
     */
    @Transactional
    public ScrmCustomerTagEntity disableTag(Long id) throws ScrmException {
        ScrmCustomerTagEntity entity = findTagOrThrow(id);
        entity.setEnabled(false);
        entity = tagRepository.save(entity);
        log.info("禁用客户标签: id={}, tagName={}", id, entity.getTagName());
        return entity;
    }

    // ============================================================
    // 客户打标 Customer Tag Assignment
    // ============================================================

    /**
     * 为客户打标 (单条)。
     * <p>校验标签存在且启用。同一客户同一标签默认覆盖更新 tagValue 与打标人信息,
     * 重复打标不增加新记录。AUTO 来源打标会设置 is_auto=true。</p>
     *
     * @param dto 打标参数 (customerId + tagId + tagValue + 来源 + 打标人)
     * @return 创建或更新后的客户标签关联
     * @throws ScrmException 标签不存在或已禁用
     */
    @Transactional
    public ScrmTagCustomerEntity assignTag(ScrmTagCustomerDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("打标参数不能为空");
        }
        if (dto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (dto.getTagId() == null) {
            throw ScrmException.badRequest("标签 ID 不能为空");
        }
        return doAssignTag(dto.getCustomerId(), dto.getTagId(), dto.getTagValue(),
                dto.getTagSource(), dto.getAssignedBy(), dto.getAssignedByName(),
                dto.getExpiresAt(), dto.getNote());
    }

    /**
     * 批量打标。
     * <p>对入参列表逐一执行打标, 单条失败跳过并记录告警, 不阻断其他客户。</p>
     *
     * @param dtos 打标参数列表
     * @return 成功打标数
     * @throws ScrmException 列表为空
     */
    @Transactional
    public int batchAssignTags(List<ScrmTagCustomerDto> dtos) throws ScrmException {
        if (dtos == null || dtos.isEmpty()) {
            throw ScrmException.badRequest("批量打标参数列表不能为空");
        }
        int success = 0;
        for (ScrmTagCustomerDto dto : dtos) {
            try {
                doAssignTag(dto.getCustomerId(), dto.getTagId(), dto.getTagValue(),
                        dto.getTagSource(), dto.getAssignedBy(), dto.getAssignedByName(),
                        dto.getExpiresAt(), dto.getNote());
                success++;
            } catch (ScrmException e) {
                log.warn("批量打标失败, 跳过: customerId={}, tagId={}, code={}, msg={}",
                        dto.getCustomerId(), dto.getTagId(), e.getCode(), e.getMessage());
            }
        }
        log.info("批量打标完成: requested={}, success={}", dtos.size(), success);
        return success;
    }

    /**
     * 移除客户标签。
     * <p>AUTO 来源标签 (is_auto=true) 不允许手动去标, 需先禁用关联规则。</p>
     *
     * @param customerId 客户 ID
     * @param tagId      标签 ID
     * @throws ScrmException 关联不存在 / 自动标签不可手动去标
     */
    @Transactional
    public void removeTag(Long customerId, Long tagId) throws ScrmException {
        ScrmTagCustomerEntity entity = tagCustomerRepository
                .findByCustomerIdAndTagId(customerId, tagId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户标签关联不存在: customerId=" + customerId + ", tagId=" + tagId));
        if (Boolean.TRUE.equals(entity.getIsAuto())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "自动打标标签不可手动去标, 请先禁用关联规则: tagId=" + tagId);
        }
        tagCustomerRepository.delete(entity);
        updateTagCustomerCount(tagId);
        log.info("移除客户标签: customerId={}, tagId={}", customerId, tagId);
    }

    /**
     * 查询客户的所有标签关联。
     *
     * @param customerId 客户 ID
     * @return 标签关联列表
     */
    @Transactional(readOnly = true)
    public List<ScrmTagCustomerEntity> getCustomerTags(Long customerId) {
        return tagCustomerRepository.findByCustomerId(customerId);
    }

    /**
     * 分页查询标签下的客户 (按标签查客户)。
     *
     * @param tagId    标签 ID
     * @param pageable 分页参数
     * @return 客户关联分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmTagCustomerEntity> getTagCustomers(Long tagId, Pageable pageable) {
        return tagCustomerRepository.findByTagId(tagId, pageable);
    }

    // ============================================================
    // 规则管理 Rule CRUD
    // ============================================================

    /**
     * 创建标签规则。
     * <p>校验关联标签存在且启用, conditions 为合法 JSON 后写入账号 ID 持久化,
     * status 缺省时填 ACTIVE。</p>
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法 / 关联标签不存在或已禁用 / conditions 非合法 JSON
     */
    @Transactional
    public ScrmTagRuleEntity createRule(ScrmTagRuleDto dto) throws ScrmException {
        validateRuleDto(dto, false);
        ScrmCustomerTagEntity tag = findTagOrThrow(dto.getTagId());
        if (Boolean.FALSE.equals(tag.getEnabled())) {
            throw ScrmException.badRequest("标签已禁用, 不允许创建规则: tagId=" + dto.getTagId());
        }
        ScrmTagRuleEntity entity = new ScrmTagRuleEntity();
        entity.setRuleName(dto.getRuleName());
        entity.setTagId(dto.getTagId());
        entity.setDescription(dto.getDescription());
        entity.setConditionType(dto.getConditionType());
        entity.setConditions(dto.getConditions());
        entity.setTargetFields(dto.getTargetFields());
        entity.setExecutionFrequency(dto.getExecutionFrequency());
        entity.setStatus(dto.getStatus() != null && !dto.getStatus().isBlank()
                ? dto.getStatus() : RULE_STATUS_ACTIVE);
        entity.setMatchedCount(0);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = ruleRepository.save(entity);
        log.info("创建标签规则: id={}, ruleName={}, tagId={}",
                entity.getId(), entity.getRuleName(), entity.getTagId());
        return entity;
    }

    /**
     * 更新标签规则（字段非空才覆盖）。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法 / 关联标签不存在或已禁用
     */
    @Transactional
    public ScrmTagRuleEntity updateRule(Long id, ScrmTagRuleDto dto) throws ScrmException {
        ScrmTagRuleEntity entity = findRuleOrThrow(id);
        validateRuleDto(dto, true);
        if (dto.getTagId() != null && !Objects.equals(dto.getTagId(), entity.getTagId())) {
            ScrmCustomerTagEntity newTag = findTagOrThrow(dto.getTagId());
            if (Boolean.FALSE.equals(newTag.getEnabled())) {
                throw ScrmException.badRequest("标签已禁用, 不允许指向: tagId=" + dto.getTagId());
            }
            entity.setTagId(dto.getTagId());
        }
        if (dto.getRuleName() != null) entity.setRuleName(dto.getRuleName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getConditionType() != null) entity.setConditionType(dto.getConditionType());
        if (dto.getConditions() != null) entity.setConditions(dto.getConditions());
        if (dto.getTargetFields() != null) entity.setTargetFields(dto.getTargetFields());
        if (dto.getExecutionFrequency() != null) entity.setExecutionFrequency(dto.getExecutionFrequency());
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) entity.setStatus(dto.getStatus());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = ruleRepository.save(entity);
        log.info("更新标签规则: id={}, ruleName={}", entity.getId(), entity.getRuleName());
        return entity;
    }

    /**
     * 删除标签规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public void deleteRule(Long id) throws ScrmException {
        ScrmTagRuleEntity entity = findRuleOrThrow(id);
        ruleRepository.delete(entity);
        log.info("删除标签规则: id={}, ruleName={}", id, entity.getRuleName());
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public ScrmTagRuleEntity getRule(Long id) throws ScrmException {
        return findRuleOrThrow(id);
    }

    /**
     * 分页查询规则, 支持按标签 ID / 状态 / 关键字过滤。
     *
     * @param tagId    标签 ID 过滤（可空）
     * @param status   状态过滤（可空）
     * @param keyword  关键字过滤（按 ruleName / description 模糊匹配, 可空）
     * @param pageable 分页参数
     * @return 规则分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmTagRuleEntity> listRules(Long tagId, String status, String keyword, Pageable pageable) {
        Specification<ScrmTagRuleEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (tagId != null) {
                predicates.add(cb.equal(root.get("tagId"), tagId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("ruleName")), kw),
                        cb.like(cb.lower(root.get("description")), kw)
                ));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return ruleRepository.findAll(spec, pageable);
    }

    /**
     * 启用规则 (status=ACTIVE)。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmTagRuleEntity enableRule(Long id) throws ScrmException {
        ScrmTagRuleEntity entity = findRuleOrThrow(id);
        entity.setStatus(RULE_STATUS_ACTIVE);
        entity = ruleRepository.save(entity);
        log.info("启用标签规则: id={}, ruleName={}", id, entity.getRuleName());
        return entity;
    }

    /**
     * 禁用规则 (status=INACTIVE)。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmTagRuleEntity disableRule(Long id) throws ScrmException {
        ScrmTagRuleEntity entity = findRuleOrThrow(id);
        entity.setStatus(RULE_STATUS_INACTIVE);
        entity = ruleRepository.save(entity);
        log.info("禁用标签规则: id={}, ruleName={}", id, entity.getRuleName());
        return entity;
    }

    // ============================================================
    // 规则评估 Rule Evaluation
    // ============================================================

    /**
     * 测试规则匹配 (不实际打标)。
     * <p>对入参客户 ID 列表逐个评估规则条件, 返回命中的客户 ID 列表与匹配详情。
     * 客户上下文由客户实体构建。</p>
     *
     * @param testDto 测试参数 (ruleId + customerIds)
     * @return 测试结果: {ruleId, ruleName, matchedCustomerIds, matchedCount, details}
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> testRule(ScrmTagRuleTestDto testDto) throws ScrmException {
        ScrmTagRuleEntity rule = findRuleOrThrow(testDto.getRuleId());
        List<Long> matchedIds = new ArrayList<>();
        List<Map<String, Object>> details = new ArrayList<>();
        for (Long customerId : testDto.getCustomerIds()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("customerId", customerId);
            try {
                ScrmCustomerEntity customer = customerRepository.findById(customerId).orElse(null);
                if (customer == null) {
                    detail.put("matched", false);
                    detail.put("error", "客户不存在");
                    details.add(detail);
                    continue;
                }
                Map<String, Object> context = buildCustomerContext(customer);
                boolean isMatched = evaluateConditions(rule, context);
                detail.put("matched", isMatched);
                if (isMatched) {
                    matchedIds.add(customerId);
                }
            } catch (Exception e) {
                detail.put("matched", false);
                detail.put("error", e.getMessage());
            }
            details.add(detail);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ruleId", rule.getId());
        result.put("ruleName", rule.getRuleName());
        result.put("matchedCustomerIds", matchedIds);
        result.put("matchedCount", matchedIds.size());
        result.put("details", details);
        return result;
    }

    /**
     * 手动评估规则 (事件驱动)。
     * <p>按入参的客户上下文评估当前账号全部 ACTIVE 规则, 命中则自动打标
     * (tag_source=AUTO, is_auto=true)。customerContext 由调用方构建, Service 层不主动
     * 查询客户实体。</p>
     *
     * @param dto 评估参数 (customerId + triggerEvent + customerContext)
     * @return 评估结果: {customerId, triggerEvent, matchedCount, matchedRules}
     * @throws ScrmException 客户不存在
     */
    @Transactional
    public Map<String, Object> evaluateRule(ScrmTagRuleEvaluateDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("评估参数不能为空");
        }
        if (dto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        findCustomerOrThrow(dto.getCustomerId());
        List<ScrmTagRuleEntity> rules = ruleRepository.findByStatus(RULE_STATUS_ACTIVE);
        List<Map<String, Object>> matchedRules = new ArrayList<>();
        for (ScrmTagRuleEntity rule : rules) {
            try {
                if (!evaluateConditions(rule, dto.getCustomerContext())) {
                    continue;
                }
                ScrmCustomerTagEntity tag = tagRepository.findById(rule.getTagId()).orElse(null);

                doAssignTag(dto.getCustomerId(), rule.getTagId(), null, TAG_SOURCE_AUTO,
                        DEFAULT_OPERATOR, DEFAULT_OPERATOR, null, "自动评估: " + rule.getRuleName());
                Map<String, Object> matched = new LinkedHashMap<>();
                matched.put("ruleId", rule.getId());
                matched.put("ruleName", rule.getRuleName());
                matched.put("tagId", rule.getTagId());
                matchedRules.add(matched);
            } catch (Exception e) {
                log.warn("规则评估异常, 跳过: ruleId={}, err={}", rule.getId(), e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", dto.getCustomerId());
        result.put("triggerEvent", dto.getTriggerEvent());
        result.put("matchedCount", matchedRules.size());
        result.put("matchedRules", matchedRules);
        log.info("手动评估规则完成: customerId={}, triggerEvent={}, matchedCount={}",
                dto.getCustomerId(), dto.getTriggerEvent(), matchedRules.size());
        return result;
    }

    /**
     * 执行单条自动打标规则: 遍历当前账号全部客户 → 评估条件 → 命中则打标。
     * <p>执行完成后增量更新规则的 matchedCount 与 lastExecutedAt, 并刷新关联标签客户数。</p>
     *
     * @param id 规则 ID
     * @return 命中的客户数
     * @throws ScrmException 规则不存在 / 关联标签不存在
     */
    @Transactional
    public int executeRule(Long id) throws ScrmException {
        ScrmTagRuleEntity rule = findRuleOrThrow(id);
        ScrmCustomerTagEntity tag = findTagOrThrow(rule.getTagId());
        List<ScrmCustomerEntity> customers = customerRepository
                .findAll((root, query, cb) -> cb.and());
        int matched = 0;
        for (ScrmCustomerEntity customer : customers) {
            try {
                Map<String, Object> context = buildCustomerContext(customer);
                if (evaluateConditions(rule, context)) {
                    doAssignTag(customer.getId(), tag.getId(), null, TAG_SOURCE_AUTO,
                            DEFAULT_OPERATOR, DEFAULT_OPERATOR, null, "规则执行: " + rule.getRuleName());
                    matched++;
                }
            } catch (Exception e) {
                log.warn("规则执行: 客户评估异常, 跳过: customerId={}, err={}",
                        customer.getId(), e.getMessage());
            }
        }
        ruleRepository.updateExecutionStats(id, LocalDateTime.now(), matched);
        updateTagCustomerCount(tag.getId());
        log.info("执行标签规则: id={}, ruleName={}, 命中客户 {} / 总客户 {}",
                id, rule.getRuleName(), matched, customers.size());
        return matched;
    }

    /**
     * 批量执行所有活跃规则 (运行所有规则)。
     *
     * @return 各规则执行结果: [{ruleId, ruleName, matchedCount}]
     */
    @Transactional
    public List<Map<String, Object>> runAllRules() {
        List<ScrmTagRuleEntity> rules = ruleRepository.findByStatus(RULE_STATUS_ACTIVE);
        List<Map<String, Object>> results = new ArrayList<>();
        for (ScrmTagRuleEntity rule : rules) {
            try {
                int matched = executeRule(rule.getId());
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ruleId", rule.getId());
                result.put("ruleName", rule.getRuleName());
                result.put("matchedCount", matched);
                results.add(result);
            } catch (Exception e) {
                log.warn("批量执行规则异常, 跳过: ruleId={}, err={}", rule.getId(), e.getMessage());
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ruleId", rule.getId());
                result.put("ruleName", rule.getRuleName());
                result.put("matchedCount", 0);
                result.put("error", e.getMessage());
                results.add(result);
            }
        }
        log.info("批量执行标签规则完成: 总规则 {} 条", rules.size());
        return results;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 实际写入客户-标签关联 (内部调用, 覆盖更新)。
     *
     * @param customerId     客户 ID
     * @param tagId          标签 ID
     * @param tagValue       标签值 (可空)
     * @param source         标签来源 (可空, 默认 MANUAL)
     * @param assignedBy     打标人 (可空)
     * @param assignedByName 打标人名称 (可空)
     * @param expiresAt      过期时间 (可空)
     * @param note           备注 (可空)
     * @return 创建或更新后的客户标签关联
     * @throws ScrmException 标签不存在或已禁用
     */
    private ScrmTagCustomerEntity doAssignTag(Long customerId, Long tagId, String tagValue,
                                                String source, String assignedBy, String assignedByName,
                                                java.time.LocalDateTime expiresAt, String note)
            throws ScrmException {
        ScrmCustomerTagEntity tag = findTagOrThrow(tagId);
        if (Boolean.FALSE.equals(tag.getEnabled())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "标签已禁用, 无法打标: " + tag.getTagCode());
        }
        String tagSource = (source == null || source.isBlank()) ? DEFAULT_TAG_SOURCE : source;
        boolean isAuto = TAG_SOURCE_AUTO.equals(tagSource);
        ScrmTagCustomerEntity entity = tagCustomerRepository
                .findByCustomerIdAndTagId(customerId, tagId)
                .orElseGet(ScrmTagCustomerEntity::new);
        boolean isNew = entity.getId() == null;
        if (isNew) {
            entity.setCustomerId(customerId);
            entity.setTagId(tagId);
        }
        entity.setTagValue(tagValue);
        entity.setTagSource(tagSource);
        entity.setAssignedBy(assignedBy);
        entity.setAssignedByName(assignedByName);
        entity.setAssignedAt(LocalDateTime.now());
        entity.setExpiresAt(expiresAt);
        entity.setNote(note);
        entity.setConfidence(DEFAULT_CONFIDENCE);
        entity.setIsAuto(isAuto);
        entity = tagCustomerRepository.save(entity);
        if (isNew) {
            updateTagCustomerCount(tagId);
        }
        log.info("为客户打标: customerId={}, tagId={}, source={}, isNew={}",
                customerId, tagId, tagSource, isNew);
        return entity;
    }

    /**
     * 刷新标签客户数 (customer_count)。
     *
     * @param tagId 标签 ID
     * @throws ScrmException 标签不存在
     */
    private void updateTagCustomerCount(Long tagId) throws ScrmException {
        ScrmCustomerTagEntity tag = tagRepository.findById(tagId).orElse(null);
        if (tag == null) {
            return;
        }
        long count = tagCustomerRepository.countByTagId(tagId);
        tag.setCustomerCount((int) Math.min(count, Integer.MAX_VALUE));
        tagRepository.save(tag);
    }

    /**
     * 构建标签查询谓词列表。
     *
     * @param cb       CriteriaBuilder
     * @param root     查询根
     * @param groupId  分组 ID 过滤（可空）
     * @param tagType  标签类型过滤（可空）
     * @param category 标签分类过滤（可空）
     * @param enabled  启用状态过滤（可空）
     * @param keyword  关键字过滤（可空）
     * @return 谓词列表
     */
    private List<Predicate> buildTagPredicates(CriteriaBuilder cb, Root<ScrmCustomerTagEntity> root, Long groupId, String tagType,
                                                 String category, Boolean enabled, String keyword) {
        List<Predicate> predicates = new ArrayList<>();
        if (groupId != null) {
            predicates.add(cb.equal(root.get("groupId"), groupId));
        }
        if (tagType != null && !tagType.isBlank()) {
            predicates.add(cb.equal(root.get("tagType"), tagType));
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
                    cb.like(cb.lower(root.get("tagName")), kw),
                    cb.like(cb.lower(root.get("tagCode")), kw),
                    cb.like(cb.lower(root.get("description")), kw)
            ));
        }
        return predicates;
    }

    /**
     * 评估规则条件是否匹配。
     * <p>解析 conditions JSON, 按 conditionType (ALL/ANY/NONE) 对 context 中的字段评估。
     * 条件为空数组视为全部匹配。context 为空时按全部不匹配处理。</p>
     *
     * @param rule    规则实体
     * @param context 客户属性快照 (可空)
     * @return 条件是否匹配
     */
    private boolean evaluateConditions(ScrmTagRuleEntity rule, Map<String, Object> context) {
        List<Map<String, Object>> conditions = parseConditions(rule.getConditions());
        if (conditions.isEmpty()) {
            return true;
        }
        if (context == null) {
            return false;
        }
        boolean all = CONDITION_TYPE_ALL.equals(rule.getConditionType());
        boolean any = CONDITION_TYPE_ANY.equals(rule.getConditionType());
        boolean none = CONDITION_TYPE_NONE.equals(rule.getConditionType());
        for (Map<String, Object> condition : conditions) {
            String field = (String) condition.get("field");
            String operator = (String) condition.get("operator");
            Object value = condition.get("value");
            boolean matched = evaluateSingleCondition(context.get(field), operator, value);
            if (all && !matched) {
                return false;
            }
            if (any && matched) {
                return true;
            }
        }
        if (none) {
            return conditions.stream().noneMatch(c -> evaluateSingleCondition(
                    context.get((String) c.get("field")),
                    (String) c.get("operator"),
                    c.get("value")));
        }
        return all;
    }

    /**
     * 评估单个条件。
     * <p>支持 eq/ne/gt/lt/contains/between 操作符, 自动处理类型转换。</p>
     *
     * @param fieldValue    客户属性值
     * @param operator      操作符
     * @param conditionValue 条件值
     * @return 条件是否满足
     */
    private boolean evaluateSingleCondition(Object fieldValue, String operator, Object conditionValue) {
        if (fieldValue == null) {
            return false;
        }
        if (operator == null) {
            return false;
        }
        switch (operator) {
            case "eq":
                return toStringValue(fieldValue).equals(toStringValue(conditionValue));
            case "ne":
                return !toStringValue(fieldValue).equals(toStringValue(conditionValue));
            case "gt":
                return toDouble(fieldValue) > toDouble(conditionValue);
            case "lt":
                return toDouble(fieldValue) < toDouble(conditionValue);
            case "contains":
                return toStringValue(fieldValue).contains(toStringValue(conditionValue));
            case "between":
                return isBetween(fieldValue, conditionValue);
            default:
                return false;
        }
    }

    /**
     * 判断字段值是否在区间内 (between 操作符)。
     * <p>条件值应为 [min, max] 二元数组, 区间两端均包含。</p>
     *
     * @param fieldValue    字段值
     * @param conditionValue 条件值 ([min, max])
     * @return 是否在区间内
     */
    private boolean isBetween(Object fieldValue, Object conditionValue) {
        if (conditionValue instanceof Collection<?> col && col.size() == 2) {
            Object[] arr = col.toArray();
            double min = toDouble(arr[0]);
            double max = toDouble(arr[1]);
            double val = toDouble(fieldValue);
            return val >= min && val <= max;
        }
        return false;
    }

    /**
     * 解析条件 JSON 为 List。
     *
     * @param conditionsJson 条件 JSON 字符串
     * @return 条件列表, 解析失败返回空列表
     */
    private List<Map<String, Object>> parseConditions(String conditionsJson) {
        try {
            return objectMapper.readValue(conditionsJson, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("条件 JSON 解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 构建客户上下文 (用于规则条件评估)。
     * <p>字段映射: customer_name / nickname -> nickname, lifecycle -> lifecycle,
     * platform_type -> platformType, order_count / total_amount 模拟为 0,
     * registration_days 由 createTime 计算, last_interaction_days 由 lastInteractionAt 计算。</p>
     *
     * @param customer 客户实体
     * @return 客户上下文 Map
     */
    private Map<String, Object> buildCustomerContext(ScrmCustomerEntity customer) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("customer_name", customer.getNickname() != null ? customer.getNickname() : "");
        context.put("nickname", customer.getNickname() != null ? customer.getNickname() : "");
        context.put("lifecycle", customer.getLifecycle() != null ? customer.getLifecycle() : "");
        context.put("platform_type", customer.getPlatformType() != null ? customer.getPlatformType() : "");
        context.put("order_count", 0);
        context.put("total_amount", 0);
        if (customer.getCreateTime() != null) {
            long days = ChronoUnit.DAYS.between(customer.getCreateTime(), LocalDateTime.now());
            context.put("registration_days", days);
        } else {
            context.put("registration_days", 0);
        }
        if (customer.getLastInteractionAt() != null) {
            long days = ChronoUnit.DAYS.between(customer.getLastInteractionAt(), LocalDateTime.now());
            context.put("last_interaction_days", days);
            context.put("last_interaction", days);
        } else {
            context.put("last_interaction_days", 0);
            context.put("last_interaction", 0);
        }
        return context;
    }

    /**
     * 将对象转换为 double 数值。
     *
     * @param obj 对象
     * @return double 值, 不可转换时返回 0
     */
    private double toDouble(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 将对象转换为字符串。
     *
     * @param obj 对象
     * @return 字符串, null 返回空字符串
     */
    private String toStringValue(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    /**
     * 校验标签参数。
     * <p>创建场景 (partial=false): tagName / tagCode / tagType 必填。更新场景允许字段为空。</p>
     *
     * @param dto     标签参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateTagDto(ScrmCustomerTagDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("标签参数不能为空");
        }
        if (dto.getTagName() != null) {
            if (dto.getTagName().isBlank()) {
                throw ScrmException.badRequest("标签名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("标签名称不能为空");
        }
        if (dto.getTagCode() != null) {
            if (dto.getTagCode().isBlank()) {
                throw ScrmException.badRequest("标签编码不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("标签编码不能为空");
        }
        if (dto.getTagType() != null) {
            if (dto.getTagType().isBlank()) {
                throw ScrmException.badRequest("标签类型不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("标签类型不能为空");
        }
        if (dto.getRuleConditions() != null) {
            try {
                objectMapper.readValue(dto.getRuleConditions(),
                        new TypeReference<List<Map<String, Object>>>() {});
            } catch (ScrmException e) {
                throw e;
            } catch (Exception e) {
                throw ScrmException.badRequest("规则条件 JSON 解析失败: " + e.getMessage());
            }
        }
    }

    /**
     * 校验规则参数。
     * <p>创建场景 (partial=false): ruleName / tagId / conditionType / conditions / executionFrequency
     * 必填。更新场景允许字段为空, conditions 非空时校验 JSON 可解析。</p>
     *
     * @param dto     规则参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateRuleDto(ScrmTagRuleDto dto, boolean partial) throws ScrmException {
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
        if (dto.getTagId() == null && !partial) {
            throw ScrmException.badRequest("标签 ID 不能为空");
        }
        if (dto.getConditionType() != null) {
            if (!CONDITION_TYPE_ALL.equals(dto.getConditionType()) && !CONDITION_TYPE_ANY.equals(dto.getConditionType()) && !CONDITION_TYPE_NONE.equals(dto.getConditionType())) {
                throw ScrmException.badRequest(
                        "条件组合类型非法: " + dto.getConditionType() + ", 仅支持 ALL/ANY/NONE");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("条件组合类型不能为空");
        }
        if (dto.getExecutionFrequency() != null) {
            if (dto.getExecutionFrequency().isBlank()) {
                throw ScrmException.badRequest("执行频率不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("执行频率不能为空");
        }
        if (dto.getConditions() != null) {
            if (dto.getConditions().isBlank()) {
                throw ScrmException.badRequest("条件 JSON 不能为空");
            }
            try {
                objectMapper.readValue(dto.getConditions(),
                        new TypeReference<List<Map<String, Object>>>() {});
            } catch (ScrmException e) {
                throw e;
            } catch (Exception e) {
                throw ScrmException.badRequest("条件 JSON 解析失败: " + e.getMessage());
            }
        } else if (!partial) {
            throw ScrmException.badRequest("条件 JSON 不能为空");
        }
    }

    /**
     * 按主键查询标签, 不存在抛异常, 并校验账号归属。
     *
     * @param id 标签 ID
     * @return 标签实体
     * @throws ScrmException 标签不存在
     */
    private ScrmCustomerTagEntity findTagOrThrow(Long id) throws ScrmException {
        ScrmCustomerTagEntity entity = tagRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户标签不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询规则, 不存在抛异常, 并校验账号归属。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    private ScrmTagRuleEntity findRuleOrThrow(Long id) throws ScrmException {
        ScrmTagRuleEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "标签规则不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询客户, 不存在抛异常, 并校验账号归属。
     *
     * @param id 客户 ID
     * @return 客户实体
     * @throws ScrmException 客户不存在
     */
    private ScrmCustomerEntity findCustomerOrThrow(Long id) throws ScrmException {
        ScrmCustomerEntity customer = customerRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户不存在: id=" + id));
        return customer;
    }

}
