/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPlatformConfigService.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmPlatformConfigDto;
import org.hiylo.scrm.entity.ScrmPlatformConfigEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmPlatformConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * SCRM 平台配置服务
 * <p>
 * 负责各平台（企微/抖音/快手/小红书等）连接配置的增删改查与连接测试。
 * 每平台类型仅一条配置记录，采用 upsert 语义。
 * 配置优先级：数据库记录 > YAML 文件默认值。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmPlatformConfigService {

    /** 连接状态常量: 已连接 */
    private static final String STATUS_CONNECTED = "CONNECTED";

    /** 连接状态常量: 已断开 */
    private static final String STATUS_DISCONNECTED = "DISCONNECTED";

    /** 连接状态常量: 未知 */
    private static final String STATUS_UNKNOWN = "UNKNOWN";

    /** 敏感字段掩码前缀 */
    private static final String MASK_PREFIX = "****";

    /** 平台配置数据仓库 */
    private final ScrmPlatformConfigRepository platformConfigRepository;

    /**
     * 获取平台配置
     *
     * @param platformType 平台类型
     * @return 平台配置 DTO，不存在返回 null
     */
    @Transactional(readOnly = true)
    public ScrmPlatformConfigDto getConfig(String platformType) {
        return platformConfigRepository.findByPlatformType(platformType)
                .map(this::toDto)
                .orElse(null);
    }

    /**
     * 获取账号下所有平台配置
     *
     * @return 平台配置列表
     */
    @Transactional(readOnly = true)
    public List<ScrmPlatformConfigDto> listConfigs() {
        return platformConfigRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 保存平台配置（upsert 语义）
     * <p>
     * 同一账号同一平台类型仅允许一条配置，存在则更新，不存在则创建。
     * </p>
     *
     * @param platformType 平台类型
     * @param dto          平台配置参数
     * @return 保存后的平台配置
     */
    @Transactional
    public ScrmPlatformConfigDto saveConfig(String platformType, ScrmPlatformConfigDto dto) {
        Optional<ScrmPlatformConfigEntity> existing =
                platformConfigRepository.findByPlatformType(platformType);
        ScrmPlatformConfigEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            applyDtoToEntity(dto, entity);
        } else {
            entity = new ScrmPlatformConfigEntity();
            entity.setPlatformType(platformType);
            entity.setConnectionStatus(STATUS_UNKNOWN);
            applyDtoToEntity(dto, entity);
        }
        entity = platformConfigRepository.save(entity);
        log.info("PLATFORM_CONFIG_CHANGED platformType={} action=save operator={}", platformType, UserContext.getUserId());
        return toDto(entity);
    }

    /**
     * 测试平台连接
     * <p>
     * 对企微平台：调用 gettoken API 验证 corpId/secret 是否正确。
     * 对其他平台：检查配置完整性。
     * </p>
     *
     * @param platformType 平台类型
     * @return 测试结果 DTO（含连接状态）
     */
    @Transactional
    public ScrmPlatformConfigDto testConnection(String platformType) {
        Optional<ScrmPlatformConfigEntity> existing =
                platformConfigRepository.findByPlatformType(platformType);
        if (existing.isEmpty()) {
            throw new ScrmException(ScrmExceptionConstants.SCRM_PLATFORM_CONFIG_NOT_FOUND,
                    "平台配置不存在: platformType=" + platformType);
        }
        ScrmPlatformConfigEntity entity = existing.get();
        boolean connected;
        switch (platformType.toLowerCase()) {
            case "wework":
                connected = testWeworkConnection(entity);
                break;
            default:
                // 其他平台仅检查配置完整性
                connected = entity.getCorpId() != null && !entity.getCorpId().isBlank() && entity.getSecret() != null && !entity.getSecret().isBlank();
                break;
        }
        entity.setConnectionStatus(connected ? STATUS_CONNECTED : STATUS_DISCONNECTED);
        entity.setLastTestedAt(LocalDateTime.now());
        entity = platformConfigRepository.save(entity);
        log.info("平台连接测试:, platformType={}, status={}", platformType,
                entity.getConnectionStatus());
        return toDto(entity);
    }

    /**
     * 删除平台配置
     *
     * @param platformType 平台类型
     */
    @Transactional
    public void deleteConfig(String platformType) {
        platformConfigRepository.deleteByPlatformType(platformType);
        log.info("PLATFORM_CONFIG_CHANGED platformType={} action=delete operator={}", platformType, UserContext.getUserId());
    }

    /**
     * 获取实体（供 WeworkConfigProvider 等内部使用）
     *
     * @param platformType 平台类型
     * @return 平台配置实体，不存在返回 empty
     */
    @Transactional(readOnly = true)
    public Optional<ScrmPlatformConfigEntity> getEntity(String platformType) {
        return platformConfigRepository.findByPlatformType(platformType);
    }

    /**
     * 测试企微连接：调用 gettoken API 验证 corpId/secret
     *
     * @param entity 企微配置实体
     * @return 连接成功返回 true
     */
    private boolean testWeworkConnection(ScrmPlatformConfigEntity entity) {
        if (entity.getCorpId() == null || entity.getCorpId().isBlank()
                || entity.getSecret() == null || entity.getSecret().isBlank()) {
            log.warn("企微连接测试失败: corpId 或 secret 为空");
            return false;
        }
        String baseUrl = entity.getBaseUrl() != null ? entity.getBaseUrl()
                : "https://qyapi.weixin.qq.com/cgi-bin";
        String url = String.format("%s/gettoken?corpid=%s&corpsecret=%s",
                baseUrl, entity.getCorpId(), entity.getSecret());
        try {
            WebClient webClient = WebClient.builder().build();
            JsonNode resp = webClient.get().uri(url).retrieve()
                    .bodyToMono(JsonNode.class).block();
            if (resp != null && resp.has("errcode")) {
                int errcode = resp.get("errcode").asInt();
                if (errcode == 0) {
                    log.info("企微连接测试成功: corpId={}", entity.getCorpId());
                    return true;
                }
                String errmsg = resp.has("errmsg") ? resp.get("errmsg").asText() : "未知错误";
                log.warn("企微连接测试失败: errcode={}, errmsg={}", errcode, errmsg);
                return false;
            }
            log.warn("企微连接测试失败: 响应格式异常");
            return false;
        } catch (Exception e) {
            log.error("企微连接测试异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 将 DTO 字段应用到实体（仅更新非空字段，掩码值跳过以保留现有加密值）
     */
    private void applyDtoToEntity(ScrmPlatformConfigDto dto, ScrmPlatformConfigEntity entity) {
        if (dto.getCorpId() != null) entity.setCorpId(dto.getCorpId());
        if (dto.getAgentId() != null) entity.setAgentId(dto.getAgentId());
        if (dto.getSecret() != null && !isMasked(dto.getSecret())) entity.setSecret(dto.getSecret());
        if (dto.getAesKey() != null && !isMasked(dto.getAesKey())) entity.setAesKey(dto.getAesKey());
        if (dto.getToken() != null && !isMasked(dto.getToken())) entity.setToken(dto.getToken());
        if (dto.getBaseUrl() != null) entity.setBaseUrl(dto.getBaseUrl());
        if (dto.getCallbackUrl() != null) entity.setCallbackUrl(dto.getCallbackUrl());
        entity.setMockMode(dto.isMockMode());
        entity.setRealApiEnabled(dto.isRealApiEnabled());
        entity.setTimeout(dto.getTimeout());
        entity.setRetryCount(dto.getRetryCount());
    }

    /**
     * 实体转 DTO（敏感字段掩码处理）
     */
    private ScrmPlatformConfigDto toDto(ScrmPlatformConfigEntity entity) {
        ScrmPlatformConfigDto dto = new ScrmPlatformConfigDto();
        dto.setId(entity.getId());
        dto.setPlatformType(entity.getPlatformType());
        dto.setCorpId(entity.getCorpId());
        dto.setAgentId(entity.getAgentId());
        dto.setSecret(maskSensitive(entity.getSecret()));
        dto.setAesKey(maskSensitive(entity.getAesKey()));
        dto.setToken(maskSensitive(entity.getToken()));
        dto.setBaseUrl(entity.getBaseUrl());
        dto.setCallbackUrl(entity.getCallbackUrl());
        dto.setMockMode(entity.isMockMode());
        dto.setRealApiEnabled(entity.isRealApiEnabled());
        dto.setTimeout(entity.getTimeout());
        dto.setRetryCount(entity.getRetryCount());
        dto.setConnectionStatus(entity.getConnectionStatus());
        dto.setLastTestedAt(entity.getLastTestedAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 对敏感字段进行掩码：仅保留最后 4 个字符，其余用 **** 替代
     *
     * @param value 原始敏感值
     * @return 掩码后的值，如 "****abcd"；null/短于 4 字符则全掩码
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
     * 判断值是否为掩码值（以 **** 开头）
     *
     * @param value 待判断值
     * @return true 表示为掩码值
     */
    static boolean isMasked(String value) {
        return value != null && value.startsWith(MASK_PREFIX);
    }
}
