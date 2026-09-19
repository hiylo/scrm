/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPlatformConfigServiceTest.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmPlatformConfigDto;
import org.hiylo.scrm.entity.ScrmPlatformConfigEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmPlatformConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmPlatformConfigService 单元测试
 * <p>
 * 验证平台配置的获取、保存 (upsert) 与连接测试逻辑,
 * 使用 Mockito 隔离 Repository。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmPlatformConfigService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmPlatformConfigServiceTest {

    /** 企微平台类型标识 */
    private static final String PLATFORM_TYPE_WEWORK = "wework";

    /** 平台配置仓库 Mock */
    @Mock
    private ScrmPlatformConfigRepository platformConfigRepository;

    /** 被测平台配置服务实例 */
    @InjectMocks
    private ScrmPlatformConfigService configService;

    // ==================== getConfig ====================

    @Test
    @DisplayName("getConfig_exists: 配置存在时返回 DTO")
    void getConfig_exists() {
        // ===== Given =====
        ScrmPlatformConfigEntity entity = buildEntity(1L, PLATFORM_TYPE_WEWORK);
        entity.setCorpId("ww1234567890abcdef");
        entity.setSecret("test-secret");
        entity.setConnectionStatus("CONNECTED");
        when(platformConfigRepository.findByPlatformType(PLATFORM_TYPE_WEWORK))
                .thenReturn(Optional.of(entity));

        // ===== When =====
        ScrmPlatformConfigDto result = configService.getConfig(PLATFORM_TYPE_WEWORK);

        // ===== Then =====
        assertThat(result).isNotNull();
        assertThat(result.getCorpId()).isEqualTo("ww1234567890abcdef");
        // secret 在 DTO 返回时会被掩码处理（仅显示后 4 位）
        assertThat(result.getSecret()).isEqualTo("****cret");
        assertThat(result.getConnectionStatus()).isEqualTo("CONNECTED");
    }

    @Test
    @DisplayName("getConfig_notExists: 配置不存在时返回 null")
    void getConfig_notExists() {
        when(platformConfigRepository.findByPlatformType(PLATFORM_TYPE_WEWORK))
                .thenReturn(Optional.empty());

        ScrmPlatformConfigDto result = configService.getConfig(PLATFORM_TYPE_WEWORK);

        assertThat(result).isNull();
    }

    // ==================== saveConfig ====================

    @Test
    @DisplayName("saveConfig_create: 配置不存在时创建新记录")
    void saveConfig_create() {
        // ===== Given =====
        ScrmPlatformConfigDto dto = new ScrmPlatformConfigDto();
        dto.setCorpId("ww_new_corp");
        dto.setSecret("new-secret");
        dto.setToken("new-token");

        when(platformConfigRepository.findByPlatformType(PLATFORM_TYPE_WEWORK))
                .thenReturn(Optional.empty());
        when(platformConfigRepository.save(any(ScrmPlatformConfigEntity.class)))
                .thenAnswer(invocation -> {
                    ScrmPlatformConfigEntity e = invocation.getArgument(0);
                    e.setId(999L);
                    return e;
                });

        // ===== When =====
        ScrmPlatformConfigDto result = configService.saveConfig(PLATFORM_TYPE_WEWORK, dto);

        // ===== Then =====
        assertThat(result).isNotNull();
        assertThat(result.getCorpId()).isEqualTo("ww_new_corp");

        ArgumentCaptor<ScrmPlatformConfigEntity> captor = ArgumentCaptor.forClass(ScrmPlatformConfigEntity.class);
        verify(platformConfigRepository, times(1)).save(captor.capture());
        ScrmPlatformConfigEntity saved = captor.getValue();
        assertThat(saved.getPlatformType()).isEqualTo(PLATFORM_TYPE_WEWORK);
        assertThat(saved.getCorpId()).isEqualTo("ww_new_corp");
        assertThat(saved.getConnectionStatus()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("saveConfig_update: 配置已存在时更新记录")
    void saveConfig_update() {
        // ===== Given =====
        ScrmPlatformConfigEntity existing = buildEntity(1L, PLATFORM_TYPE_WEWORK);
        existing.setCorpId("ww_old_corp");
        existing.setSecret("old-secret");
        existing.setConnectionStatus("CONNECTED");

        ScrmPlatformConfigDto dto = new ScrmPlatformConfigDto();
        dto.setCorpId("ww_updated_corp");
        dto.setSecret("updated-secret");

        when(platformConfigRepository.findByPlatformType(PLATFORM_TYPE_WEWORK))
                .thenReturn(Optional.of(existing));
        when(platformConfigRepository.save(any(ScrmPlatformConfigEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // ===== When =====
        ScrmPlatformConfigDto result = configService.saveConfig(PLATFORM_TYPE_WEWORK, dto);

        // ===== Then =====
        assertThat(result).isNotNull();
        assertThat(result.getCorpId()).isEqualTo("ww_updated_corp");

        ArgumentCaptor<ScrmPlatformConfigEntity> captor = ArgumentCaptor.forClass(ScrmPlatformConfigEntity.class);
        verify(platformConfigRepository, times(1)).save(captor.capture());
        ScrmPlatformConfigEntity saved = captor.getValue();
        // 更新时保留原 connectionStatus, 仅更新 DTO 中的字段
        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getCorpId()).isEqualTo("ww_updated_corp");
        assertThat(saved.getSecret()).isEqualTo("updated-secret");
    }

    // ==================== testConnection ====================

    @Test
    @DisplayName("testConnection_notFound: 配置不存在时抛 ScrmException")
    void testConnection_notFound() {
        when(platformConfigRepository.findByPlatformType(PLATFORM_TYPE_WEWORK))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> configService.testConnection(PLATFORM_TYPE_WEWORK))
                .isInstanceOf(ScrmException.class)
                .hasFieldOrPropertyWithValue("code", "SCRM_PLATFORM_CONFIG_NOT_FOUND");
    }

    @Test
    @DisplayName("testConnection_weworkEmptyCredentials: corpId 或 secret 为空时标记 DISCONNECTED")
    void testConnection_weworkEmptyCredentials() {
        // ===== Given =====
        ScrmPlatformConfigEntity entity = buildEntity(1L, PLATFORM_TYPE_WEWORK);
        entity.setCorpId("");
        entity.setSecret(null);
        when(platformConfigRepository.findByPlatformType(PLATFORM_TYPE_WEWORK))
                .thenReturn(Optional.of(entity));
        when(platformConfigRepository.save(any(ScrmPlatformConfigEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // ===== When =====
        ScrmPlatformConfigDto result = configService.testConnection(PLATFORM_TYPE_WEWORK);

        // ===== Then =====
        assertThat(result).isNotNull();
        assertThat(result.getConnectionStatus()).isEqualTo("DISCONNECTED");
    }

    // ==================== 辅助方法 ====================

    private ScrmPlatformConfigEntity buildEntity(Long id, String platformType) {
        ScrmPlatformConfigEntity entity = new ScrmPlatformConfigEntity();
        entity.setId(id);
        entity.setPlatformType(platformType);
        return entity;
    }
}
