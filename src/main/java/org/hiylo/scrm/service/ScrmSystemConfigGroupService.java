/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSystemConfigGroupService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmConfigGroupDto;
import org.hiylo.scrm.entity.ScrmConfigGroupEntity;
import org.hiylo.scrm.entity.ScrmSystemConfigEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmConfigGroupRepository;
import org.hiylo.scrm.repository.ScrmSystemConfigRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 系统配置分组管理服务。
 * <p>
 * 承载配置分组子域: 分组增删改查与按编码查询、分页/树/子分组、启停、更新分组配置数、
 * 配置移动分组与分组配置数统计。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmSystemConfigGroupService {

    /** 配置分组数据访问层 */
    private final ScrmConfigGroupRepository groupRepository;
    /** 系统配置数据访问层 */
    private final ScrmSystemConfigRepository configRepository;
    /** 配置项管理服务 (共享工具) */
    private final ScrmSystemConfigItemService itemService;

    // ============================================================
    // Group 配置分组管理
    // ============================================================

    /**
     * 创建配置分组。
     * <p>校验 environment 合法性与 groupCode 唯一性, 若指定 parentGroupCode 则父分组必须存在,
     * 写入归属账号 ID 持久化, 缺省字段填默认值。</p>
     *
     * @param dto 分组参数
     * @return 创建后的分组
     * @throws ScrmException 参数非法 / 编码重复 / 父分组不存在
     */
    @Transactional
    public ScrmConfigGroupEntity createGroup(ScrmConfigGroupDto dto) throws ScrmException {
        validateGroupDto(dto, false);
        if (groupRepository.findByGroupCode(dto.getGroupCode()).isPresent()) {
            throw ScrmException.conflict("分组编码已存在: " + dto.getGroupCode());
        }
        // 校验父分组存在
        if (dto.getParentGroupCode() != null && !dto.getParentGroupCode().isBlank()) {
            if (groupRepository.findByGroupCode(dto.getParentGroupCode()).isEmpty()) {
                throw ScrmException.badRequest("父分组不存在: " + dto.getParentGroupCode());
            }
        }
        ScrmConfigGroupEntity entity = new ScrmConfigGroupEntity();
        entity.setGroupName(dto.getGroupName());
        entity.setGroupCode(dto.getGroupCode());
        entity.setDescription(dto.getDescription());
        entity.setParentGroupCode(dto.getParentGroupCode());
        entity.setGroupLevel(dto.getGroupLevel() != null ? dto.getGroupLevel() : ScrmSystemConfigItemService.DEFAULT_GROUP_LEVEL);
        entity.setGroupIcon(dto.getGroupIcon());
        entity.setDisplayOrder(dto.getDisplayOrder() != null ? dto.getDisplayOrder() : ScrmSystemConfigItemService.DEFAULT_DISPLAY_ORDER);
        entity.setConfigCount(ScrmSystemConfigItemService.DEFAULT_CONFIG_COUNT);
        entity.setIsVisible(dto.getIsVisible() != null ? dto.getIsVisible() : Boolean.TRUE);
        entity.setIsExpanded(dto.getIsExpanded() != null ? dto.getIsExpanded() : Boolean.TRUE);
        entity.setApplicableRoles(dto.getApplicableRoles());
        entity.setApplicableModules(dto.getApplicableModules());
        entity.setEnvironment(dto.getEnvironment() != null && !dto.getEnvironment().isBlank()
                ? dto.getEnvironment() : ScrmSystemConfigItemService.DEFAULT_ENVIRONMENT);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = groupRepository.save(entity);
        log.info("创建配置分组: id={}, groupCode={}, groupName={}",
                entity.getId(), entity.getGroupCode(), entity.getGroupName());
        return entity;
    }

    /**
     * 更新配置分组（字段非空才覆盖）。
     *
     * @param id  分组 ID
     * @param dto 分组参数
     * @return 更新后的分组
     * @throws ScrmException 分组不存在 / 参数非法 / 编码重复 / 父分组不存在
     */
    @Transactional
    public ScrmConfigGroupEntity updateGroup(Long id, ScrmConfigGroupDto dto) throws ScrmException {
        ScrmConfigGroupEntity entity = findGroupOrThrow(id);
        validateGroupDto(dto, true);
        if (dto.getGroupCode() != null && !dto.getGroupCode().equals(entity.getGroupCode())) {
            if (groupRepository.findByGroupCode(dto.getGroupCode()).isPresent()) {
                throw ScrmException.conflict("分组编码已存在: " + dto.getGroupCode());
            }
        }
        // 父分组校验 (防止自引用)
        if (dto.getParentGroupCode() != null && !dto.getParentGroupCode().isBlank()) {
            if (dto.getParentGroupCode().equals(entity.getGroupCode())) {
                throw ScrmException.badRequest("父分组不能为自身: " + dto.getParentGroupCode());
            }
            if (groupRepository.findByGroupCode(dto.getParentGroupCode()).isEmpty()) {
                throw ScrmException.badRequest("父分组不存在: " + dto.getParentGroupCode());
            }
        }
        if (dto.getGroupName() != null) entity.setGroupName(dto.getGroupName());
        if (dto.getGroupCode() != null) entity.setGroupCode(dto.getGroupCode());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getParentGroupCode() != null) entity.setParentGroupCode(dto.getParentGroupCode());
        if (dto.getGroupLevel() != null) entity.setGroupLevel(dto.getGroupLevel());
        if (dto.getGroupIcon() != null) entity.setGroupIcon(dto.getGroupIcon());
        if (dto.getDisplayOrder() != null) entity.setDisplayOrder(dto.getDisplayOrder());
        if (dto.getIsVisible() != null) entity.setIsVisible(dto.getIsVisible());
        if (dto.getIsExpanded() != null) entity.setIsExpanded(dto.getIsExpanded());
        if (dto.getApplicableRoles() != null) entity.setApplicableRoles(dto.getApplicableRoles());
        if (dto.getApplicableModules() != null) entity.setApplicableModules(dto.getApplicableModules());
        if (dto.getEnvironment() != null && !dto.getEnvironment().isBlank()) entity.setEnvironment(dto.getEnvironment());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity.setLastModifiedAt(LocalDateTime.now());
        entity = groupRepository.save(entity);
        log.info("更新配置分组: id={}, groupCode={}", entity.getId(), entity.getGroupCode());
        return entity;
    }

    /**
     * 删除配置分组 (仅允许删除空分组)。
     *
     * @param id 分组 ID
     * @throws ScrmException 分组不存在 / 非空不可删
     */
    @Transactional
    public void deleteGroup(Long id) throws ScrmException {
        ScrmConfigGroupEntity entity = findGroupOrThrow(id);
        // 校验分组下无配置
        int count = getGroupConfigCount(entity.getGroupCode());
        if (count > 0) {
            throw ScrmException.badRequest("分组下存在 " + count + " 个配置, 无法删除: " + entity.getGroupCode());
        }
        // 校验无子分组
        List<ScrmConfigGroupEntity> children = groupRepository.findByParentGroupCode(
                 entity.getGroupCode());
        if (!children.isEmpty()) {
            throw ScrmException.badRequest("分组下存在子分组, 无法删除: " + entity.getGroupCode());
        }
        groupRepository.delete(entity);
        log.info("删除配置分组: id={}, groupCode={}", id, entity.getGroupCode());
    }

    /**
     * 查询分组详情。
     *
     * @param id 分组 ID
     * @return 分组实体
     * @throws ScrmException 分组不存在
     */
    @Transactional(readOnly = true)
    public ScrmConfigGroupEntity getGroup(Long id) throws ScrmException {
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
    public ScrmConfigGroupEntity getGroupByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("分组编码不能为空");
        }
        return groupRepository.findByGroupCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "配置分组不存在: code=" + code));
    }

    /**
     * 分页查询分组, 支持按父分组 / 启用状态 / 关键字过滤。
     *
     * @param parentGroupCode 父分组编码（可空）
     * @param enabled         启用状态（可空）
     * @param keyword         分组名称关键字模糊匹配（可空）
     * @param pageable        分页参数
     * @return 分组分页结果 (按 displayOrder ASC, createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmConfigGroupEntity> listGroups(String parentGroupCode, Boolean enabled,
                                                    String keyword, Pageable pageable) {
        Specification<ScrmConfigGroupEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (parentGroupCode != null && !parentGroupCode.isBlank()) {
                predicates.add(cb.equal(root.get("parentGroupCode"), parentGroupCode));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("groupName"), "%" + keyword + "%"));
            }
            query.orderBy(cb.asc(root.get("displayOrder")), cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return groupRepository.findAll(spec, pageable);
    }

    /**
     * 获取分组树 (基于 parentGroupCode 构建层级结构)。
     *
     * @return 分组树 [{group, children: [...]}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getGroupTree() {
        List<ScrmConfigGroupEntity> all = groupRepository.findAll();
        Map<String, List<ScrmConfigGroupEntity>> byParent = new LinkedHashMap<>();
        for (ScrmConfigGroupEntity g : all) {
            String parent = g.getParentGroupCode() != null ? g.getParentGroupCode() : "";
            byParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(g);
        }
        return buildGroupTree("", byParent);
    }

    /**
     * 递归构建分组树。
     *
     * @param parentCode 父分组编码
     * @param byParent   按父分组编码索引的分组映射
     * @return 子树列表
     */
    private List<Map<String, Object>> buildGroupTree(String parentCode,
                                                       Map<String, List<ScrmConfigGroupEntity>> byParent) {
        List<Map<String, Object>> tree = new ArrayList<>();
        List<ScrmConfigGroupEntity> children = byParent.getOrDefault(parentCode, new ArrayList<>());
        for (ScrmConfigGroupEntity g : children) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", g.getId());
            node.put("groupCode", g.getGroupCode());
            node.put("groupName", g.getGroupName());
            node.put("description", g.getDescription());
            node.put("parentGroupCode", g.getParentGroupCode());
            node.put("groupLevel", g.getGroupLevel());
            node.put("groupIcon", g.getGroupIcon());
            node.put("displayOrder", g.getDisplayOrder());
            node.put("configCount", g.getConfigCount());
            node.put("isVisible", g.getIsVisible());
            node.put("isExpanded", g.getIsExpanded());
            node.put("enabled", g.getEnabled());
            node.put("children", buildGroupTree(g.getGroupCode(), byParent));
            tree.add(node);
        }
        return tree;
    }

    /**
     * 查询子分组。
     *
     * @param parentCode 父分组编码
     * @return 子分组列表
     */
    @Transactional(readOnly = true)
    public List<ScrmConfigGroupEntity> getChildGroups(String parentCode) {
        if (parentCode == null || parentCode.isBlank()) {
            throw ScrmException.badRequest("父分组编码不能为空");
        }
        return groupRepository.findByParentGroupCode(parentCode);
    }

    /**
     * 启用分组。
     *
     * @param id 分组 ID
     * @return 更新后的分组
     * @throws ScrmException 分组不存在
     */
    @Transactional
    public ScrmConfigGroupEntity enableGroup(Long id) throws ScrmException {
        ScrmConfigGroupEntity entity = findGroupOrThrow(id);
        entity.setEnabled(Boolean.TRUE);
        entity.setLastModifiedAt(LocalDateTime.now());
        entity = groupRepository.save(entity);
        log.info("启用配置分组: id={}, groupCode={}", id, entity.getGroupCode());
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
    public ScrmConfigGroupEntity disableGroup(Long id) throws ScrmException {
        ScrmConfigGroupEntity entity = findGroupOrThrow(id);
        entity.setEnabled(Boolean.FALSE);
        entity.setLastModifiedAt(LocalDateTime.now());
        entity = groupRepository.save(entity);
        log.info("禁用配置分组: id={}, groupCode={}", id, entity.getGroupCode());
        return entity;
    }

    /**
     * 更新分组配置数 (重新统计指定分组的配置数)。
     *
     * @param id 分组 ID
     * @return 更新后的分组
     * @throws ScrmException 分组不存在
     */
    @Transactional
    public ScrmConfigGroupEntity updateGroupStats(Long id) throws ScrmException {
        ScrmConfigGroupEntity entity = findGroupOrThrow(id);
        int count = getGroupConfigCount(entity.getGroupCode());
        entity.setConfigCount(count);
        entity.setLastModifiedAt(LocalDateTime.now());
        entity = groupRepository.save(entity);
        log.info("更新分组配置数: id={}, groupCode={}, count={}", id, entity.getGroupCode(), count);
        return entity;
    }

    /**
     * 移动配置到指定分组。
     *
     * @param configId  配置 ID
     * @param groupCode 目标分组编码
     * @return 更新后的配置
     * @throws ScrmException 配置不存在 / 分组不存在
     */
    @Transactional
    public ScrmSystemConfigEntity moveConfigToGroup(Long configId, String groupCode) throws ScrmException {
        ScrmSystemConfigEntity entity = itemService.findConfigOrThrow(configId);
        if (groupCode == null || groupCode.isBlank()) {
            throw ScrmException.badRequest("目标分组编码不能为空");
        }
        ScrmConfigGroupEntity group = groupRepository.findByGroupCode(groupCode)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "配置分组不存在: code=" + groupCode));
        String oldGroup = entity.getConfigGroup();
        if (java.util.Objects.equals(oldGroup, groupCode)) {
            return entity;
        }
        entity.setConfigGroup(groupCode);
        entity = configRepository.save(entity);
        // 调整分组配置数
        itemService.decrementGroupConfigCount(oldGroup);
        itemService.incrementGroupConfigCount(groupCode);
        // 更新分组的最近修改时间
        group.setLastModifiedAt(LocalDateTime.now());
        groupRepository.save(group);
        itemService.evictCache(entity.getConfigKey());
        log.info("移动配置到分组: configId={}, oldGroup={}, newGroup={}", configId, oldGroup, groupCode);
        return entity;
    }

    /**
     * 查询分组配置数。
     *
     * @param groupCode 分组编码
     * @return 配置数
     */
    @Transactional(readOnly = true)
    public int getGroupConfigCount(String groupCode) {
        List<ScrmSystemConfigEntity> configs = configRepository.findByConfigGroup(groupCode);
        return configs.size();
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验分组参数。
     *
     * @param dto     分组参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateGroupDto(ScrmConfigGroupDto dto, boolean partial) throws ScrmException {
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
        if (dto.getGroupCode() != null) {
            if (dto.getGroupCode().isBlank()) {
                throw ScrmException.badRequest("分组编码不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("分组编码不能为空");
        }
        if (dto.getEnvironment() != null && !dto.getEnvironment().isBlank()
                && !ScrmSystemConfigItemService.VALID_ENVIRONMENTS.contains(dto.getEnvironment())) {
            throw ScrmException.badRequest("环境限定非法: " + dto.getEnvironment()
                    + ", 仅支持 " + ScrmSystemConfigItemService.VALID_ENVIRONMENTS);
        }
    }

    /**
     * 按主键查询分组, 不存在抛异常, 并校验归属账号。
     *
     * @param id 分组 ID
     * @return 分组实体
     * @throws ScrmException 分组不存在
     */
    private ScrmConfigGroupEntity findGroupOrThrow(Long id) throws ScrmException {
        ScrmConfigGroupEntity entity = groupRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "配置分组不存在: id=" + id));
        return entity;
    }
}