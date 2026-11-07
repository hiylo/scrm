/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmAiProviderService.java
 * Date : 2026/11/07 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.AiProviderRegistry;
import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmAiProviderDto;
import org.hiylo.scrm.entity.ScrmAiProviderEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmAiProviderRepository;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AI Provider 配置服务。
 * <p>
 * 统一管理模型接入点（Magpie / Ollama / OpenAI 兼容 / 本地 llama-server）：
 * 增删改查、连通测试、拉取模型列表、设默认、启停。配置全部落库，任何变更
 * 立即刷新 {@link AiProviderRegistry} 路由快照——文本任务与证照 OCR 无需重启
 * 即可切换模型。apiKey 采用「写入不读」语义：回显掩码，永不回显明文。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmAiProviderService {

    /** 连接状态：已连接 */
    private static final String STATUS_CONNECTED = "CONNECTED";

    /** 连接状态：断开 */
    private static final String STATUS_DISCONNECTED = "DISCONNECTED";

    /** 连接状态：未知 */
    private static final String STATUS_UNKNOWN = "UNKNOWN";

    /** 敏感字段掩码前缀 */
    private static final String MASK_PREFIX = "****";

    /** 能力分离符 */
    private static final String CAPABILITY_SEPARATOR = ",";

    /** Provider 数据访问 */
    private final ScrmAiProviderRepository providerRepository;

    /** 运行时路由注册表（配置变更后刷新） */
    private final AiProviderRegistry providerRegistry;

    /** JSON 解析器 */
    private final ObjectMapper objectMapper;

    /**
     * 获取全部 provider（按优先级升序，含掩码密钥）。
     *
     * @return provider 列表
     */
    @Transactional(readOnly = true)
    public List<ScrmAiProviderDto> list() {
        return providerRepository.findAllByOrderByPriorityAsc().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 获取单个 provider。
     *
     * @param id Provider ID
     * @return provider 详情
     */
    @Transactional(readOnly = true)
    public ScrmAiProviderDto get(Long id) {
        return providerRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> ScrmException.notFound("AI Provider 不存在: id=" + id));
    }

    /**
     * 新增 provider。
     *
     * @param dto Provider 参数
     * @return 新增后的 provider
     */
    @Transactional
    public ScrmAiProviderDto create(ScrmAiProviderDto dto) {
        Optional<ScrmAiProviderEntity> dup = providerRepository.findByProviderName(dto.getProviderName());
        if (dup.isPresent()) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "Provider 名称已存在: " + dto.getProviderName());
        }
        ScrmAiProviderEntity entity = new ScrmAiProviderEntity();
        entity.setProviderName(dto.getProviderName());
        entity.setProviderType(normalizeProviderType(dto.getProviderType()));
        entity.setApiBaseUrl(dto.getApiBaseUrl());
        entity.setChatPath(defaultIfBlank(dto.getChatPath(), "/v1/chat/completions"));
        entity.setHealthPath(defaultIfBlank(dto.getHealthPath(), "/v1/models"));
        entity.setApiKey(dto.getApiKey());
        entity.setCapability(defaultIfBlank(dto.getCapability(), "text"));
        entity.setDefaultModel(dto.getDefaultModel());
        entity.setTemperature(defaultIfNull(dto.getTemperature(), 0.7));
        entity.setMaxTokens(defaultIfNull(dto.getMaxTokens(), 500));
        entity.setEnabled(Boolean.TRUE.equals(dto.getEnabled()));
        entity.setPriority(defaultIfNull(dto.getPriority(), 100));
        entity.setDefaultText(Boolean.TRUE.equals(dto.getDefaultText()));
        entity.setDefaultImage(Boolean.TRUE.equals(dto.getDefaultImage()));
        entity.setRemark(dto.getRemark());
        entity.setCreatedBy(UserContext.getUsername());
        entity.setConnectionStatus(STATUS_UNKNOWN);
        ScrmAiProviderEntity saved = providerRepository.save(entity);
        applyDefaultFlags(saved);
        providerRegistry.reload();
        log.info("AI_PROVIDER_CREATED id={} name={} operator={}",
                saved.getId(), saved.getProviderName(), UserContext.getUserId());
        return toDto(saved);
    }

    /**
     * 更新 provider。
     *
     * @param id  Provider ID
     * @param dto Provider 参数（apiKey 掩码值跳过，保留原密钥）
     * @return 更新后的 provider
     */
    @Transactional
    public ScrmAiProviderDto update(Long id, ScrmAiProviderDto dto) {
        ScrmAiProviderEntity entity = providerRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("AI Provider 不存在: id=" + id));
        Optional<ScrmAiProviderEntity> dup = providerRepository.findByProviderName(dto.getProviderName());
        if (dup.isPresent() && !dup.get().getId().equals(id)) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "Provider 名称已存在: " + dto.getProviderName());
        }
        applyDtoToEntity(dto, entity);
        ScrmAiProviderEntity saved = providerRepository.save(entity);
        applyDefaultFlags(saved);
        providerRegistry.reload();
        log.info("AI_PROVIDER_UPDATED id={} name={} operator={}",
                saved.getId(), saved.getProviderName(), UserContext.getUserId());
        return toDto(saved);
    }

    /**
     * 删除 provider。
     *
     * @param id Provider ID
     */
    @Transactional
    public void delete(Long id) {
        ScrmAiProviderEntity entity = providerRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("AI Provider 不存在: id=" + id));
        providerRepository.delete(entity);
        providerRegistry.reload();
        log.info("AI_PROVIDER_DELETED id={} name={} operator={}",
                id, entity.getProviderName(), UserContext.getUserId());
    }

    /**
     * 测试 provider 连通性（GET {base}{healthPath}，带 Bearer 鉴权）。
     *
     * @param id Provider ID
     * @return 更新连接状态后的 provider
     */
    @Transactional
    public ScrmAiProviderDto testConnection(Long id) {
        ScrmAiProviderEntity entity = providerRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("AI Provider 不存在: id=" + id));
        boolean connected = probe(entity);
        entity.setConnectionStatus(connected ? STATUS_CONNECTED : STATUS_DISCONNECTED);
        entity.setLastTestedAt(LocalDateTime.now());
        ScrmAiProviderEntity saved = providerRepository.save(entity);
        log.info("AI_PROVIDER_TEST id={} name={} status={}", id, saved.getProviderName(),
                saved.getConnectionStatus());
        return toDto(saved);
    }

    /**
     * 从 provider 拉取模型列表（GET {base}{healthPath}，解析 data[].id 与能力），
     * 结果缓存到 provider.modelsCache 供管理端展示。
     *
     * @param id Provider ID
     * @return 模型清单（含能力标记）
     */
    @Transactional
    public List<ScrmAiProviderDto.AiModelInfo> fetchModels(Long id) {
        ScrmAiProviderEntity entity = providerRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("AI Provider 不存在: id=" + id));
        List<ScrmAiProviderDto.AiModelInfo> models = fetchModelList(entity);
        try {
            entity.setModelsCache(objectMapper.writeValueAsString(models));
        } catch (Exception e) {
            log.warn("模型清单缓存序列化失败: id={}, err={}", id, e.getMessage());
        }
        providerRepository.save(entity);
        return models;
    }

    /**
     * 设默认文本 provider（全局仅一个，其余自动清除标记）。
     *
     * @param id Provider ID
     * @return 更新后的 provider
     */
    @Transactional
    public ScrmAiProviderDto setDefaultText(Long id) {
        ScrmAiProviderEntity entity = mustContainCapability(
                providerRepository.findById(id)
                        .orElseThrow(() -> ScrmException.notFound("AI Provider 不存在: id=" + id)),
                "text", "设置默认文本 Provider 需要 text 能力");
        List<ScrmAiProviderEntity> all = providerRepository.findAll();
        for (ScrmAiProviderEntity p : all) {
            if (!p.getId().equals(id) && Boolean.TRUE.equals(p.getDefaultText())) {
                p.setDefaultText(false);
                providerRepository.save(p);
            }
        }
        entity.setDefaultText(true);
        scrmProviderSave(entity);
        providerRegistry.reload();
        return toDto(entity);
    }

    /**
     * 设默认多模态 provider（全局仅一个，其余自动清除标记）。
     *
     * @param id Provider ID
     * @return 更新后的 provider
     */
    @Transactional
    public ScrmAiProviderDto setDefaultImage(Long id) {
        ScrmAiProviderEntity entity = mustContainCapability(
                providerRepository.findById(id)
                        .orElseThrow(() -> ScrmException.notFound("AI Provider 不存在: id=" + id)),
                "image", "设置默认多模态 Provider 需要 image 能力");
        List<ScrmAiProviderEntity> all = providerRepository.findAll();
        for (ScrmAiProviderEntity p : all) {
            if (!p.getId().equals(id) && Boolean.TRUE.equals(p.getDefaultImage())) {
                p.setDefaultImage(false);
                providerRepository.save(p);
            }
        }
        entity.setDefaultImage(true);
        scrmProviderSave(entity);
        providerRegistry.reload();
        return toDto(entity);
    }

    /**
     * 启停 provider。
     *
     * @param id      Provider ID
     * @param enabled 是否启用
     * @return 更新后的 provider
     */
    @Transactional
    public ScrmAiProviderDto setEnabled(Long id, boolean enabled) {
        ScrmAiProviderEntity entity = providerRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("AI Provider 不存在: id=" + id));
        entity.setEnabled(enabled);
        scrmProviderSave(entity);
        providerRegistry.reload();
        return toDto(entity);
    }

    /**
     * 探测 provider 连通性。
     *
     * @param entity Provider 实体
     * @return true 表示连通
     */
    private boolean probe(ScrmAiProviderEntity entity) {
        String url = buildBase(entity) + entity.getHealthPath();
        try {
            WebClient client = WebClient.builder().build();
            String raw = client.get().uri(url).headers(h -> {
                if (entity.getApiKey() != null && !entity.getApiKey().isBlank()) {
                    h.setBearerAuth(entity.getApiKey());
                }
            }).retrieve().bodyToMono(String.class).block();
            if (raw == null || raw.isBlank()) {
                log.warn("AI Provider 探测失败, 响应为空: {} {}", entity.getProviderName(), url);
                return false;
            }
            JsonNode node = objectMapper.readTree(raw);
            if (node == null || node.isMissingNode()) {
                log.warn("AI Provider 探测失败, 响应无法解析: {} {}", entity.getProviderName(), url);
                return false;
            }
            log.info("AI Provider 探测成功: {} {}", entity.getProviderName(), url);
            return true;
        } catch (Exception e) {
            log.warn("AI Provider 探测失败: {}, url={}, err={}",
                    entity.getProviderName(), url, e.getMessage());
            return false;
        }
    }

    /**
     * 拉取模型列表并解析能力。
     *
     * @param entity Provider 实体
     * @return 模型清单
     */
    private List<ScrmAiProviderDto.AiModelInfo> fetchModelList(ScrmAiProviderEntity entity) {
        List<ScrmAiProviderDto.AiModelInfo> result = new ArrayList<>();
        String url = buildBase(entity) + entity.getHealthPath();
        try {
            WebClient client = WebClient.builder().build();
            String raw = client.get().uri(url)
                    .headers(h -> {
                        if (entity.getApiKey() != null && !entity.getApiKey().isBlank()) {
                            h.setBearerAuth(entity.getApiKey());
                        }
                    })
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode root = objectMapper.readTree(raw == null ? "{}" : raw);
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode m : data) {
                    ScrmAiProviderDto.AiModelInfo info = new ScrmAiProviderDto.AiModelInfo();
                    info.setName(m.path("id").asText(""));
                    if (info.getName().isBlank()) {
                        info.setName(m.path("name").asText(""));
                    }
                    JsonNode modalities = m.path("modalities");
                    if (modalities.isObject() && modalities.has("input")) {
                        for (JsonNode cap : modalities.path("input")) {
                            String c = cap.asText().toLowerCase(Locale.ROOT);
                            if (c.contains("image")) {
                                info.setImage(true);
                            } else if (c.contains("text") || c.contains("audio") || c.contains("video")) {
                                info.setText(true);
                            }
                        }
                    } else {
                        // 无模态描述时按 OpenAI 兼容默认视为文本模型
                        info.setText(true);
                    }
                    result.add(info);
                }
            }
        } catch (Exception e) {
            log.warn("AI Provider 拉取模型失败: {}, url={}, err={}",
                    entity.getProviderName(), url, e.getMessage());
        }
        return result;
    }

    /**
     * 构造探测基础地址（保留原始地址，路径由各字段拼接）。
     *
     * @param entity Provider 实体
     * @return 基础地址（去除尾部斜杠）
     */
    private String buildBase(ScrmAiProviderEntity entity) {
        String base = entity.getApiBaseUrl() == null ? "" : entity.getApiBaseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    /**
     * 应用默认标记冲突处理：新设默认时清空其余 provider 的同类默认标记。
     *
     * @param saved 已保存的 provider
     */
    private void applyDefaultFlags(ScrmAiProviderEntity saved) {
        if (Boolean.TRUE.equals(saved.getDefaultText())
                || Boolean.TRUE.equals(saved.getDefaultImage())) {
            List<ScrmAiProviderEntity> all = providerRepository.findAll();
            for (ScrmAiProviderEntity p : all) {
                if (p.getId().equals(saved.getId())) {
                    continue;
                }
                boolean dirty = false;
                if (Boolean.TRUE.equals(saved.getDefaultText())
                        && Boolean.TRUE.equals(p.getDefaultText())) {
                    p.setDefaultText(false);
                    dirty = true;
                }
                if (Boolean.TRUE.equals(saved.getDefaultImage())
                        && Boolean.TRUE.equals(p.getDefaultImage())) {
                    p.setDefaultImage(false);
                    dirty = true;
                }
                if (dirty) {
                    providerRepository.save(p);
                }
            }
        }
    }

    /**
     * 应用 DTO 到实体（apiKey 掩码值跳过保留原密钥）。
     *
     * @param dto    DTO
     * @param entity 实体
     */
    private void applyDtoToEntity(ScrmAiProviderDto dto, ScrmAiProviderEntity entity) {
        if (dto.getProviderName() != null) {
            entity.setProviderName(dto.getProviderName());
        }
        if (dto.getProviderType() != null) {
            entity.setProviderType(normalizeProviderType(dto.getProviderType()));
        }
        if (dto.getApiBaseUrl() != null) {
            entity.setApiBaseUrl(dto.getApiBaseUrl());
        }
        if (dto.getChatPath() != null) {
            entity.setChatPath(defaultIfBlank(dto.getChatPath(), "/v1/chat/completions"));
        }
        if (dto.getHealthPath() != null) {
            entity.setHealthPath(defaultIfBlank(dto.getHealthPath(), "/v1/models"));
        }
        if (dto.getApiKey() != null && !isMasked(dto.getApiKey())) {
            entity.setApiKey(dto.getApiKey());
        }
        if (dto.getCapability() != null) {
            entity.setCapability(defaultIfBlank(dto.getCapability(), "text"));
        }
        if (dto.getDefaultModel() != null) {
            entity.setDefaultModel(dto.getDefaultModel());
        }
        if (dto.getTemperature() != null) {
            entity.setTemperature(dto.getTemperature());
        }
        if (dto.getMaxTokens() != null) {
            entity.setMaxTokens(dto.getMaxTokens());
        }
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        }
        if (dto.getPriority() != null) {
            entity.setPriority(dto.getPriority());
        }
        if (dto.getDefaultText() != null) {
            entity.setDefaultText(dto.getDefaultText());
        }
        if (dto.getDefaultImage() != null) {
            entity.setDefaultImage(dto.getDefaultImage());
        }
        if (dto.getRemark() != null) {
            entity.setRemark(dto.getRemark());
        }
    }

    /**
     * 实体转 DTO（密钥掩码、模型清单解析）。
     *
     * @param entity 实体
     * @return DTO
     */
    private ScrmAiProviderDto toDto(ScrmAiProviderEntity entity) {
        ScrmAiProviderDto dto = new ScrmAiProviderDto();
        dto.setId(entity.getId());
        dto.setProviderName(entity.getProviderName());
        dto.setProviderType(entity.getProviderType());
        dto.setApiBaseUrl(entity.getApiBaseUrl());
        dto.setChatPath(entity.getChatPath());
        dto.setHealthPath(entity.getHealthPath());
        dto.setApiKey(maskSensitive(entity.getApiKey()));
        dto.setHasApiKey(entity.getApiKey() != null && !entity.getApiKey().isBlank());
        dto.setCapability(entity.getCapability());
        dto.setDefaultModel(entity.getDefaultModel());
        dto.setTemperature(entity.getTemperature());
        dto.setMaxTokens(entity.getMaxTokens());
        dto.setEnabled(entity.getEnabled());
        dto.setDefaultText(entity.getDefaultText());
        dto.setDefaultImage(entity.getDefaultImage());
        dto.setPriority(entity.getPriority());
        dto.setConnectionStatus(entity.getConnectionStatus());
        dto.setLastTestedAt(entity.getLastTestedAt());
        dto.setModels(parseModelsCache(entity.getModelsCache()));
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setRemark(entity.getRemark());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 解析模型清单缓存 JSON。
     *
     * @param cache 缓存 JSON
     * @return 模型清单，解析失败返回空列表
     */
    private List<ScrmAiProviderDto.AiModelInfo> parseModelsCache(String cache) {
        if (cache == null || cache.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(cache,
                    new TypeReference<List<ScrmAiProviderDto.AiModelInfo>>() {
                    });
        } catch (Exception e) {
            log.warn("模型清单缓存解析失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 掩码敏感值（仅保留后 4 位）。
     *
     * @param value 原始值
     * @return 掩码后的值
     */
    private String maskSensitive(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.length() <= 4) {
            return MASK_PREFIX;
        }
        return MASK_PREFIX + value.substring(value.length() - 4);
    }

    /**
     * 判断是否为掩码值。
     *
     * @param value 值
     * @return true 表示掩码值
     */
    static boolean isMasked(String value) {
        return value != null && value.startsWith(MASK_PREFIX);
    }

    /**
     * 规范化 provider 类型（去空格转大写）。
     *
     * @param type 原始类型
     * @return 规范化类型
     */
    private String normalizeProviderType(String type) {
        return type == null ? "OPENAI_LLM" : type.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 空值回退。
     *
     * @param value  值
     * @param fallback 默认值
     * @return 非空值或默认值
     */
    private String defaultIfBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    /**
     * null 回退。
     *
     * @param value    值
     * @param fallback 默认值
     * @return 非空值或默认值
     */
    private <T> T defaultIfNull(T value, T fallback) {
        return value == null ? fallback : value;
    }

    /**
     * 校验 provider 具备能力，否则抛错。
     *
     * @param entity     Provider 实体
     * @param capability 能力名
     * @param message    错误消息
     * @return 实体
     */
    private ScrmAiProviderEntity mustContainCapability(ScrmAiProviderEntity entity,
                                                       String capability, String message) {
        if (!AiProviderRegistry.hasCapability(entity, capability)) {
            throw ScrmException.badRequest(message + ": " + entity.getProviderName());
        }
        return entity;
    }

    /**
     * 保存实体（更新 updateTime）。
     *
     * @param entity 实体
     * @return 保存后的实体
     */
    private ScrmAiProviderEntity scrmProviderSave(ScrmAiProviderEntity entity) {
        return providerRepository.save(entity);
    }
}