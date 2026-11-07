/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmInspectionSnapshotServiceTest.java
 * Date : 2026/10/23 11:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.entity.ScrmInspectionSnapshotEntity;
import org.hiylo.scrm.repository.ScrmInspectionSnapshotRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmInspectionSnapshotService} 单元测试（「变化才告警」核心语义回归）。
 * <p>
 * 锁定三条判定:
 * <ul>
 *   <li>首次巡检无旧快照 → 基线入库, {@code changed=false}（不告警）</li>
 *   <li>新快照哈希与最新快照一致 → 无变化, {@code changed=false}</li>
 *   <li>哈希不一致 → {@code changed=true}, 且旧快照被置为非最新</li>
 * </ul>
 * 同时校验哈希对相同输入稳定、对不同输入不同（SHA-256 确定性）。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmInspectionSnapshotService 变化比对")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScrmInspectionSnapshotServiceTest {

    /** 快照数据访问层 */
    @Mock
    private ScrmInspectionSnapshotRepository snapshotRepository;

    /** 被测服务 */
    @InjectMocks
    private ScrmInspectionSnapshotService snapshotService;

    /**
     * 首次巡检: 无旧快照, 基线入库不标记变化, 不触发旧快照置非最新。
     */
    @Test
    @DisplayName("首次巡检: 基线快照 changed=false, 不告警")
    void firstInspectionIsBaseline() {
        when(snapshotRepository.findFirstByTaskIdAndDimensionAndIsLatestTrue(anyLong(), anyString()))
                .thenReturn(Optional.empty());
        when(snapshotRepository.save(any(ScrmInspectionSnapshotEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScrmInspectionSnapshotEntity saved = snapshotService.saveSnapshot(
                1L, ScrmInspectionSnapshotService.DIM_BUSINESS, "91330100MA27X00001",
                "示例企业", "QIXIN", "正常", "{\"status\":\"正常\"}", null);

        assertThat(saved.getChanged()).isFalse();
        assertThat(saved.getIsLatest()).isTrue();
        assertThat(saved.getStateHash()).hasSize(64);
        verify(snapshotRepository, never()).markAllNotLatest(anyLong(), anyString());
    }

    /**
     * 状态哈希一致: 视为无变化, 不告警, 旧快照保持最新标记。
     */
    @Test
    @DisplayName("哈希一致: 无变化 changed=false")
    void sameHashMeansNoChange() {
        String detail = "{\"status\":\"正常\"}";
        ScrmInspectionSnapshotEntity old = new ScrmInspectionSnapshotEntity();
        old.setTaskId(1L);
        old.setDimension(ScrmInspectionSnapshotService.DIM_BUSINESS);
        old.setStateHash(snapshotService.sha256(detail));
        old.setIsLatest(true);
        when(snapshotRepository.findFirstByTaskIdAndDimensionAndIsLatestTrue(anyLong(), anyString()))
                .thenReturn(Optional.of(old));
        when(snapshotRepository.save(any(ScrmInspectionSnapshotEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScrmInspectionSnapshotEntity saved = snapshotService.saveSnapshot(
                1L, ScrmInspectionSnapshotService.DIM_BUSINESS, "91330100MA27X00001",
                "示例企业", "QIXIN", "正常", detail, null);

        assertThat(saved.getChanged()).isFalse();
        verify(snapshotRepository, never()).markAllNotLatest(anyLong(), anyString());
    }

    /**
     * 哈希变化: 标记 changed=true 并把旧快照置为非最新（交给告警服务推送）。
     */
    @Test
    @DisplayName("哈希变化: changed=true 且旧快照置非最新")
    void differentHashMeansChange() {
        ScrmInspectionSnapshotEntity old = new ScrmInspectionSnapshotEntity();
        old.setTaskId(1L);
        old.setDimension(ScrmInspectionSnapshotService.DIM_BUSINESS);
        old.setStateHash(snapshotService.sha256("{\"status\":\"正常\"}"));
        old.setIsLatest(true);
        when(snapshotRepository.findFirstByTaskIdAndDimensionAndIsLatestTrue(anyLong(), anyString()))
                .thenReturn(Optional.of(old));
        when(snapshotRepository.save(any(ScrmInspectionSnapshotEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScrmInspectionSnapshotEntity saved = snapshotService.saveSnapshot(
                1L, ScrmInspectionSnapshotService.DIM_BUSINESS, "91330100MA27X00001",
                "示例企业", "QIXIN", "注销", "{\"status\":\"注销\"}", null);

        assertThat(saved.getChanged()).isTrue();
        verify(snapshotRepository).markAllNotLatest(1L, ScrmInspectionSnapshotService.DIM_BUSINESS);
        ArgumentCaptor<ScrmInspectionSnapshotEntity> captor =
                ArgumentCaptor.forClass(ScrmInspectionSnapshotEntity.class);
        verify(snapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("注销");
    }

    /**
     * 哈希确定性: 相同输入稳定, 不同输入不同, null 视为空串。
     */
    @Test
    @DisplayName("哈希: 同输入稳定/异输入不同/null 等价空串")
    void hashIsDeterministic() {
        assertThat(snapshotService.sha256("{\"a\":1}"))
                .isEqualTo(snapshotService.sha256("{\"a\":1}"))
                .hasSize(64)
                .isNotEqualTo(snapshotService.sha256("{\"a\":2}"));
        assertThat(snapshotService.sha256(null)).isEqualTo(snapshotService.sha256(""));
    }
}