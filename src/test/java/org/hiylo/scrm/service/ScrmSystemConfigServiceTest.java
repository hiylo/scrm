/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSystemConfigServiceTest.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmConfigHistoryDto;
import org.hiylo.scrm.entity.ScrmConfigHistoryEntity;
import org.hiylo.scrm.entity.ScrmSystemConfigEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmConfigHistoryRepository;
import org.hiylo.scrm.repository.ScrmSystemConfigRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmSystemConfigHistoryService 单元测试
 * <p>
 * 覆盖手动创建配置变更历史 ({@code createManualHistory}) 的参数校验与快照补全逻辑,
 * 验证 configKey 存在时补全 configId / configName / configGroup, 不存在时 configId 为 null,
 * 使用 Mockito 隔离 Repository。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmSystemConfigHistoryService 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScrmSystemConfigServiceTest {

    /** 系统配置数据访问层 Mock */
    @Mock
    private ScrmSystemConfigRepository configRepository;

    /** 配置变更历史数据访问层 Mock */
    @Mock
    private ScrmConfigHistoryRepository historyRepository;

    /** 配置项管理服务 Mock */
    @Mock
    private ScrmSystemConfigItemService itemService;

    /** 配置值处理服务 Mock */
    @Mock
    private ScrmSystemConfigValueService valueService;

    /** 被测对象 (历史与回滚子域服务) */
    @InjectMocks
    private ScrmSystemConfigHistoryService configService;

    /**
     * 测试前设置请求上下文
     */
    @BeforeEach
    void setUp() {
    }

    /**
     * 测试后清理请求上下文
     */
    @AfterEach
    void tearDown() {
    }

    // ============================================================
    // createManualHistory
    // ============================================================

    @Test
    @DisplayName("createManualHistory_nullDto: 参数为空抛 ScrmException (code=SCRM_BAD_REQUEST)")
    void createManualHistory_nullDto() {
        assertThatThrownBy(() -> configService.createManualHistory(null))
                .isInstanceOf(ScrmException.class)
                .hasFieldOrPropertyWithValue("code", "SCRM_BAD_REQUEST")
                .hasMessageContaining("历史参数不能为空");

        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("createManualHistory_blankConfigKey: configKey 为空抛 ScrmException")
    void createManualHistory_blankConfigKey() {
        ScrmConfigHistoryDto dto = new ScrmConfigHistoryDto();
        dto.setConfigKey("  ");
        dto.setChangeType("UPDATE");
        dto.setChangedBy("admin");

        assertThatThrownBy(() -> configService.createManualHistory(dto))
                .isInstanceOf(ScrmException.class)
                .hasFieldOrPropertyWithValue("code", "SCRM_BAD_REQUEST")
                .hasMessageContaining("配置键不能为空");

        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("createManualHistory_blankChangeType: changeType 为空抛 ScrmException")
    void createManualHistory_blankChangeType() {
        ScrmConfigHistoryDto dto = new ScrmConfigHistoryDto();
        dto.setConfigKey("sys.title");
        dto.setChangeType("");
        dto.setChangedBy("admin");

        assertThatThrownBy(() -> configService.createManualHistory(dto))
                .isInstanceOf(ScrmException.class)
                .hasFieldOrPropertyWithValue("code", "SCRM_BAD_REQUEST")
                .hasMessageContaining("变更类型不能为空");

        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("createManualHistory_blankChangedBy: changedBy 为空抛 ScrmException")
    void createManualHistory_blankChangedBy() {
        ScrmConfigHistoryDto dto = new ScrmConfigHistoryDto();
        dto.setConfigKey("sys.title");
        dto.setChangeType("UPDATE");
        dto.setChangedBy("");

        assertThatThrownBy(() -> configService.createManualHistory(dto))
                .isInstanceOf(ScrmException.class)
                .hasFieldOrPropertyWithValue("code", "SCRM_BAD_REQUEST")
                .hasMessageContaining("变更人不能为空");

        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("createManualHistory_configExists: configKey 存在时补全 configId/configName/configGroup 快照")
    void createManualHistory_configExists() throws ScrmException {
        // ===== Given =====
        ScrmSystemConfigEntity config = new ScrmSystemConfigEntity();
        config.setId(5L);
        config.setConfigKey("sys.title");
        config.setConfigName("系统标题");
        config.setConfigGroup("GENERAL");
        config.setConfigType("STRING");
        when(configRepository.findByConfigKey("sys.title"))
                .thenReturn(Optional.of(config));
        when(historyRepository.save(any(ScrmConfigHistoryEntity.class)))
                .thenAnswer(invocation -> {
                    ScrmConfigHistoryEntity history = invocation.getArgument(0);
                    history.setId(99L);
                    return history;
                });

        ScrmConfigHistoryDto dto = new ScrmConfigHistoryDto();
        dto.setConfigKey("sys.title");
        dto.setChangeType("UPDATE");
        dto.setChangedBy("admin");
        dto.setOldValue("A");
        dto.setNewValue("B");
        dto.setOldDisplayValue("旧显示");
        dto.setNewDisplayValue("新显示");

        // ===== When =====
        ScrmConfigHistoryEntity result = configService.createManualHistory(dto);

        // ===== Then =====
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(99L);
        // 快照补全 (dto 未提供, 从 config 取)
        assertThat(result.getConfigId()).isEqualTo(5L);
        assertThat(result.getConfigKey()).isEqualTo("sys.title");
        assertThat(result.getConfigName()).isEqualTo("系统标题");
        assertThat(result.getConfigGroup()).isEqualTo("GENERAL");
        // 业务字段
        assertThat(result.getChangeType()).isEqualTo("UPDATE");
        assertThat(result.getChangedBy()).isEqualTo("admin");
        assertThat(result.getOldValue()).isEqualTo("A");
        assertThat(result.getNewValue()).isEqualTo("B");
        // UPDATE 类型可回滚
        assertThat(result.getRollbackPossible()).isTrue();
        assertThat(result.getIsRolledBack()).isFalse();
        assertThat(result.getChangedAt()).isNotNull();

        // 验证 save 调用一次, 实体字段正确
        ArgumentCaptor<ScrmConfigHistoryEntity> entityCaptor = ArgumentCaptor.forClass(ScrmConfigHistoryEntity.class);
        verify(historyRepository, times(1)).save(entityCaptor.capture());
        ScrmConfigHistoryEntity saved = entityCaptor.getValue();
        assertThat(saved.getConfigId()).isEqualTo(5L);
        assertThat(saved.getConfigName()).isEqualTo("系统标题");
        assertThat(saved.getConfigGroup()).isEqualTo("GENERAL");
        assertThat(saved.getChangeType()).isEqualTo("UPDATE");
    }

    @Test
    @DisplayName("createManualHistory_configNotExists: configKey 不存在时 configId 为 null")
    void createManualHistory_configNotExists() throws ScrmException {
        // ===== Given =====
        when(configRepository.findByConfigKey("sys.missing"))
                .thenReturn(Optional.empty());
        when(historyRepository.save(any(ScrmConfigHistoryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScrmConfigHistoryDto dto = new ScrmConfigHistoryDto();
        dto.setConfigKey("sys.missing");
        dto.setChangeType("CREATE");
        dto.setChangedBy("admin");

        // ===== When =====
        ScrmConfigHistoryEntity result = configService.createManualHistory(dto);

        // ===== Then =====
        assertThat(result).isNotNull();
        // config 不存在且 dto 未提供 → configId / configName / configGroup 均为 null
        assertThat(result.getConfigId()).isNull();
        assertThat(result.getConfigName()).isNull();
        assertThat(result.getConfigGroup()).isNull();
        assertThat(result.getConfigKey()).isEqualTo("sys.missing");
        assertThat(result.getChangeType()).isEqualTo("CREATE");
        assertThat(result.getChangedBy()).isEqualTo("admin");
        // CREATE 类型不可回滚
        assertThat(result.getRollbackPossible()).isFalse();
        assertThat(result.getIsRolledBack()).isFalse();

        verify(historyRepository, times(1)).save(any(ScrmConfigHistoryEntity.class));
    }
}
