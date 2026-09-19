/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTagSystemTagService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmBatchTagDto;
import org.hiylo.scrm.dto.ScrmTagDto;
import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.hiylo.scrm.entity.ScrmTagEntity;
import org.hiylo.scrm.entity.ScrmTagRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmTagCustomerRepository;
import org.hiylo.scrm.repository.ScrmTagRepository;
import org.hiylo.scrm.repository.ScrmTagRuleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SCRM 标签管理与客户标签管理服务。
 * <p>
 * 承载标签定义子域 (标签创建 / 更新 / 删除 / 启停 / 合并 / 客户数刷新) 与客户标签关联子域
 * (打标 / 批量打标去标 / 去标 / 查询 / 校验)。标签存在性校验 ({@link #findTagOrThrow})
 * 提供给规则管理子域复用, 分组存在性校验委托给 {@link ScrmTagSystemGroupService}。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmTagSystemTagService {

    /** 默认是否系统内置 */
    private static final boolean DEFAULT_IS_SYSTEM = false;

    /** 默认排序 */
    private static final int DEFAULT_SORT_ORDER = 0;

    /** 默认启用状态 */
    private static final boolean DEFAULT_ENABLED = true;

    /** 默认标签来源 */
    private static final String DEFAULT_TAG_SOURCE = "MANUAL";

    /** 默认置信度 */
    private static final double DEFAULT_CONFIDENCE = 1.0;

    /** 规则状态: 活跃 */
    private static final String RULE_STATUS_ACTIVE = "ACTIVE";

    /** 标签来源: 自动 */
    private static final String TAG_SOURCE_AUTO = "AUTO";

    /** 动作: 添加 */
    private static final String ACTION_ADD = "ADD";
    /** 动作: 移除 */
    private static final String ACTION_REMOVE = "REMOVE";

    /** 合法的标签类型 */
    private static final List<String> VALID_TAG_TYPES = List.of("MANUAL", "AUTO", "COMPUTED");

    /** 合法的值类型 */
    private static final List<String> VALID_VALUE_TYPES = List.of(
            "BOOLEAN", "TEXT", "NUMBER", "DATE", "ENUM");

    /** 标签定义数据访问层 */
    private final ScrmTagRepository tagRepository;

    /** 客户标签关联数据访问层 */
    private final ScrmTagCustomerRepository tagCustomerRepository;

    /** 标签规则数据访问层 */
    private final ScrmTagRuleRepository ruleRepository;

    /** 标签分组管理服务 (分组存在性校验) */
    private final ScrmTagSystemGroupService groupService;

    // ============================================================
    // 标签管理 Tag
    // ============================================================

    /**
     * 创建标签。
     * <p>校验标签编码在唯一、所属分组存在后写入归属账号 ID 持久化。AUTO 类型标签需要
     * 后续创建关联规则后由 Service 反向写入 ruleId。</p>
     *
     * @param dto 标签参数
     * @return 创建后的标签
     * @throws ScrmException 参数非法 / 标签编码重复 / 所属分组不存在
     */
    @Transactional
    public ScrmTagEntity createTag(ScrmTagDto dto) throws ScrmException {
        validateTagDto(dto, false);
        if (tagRepository.findByTagCode(dto.getTagCode()).isPresent()) {
            throw ScrmException.conflict("标签编码已存在: " + dto.getTagCode());
        }
        if (dto.getGroupId() != null) {
            // 校验分组存在且归属当前账号
            groupService.findGroupOrThrow(dto.getGroupId());
        }
        ScrmTagEntity entity = new ScrmTagEntity();
        entity.setGroupId(dto.getGroupId());
        entity.setTagName(dto.getTagName());
        entity.setTagCode(dto.getTagCode());
        entity.setTagType(dto.getTagType());
        entity.setValueType(dto.getValueType());
        entity.setTagValue(dto.getTagValue());
        entity.setDescription(dto.getDescription());
        entity.setColor(dto.getColor());
        entity.setIcon(dto.getIcon());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : DEFAULT_SORT_ORDER);
        entity.setCustomerCount(0);
        entity.setRuleId(null);
        entity.setIsSystem(dto.getIsSystem() != null ? dto.getIsSystem() : DEFAULT_IS_SYSTEM);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : DEFAULT_ENABLED);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = tagRepository.save(entity);
        log.info("创建标签: id={}, tagName={}, tagCode={}, tagType={}",
                entity.getId(), entity.getTagName(), entity.getTagCode(), entity.getTagType());
        return entity;
    }

    /**
     * 更新标签（字段非空才覆盖, tagCode / tagType 不允许修改）。
     *
     * @param id  标签 ID
     * @param dto 标签参数
     * @return 更新后的标签
     * @throws ScrmException 标签不存在 / 参数非法 / 系统内置标签不可修改编码
     */
    @Transactional
    public ScrmTagEntity updateTag(Long id, ScrmTagDto dto) throws ScrmException {
        ScrmTagEntity entity = findTagOrThrow(id);
        validateTagDto(dto, true);
        if (dto.getGroupId() != null) {
            if (dto.getGroupId() > 0) {
                groupService.findGroupOrThrow(dto.getGroupId());
            }
            entity.setGroupId(dto.getGroupId());
        }
        if (dto.getTagName() != null) entity.setTagName(dto.getTagName());
        // tagCode / tagType 创建后不可修改, 忽略
        if (dto.getValueType() != null) entity.setValueType(dto.getValueType());
        if (dto.getTagValue() != null) entity.setTagValue(dto.getTagValue());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getColor() != null) entity.setColor(dto.getColor());
        if (dto.getIcon() != null) entity.setIcon(dto.getIcon());
        if (dto.getSortOrder() != null) entity.setSortOrder(dto.getSortOrder());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = tagRepository.save(entity);
        log.info("更新标签: id={}, tagName={}", entity.getId(), entity.getTagName());
        return entity;
    }

    /**
     * 删除标签。
     * <p>系统内置标签 (is_system=true) 不可删除。删除前清理客户标签关联与关联规则。
     * AUTO 类型标签若仍有关联的 ACTIVE 规则, 拒绝删除 (需先禁用规则)。</p>
     *
     * @param id 标签 ID
     * @throws ScrmException 标签不存在 / 系统内置 / 仍有活跃规则
     */
    @Transactional
    public void deleteTag(Long id) throws ScrmException {
        ScrmTagEntity entity = findTagOrThrow(id);
        if (Boolean.TRUE.equals(entity.getIsSystem())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "系统内置标签不可删除: " + entity.getTagCode());
        }
        // 检查关联规则
        List<ScrmTagRuleEntity> rules = ruleRepository.findByTagId(id);
        boolean hasActiveRule = rules.stream().anyMatch(r -> RULE_STATUS_ACTIVE.equals(r.getStatus()));
        if (hasActiveRule) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "无法删除标签: 仍有活跃规则关联, 请先禁用规则");
        }
        // 清理客户标签关联
        int deleted = tagCustomerRepository.deleteByTagId(id);
        // 删除关联的非活跃规则 (INACTIVE / DRAFT)
        ruleRepository.deleteAll(rules);
        tagRepository.delete(entity);
        log.info("删除标签: id={}, tagName={}, 清理客户关联 {} 条", id, entity.getTagName(), deleted);
    }

    /**
     * 查询标签详情。
     *
     * @param id 标签 ID
     * @return 标签实体
     * @throws ScrmException 标签不存在
     */
    @Transactional(readOnly = true)
    public ScrmTagEntity getTag(Long id) throws ScrmException {
        return findTagOrThrow(id);
    }

    /**
     * 按编码查询标签。
     *
     * @param code 标签编码
     * @return 标签实体
     * @throws ScrmException 标签不存在
     */
    @Transactional(readOnly = true)
    public ScrmTagEntity getTagByCode(String code) throws ScrmException {
        return tagRepository.findByTagCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "标签不存在: code=" + code));
    }

    /**
     * 分页查询标签, 支持按分组、类型、启用状态与关键字过滤。
     *
     * @param groupId 分组 ID 过滤（可空）
     * @param tagType 标签类型过滤（可空）
     * @param enabled 启用状态过滤（可空）
     * @param keyword 关键字过滤（按 tagName / description / tagCode 模糊匹配, 可空）
     * @param pageable 分页参数
     * @return 标签分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmTagEntity> listTags(Long groupId, String tagType, Boolean enabled,
                                        String keyword, Pageable pageable) {
        Specification<ScrmTagEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (groupId != null) {
                predicates.add(cb.equal(root.get("groupId"), groupId));
            }
            if (tagType != null && !tagType.isBlank()) {
                predicates.add(cb.equal(root.get("tagType"), tagType));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("tagName")), kw),
                        cb.like(cb.lower(root.get("description")), kw),
                        cb.like(cb.lower(root.get("tagCode")), kw)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return tagRepository.findAll(spec, pageable);
    }

    /**
     * 启用标签。
     *
     * @param id 标签 ID
     * @return 更新后的标签
     * @throws ScrmException 标签不存在
     */
    @Transactional
    public ScrmTagEntity enableTag(Long id) throws ScrmException {
        ScrmTagEntity entity = findTagOrThrow(id);
        entity.setEnabled(true);
        tagRepository.save(entity);
        log.info("启用标签: id={}, tagName={}", id, entity.getTagName());
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
    public ScrmTagEntity disableTag(Long id) throws ScrmException {
        ScrmTagEntity entity = findTagOrThrow(id);
        entity.setEnabled(false);
        tagRepository.save(entity);
        log.info("禁用标签: id={}, tagName={}", id, entity.getTagName());
        return entity;
    }

    /**
     * 合并标签: 将源标签的全部客户关联迁移到目标标签, 然后删除源标签。
     * <p>
     * 迁移策略: 源标签下所有客户关联的 tagId 更新为目标标签 ID。若客户已存在目标标签关联,
     * 该源关联直接被覆盖 (依赖唯一约束 customer_id+tag_id)。迁移完成后删除源标签
     * 及其关联规则 (非 ACTIVE)。
     * </p>
     *
     * @param sourceId 源标签 ID (将被删除)
     * @param targetId 目标标签 ID (将接收客户关联)
     * @return 迁移的客户关联数
     * @throws ScrmException 标签不存在 / 同一标签 / 源标签为系统内置
     */
    @Transactional
    public int mergeTags(Long sourceId, Long targetId) throws ScrmException {
        if (Objects.equals(sourceId, targetId)) {
            throw ScrmException.badRequest("源标签与目标标签不能相同");
        }
        ScrmTagEntity source = findTagOrThrow(sourceId);
        findTagOrThrow(targetId);
        if (Boolean.TRUE.equals(source.getIsSystem())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "系统内置标签不可合并: " + source.getTagCode());
        }
        // 迁移客户关联 (UPDATE 直接修改 tagId)
        int migrated = tagCustomerRepository.migrateTagRelations(sourceId, targetId);
        // 清理源标签关联规则 (非 ACTIVE)
        List<ScrmTagRuleEntity> rules = ruleRepository.findByTagId(sourceId);
        ruleRepository.deleteAll(rules);
        // 删除源标签
        tagRepository.delete(source);
        // 刷新目标标签客户数
        updateTagCustomerCount(targetId);
        log.info("合并标签: source={}, target={}, 迁移客户关联 {} 条", sourceId, targetId, migrated);
        return migrated;
    }

    /**
     * 更新标签的被打标客户数。
     *
     * @param id 标签 ID
     * @return 更新后的标签
     * @throws ScrmException 标签不存在
     */
    @Transactional
    public ScrmTagEntity updateTagCustomerCount(Long id) throws ScrmException {
        ScrmTagEntity entity = findTagOrThrow(id);
        long count = tagCustomerRepository.countByTagId(id);
        entity.setCustomerCount((int) count);
        entity = tagRepository.save(entity);
        log.info("更新标签客户数: id={}, tagName={}, customerCount={}", id, entity.getTagName(), count);
        return entity;
    }

    // ============================================================
    // 客户标签 CustomerTag
    // ============================================================

    /**
     * 为客户打标。
     * <p>校验标签存在且启用。同一客户同一标签默认覆盖更新 tag_value 与打标人信息,
     * 重复打标不增加新记录 (依赖唯一约束)。AUTO 来源打标会设置 is_auto=true。</p>
     *
     * @param customerId 客户 ID
     * @param tagId      标签 ID
     * @param tagValue   标签值 (可空)
     * @param source     标签来源 (可空, 默认 MANUAL)
     * @param assignedBy 打标人 (可空)
     * @return 创建或更新后的客户标签关联
     * @throws ScrmException 标签不存在或已禁用
     */
    @Transactional
    public ScrmTagCustomerEntity assignTag(Long customerId, Long tagId, String tagValue,
                                           String source, String assignedBy) throws ScrmException {
        ScrmTagEntity tag = findTagOrThrow(tagId);
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
        entity.setAssignedByName(assignedBy);
        entity.setAssignedAt(LocalDateTime.now());
        entity.setConfidence(DEFAULT_CONFIDENCE);
        entity.setIsAuto(isAuto);
        entity = tagCustomerRepository.save(entity);
        // 新打标时刷新标签客户数
        if (isNew) {
            updateTagCustomerCount(tagId);
        }
        log.info("为客户打标: customerId={}, tagId={}, source={}, isNew={}",
                customerId, tagId, tagSource, isNew);
        return entity;
    }

    /**
     * 批量打标/去标。
     * <p>对入参客户 × 标签笛卡尔积执行 ADD 或 REMOVE 动作, 返回成功操作数。</p>
     *
     * @param batchDto 批量参数
     * @return 成功操作数
     * @throws ScrmException 标签不存在或已禁用
     */
    @Transactional
    public int batchAssign(ScrmBatchTagDto batchDto) throws ScrmException {
        if (ACTION_ADD.equals(batchDto.getAction())) {
            return batchAdd(batchDto);
        } else if (ACTION_REMOVE.equals(batchDto.getAction())) {
            return batchRemove(batchDto);
        }
        throw ScrmException.badRequest("未知动作: " + batchDto.getAction());
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
     * 分页查询标签下的客户。
     *
     * @param tagId    标签 ID
     * @param pageable 分页参数
     * @return 客户关联分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmTagCustomerEntity> getTagCustomers(Long tagId, Pageable pageable) {
        return tagCustomerRepository.findByTagId(tagId, pageable);
    }

    /**
     * 检查客户是否具有指定标签。
     *
     * @param customerId 客户 ID
     * @param tagId      标签 ID
     * @return 是否具有标签
     */
    @Transactional(readOnly = true)
    public boolean checkTag(Long customerId, Long tagId) {
        return tagCustomerRepository
                .findByCustomerIdAndTagId(customerId, tagId)
                .isPresent();
    }

    /**
     * 批量获取客户标签关联。
     *
     * @param customerIds 客户 ID 列表
     * @return 客户标签关联列表 (按 customerId 分组)
     */
    @Transactional(readOnly = true)
    public Map<Long, List<ScrmTagCustomerEntity>> getTagsByCustomerIds(List<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return Map.of();
        }
        List<ScrmTagCustomerEntity> relations = tagCustomerRepository
                .findByCustomerIdIn(customerIds);
        return relations.stream().collect(Collectors.groupingBy(ScrmTagCustomerEntity::getCustomerId));
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 批量打标 (ADD 动作)。
     *
     * @param batchDto 批量参数
     * @return 成功操作数
     * @throws ScrmException 标签不存在或已禁用
     */
    private int batchAdd(ScrmBatchTagDto batchDto) throws ScrmException {
        int success = 0;
        for (Long tagId : batchDto.getTagIds()) {
            ScrmTagEntity tag = findTagOrThrow(tagId);
            if (Boolean.FALSE.equals(tag.getEnabled())) {
                log.warn("批量打标跳过已禁用标签: tagId={}", tagId);
                continue;
            }
            String source = (batchDto.getTagSource() == null || batchDto.getTagSource().isBlank())
                    ? DEFAULT_TAG_SOURCE : batchDto.getTagSource();
            for (Long customerId : batchDto.getCustomerIds()) {
                try {
                    assignTag(customerId, tagId, batchDto.getTagValue(), source,
                            batchDto.getAssignedBy());
                    success++;
                } catch (Exception e) {
                    log.warn("批量打标失败: customerId={}, tagId={}, err={}",
                            customerId, tagId, e.getMessage());
                }
            }
        }
        log.info("批量打标完成: 成功 {} 条", success);
        return success;
    }

    /**
     * 批量去标 (REMOVE 动作)。
     *
     * @param batchDto 批量参数
     * @return 成功操作数
     */
    private int batchRemove(ScrmBatchTagDto batchDto) {
        int success = 0;
        for (Long customerId : batchDto.getCustomerIds()) {
            int deleted = tagCustomerRepository.deleteByCustomerIdAndTagIdIn(
                     customerId, batchDto.getTagIds());
            success += deleted;
        }
        // 刷新涉及标签的客户数
        for (Long tagId : batchDto.getTagIds()) {
            try {
                updateTagCustomerCount(tagId);
            } catch (Exception e) {
                log.warn("批量去标后刷新标签客户数失败: tagId={}, err={}", tagId, e.getMessage());
            }
        }
        log.info("批量去标完成: 成功 {} 条", success);
        return success;
    }

    /**
     * 校验标签参数。
     *
     * @param dto     标签参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateTagDto(ScrmTagDto dto, boolean partial) throws ScrmException {
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
        if (!partial && (dto.getTagCode() == null || dto.getTagCode().isBlank())) {
            throw ScrmException.badRequest("标签编码不能为空");
        }
        if (dto.getTagType() != null && !VALID_TAG_TYPES.contains(dto.getTagType())) {
            throw ScrmException.badRequest(
                    "标签类型非法: " + dto.getTagType() + ", 仅支持 " + VALID_TAG_TYPES);
        }
        if (dto.getValueType() != null && !dto.getValueType().isBlank() && !VALID_VALUE_TYPES.contains(dto.getValueType())) {
            throw ScrmException.badRequest(
                    "值类型非法: " + dto.getValueType() + ", 仅支持 " + VALID_VALUE_TYPES);
        }
    }

    /**
     * 按主键查询标签, 不存在抛异常, 并校验归属账号。
     * <p>规则管理子域通过兄弟类协作复用。</p>
     *
     * @param id 标签 ID
     * @return 标签实体
     * @throws ScrmException 标签不存在
     */
    ScrmTagEntity findTagOrThrow(Long id) throws ScrmException {
        ScrmTagEntity entity = tagRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "标签不存在: id=" + id));

        return entity;
    }
}
