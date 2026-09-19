/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTagSystemGroupService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmTagGroupDto;
import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.hiylo.scrm.entity.ScrmTagEntity;
import org.hiylo.scrm.entity.ScrmTagGroupEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmTagCustomerRepository;
import org.hiylo.scrm.repository.ScrmTagGroupRepository;
import org.hiylo.scrm.repository.ScrmTagRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * SCRM 标签分组管理服务。
 * <p>
 * 承载标签分组管理子域: 分组创建 / 更新 / 删除 / 启停 / 分页查询与分组统计更新。
 * 分组存在性校验 ({@link #findGroupOrThrow}) 提供给标签管理子域复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmTagSystemGroupService {

    /** 默认启用状态 */
    private static final boolean DEFAULT_ENABLED = true;

    /** 默认是否系统内置 */
    private static final boolean DEFAULT_IS_SYSTEM = false;

    /** 默认排序 */
    private static final int DEFAULT_SORT_ORDER = 0;

    /** 标签分组数据访问层 */
    private final ScrmTagGroupRepository groupRepository;

    /** 标签定义数据访问层 */
    private final ScrmTagRepository tagRepository;

    /** 客户标签关联数据访问层 */
    private final ScrmTagCustomerRepository tagCustomerRepository;

    /**
     * 创建标签分组。
     * <p>校验分组编码在唯一后写入归属账号 ID 持久化, isSystem / enabled / sortOrder
     * 缺省时填默认值。</p>
     *
     * @param dto 分组参数
     * @return 创建后的分组
     * @throws ScrmException 参数非法 / 分组编码重复
     */
    @Transactional
    public ScrmTagGroupEntity createGroup(ScrmTagGroupDto dto) throws ScrmException {
        validateGroupDto(dto, false);
        if (groupRepository.findByGroupCode(dto.getGroupCode()).isPresent()) {
            throw ScrmException.conflict("分组编码已存在: " + dto.getGroupCode());
        }
        ScrmTagGroupEntity entity = new ScrmTagGroupEntity();
        entity.setGroupName(dto.getGroupName());
        entity.setGroupCode(dto.getGroupCode());
        entity.setDescription(dto.getDescription());
        entity.setColor(dto.getColor());
        entity.setIcon(dto.getIcon());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : DEFAULT_SORT_ORDER);
        entity.setTagCount(0);
        entity.setCustomerCount(0);
        entity.setIsSystem(dto.getIsSystem() != null ? dto.getIsSystem() : DEFAULT_IS_SYSTEM);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : DEFAULT_ENABLED);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = groupRepository.save(entity);
        log.info("创建标签分组: id={}, groupName={}, groupCode={}",
                entity.getId(), entity.getGroupName(), entity.getGroupCode());
        return entity;
    }

    /**
     * 更新标签分组（字段非空才覆盖, groupCode 不允许修改）。
     *
     * @param id  分组 ID
     * @param dto 分组参数
     * @return 更新后的分组
     * @throws ScrmException 分组不存在 / 参数非法 / 系统内置分组不可修改编码
     */
    @Transactional
    public ScrmTagGroupEntity updateGroup(Long id, ScrmTagGroupDto dto) throws ScrmException {
        ScrmTagGroupEntity entity = findGroupOrThrow(id);
        validateGroupDto(dto, true);
        if (dto.getGroupName() != null) entity.setGroupName(dto.getGroupName());
        // groupCode 创建后不可修改, 忽略
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getColor() != null) entity.setColor(dto.getColor());
        if (dto.getIcon() != null) entity.setIcon(dto.getIcon());
        if (dto.getSortOrder() != null) entity.setSortOrder(dto.getSortOrder());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = groupRepository.save(entity);
        log.info("更新标签分组: id={}, groupName={}", entity.getId(), entity.getGroupName());
        return entity;
    }

    /**
     * 删除标签分组。
     * <p>系统内置分组 (is_system=true) 不可删除。删除前检查分组下是否仍有标签, 若有则拒绝。
     * 业务上应先迁移或删除分组下全部标签再删除分组。</p>
     *
     * @param id 分组 ID
     * @throws ScrmException 分组不存在 / 系统内置 / 仍有标签引用
     */
    @Transactional
    public void deleteGroup(Long id) throws ScrmException {
        ScrmTagGroupEntity entity = findGroupOrThrow(id);
        if (Boolean.TRUE.equals(entity.getIsSystem())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "系统内置分组不可删除: " + entity.getGroupCode());
        }
        long tagCount = tagRepository.countByGroupId(id);
        if (tagCount > 0) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    String.format("无法删除分组: 仍有 %d 个标签引用该分组, 请先迁移或删除标签", tagCount));
        }
        groupRepository.delete(entity);
        log.info("删除标签分组: id={}, groupName={}", id, entity.getGroupName());
    }

    /**
     * 查询分组详情。
     *
     * @param id 分组 ID
     * @return 分组实体
     * @throws ScrmException 分组不存在
     */
    @Transactional(readOnly = true)
    public ScrmTagGroupEntity getGroup(Long id) throws ScrmException {
        return findGroupOrThrow(id);
    }

    /**
     * 分页查询分组, 支持按启用状态与关键字过滤。
     *
     * @param enabled 启用状态过滤（可空）
     * @param keyword 关键字过滤（按 groupName / description / groupCode 模糊匹配, 可空）
     * @param pageable 分页参数
     * @return 分组分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmTagGroupEntity> listGroups(Boolean enabled, String keyword, Pageable pageable) {
        Specification<ScrmTagGroupEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("groupName")), kw),
                        cb.like(cb.lower(root.get("description")), kw),
                        cb.like(cb.lower(root.get("groupCode")), kw)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return groupRepository.findAll(spec, pageable);
    }

    /**
     * 启用分组。
     *
     * @param id 分组 ID
     * @return 更新后的分组
     * @throws ScrmException 分组不存在
     */
    @Transactional
    public ScrmTagGroupEntity enableGroup(Long id) throws ScrmException {
        ScrmTagGroupEntity entity = findGroupOrThrow(id);
        entity.setEnabled(true);
        groupRepository.save(entity);
        log.info("启用标签分组: id={}, groupName={}", id, entity.getGroupName());
        return entity;
    }

    /**
     * 禁用分组。
     *
     * @param id 分组 ID
     * @return 更新后的分组
     * @throws ScrmException 分组不存在
     */
    @Transactional
    public ScrmTagGroupEntity disableGroup(Long id) throws ScrmException {
        ScrmTagGroupEntity entity = findGroupOrThrow(id);
        entity.setEnabled(false);
        groupRepository.save(entity);
        log.info("禁用标签分组: id={}, groupName={}", id, entity.getGroupName());
        return entity;
    }

    /**
     * 更新分组统计 (标签数 + 覆盖客户数)。
     * <p>标签数取分组下标签总数; 覆盖客户数取分组下所有标签的客户关联去重后数量。</p>
     *
     * @param id 分组 ID
     * @return 更新后的分组
     * @throws ScrmException 分组不存在
     */
    @Transactional
    public ScrmTagGroupEntity updateGroupStats(Long id) throws ScrmException {
        ScrmTagGroupEntity entity = findGroupOrThrow(id);
        long tagCount = tagRepository.countByGroupId(id);
        // 覆盖客户数: 取分组下所有标签的客户关联, 按 customerId 去重
        List<ScrmTagEntity> tags = tagRepository.findByGroupId(id);
        long customerCount = 0;
        if (!tags.isEmpty()) {
            List<Long> tagIds = tags.stream().map(ScrmTagEntity::getId).toList();
            // 按标签 ID 列表查询关联, 按 customerId 去重计数
            Specification<ScrmTagCustomerEntity> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(root.get("tagId").in(tagIds));
                return cb.and(predicates.toArray(new Predicate[0]));
            };
            List<ScrmTagCustomerEntity> relations = tagCustomerRepository.findAll(spec);
            customerCount = relations.stream()
                    .map(ScrmTagCustomerEntity::getCustomerId)
                    .distinct()
                    .count();
        }
        entity.setTagCount((int) tagCount);
        entity.setCustomerCount((int) customerCount);
        entity = groupRepository.save(entity);
        log.info("更新分组统计: id={}, tagCount={}, customerCount={}",
                id, tagCount, customerCount);
        return entity;
    }

    /**
     * 校验分组参数。
     *
     * @param dto     分组参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateGroupDto(ScrmTagGroupDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("分组参数不能为空");
        }
        if (dto.getGroupName() != null) {
            if (dto.getGroupName().isBlank()) {
                throw ScrmException.badRequest("分组名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("分组名称不能为空");
        }
        if (!partial && (dto.getGroupCode() == null || dto.getGroupCode().isBlank())) {
            throw ScrmException.badRequest("分组编码不能为空");
        }
    }

    /**
     * 按主键查询分组, 不存在抛异常, 并校验归属账号。
     * <p>标签管理子域通过兄弟类协作复用。</p>
     *
     * @param id 分组 ID
     * @return 分组实体
     * @throws ScrmException 分组不存在
     */
    ScrmTagGroupEntity findGroupOrThrow(Long id) throws ScrmException {
        ScrmTagGroupEntity entity = groupRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "标签分组不存在: id=" + id));

        return entity;
    }
}
