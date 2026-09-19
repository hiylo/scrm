/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSystemConfigQueryService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmConfigImportDto;
import org.hiylo.scrm.dto.ScrmConfigSearchDto;
import org.hiylo.scrm.entity.ScrmConfigGroupEntity;
import org.hiylo.scrm.entity.ScrmSystemConfigEntity;
import org.hiylo.scrm.exception.ScrmException;
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
 * SCRM 系统配置检索与运维服务。
 * <p>
 * 承载配置项检索与批量运维子域: 按模块 / 环境 / 角色 / 敏感 / 可覆盖分页查询、高级搜索、
 * 配置树、配置导入导出。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmSystemConfigQueryService {

    /** 系统配置数据访问层 */
    private final ScrmSystemConfigRepository configRepository;
    /** 配置分组数据访问层 */
    private final ScrmConfigGroupRepository groupRepository;
    /** 配置项管理服务 (共享工具) */
    private final ScrmSystemConfigItemService itemService;

    // ============================================================
    // Config 检索与运维
    // ============================================================

    /**
     * 按适用模块分页查询配置。
     *
     * @param module   模块名 (模糊匹配 applicableModules)
     * @param pageable 分页参数
     * @return 配置分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmSystemConfigEntity> getConfigsByModule(String module, Pageable pageable) {
        if (module == null || module.isBlank()) {
            throw ScrmException.badRequest("模块名不能为空");
        }
        Specification<ScrmSystemConfigEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.like(root.get("applicableModules"), "%" + module + "%"));
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return configRepository.findAll(spec, pageable);
    }

    /**
     * 按环境限定分页查询配置。
     *
     * @param env      环境限定: ALL/DEV/STAGING/PRODUCTION
     * @param pageable 分页参数
     * @return 配置分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmSystemConfigEntity> getConfigsByEnvironment(String env, Pageable pageable) {
        if (env == null || env.isBlank()) {
            throw ScrmException.badRequest("环境限定不能为空");
        }
        if (!ScrmSystemConfigItemService.VALID_ENVIRONMENTS.contains(env)) {
            throw ScrmException.badRequest("环境限定非法: " + env + ", 仅支持 "
                    + ScrmSystemConfigItemService.VALID_ENVIRONMENTS);
        }
        return configRepository.findByEnvironment(env, pageable);
    }

    /**
     * 按可见角色分页查询配置。
     *
     * @param role     角色名 (模糊匹配 applicableRoles)
     * @param pageable 分页参数
     * @return 配置分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmSystemConfigEntity> getConfigsByRole(String role, Pageable pageable) {
        if (role == null || role.isBlank()) {
            throw ScrmException.badRequest("角色名不能为空");
        }
        Specification<ScrmSystemConfigEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.like(root.get("applicableRoles"), "%" + role + "%"));
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return configRepository.findAll(spec, pageable);
    }

    /**
     * 查询敏感配置 (isSensitive=TRUE)。
     *
     * @param pageable 分页参数
     * @return 配置分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmSystemConfigEntity> getSensitiveConfigs(Pageable pageable) {
        return configRepository.findByIsSensitive(Boolean.TRUE, pageable);
    }

    /**
     * 查询可覆盖配置 (isOverridable=TRUE)。
     *
     * @param pageable 分页参数
     * @return 配置分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmSystemConfigEntity> getOverridableConfigs(Pageable pageable) {
        return configRepository.findByIsOverridable(Boolean.TRUE, pageable);
    }

    /**
     * 高级搜索配置 (支持分组 / 类型 / 关键字 / 环境 / 可见性 / 启用 / 敏感 / 系统级 / 模块 / 角色多维度组合)。
     *
     * @param searchDto 搜索参数
     * @param pageable  分页参数
     * @return 配置分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmSystemConfigEntity> searchConfigs(ScrmConfigSearchDto searchDto, Pageable pageable) {
        Specification<ScrmSystemConfigEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (searchDto != null) {
                if (searchDto.getGroup() != null && !searchDto.getGroup().isBlank()) {
                    predicates.add(cb.equal(root.get("configGroup"), searchDto.getGroup()));
                }
                if (searchDto.getType() != null && !searchDto.getType().isBlank()) {
                    predicates.add(cb.equal(root.get("configType"), searchDto.getType()));
                }
                if (searchDto.getKeyword() != null && !searchDto.getKeyword().isBlank()) {
                    String kw = "%" + searchDto.getKeyword() + "%";
                    predicates.add(cb.or(
                            cb.like(root.get("configKey"), kw),
                            cb.like(root.get("configName"), kw),
                            cb.like(root.get("description"), kw)));
                }
                if (searchDto.getEnvironment() != null && !searchDto.getEnvironment().isBlank()) {
                    predicates.add(cb.equal(root.get("environment"), searchDto.getEnvironment()));
                }
                if (searchDto.getIsVisible() != null) {
                    predicates.add(cb.equal(root.get("isVisible"), searchDto.getIsVisible()));
                }
                if (searchDto.getEnabled() != null) {
                    predicates.add(cb.equal(root.get("enabled"), searchDto.getEnabled()));
                }
                if (searchDto.getIsSensitive() != null) {
                    predicates.add(cb.equal(root.get("isSensitive"), searchDto.getIsSensitive()));
                }
                if (searchDto.getIsSystem() != null) {
                    predicates.add(cb.equal(root.get("isSystem"), searchDto.getIsSystem()));
                }
                if (searchDto.getApplicableModule() != null && !searchDto.getApplicableModule().isBlank()) {
                    predicates.add(cb.like(root.get("applicableModules"),
                            "%" + searchDto.getApplicableModule() + "%"));
                }
                if (searchDto.getApplicableRole() != null && !searchDto.getApplicableRole().isBlank()) {
                    predicates.add(cb.like(root.get("applicableRoles"),
                            "%" + searchDto.getApplicableRole() + "%"));
                }
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return configRepository.findAll(spec, pageable);
    }

    /**
     * 获取配置树 (分组 → 配置列表)。
     *
     * @return 配置树 [{groupCode, groupName, configs: [...]}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getConfigTree() {
        List<ScrmConfigGroupEntity> groups = groupRepository.findByEnabled(Boolean.TRUE);
        List<ScrmSystemConfigEntity> configs = configRepository.findByEnabled(Boolean.TRUE);
        Map<String, List<ScrmSystemConfigEntity>> byGroup = new LinkedHashMap<>();
        for (ScrmSystemConfigEntity c : configs) {
            byGroup.computeIfAbsent(c.getConfigGroup(), k -> new ArrayList<>()).add(c);
        }
        List<Map<String, Object>> tree = new ArrayList<>();
        for (ScrmConfigGroupEntity g : groups) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("groupCode", g.getGroupCode());
            node.put("groupName", g.getGroupName());
            node.put("displayOrder", g.getDisplayOrder());
            node.put("configs", byGroup.getOrDefault(g.getGroupCode(), new ArrayList<>()));
            tree.add(node);
        }
        // 处理无分组的配置 (归入 GENERAL)
        List<ScrmSystemConfigEntity> orphan = byGroup.getOrDefault(ScrmSystemConfigItemService.DEFAULT_CONFIG_GROUP,
                new ArrayList<>());
        if (!orphan.isEmpty() && groups.stream().noneMatch(
                g -> ScrmSystemConfigItemService.DEFAULT_CONFIG_GROUP.equals(g.getGroupCode()))) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("groupCode", ScrmSystemConfigItemService.DEFAULT_CONFIG_GROUP);
            node.put("groupName", "通用配置");
            node.put("displayOrder", ScrmSystemConfigItemService.DEFAULT_DISPLAY_ORDER);
            node.put("configs", orphan);
            tree.add(node);
        }
        return tree;
    }

    /**
     * 导出配置 (按分组 / 环境过滤, 导出为 key-value 列表)。
     *
     * @param group       配置分组（可空, 空则导出全部分组）
     * @param environment 环境限定（可空, 空则导出全部环境）
     * @return 导出结果 Map {group, environment, count, configs}
     */
    @Transactional
    public Map<String, Object> exportConfigs(String group, String environment) {
        List<ScrmSystemConfigEntity> configs;
        if (group != null && !group.isBlank()) {
            configs = configRepository.findByConfigGroup(group);
            if (environment != null && !environment.isBlank()) {
                configs = configs.stream()
                        .filter(c -> environment.equals(c.getEnvironment()))
                        .toList();
            }
        } else if (environment != null && !environment.isBlank()) {
            configs = configRepository.findByEnvironment(environment);
        } else {
            configs = configRepository.findByEnabled(Boolean.TRUE);
        }
        List<Map<String, Object>> exportList = new ArrayList<>();
        for (ScrmSystemConfigEntity c : configs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("configKey", c.getConfigKey());
            item.put("configValue", c.getConfigValue());
            item.put("configName", c.getConfigName());
            item.put("configGroup", c.getConfigGroup());
            item.put("configType", c.getConfigType());
            item.put("defaultValue", c.getDefaultValue());
            item.put("description", c.getDescription());
            item.put("environment", c.getEnvironment());
            exportList.add(item);
            // 记录导出历史
            itemService.recordHistory(c, null, null, ScrmSystemConfigItemService.CHANGE_TYPE_EXPORT,
                    "导出配置", null);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("group", group);
        result.put("environment", environment);
        result.put("count", exportList.size());
        result.put("configs", exportList);
        result.put("exportedAt", LocalDateTime.now());
        log.info("导出系统配置: group={}, environment={}, count={}", group, environment, exportList.size());
        return result;
    }

    /**
     * 导入配置 (按 overwrite 决定是否覆盖已存在配置)。
     *
     * @param importDto 导入参数
     * @return 导入结果 Map {total, created, updated, skipped, failed, results}
     */
    @Transactional
    public Map<String, Object> importConfigs(ScrmConfigImportDto importDto) {
        if (importDto == null || importDto.getConfigs() == null || importDto.getConfigs().isEmpty()) {
            throw ScrmException.badRequest("导入参数不能为空");
        }
        boolean overwrite = Boolean.TRUE.equals(importDto.getOverwrite());
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        List<Map<String, Object>> results = new ArrayList<>();
        for (ScrmConfigImportDto.ConfigImportItem item : importDto.getConfigs()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("key", item.getKey());
            r.put("value", item.getValue());
            try {
                if (item.getKey() == null || item.getKey().isBlank()) {
                    throw ScrmException.badRequest("配置键不能为空");
                }
                ScrmSystemConfigEntity existing = configRepository
                        .findByConfigKey(item.getKey()).orElse(null);
                if (existing == null) {
                    // 不存在 -> 创建 (使用 STRING 类型, GENERAL 分组, ALL 环境)
                    ScrmSystemConfigEntity entity = new ScrmSystemConfigEntity();
                    entity.setConfigKey(item.getKey());
                    entity.setConfigValue(item.getValue());
                    entity.setConfigName(item.getKey());
                    entity.setConfigGroup(ScrmSystemConfigItemService.DEFAULT_CONFIG_GROUP);
                    entity.setConfigType(ScrmSystemConfigValueService.TYPE_STRING);
                    entity.setEnvironment(ScrmSystemConfigItemService.DEFAULT_ENVIRONMENT);
                    entity.setIsRequired(Boolean.FALSE);
                    entity.setIsReadOnly(Boolean.FALSE);
                    entity.setIsEncrypted(Boolean.FALSE);
                    entity.setIsSensitive(Boolean.FALSE);
                    entity.setIsSystem(Boolean.FALSE);
                    entity.setIsVisible(Boolean.TRUE);
                    entity.setIsSearchable(Boolean.FALSE);
                    entity.setDisplayOrder(ScrmSystemConfigItemService.DEFAULT_DISPLAY_ORDER);
                    entity.setIsOverridable(Boolean.TRUE);
                    entity.setIsCachable(Boolean.TRUE);
                    entity.setCacheTtlSeconds(ScrmSystemConfigItemService.DEFAULT_CACHE_TTL_SECONDS);
                    entity.setChangeCount(ScrmSystemConfigItemService.DEFAULT_CHANGE_COUNT);
                    entity.setEnabled(Boolean.TRUE);
                    entity.setCreatedBy(importDto.getImportedBy());
                    entity = configRepository.save(entity);
                    itemService.recordHistory(entity, null, entity.getConfigValue(),
                            ScrmSystemConfigItemService.CHANGE_TYPE_IMPORT,
                            importDto.getChangeReason(), importDto.getImportedBy());
                    itemService.incrementGroupConfigCount(entity.getConfigGroup());
                    r.put("status", "CREATED");
                    r.put("configId", entity.getId());
                    created++;
                } else if (overwrite) {
                    // 已存在 -> 覆盖
                    String oldValue = existing.getConfigValue();
                    existing.setConfigValue(item.getValue());
                    existing = configRepository.save(existing);
                    itemService.recordHistory(existing, oldValue, item.getValue(),
                            ScrmSystemConfigItemService.CHANGE_TYPE_IMPORT,
                            importDto.getChangeReason(), importDto.getImportedBy());
                    itemService.bumpChangeStat(existing, importDto.getImportedBy());
                    itemService.evictCache(existing.getConfigKey());
                    r.put("status", "UPDATED");
                    r.put("configId", existing.getId());
                    updated++;
                } else {
                    r.put("status", "SKIPPED");
                    skipped++;
                }
            } catch (ScrmException e) {
                r.put("status", "FAILED");
                r.put("error", e.getMessage());
                failed++;
            }
            results.add(r);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", importDto.getConfigs().size());
        summary.put("created", created);
        summary.put("updated", updated);
        summary.put("skipped", skipped);
        summary.put("failed", failed);
        summary.put("results", results);
        summary.put("importedAt", LocalDateTime.now());
        log.info("导入系统配置: total={}, created={}, updated={}, skipped={}, failed={}",
                importDto.getConfigs().size(), created, updated, skipped, failed);
        return summary;
    }
}