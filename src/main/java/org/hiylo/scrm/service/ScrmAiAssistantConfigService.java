/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiAssistantConfigService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

package org.hiylo.scrm.service;


import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.hiylo.scrm.dto.ScrmAiAssistantConfigDto;
import org.hiylo.scrm.entity.ScrmAiAssistantConfigEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmAiAssistantConfigRepository;

/**
 * AI 助手配置管理兄弟服务。
 * <p>
 * 承载 AI 助手配置的增删改查、启停与默认配置管理, 以及配置参数校验。
 * 作为 {@link ScrmAiAssistantService} 的配置子域拆分产物, 由门面注入并委托调用。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmAiAssistantConfigService {

    // ==================== 默认值常量 ====================

    /** 默认启用状态 */
    static final boolean DEFAULT_ENABLED = true;

    /** 默认是否为默认配置 */
    private static final boolean DEFAULT_IS_DEFAULT = false;

    /** 默认模型 */
    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    /** 默认温度 */
    private static final double DEFAULT_TEMPERATURE = 0.7;

    /** 默认最大 token 数 */
    private static final int DEFAULT_MAX_TOKENS = 1000;

    /** 默认请求次数初值 */
    private static final int DEFAULT_REQUEST_COUNT = 0;

    // ==================== 枚举值常量 ====================

    /** 服务提供方: OPENAI */
    private static final String PROVIDER_OPENAI = "OPENAI";
    /** 服务提供方: AZURE */
    private static final String PROVIDER_AZURE = "AZURE";
    /** 服务提供方: LOCAL */
    private static final String PROVIDER_LOCAL = "LOCAL";
    /** 服务提供方: ZHIPU */
    private static final String PROVIDER_ZHIPU = "ZHIPU";
    /** 服务提供方: QWEN */
    private static final String PROVIDER_QWEN = "QWEN";

    /** 合法的服务提供方 */
    private static final List<String> VALID_PROVIDERS = List.of(
            PROVIDER_OPENAI, PROVIDER_AZURE, PROVIDER_LOCAL, PROVIDER_ZHIPU, PROVIDER_QWEN);

    // ==================== 依赖注入 ====================

    /** AI 助手配置数据访问层 */
    private final ScrmAiAssistantConfigRepository configRepository;

    /**
     * 创建 AI 助手配置。
     * <p>校验 provider 合法性后写入归属账号 ID 持久化, model / temperature / maxTokens /
     * enabled / isDefault 缺省时填默认值。设为默认配置时清除其他默认标记。</p>
     *
     * @param dto 配置参数
     * @return 创建后的配置
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmAiAssistantConfigEntity createConfig(ScrmAiAssistantConfigDto dto) throws ScrmException {
        validateConfigDto(dto, false);
        boolean isDefault = dto.getIsDefault() != null ? dto.getIsDefault() : DEFAULT_IS_DEFAULT;
        if (isDefault) {
            configRepository.clearDefault();
        }
        ScrmAiAssistantConfigEntity entity = new ScrmAiAssistantConfigEntity();
        entity.setConfigName(dto.getConfigName());
        entity.setProvider(dto.getProvider());
        entity.setModel(dto.getModel() != null && !dto.getModel().isBlank() ? dto.getModel() : DEFAULT_MODEL);
        entity.setApiKey(dto.getApiKey());
        entity.setApiEndpoint(dto.getApiEndpoint());
        entity.setSystemPrompt(dto.getSystemPrompt());
        entity.setTemperature(dto.getTemperature() != null ? dto.getTemperature() : DEFAULT_TEMPERATURE);
        entity.setMaxTokens(dto.getMaxTokens() != null ? dto.getMaxTokens() : DEFAULT_MAX_TOKENS);
        entity.setKnowledgeBaseId(dto.getKnowledgeBaseId());
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : DEFAULT_ENABLED);
        entity.setIsDefault(isDefault);
        entity.setRequestCount(DEFAULT_REQUEST_COUNT);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = configRepository.save(entity);
        log.info("创建 AI 助手配置: id={}, configName={}, provider={}",
                entity.getId(), entity.getConfigName(), entity.getProvider());
        return entity;
    }

    /**
     * 更新 AI 助手配置（字段非空才覆盖）。
     *
     * @param id  配置 ID
     * @param dto 配置参数
     * @return 更新后的配置
     * @throws ScrmException 配置不存在 / 参数非法
     */
    @Transactional
    public ScrmAiAssistantConfigEntity updateConfig(Long id, ScrmAiAssistantConfigDto dto) throws ScrmException {
        ScrmAiAssistantConfigEntity entity = findConfigOrThrow(id);
        validateConfigDto(dto, true);
        if (dto.getConfigName() != null) entity.setConfigName(dto.getConfigName());
        if (dto.getProvider() != null) entity.setProvider(dto.getProvider());
        if (dto.getModel() != null) entity.setModel(dto.getModel());
        if (dto.getApiKey() != null) entity.setApiKey(dto.getApiKey());
        if (dto.getApiEndpoint() != null) entity.setApiEndpoint(dto.getApiEndpoint());
        if (dto.getSystemPrompt() != null) entity.setSystemPrompt(dto.getSystemPrompt());
        if (dto.getTemperature() != null) entity.setTemperature(dto.getTemperature());
        if (dto.getMaxTokens() != null) entity.setMaxTokens(dto.getMaxTokens());
        if (dto.getKnowledgeBaseId() != null) entity.setKnowledgeBaseId(dto.getKnowledgeBaseId());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getIsDefault() != null && dto.getIsDefault()) {
            configRepository.clearDefault();
            entity.setIsDefault(true);
        }
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = configRepository.save(entity);
        log.info("更新 AI 助手配置: id={}, configName={}", entity.getId(), entity.getConfigName());
        return entity;
    }

    /**
     * 删除 AI 助手配置。
     *
     * @param id 配置 ID
     * @throws ScrmException 配置不存在
     */
    @Transactional
    public void deleteConfig(Long id) throws ScrmException {
        ScrmAiAssistantConfigEntity entity = findConfigOrThrow(id);
        configRepository.delete(entity);
        log.info("删除 AI 助手配置: id={}, configName={}", id, entity.getConfigName());
    }

    /**
     * 查询配置详情。
     *
     * @param id 配置 ID
     * @return 配置实体
     * @throws ScrmException 配置不存在
     */
    @Transactional(readOnly = true)
    public ScrmAiAssistantConfigEntity getConfig(Long id) throws ScrmException {
        return findConfigOrThrow(id);
    }

    /**
     * 分页查询配置, 支持按服务提供方与启用状态过滤。
     *
     * @param provider 服务提供方过滤（可空）: OPENAI / AZURE / LOCAL / ZHIPU / QWEN
     * @param enabled  启用状态过滤（可空）
     * @param pageable 分页参数
     * @return 配置分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmAiAssistantConfigEntity> listConfigs(String provider, Boolean enabled, Pageable pageable) {
        Specification<ScrmAiAssistantConfigEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (provider != null && !provider.isBlank()) {
                predicates.add(cb.equal(root.get("provider"), provider));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return configRepository.findAll(spec, pageable);
    }

    /**
     * 设置为默认配置。
     * <p>清除其他默认标记后, 将当前配置置为默认。</p>
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在
     */
    @Transactional
    public ScrmAiAssistantConfigEntity setDefaultConfig(Long id) throws ScrmException {
        ScrmAiAssistantConfigEntity entity = findConfigOrThrow(id);
        configRepository.clearDefault();
        entity.setIsDefault(true);
        entity = configRepository.save(entity);
        log.info("设置 AI 助手默认配置: id={}, configName={}", id, entity.getConfigName());
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
    public ScrmAiAssistantConfigEntity enableConfig(Long id) throws ScrmException {
        ScrmAiAssistantConfigEntity entity = findConfigOrThrow(id);
        entity.setEnabled(true);
        entity = configRepository.save(entity);
        log.info("启用 AI 助手配置: id={}, configName={}", id, entity.getConfigName());
        return entity;
    }

    /**
     * 禁用配置。
     * <p>禁用默认配置时会同时清除默认标记, 避免下次默认配置查询失败。</p>
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在
     */
    @Transactional
    public ScrmAiAssistantConfigEntity disableConfig(Long id) throws ScrmException {
        ScrmAiAssistantConfigEntity entity = findConfigOrThrow(id);
        entity.setEnabled(false);
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            entity.setIsDefault(false);
        }
        entity = configRepository.save(entity);
        log.info("禁用 AI 助手配置: id={}, configName={}", id, entity.getConfigName());
        return entity;
    }

    /**
     * 校验 AI 助手配置参数。
     *
     * @param dto     配置参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateConfigDto(ScrmAiAssistantConfigDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("配置参数不能为空");
        }
        if (dto.getConfigName() != null) {
            if (dto.getConfigName().isBlank()) {
                throw ScrmException.badRequest("配置名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("配置名称不能为空");
        }
        if (dto.getProvider() != null) {
            if (!VALID_PROVIDERS.contains(dto.getProvider())) {
                throw ScrmException.badRequest(
                        "服务提供方非法: " + dto.getProvider() + ", 仅支持 " + VALID_PROVIDERS);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("服务提供方不能为空");
        }
        if (dto.getTemperature() != null && (dto.getTemperature() < 0 || dto.getTemperature() > 2)) {
            throw ScrmException.badRequest("温度参数需在 0-2 之间: " + dto.getTemperature());
        }
        if (dto.getMaxTokens() != null && dto.getMaxTokens() <= 0) {
            throw ScrmException.badRequest("最大 token 数必须大于 0: " + dto.getMaxTokens());
        }
    }

    /**
     * 按主键查询配置, 不存在抛异常, 并校验账号归属。
     *
     * @param id 配置 ID
     * @return 配置实体
     * @throws ScrmException 配置不存在
     */
    private ScrmAiAssistantConfigEntity findConfigOrThrow(Long id) throws ScrmException {
        ScrmAiAssistantConfigEntity entity = configRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "AI 助手配置不存在: id=" + id));
        return entity;
    }

}
