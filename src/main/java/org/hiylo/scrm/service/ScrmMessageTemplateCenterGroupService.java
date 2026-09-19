/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTemplateCenterGroupService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmMessageTemplateGroupDto;
import org.hiylo.scrm.entity.ScrmMessageTemplateGroupEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmMessageTemplateCenterRepository;
import org.hiylo.scrm.repository.ScrmMessageTemplateGroupRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 消息模板中心分组管理子域服务。
 * <p>
 * 承载模板分组的创建 / 更新 / 删除 / 查询 / 启停 / 模板迁移 / 分组树构建与分组统计刷新,
 * 提供 {@code findGroupOrThrow} 校验分组存在性。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmMessageTemplateCenterGroupService {

    /** 模板分组数据访问层 */
    private final ScrmMessageTemplateGroupRepository groupRepository;

    /** 模板定义数据访问层 */
    private final ScrmMessageTemplateCenterRepository centerRepository;

    /**
     * 创建模板分组。
     *
     * @param dto 分组参数
     * @return 创建后的分组
     * @throws ScrmException 分组编码已存在 / 参数非法
     */
    @Transactional
    public ScrmMessageTemplateGroupEntity createGroup(ScrmMessageTemplateGroupDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("分组参数不能为空");
        }
        if (groupRepository.findByGroupCode(dto.getGroupCode()).isPresent()) {
            throw ScrmException.conflict("分组编码已存在: groupCode=" + dto.getGroupCode());
        }
        // 父分组存在性校验
        if (dto.getParentGroupId() != null) {
            findGroupOrThrow(dto.getParentGroupId());
        }
        ScrmMessageTemplateGroupEntity entity = new ScrmMessageTemplateGroupEntity();
        entity.setGroupName(dto.getGroupName());
        entity.setGroupCode(dto.getGroupCode());
        entity.setDescription(dto.getDescription());
        entity.setGroupType(dto.getGroupType());
        entity.setParentGroupId(dto.getParentGroupId());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        entity.setTemplateCount(0);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setColor(dto.getColor());
        entity.setIcon(dto.getIcon());
        entity.setCreatedBy(dto.getCreatedBy());
        entity = groupRepository.save(entity);
        log.info("创建模板分组: id={}, groupCode={}", entity.getId(), entity.getGroupCode());
        return entity;
    }

    /**
     * 更新模板分组（字段非空才覆盖）。
     *
     * @param id  分组 ID
     * @param dto 分组参数
     * @return 更新后的分组
     * @throws ScrmException 分组不存在 / 分组编码冲突 / 父分组不存在
     */
    @Transactional
    public ScrmMessageTemplateGroupEntity updateGroup(
            Long id, ScrmMessageTemplateGroupDto dto) throws ScrmException {
        ScrmMessageTemplateGroupEntity entity = findGroupOrThrow(id);
        if (dto == null) {
            throw ScrmException.badRequest("分组参数不能为空");
        }
        if (dto.getGroupCode() != null && !dto.getGroupCode().equals(entity.getGroupCode())) {
            groupRepository.findByGroupCode(dto.getGroupCode())
                    .ifPresent(other -> {
                        if (!other.getId().equals(id)) {
                            throw ScrmException.conflict("分组编码已被其他分组占用: groupCode=" + dto.getGroupCode());
                        }
                    });
            entity.setGroupCode(dto.getGroupCode());
        }
        if (dto.getParentGroupId() != null) {
            if (dto.getParentGroupId().equals(id)) {
                throw ScrmException.badRequest("父分组不能为自身");
            }
            findGroupOrThrow(dto.getParentGroupId());
            entity.setParentGroupId(dto.getParentGroupId());
        }
        if (dto.getGroupName() != null) entity.setGroupName(dto.getGroupName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getGroupType() != null) entity.setGroupType(dto.getGroupType());
        if (dto.getSortOrder() != null) entity.setSortOrder(dto.getSortOrder());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getColor() != null) entity.setColor(dto.getColor());
        if (dto.getIcon() != null) entity.setIcon(dto.getIcon());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = groupRepository.save(entity);
        log.info("更新模板分组: id={}, groupCode={}", entity.getId(), entity.getGroupCode());
        return entity;
    }

    /**
     * 删除模板分组。
     *
     * @param id 分组 ID
     * @throws ScrmException 分组不存在
     */
    @Transactional
    public void deleteGroup(Long id) throws ScrmException {
        findGroupOrThrow(id);
        // 分组下存在模板时阻止删除, 需先迁移模板
        long count = centerRepository.countByGroupId(id);
        if (count > 0) {
            throw ScrmException.badRequest("分组下存在 " + count + " 个模板, 请先迁移后再删除");
        }
        groupRepository.deleteById(id);
        log.info("删除模板分组: id={}", id);
    }

    /**
     * 查询分组详情。
     *
     * @param id 分组 ID
     * @return 分组实体
     * @throws ScrmException 分组不存在
     */
    @Transactional(readOnly = true)
    public ScrmMessageTemplateGroupEntity getGroup(Long id) throws ScrmException {
        return findGroupOrThrow(id);
    }

    /**
     * 按分组编码查询分组。
     *
     * @param code 分组编码
     * @return 分组实体
     * @throws ScrmException 分组不存在
     */
    @Transactional(readOnly = true)
    public ScrmMessageTemplateGroupEntity getGroupByCode(String code) throws ScrmException {
        return groupRepository.findByGroupCode(code)
                .orElseThrow(() -> ScrmException.notFound("模板分组不存在: groupCode=" + code));
    }

    /**
     * 分页查询分组列表, 支持按分组类型、父分组、启用状态过滤。
     *
     * @param groupType 分组类型过滤（可空）
     * @param parentId  父分组 ID 过滤（可空）
     * @param enabled   启用状态过滤（可空）
     * @param pageable  分页参数
     * @return 分组分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmMessageTemplateGroupEntity> listGroups(String groupType, Long parentId,
                                                            Boolean enabled, Pageable pageable) {
        Specification<ScrmMessageTemplateGroupEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (groupType != null && !groupType.isBlank()) {
                predicates.add(cb.equal(root.get("groupType"), groupType));
            }
            if (parentId != null) {
                predicates.add(cb.equal(root.get("parentGroupId"), parentId));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return groupRepository.findAll(spec, pageable);
    }

    /**
     * 启用分组。
     *
     * @param id 分组 ID
     * @throws ScrmException 分组不存在
     */
    @Transactional
    public void enableGroup(Long id) throws ScrmException {
        ScrmMessageTemplateGroupEntity entity = findGroupOrThrow(id);
        entity.setEnabled(true);
        groupRepository.save(entity);
        log.info("启用模板分组: id={}", id);
    }

    /**
     * 禁用分组。
     *
     * @param id 分组 ID
     * @throws ScrmException 分组不存在
     */
    @Transactional
    public void disableGroup(Long id) throws ScrmException {
        ScrmMessageTemplateGroupEntity entity = findGroupOrThrow(id);
        entity.setEnabled(false);
        groupRepository.save(entity);
        log.info("禁用模板分组: id={}", id);
    }

    /**
     * 将源分组下的全部模板迁移到目标分组。
     *
     * @param sourceGroupId 源分组 ID
     * @param targetGroupId 目标分组 ID
     * @return 迁移的模板数量
     * @throws ScrmException 源/目标分组不存在
     */
    @Transactional
    public int moveTemplatesToGroup(Long sourceGroupId, Long targetGroupId) throws ScrmException {
        findGroupOrThrow(sourceGroupId);
        ScrmMessageTemplateGroupEntity target = findGroupOrThrow(targetGroupId);
        int moved = centerRepository.moveTemplatesToGroup(
                 sourceGroupId, targetGroupId, target.getGroupName());
        updateGroupStats(sourceGroupId);
        updateGroupStats(targetGroupId);
        log.info("迁移模板: sourceGroupId={}, targetGroupId={}, moved={}", sourceGroupId, targetGroupId, moved);
        return moved;
    }

    /**
     * 更新分组的模板数量统计。
     *
     * @param id 分组 ID
     * @throws ScrmException 分组不存在
     */
    @Transactional
    public void updateGroupStats(Long id) throws ScrmException {
        ScrmMessageTemplateGroupEntity entity = findGroupOrThrow(id);
        long count = centerRepository.countByGroupId(id);
        entity.setTemplateCount((int) count);
        groupRepository.save(entity);
    }

    /**
     * 构建分组树 (含子分组递归)。
     *
     * @return 分组树列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getGroupTree() {
        List<ScrmMessageTemplateGroupEntity> all = groupRepository.findAllByOrderBySortOrderAsc();
        Map<Long, List<ScrmMessageTemplateGroupEntity>> byParent = new HashMap<>();
        List<ScrmMessageTemplateGroupEntity> roots = new ArrayList<>();
        for (ScrmMessageTemplateGroupEntity g : all) {
            if (g.getParentGroupId() == null) {
                roots.add(g);
            } else {
                byParent.computeIfAbsent(g.getParentGroupId(), k -> new ArrayList<>()).add(g);
            }
        }
        List<Map<String, Object>> tree = new ArrayList<>();
        for (ScrmMessageTemplateGroupEntity root : roots) {
            tree.add(buildGroupNode(root, byParent));
        }
        return tree;
    }

    /**
     * 按主键查询分组, 不存在抛异常。
     *
     * @param id 分组 ID
     * @return 分组实体
     * @throws ScrmException 分组不存在
     */
    private ScrmMessageTemplateGroupEntity findGroupOrThrow(Long id) throws ScrmException {
        return groupRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("模板分组不存在: id=" + id));
    }

    /**
     * 构建分组树节点 (递归)。
     *
     * @param group    分组实体
     * @param byParent 按父分组 ID 索引的子分组
     * @return 分组树节点
     */
    private Map<String, Object> buildGroupNode(ScrmMessageTemplateGroupEntity group,
                                                Map<Long, List<ScrmMessageTemplateGroupEntity>> byParent) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", group.getId());
        node.put("groupName", group.getGroupName());
        node.put("groupCode", group.getGroupCode());
        node.put("groupType", group.getGroupType());
        node.put("parentGroupId", group.getParentGroupId());
        node.put("sortOrder", group.getSortOrder());
        node.put("templateCount", group.getTemplateCount());
        node.put("enabled", group.getEnabled());
        node.put("color", group.getColor());
        node.put("icon", group.getIcon());
        List<Map<String, Object>> children = new ArrayList<>();
        List<ScrmMessageTemplateGroupEntity> childList = byParent.get(group.getId());
        if (childList != null) {
            for (ScrmMessageTemplateGroupEntity child : childList) {
                children.add(buildGroupNode(child, byParent));
            }
        }
        node.put("children", children);
        return node;
    }
}