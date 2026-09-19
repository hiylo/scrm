/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSystemConfigItemService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmConfigBatchUpdateDto;
import org.hiylo.scrm.dto.ScrmConfigUpdateDto;
import org.hiylo.scrm.dto.ScrmSystemConfigDto;
import org.hiylo.scrm.entity.ScrmConfigHistoryEntity;
import org.hiylo.scrm.entity.ScrmSystemConfigEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmConfigGroupRepository;
import org.hiylo.scrm.repository.ScrmConfigHistoryRepository;
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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SCRM 系统配置项管理服务。
 * <p>
 * 承载配置项管理子域: 配置 CRUD / 取值 / 设值 / 批量更新 / 重置 / 启停 / 验证 / 缓存 / 依赖。
 * 同时托管配置共享常量、变更历史记录、变更统计、分组配置数调整、缓存操作、按主键查找
 * 与参数校验等辅助方法, 供查询 / 组 / 历史 / 统计兄弟类以 package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmSystemConfigItemService {

    /** 默认配置分组 (共享) */
    static final String DEFAULT_CONFIG_GROUP = "GENERAL";
    /** 默认环境限定 (共享) */
    static final String DEFAULT_ENVIRONMENT = "ALL";
    /** 默认缓存 TTL 秒 (共享) */
    static final int DEFAULT_CACHE_TTL_SECONDS = 300;
    /** 默认显示顺序 (共享) */
    static final int DEFAULT_DISPLAY_ORDER = 0;
    /** 默认分组层级 (共享) */
    static final int DEFAULT_GROUP_LEVEL = 1;
    /** 默认变更次数 (共享) */
    static final int DEFAULT_CHANGE_COUNT = 0;
    /** 默认配置数 (共享) */
    static final int DEFAULT_CONFIG_COUNT = 0;
    /** 默认热门配置返回数 (共享) */
    static final int DEFAULT_POPULAR_LIMIT = 10;
    /** 默认近期变更天数 (共享) */
    static final int DEFAULT_RECENT_DAYS = 7;
    /** 默认过期配置判定天数 (共享) */
    static final int DEFAULT_STALE_DAYS = 90;

    /** 合法的配置类型 (共享) */
    static final List<String> VALID_CONFIG_TYPES = List.of(
            "STRING", "INTEGER", "DOUBLE", "BOOLEAN", "JSON", "XML",
            "DATE", "TIME", "DATETIME", "ENUM", "PASSWORD", "ENCRYPTED",
            "FILE", "URL", "EMAIL", "PHONE", "COLOR", "RICH_TEXT");
    /** 合法的环境限定 (共享) */
    static final List<String> VALID_ENVIRONMENTS = List.of("ALL", "DEV", "STAGING", "PRODUCTION");
    /** 合法的 UI 组件 (共享) */
    static final List<String> VALID_UI_COMPONENTS = List.of(
            "INPUT", "TEXTAREA", "SELECT", "MULTI_SELECT", "RADIO", "CHECKBOX",
            "SWITCH", "SLIDER", "DATE_PICKER", "TIME_PICKER", "COLOR_PICKER",
            "FILE_UPLOAD", "RICH_EDITOR", "CODE_EDITOR", "JSON_EDITOR");
    /** 合法的变更类型 (共享) */
    static final List<String> VALID_CHANGE_TYPES = List.of(
            "CREATE", "UPDATE", "DELETE", "ENABLE", "DISABLE", "IMPORT", "EXPORT", "RESET");
    /** 合法的审核状态 (共享) */
    static final List<String> VALID_REVIEW_STATUSES = List.of("PENDING", "APPROVED", "REJECTED");

    /** 变更类型: 创建 (共享) */
    static final String CHANGE_TYPE_CREATE = "CREATE";
    /** 变更类型: 更新 (共享) */
    static final String CHANGE_TYPE_UPDATE = "UPDATE";
    /** 变更类型: 删除 (共享) */
    static final String CHANGE_TYPE_DELETE = "DELETE";
    /** 变更类型: 启用 (共享) */
    static final String CHANGE_TYPE_ENABLE = "ENABLE";
    /** 变更类型: 禁用 (共享) */
    static final String CHANGE_TYPE_DISABLE = "DISABLE";
    /** 变更类型: 导入 (共享) */
    static final String CHANGE_TYPE_IMPORT = "IMPORT";
    /** 变更类型: 导出 (共享) */
    static final String CHANGE_TYPE_EXPORT = "EXPORT";
    /** 变更类型: 重置 (共享) */
    static final String CHANGE_TYPE_RESET = "RESET";

    /** 配置缓存: configKey -> [expireAt, value] */
    private final Map<String, CacheEntry> configValueCache = new ConcurrentHashMap<>();

    /** 系统配置数据访问层 */
    private final ScrmSystemConfigRepository configRepository;
    /** 配置分组数据访问层 */
    private final ScrmConfigGroupRepository groupRepository;
    /** 配置变更历史数据访问层 */
    private final ScrmConfigHistoryRepository historyRepository;
    /** 配置值处理服务 */
    private final ScrmSystemConfigValueService valueService;

    // ============================================================
    // Config 系统配置管理
    // ============================================================

    /**
     * 创建系统配置。
     * <p>校验 configType / environment / uiComponent 合法性与 configKey 唯一性,
     * 若 configValue 非空则按类型校验合法性, 写入归属账号 ID 持久化, 缺省字段填默认值,
     * 并记录 CREATE 历史。</p>
     *
     * @param dto 配置参数
     * @return 创建后的配置
     * @throws ScrmException 参数非法 / 键重复
     */
    @Transactional
    public ScrmSystemConfigEntity createConfig(ScrmSystemConfigDto dto) throws ScrmException {
        validateConfigDto(dto, false);
        if (configRepository.findByConfigKey(dto.getConfigKey()).isPresent()) {
            throw ScrmException.conflict("配置键已存在: " + dto.getConfigKey());
        }
        ScrmSystemConfigEntity entity = new ScrmSystemConfigEntity();
        entity.setConfigKey(dto.getConfigKey());
        entity.setConfigValue(dto.getConfigValue());
        entity.setDefaultValue(dto.getDefaultValue());
        entity.setConfigName(dto.getConfigName());
        entity.setDescription(dto.getDescription());
        entity.setConfigGroup(dto.getConfigGroup() != null && !dto.getConfigGroup().isBlank()
                ? dto.getConfigGroup() : DEFAULT_CONFIG_GROUP);
        entity.setConfigType(dto.getConfigType());
        entity.setDataType(dto.getDataType());
        entity.setEnumOptions(dto.getEnumOptions());
        entity.setValidationRegex(dto.getValidationRegex());
        entity.setValidationMessage(dto.getValidationMessage());
        entity.setMinValue(dto.getMinValue());
        entity.setMaxValue(dto.getMaxValue());
        entity.setMaxLength(dto.getMaxLength());
        entity.setIsRequired(dto.getIsRequired() != null ? dto.getIsRequired() : Boolean.FALSE);
        entity.setIsReadOnly(dto.getIsReadOnly() != null ? dto.getIsReadOnly() : Boolean.FALSE);
        entity.setIsEncrypted(dto.getIsEncrypted() != null ? dto.getIsEncrypted() : Boolean.FALSE);
        entity.setIsSensitive(dto.getIsSensitive() != null ? dto.getIsSensitive() : Boolean.FALSE);
        entity.setIsSystem(dto.getIsSystem() != null ? dto.getIsSystem() : Boolean.FALSE);
        entity.setIsVisible(dto.getIsVisible() != null ? dto.getIsVisible() : Boolean.TRUE);
        entity.setIsSearchable(dto.getIsSearchable() != null ? dto.getIsSearchable() : Boolean.FALSE);
        entity.setDisplayOrder(dto.getDisplayOrder() != null ? dto.getDisplayOrder() : DEFAULT_DISPLAY_ORDER);
        entity.setHelpText(dto.getHelpText());
        entity.setPlaceholder(dto.getPlaceholder());
        entity.setUiComponent(dto.getUiComponent());
        entity.setUiProps(dto.getUiProps());
        entity.setDependsOn(dto.getDependsOn());
        entity.setDependencyCondition(dto.getDependencyCondition());
        entity.setApplicableModules(dto.getApplicableModules());
        entity.setApplicableRoles(dto.getApplicableRoles());
        entity.setEnvironment(dto.getEnvironment() != null && !dto.getEnvironment().isBlank()
                ? dto.getEnvironment() : DEFAULT_ENVIRONMENT);
        entity.setIsOverridable(dto.getIsOverridable() != null ? dto.getIsOverridable() : Boolean.TRUE);
        entity.setIsCachable(dto.getIsCachable() != null ? dto.getIsCachable() : Boolean.TRUE);
        entity.setCacheTtlSeconds(dto.getCacheTtlSeconds() != null
                ? dto.getCacheTtlSeconds() : DEFAULT_CACHE_TTL_SECONDS);
        entity.setChangeCount(DEFAULT_CHANGE_COUNT);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setTags(dto.getTags());
        entity.setCreatedBy(dto.getCreatedBy());
        // 校验配置值合法性 (若非空)
        if (dto.getConfigValue() != null && !dto.getConfigValue().isEmpty()) {
            valueService.validateValue(dto.getConfigValue(), entity.getConfigType(), entity.getValidationRegex(),
                    entity.getMinValue(), entity.getMaxValue(), entity.getMaxLength());
        }
        // 必填校验
        if (Boolean.TRUE.equals(entity.getIsRequired()) && (dto.getConfigValue() == null || dto.getConfigValue().isEmpty())) {
            throw ScrmException.badRequest("配置值不能为空 (必填): " + dto.getConfigKey());
        }
        entity = configRepository.save(entity);
        // 记录创建历史
        recordHistory(entity, null, entity.getConfigValue(), CHANGE_TYPE_CREATE,
                "创建配置", dto.getCreatedBy());
        // 更新所属分组配置数
        incrementGroupConfigCount(entity.getConfigGroup());
        log.info("创建系统配置: id={}, configKey={}, configName={}, group={}",
                entity.getId(), entity.getConfigKey(), entity.getConfigName(), entity.getConfigGroup());
        return entity;
    }

    /**
     * 更新系统配置（字段非空才覆盖）。
     *
     * @param id  配置 ID
     * @param dto 配置参数
     * @return 更新后的配置
     * @throws ScrmException 配置不存在 / 参数非法 / 键重复
     */
    @Transactional
    public ScrmSystemConfigEntity updateConfig(Long id, ScrmSystemConfigDto dto) throws ScrmException {
        ScrmSystemConfigEntity entity = findConfigOrThrow(id);
        validateConfigDto(dto, true);
        if (dto.getConfigKey() != null && !dto.getConfigKey().equals(entity.getConfigKey())) {
            if (configRepository.findByConfigKey(dto.getConfigKey()).isPresent()) {
                throw ScrmException.conflict("配置键已存在: " + dto.getConfigKey());
            }
        }
        // 只读配置不允许修改值
        if (Boolean.TRUE.equals(entity.getIsReadOnly()) && dto.getConfigValue() != null && !Objects.equals(dto.getConfigValue(), entity.getConfigValue())) {
            throw ScrmException.badRequest("只读配置不允许修改值: " + entity.getConfigKey());
        }
        String oldGroup = entity.getConfigGroup();
        String oldValue = entity.getConfigValue();
        if (dto.getConfigKey() != null) entity.setConfigKey(dto.getConfigKey());
        if (dto.getConfigValue() != null) entity.setConfigValue(dto.getConfigValue());
        if (dto.getDefaultValue() != null) entity.setDefaultValue(dto.getDefaultValue());
        if (dto.getConfigName() != null) entity.setConfigName(dto.getConfigName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getConfigGroup() != null && !dto.getConfigGroup().isBlank()) entity.setConfigGroup(dto.getConfigGroup());
        if (dto.getConfigType() != null) entity.setConfigType(dto.getConfigType());
        if (dto.getDataType() != null) entity.setDataType(dto.getDataType());
        if (dto.getEnumOptions() != null) entity.setEnumOptions(dto.getEnumOptions());
        if (dto.getValidationRegex() != null) entity.setValidationRegex(dto.getValidationRegex());
        if (dto.getValidationMessage() != null) entity.setValidationMessage(dto.getValidationMessage());
        if (dto.getMinValue() != null) entity.setMinValue(dto.getMinValue());
        if (dto.getMaxValue() != null) entity.setMaxValue(dto.getMaxValue());
        if (dto.getMaxLength() != null) entity.setMaxLength(dto.getMaxLength());
        if (dto.getIsRequired() != null) entity.setIsRequired(dto.getIsRequired());
        if (dto.getIsReadOnly() != null) entity.setIsReadOnly(dto.getIsReadOnly());
        if (dto.getIsEncrypted() != null) entity.setIsEncrypted(dto.getIsEncrypted());
        if (dto.getIsSensitive() != null) entity.setIsSensitive(dto.getIsSensitive());
        if (dto.getIsSystem() != null) entity.setIsSystem(dto.getIsSystem());
        if (dto.getIsVisible() != null) entity.setIsVisible(dto.getIsVisible());
        if (dto.getIsSearchable() != null) entity.setIsSearchable(dto.getIsSearchable());
        if (dto.getDisplayOrder() != null) entity.setDisplayOrder(dto.getDisplayOrder());
        if (dto.getHelpText() != null) entity.setHelpText(dto.getHelpText());
        if (dto.getPlaceholder() != null) entity.setPlaceholder(dto.getPlaceholder());
        if (dto.getUiComponent() != null) entity.setUiComponent(dto.getUiComponent());
        if (dto.getUiProps() != null) entity.setUiProps(dto.getUiProps());
        if (dto.getDependsOn() != null) entity.setDependsOn(dto.getDependsOn());
        if (dto.getDependencyCondition() != null) entity.setDependencyCondition(dto.getDependencyCondition());
        if (dto.getApplicableModules() != null) entity.setApplicableModules(dto.getApplicableModules());
        if (dto.getApplicableRoles() != null) entity.setApplicableRoles(dto.getApplicableRoles());
        if (dto.getEnvironment() != null && !dto.getEnvironment().isBlank()) entity.setEnvironment(dto.getEnvironment());
        if (dto.getIsOverridable() != null) entity.setIsOverridable(dto.getIsOverridable());
        if (dto.getIsCachable() != null) entity.setIsCachable(dto.getIsCachable());
        if (dto.getCacheTtlSeconds() != null) entity.setCacheTtlSeconds(dto.getCacheTtlSeconds());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        // 校验新值合法性
        if (entity.getConfigValue() != null && !entity.getConfigValue().isEmpty()) {
            valueService.validateValue(entity.getConfigValue(), entity.getConfigType(), entity.getValidationRegex(),
                    entity.getMinValue(), entity.getMaxValue(), entity.getMaxLength());
        }
        // 必填校验
        if (Boolean.TRUE.equals(entity.getIsRequired()) && (entity.getConfigValue() == null || entity.getConfigValue().isEmpty())) {
            throw ScrmException.badRequest("配置值不能为空 (必填): " + entity.getConfigKey());
        }
        entity = configRepository.save(entity);
        // 记录更新历史 (仅当值变化)
        if (dto.getConfigValue() != null && !Objects.equals(oldValue, entity.getConfigValue())) {
            recordHistory(entity, oldValue, entity.getConfigValue(), CHANGE_TYPE_UPDATE,
                    "更新配置", dto.getCreatedBy());
            bumpChangeStat(entity, dto.getCreatedBy());
        }
        // 分组迁移: 调整计数
        if (dto.getConfigGroup() != null && !dto.getConfigGroup().isBlank() && !Objects.equals(oldGroup, entity.getConfigGroup())) {
            decrementGroupConfigCount(oldGroup);
            incrementGroupConfigCount(entity.getConfigGroup());
        }
        // 清除缓存
        evictCache(entity.getConfigKey());
        log.info("更新系统配置: id={}, configKey={}", entity.getId(), entity.getConfigKey());
        return entity;
    }

    /**
     * 删除系统配置 (系统级配置不允许删除)。
     *
     * @param id 配置 ID
     * @throws ScrmException 配置不存在 / 系统级不可删
     */
    @Transactional
    public void deleteConfig(Long id) throws ScrmException {
        ScrmSystemConfigEntity entity = findConfigOrThrow(id);
        if (Boolean.TRUE.equals(entity.getIsSystem())) {
            throw ScrmException.badRequest("系统级配置不允许删除: " + entity.getConfigKey());
        }
        // 记录删除历史
        recordHistory(entity, entity.getConfigValue(), null, CHANGE_TYPE_DELETE,
                "删除配置", null);
        configRepository.delete(entity);
        // 减少所属分组配置数
        decrementGroupConfigCount(entity.getConfigGroup());
        // 清除缓存
        evictCache(entity.getConfigKey());
        log.info("删除系统配置: id={}, configKey={}", id, entity.getConfigKey());
    }

    /**
     * 查询配置详情。
     *
     * @param id 配置 ID
     * @return 配置实体
     * @throws ScrmException 配置不存在
     */
    @Transactional(readOnly = true)
    public ScrmSystemConfigEntity getConfig(Long id) throws ScrmException {
        return findConfigOrThrow(id);
    }

    /**
     * 按配置键查询配置。
     *
     * @param key 配置键
     * @return 配置实体
     * @throws ScrmException 配置不存在
     */
    @Transactional(readOnly = true)
    public ScrmSystemConfigEntity getConfigByKey(String key) throws ScrmException {
        if (key == null || key.isBlank()) {
            throw ScrmException.badRequest("配置键不能为空");
        }
        return configRepository.findByConfigKey(key)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "系统配置不存在: key=" + key));
    }

    /**
     * 分页查询配置, 支持按分组 / 类型 / 环境 / 启用状态 / 关键字过滤。
     *
     * @param group       配置分组过滤（可空）
     * @param type        配置类型过滤（可空）
     * @param environment 环境限定过滤（可空）
     * @param enabled     启用状态过滤（可空）
     * @param keyword     关键字模糊匹配 configKey / configName（可空）
     * @param pageable    分页参数
     * @return 配置分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmSystemConfigEntity> listConfigs(String group, String type, String environment,
                                                     Boolean enabled, String keyword, Pageable pageable) {
        Specification<ScrmSystemConfigEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (group != null && !group.isBlank()) {
                predicates.add(cb.equal(root.get("configGroup"), group));
            }
            if (type != null && !type.isBlank()) {
                predicates.add(cb.equal(root.get("configType"), type));
            }
            if (environment != null && !environment.isBlank()) {
                predicates.add(cb.equal(root.get("environment"), environment));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.or(
                        cb.like(root.get("configKey"), "%" + keyword + "%"),
                        cb.like(root.get("configName"), "%" + keyword + "%")));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return configRepository.findAll(spec, pageable);
    }

    /**
     * 按配置分组分页查询配置 (按 displayOrder ASC, createTime DESC)。
     *
     * @param group    配置分组
     * @param pageable 分页参数
     * @return 配置分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmSystemConfigEntity> getConfigsByGroup(String group, Pageable pageable) {
        if (group == null || group.isBlank()) {
            throw ScrmException.badRequest("配置分组不能为空");
        }
        return configRepository.findByConfigGroup(group, pageable);
    }

    /**
     * 获取配置值 (带缓存)。
     * <p>若配置可缓存且缓存未过期, 直接返回缓存值; 否则从数据库加载, 解析后回填缓存。
     * 配置不存在或未启用时返回 null。</p>
     *
     * @param key 配置键
     * @return 配置值 (不存在或未启用返回 null)
     */
    @Transactional(readOnly = true)
    public String getConfigValue(String key) {
        String cacheKey = buildCacheKey(key);
        CacheEntry cached = configValueCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.value;
        }
        try {
            ScrmSystemConfigEntity entity = getConfigByKey(key);
            if (Boolean.FALSE.equals(entity.getEnabled())) {
                return null;
            }
            String value = entity.getConfigValue();
            if (Boolean.TRUE.equals(entity.getIsCachable())) {
                int ttl = entity.getCacheTtlSeconds() != null ? entity.getCacheTtlSeconds() : DEFAULT_CACHE_TTL_SECONDS;
                configValueCache.put(cacheKey, new CacheEntry(value, ttl));
            }
            return value;
        } catch (ScrmException e) {
            return null;
        }
    }

    /**
     * 获取配置显示值 (解密/格式化)。
     * <p>敏感/密码/加密配置显示为 ********, 其它配置按类型格式化为人类可读形式。</p>
     *
     * @param key 配置键
     * @return 显示值
     * @throws ScrmException 配置不存在
     */
    @Transactional(readOnly = true)
    public String getConfigDisplayValue(String key) throws ScrmException {
        ScrmSystemConfigEntity entity = getConfigByKey(key);
        String value = entity.getConfigValue();
        if (value == null || value.isEmpty()) {
            return value;
        }
        // 敏感/密码/加密配置统一脱敏
        if (Boolean.TRUE.equals(entity.getIsSensitive())
                || ScrmSystemConfigValueService.TYPE_PASSWORD.equals(entity.getConfigType())
                || ScrmSystemConfigValueService.TYPE_ENCRYPTED.equals(entity.getConfigType())) {
            return "********";
        }
        return valueService.formatDisplayValue(value, entity.getConfigType());
    }

    /**
     * 设置配置值 (完整实现: 验证 → 记录历史 → 清除缓存)。
     * <p>校验配置值类型/正则/范围合法性, 通过后写入新值并记录变更历史,
     * 同步清除缓存并更新变更统计。只读配置不允许修改。</p>
     *
     * @param updateDto 更新参数 (configKey + configValue + changeReason)
     * @return 更新后的配置
     * @throws ScrmException 配置不存在 / 只读 / 值非法
     */
    @Transactional
    public ScrmSystemConfigEntity setConfigValue(ScrmConfigUpdateDto updateDto) throws ScrmException {
        if (updateDto == null) {
            throw ScrmException.badRequest("更新参数不能为空");
        }
        if (updateDto.getConfigKey() == null || updateDto.getConfigKey().isBlank()) {
            throw ScrmException.badRequest("配置键不能为空");
        }
        ScrmSystemConfigEntity entity = getConfigByKey(updateDto.getConfigKey());
        if (Boolean.TRUE.equals(entity.getIsReadOnly())) {
            throw ScrmException.badRequest("只读配置不允许修改值: " + entity.getConfigKey());
        }
        String oldValue = entity.getConfigValue();
        String newValue = updateDto.getConfigValue();
        // 必填校验
        if (Boolean.TRUE.equals(entity.getIsRequired()) && (newValue == null || newValue.isEmpty())) {
            throw ScrmException.badRequest("配置值不能为空 (必填): " + entity.getConfigKey());
        }
        // 值未变化直接返回
        if (Objects.equals(oldValue, newValue)) {
            return entity;
        }
        // 完整校验: 类型 + 正则 + 范围
        if (newValue != null && !newValue.isEmpty()) {
            valueService.validateValue(newValue, entity.getConfigType(), entity.getValidationRegex(),
                    entity.getMinValue(), entity.getMaxValue(), entity.getMaxLength());
        }
        entity.setConfigValue(newValue);
        entity = configRepository.save(entity);
        // 记录变更历史
        ScrmConfigHistoryEntity history = recordHistory(entity, oldValue, newValue, CHANGE_TYPE_UPDATE,
                updateDto.getChangeReason(), updateDto.getChangedBy());
        // 补充审计上下文
        if (updateDto.getIpAddress() != null) {
            history.setIpAddress(updateDto.getIpAddress());
        }
        if (updateDto.getUserAgent() != null) {
            history.setUserAgent(updateDto.getUserAgent());
        }
        if (updateDto.getSessionId() != null) {
            history.setSessionId(updateDto.getSessionId());
        }
        historyRepository.save(history);
        // 更新变更统计
        bumpChangeStat(entity, updateDto.getChangedBy());
        // 清除缓存
        evictCache(entity.getConfigKey());
        log.info("设置配置值: configKey={}, oldValue={}, newValue={}, reason={}",
                entity.getConfigKey(), oldValue, newValue, updateDto.getChangeReason());
        return entity;
    }

    /**
     * 批量更新配置值 (逐条校验, 失败条目不影响其它条目)。
     *
     * @param batchDto 批量更新参数
     * @return 批量处理结果 Map {total, success, failed, results}
     */
    @Transactional
    public Map<String, Object> batchUpdate(ScrmConfigBatchUpdateDto batchDto) {
        if (batchDto == null || batchDto.getUpdates() == null || batchDto.getUpdates().isEmpty()) {
            throw ScrmException.badRequest("批量更新参数不能为空");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0;
        for (ScrmConfigBatchUpdateDto.ConfigUpdateItem item : batchDto.getUpdates()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("configKey", item.getConfigKey());
            r.put("configValue", item.getConfigValue());
            try {
                ScrmConfigUpdateDto update = new ScrmConfigUpdateDto();
                update.setConfigKey(item.getConfigKey());
                update.setConfigValue(item.getConfigValue());
                update.setChangeReason(batchDto.getChangeReason());
                update.setChangedBy(batchDto.getChangedBy());
                update.setIpAddress(batchDto.getIpAddress());
                ScrmSystemConfigEntity entity = setConfigValue(update);
                r.put("success", true);
                r.put("configId", entity.getId());
                success++;
            } catch (ScrmException e) {
                r.put("success", false);
                r.put("error", e.getMessage());
            }
            results.add(r);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", batchDto.getUpdates().size());
        summary.put("success", success);
        summary.put("failed", batchDto.getUpdates().size() - success);
        summary.put("results", results);
        log.info("批量更新配置: total={}, success={}", batchDto.getUpdates().size(), success);
        return summary;
    }

    /**
     * 重置配置为默认值。
     *
     * @param key 配置键
     * @return 重置后的配置
     * @throws ScrmException 配置不存在 / 无默认值
     */
    @Transactional
    public ScrmSystemConfigEntity resetConfig(String key) throws ScrmException {
        ScrmSystemConfigEntity entity = getConfigByKey(key);
        String oldValue = entity.getConfigValue();
        String defaultValue = entity.getDefaultValue();
        if (defaultValue == null) {
            throw ScrmException.badRequest("配置无默认值, 无法重置: " + key);
        }
        if (Objects.equals(oldValue, defaultValue)) {
            return entity;
        }
        entity.setConfigValue(defaultValue);
        entity = configRepository.save(entity);
        recordHistory(entity, oldValue, defaultValue, CHANGE_TYPE_RESET,
                "重置为默认值", null);
        bumpChangeStat(entity, null);
        evictCache(entity.getConfigKey());
        log.info("重置配置: configKey={}, defaultValue={}", key, defaultValue);
        return entity;
    }

    /**
     * 启用配置。
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在
     */
    @Transactional
    public ScrmSystemConfigEntity enableConfig(Long id) throws ScrmException {
        ScrmSystemConfigEntity entity = findConfigOrThrow(id);
        boolean wasEnabled = Boolean.TRUE.equals(entity.getEnabled());
        entity.setEnabled(Boolean.TRUE);
        entity = configRepository.save(entity);
        if (!wasEnabled) {
            recordHistory(entity, null, null, CHANGE_TYPE_ENABLE, "启用配置", null);
        }
        evictCache(entity.getConfigKey());
        log.info("启用系统配置: id={}, configKey={}", id, entity.getConfigKey());
        return entity;
    }

    /**
     * 禁用配置。
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在
     */
    @Transactional
    public ScrmSystemConfigEntity disableConfig(Long id) throws ScrmException {
        ScrmSystemConfigEntity entity = findConfigOrThrow(id);
        boolean wasEnabled = Boolean.TRUE.equals(entity.getEnabled());
        entity.setEnabled(Boolean.FALSE);
        entity = configRepository.save(entity);
        if (wasEnabled) {
            recordHistory(entity, null, null, CHANGE_TYPE_DISABLE, "禁用配置", null);
        }
        evictCache(entity.getConfigKey());
        log.info("禁用系统配置: id={}, configKey={}", id, entity.getConfigKey());
        return entity;
    }

    /**
     * 切换配置启用状态。
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在
     */
    @Transactional
    public ScrmSystemConfigEntity toggleConfig(Long id) throws ScrmException {
        ScrmSystemConfigEntity entity = findConfigOrThrow(id);
        Boolean current = entity.getEnabled();
        if (Boolean.TRUE.equals(current)) {
            return disableConfig(id);
        } else {
            return enableConfig(id);
        }
    }

    /**
     * 验证配置值合法性 (类型 + 正则 + 范围, 完整实现)。
     *
     * @param key   配置键
     * @param value 待验证值
     * @return 验证结果 Map {valid, message}
     * @throws ScrmException 配置不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> validateConfigValue(String key, String value) throws ScrmException {
        ScrmSystemConfigEntity entity = getConfigByKey(key);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configKey", key);
        result.put("configType", entity.getConfigType());
        result.put("value", value);
        // 必填校验
        if (Boolean.TRUE.equals(entity.getIsRequired()) && (value == null || value.isEmpty())) {
            result.put("valid", false);
            result.put("message", "配置值不能为空 (必填)");
            return result;
        }
        // 空值视为合法 (非必填)
        if (value == null || value.isEmpty()) {
            result.put("valid", true);
            result.put("message", "配置值为空 (非必填), 视为合法");
            return result;
        }
        try {
            valueService.validateValue(value, entity.getConfigType(), entity.getValidationRegex(),
                    entity.getMinValue(), entity.getMaxValue(), entity.getMaxLength());
            result.put("valid", true);
            result.put("message", "配置值合法");
        } catch (ScrmException e) {
            result.put("valid", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 刷新配置缓存 (清空全部缓存条目)。
     *
     * @return 刷新结果 Map {cleared, remaining}
     */
    public Map<String, Object> refreshCache() {
        int size = configValueCache.size();
        configValueCache.clear();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cleared", size);
        result.put("remaining", configValueCache.size());
        result.put("refreshedAt", LocalDateTime.now());
        log.info("刷新系统配置缓存: cleared={}", size);
        return result;
    }

    /**
     * 查询配置依赖 (哪些配置依赖了指定配置)。
     *
     * @param key 配置键
     * @return 依赖配置列表
     * @throws ScrmException 配置不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmSystemConfigEntity> getConfigDependencies(String key) throws ScrmException {
        getConfigByKey(key); // 确保配置存在
        return configRepository.findByDependsOn(key);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验配置参数。
     *
     * @param dto     配置参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    void validateConfigDto(ScrmSystemConfigDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("配置参数不能为空");
        }
        if (dto.getConfigKey() != null) {
            if (dto.getConfigKey().isBlank()) {
                throw ScrmException.badRequest("配置键不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("配置键不能为空");
        }
        if (dto.getConfigName() != null) {
            if (dto.getConfigName().isBlank()) {
                throw ScrmException.badRequest("配置名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("配置名称不能为空");
        }
        if (dto.getConfigType() != null && !VALID_CONFIG_TYPES.contains(dto.getConfigType())) {
            throw ScrmException.badRequest("配置类型非法: " + dto.getConfigType()
                    + ", 仅支持 " + VALID_CONFIG_TYPES);
        } else if (dto.getConfigType() == null && !partial) {
            throw ScrmException.badRequest("配置类型不能为空");
        }
        if (dto.getEnvironment() != null && !dto.getEnvironment().isBlank() && !VALID_ENVIRONMENTS.contains(dto.getEnvironment())) {
            throw ScrmException.badRequest("环境限定非法: " + dto.getEnvironment()
                    + ", 仅支持 " + VALID_ENVIRONMENTS);
        }
        if (dto.getUiComponent() != null && !dto.getUiComponent().isBlank() && !VALID_UI_COMPONENTS.contains(dto.getUiComponent())) {
            throw ScrmException.badRequest("UI 组件非法: " + dto.getUiComponent()
                    + ", 仅支持 " + VALID_UI_COMPONENTS);
        }
    }

    /**
     * 记录变更历史 (统一入口)。
     *
     * @param entity       配置实体
     * @param oldValue     旧值
     * @param newValue     新值
     * @param changeType   变更类型
     * @param changeReason 变更原因
     * @param changedBy    变更人
     * @return 创建的历史实体
     */
    ScrmConfigHistoryEntity recordHistory(ScrmSystemConfigEntity entity, String oldValue, String newValue,
                                          String changeType, String changeReason, String changedBy) {
        ScrmConfigHistoryEntity history = new ScrmConfigHistoryEntity();
        history.setConfigId(entity.getId());
        history.setConfigKey(entity.getConfigKey());
        history.setConfigName(entity.getConfigName());
        history.setConfigGroup(entity.getConfigGroup());
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        history.setOldDisplayValue(oldValue != null
                ? valueService.truncate(valueService.formatDisplayValue(oldValue, entity.getConfigType()), 2000)
                : null);
        history.setNewDisplayValue(newValue != null
                ? valueService.truncate(valueService.formatDisplayValue(newValue, entity.getConfigType()), 2000)
                : null);
        history.setChangeType(changeType);
        history.setChangeReason(changeReason);
        history.setChangedBy(changedBy != null ? changedBy : "SYSTEM");
        history.setChangedAt(LocalDateTime.now());
        history.setRollbackPossible(CHANGE_TYPE_UPDATE.equals(changeType) || CHANGE_TYPE_RESET.equals(changeType)
                || CHANGE_TYPE_IMPORT.equals(changeType));
        history.setIsRolledBack(Boolean.FALSE);
        history = historyRepository.save(history);
        return history;
    }

    /**
     * 更新配置变更统计 (changeCount / lastChangedAt / lastChangedBy)。
     *
     * @param entity    配置实体
     * @param changedBy 变更人
     */
    void bumpChangeStat(ScrmSystemConfigEntity entity, String changedBy) {
        entity.setChangeCount((entity.getChangeCount() != null ? entity.getChangeCount() : 0) + 1);
        entity.setLastChangedAt(LocalDateTime.now());
        entity.setLastChangedBy(changedBy != null ? changedBy : "SYSTEM");
        configRepository.save(entity);
    }

    /**
     * 增加分组配置数。
     *
     * @param groupCode 分组编码
     */
    void incrementGroupConfigCount(String groupCode) {
        adjustGroupConfigCount(groupCode, 1);
    }

    /**
     * 减少分组配置数。
     *
     * @param groupCode 分组编码
     */
    void decrementGroupConfigCount(String groupCode) {
        adjustGroupConfigCount(groupCode, -1);
    }

    /**
     * 调整分组配置数 (delta 正负)。
     *
     * @param groupCode 分组编码
     * @param delta     变化量
     */
    private void adjustGroupConfigCount(String groupCode, int delta) {
        if (groupCode == null || groupCode.isBlank()) {
            return;
        }
        groupRepository.findByGroupCode(groupCode).ifPresent(g -> {
            int current = g.getConfigCount() != null ? g.getConfigCount() : 0;
            g.setConfigCount(Math.max(0, current + delta));
            g.setLastModifiedAt(LocalDateTime.now());
            groupRepository.save(g);
        });
    }

    /**
     * 构建缓存键。
     *
     * @param key 配置键
     * @return 缓存键
     */
    private String buildCacheKey(String key) {
        return key;
    }

    /**
     * 清除指定配置缓存。
     *
     * @param key 配置键
     */
    void evictCache(String key) {
        if (key == null) {
            return;
        }
        configValueCache.remove(buildCacheKey(key));
    }

    /**
     * 按主键查询配置, 不存在抛异常, 并校验归属账号。
     *
     * @param id 配置 ID
     * @return 配置实体
     * @throws ScrmException 配置不存在
     */
    ScrmSystemConfigEntity findConfigOrThrow(Long id) throws ScrmException {
        ScrmSystemConfigEntity entity = configRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "系统配置不存在: id=" + id));
        return entity;
    }

    /**
     * 缓存条目 (含过期时间)。
     *
     * @author Hsi Chu
     * @since 2026-09-19
     */
    private static final class CacheEntry {
        /** 缓存值 */
        final String value;
        /** 过期时间戳 (毫秒) */
        final long expireAt;

        /**
         * 构造缓存条目。
         *
         * @param value     值
         * @param ttlSeconds TTL 秒
         */
        CacheEntry(String value, int ttlSeconds) {
            this.value = value;
            this.expireAt = System.currentTimeMillis() + ttlSeconds * 1000L;
        }

        /**
         * 是否已过期。
         *
         * @return true 表示已过期
         */
        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}