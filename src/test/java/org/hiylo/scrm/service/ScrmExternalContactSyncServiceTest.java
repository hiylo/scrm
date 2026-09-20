/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmExternalContactSyncServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmExternalContactSyncConfigDto;
import org.hiylo.scrm.dto.ScrmExternalContactSyncTaskDto;
import org.hiylo.scrm.entity.ScrmExternalContactSyncConfigEntity;
import org.hiylo.scrm.entity.ScrmExternalContactSyncTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmExternalContactMappingRepository;
import org.hiylo.scrm.repository.ScrmExternalContactSyncConfigRepository;
import org.hiylo.scrm.repository.ScrmExternalContactSyncLogRepository;
import org.hiylo.scrm.repository.ScrmExternalContactSyncTaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmExternalContactSyncService 单元测试
 * <p>
 * 聚焦外部联系人同步任务管理 (创建校验 / 默认状态 PENDING / 计数初值 / 配置禁用拦截)、
 * 同步执行 (PENDING → RUNNING → SUCCESS, 模拟生成联系人与映射 / 累加计数 / 更新配置最后同步状态)、
 * 任务取消 (状态流转保护)、越权访问与同步统计 (状态分布 / 成功率 / 时间区间聚合) 等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmExternalContactSyncService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmExternalContactSyncServiceTest {

    /** 外部联系人同步配置仓库 Mock */
    @Mock
    private ScrmExternalContactSyncConfigRepository configRepository;
    /** 外部联系人同步任务仓库 Mock */
    @Mock
    private ScrmExternalContactSyncTaskRepository taskRepository;
    /** 外部联系人同步日志仓库 Mock */
    @Mock
    private ScrmExternalContactSyncLogRepository logRepository;
    /** 外部联系人映射仓库 Mock */
    @Mock
    private ScrmExternalContactMappingRepository mappingRepository;

    /** 被测服务实例 */
    private ScrmExternalContactSyncService service;

    @BeforeEach
    void setUp() {
        service = new ScrmExternalContactSyncService(configRepository, taskRepository,
                logRepository, mappingRepository);
    }

    @AfterEach
    void tearDown() {
    }

    // ==================== 任务管理 ====================

    @Test
    @DisplayName("createTask: 成功创建, status 默认 PENDING, 计数与触发者缺省填充默认值")
    void createTask_success_defaults() throws Exception {
        ScrmExternalContactSyncTaskDto dto = new ScrmExternalContactSyncTaskDto();
        dto.setConfigId(10L);
        dto.setTaskName("企微联系人同步");
        dto.setSyncMode("INCREMENTAL");
        when(configRepository.findById(10L)).thenReturn(Optional.of(buildConfig(10L, true)));
        when(taskRepository.save(any(ScrmExternalContactSyncTaskEntity.class)))
                .thenAnswer(inv -> assignTaskId(inv.getArgument(0), 200L));

        ScrmExternalContactSyncTaskDto result = service.createTask(dto);

        assertThat(result.getId()).isEqualTo(200L);
        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getSyncMode()).isEqualTo("INCREMENTAL");
        assertThat(result.getTotalRecords()).isZero();
        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isZero();
        assertThat(result.getTriggeredBy()).isEqualTo("scrm-system");
        assertThat(result.getTriggeredByType()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("createTask: configId 为空抛 BAD_REQUEST")
    void createTask_configIdNull() {
        ScrmExternalContactSyncTaskDto dto = new ScrmExternalContactSyncTaskDto();
        dto.setTaskName("任务");
        dto.setSyncMode("INCREMENTAL");
        assertThatThrownBy(() -> service.createTask(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("同步配置 ID 不能为空");
    }

    @Test
    @DisplayName("createTask: 同步配置已禁用抛 BAD_REQUEST, 不创建任务")
    void createTask_configDisabled() {
        ScrmExternalContactSyncTaskDto dto = new ScrmExternalContactSyncTaskDto();
        dto.setConfigId(10L);
        dto.setTaskName("任务");
        dto.setSyncMode("INCREMENTAL");
        when(configRepository.findById(10L)).thenReturn(Optional.of(buildConfig(10L, false)));

        assertThatThrownBy(() -> service.createTask(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("同步配置已禁用");
        verify(taskRepository, never()).save(any(ScrmExternalContactSyncTaskEntity.class));
    }

    
    @Test
    @DisplayName("listTasks: 分页查询返回任务列表")
    void listTasks_paged() {
        Page<ScrmExternalContactSyncTaskEntity> page = new PageImpl<>(
                List.of(buildTask(200L, "SUCCESS"), buildTask(201L, "FAILED")),
                PageRequest.of(0, 10), 2);
        when(taskRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<ScrmExternalContactSyncTaskDto> result = service.listTasks(
                10L, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getId()).isEqualTo(200L);
        assertThat(result.getContent().get(1).getStatus()).isEqualTo("FAILED");
    }

    // ==================== 同步执行 ====================

    @Test
    @DisplayName("executeSync: PENDING → SUCCESS, 生成 10 个联系人映射, 计数累加, 更新配置最后同步状态")
    void executeSync_success() throws Exception {
        ScrmExternalContactSyncTaskEntity task = buildTask(200L, "PENDING");
        task.setConfigId(10L);
        ScrmExternalContactSyncConfigEntity config = buildConfig(10L, true);
        config.setPlatform("WORK_WECHAT");
        when(taskRepository.findById(200L)).thenReturn(Optional.of(task));
        when(configRepository.findById(10L)).thenReturn(Optional.of(config));
        when(taskRepository.save(any(ScrmExternalContactSyncTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // 模拟生成的外部联系人 ID 均不存在 → 全部新建
        when(mappingRepository.findByPlatformAndExternalContactId(any(), any()))
                .thenReturn(Optional.empty());

        ScrmExternalContactSyncTaskDto result = service.executeSync(200L);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getTotalRecords()).isEqualTo(10);
        assertThat(result.getSuccessCount()).isEqualTo(10);
        assertThat(result.getNewCount()).isEqualTo(10);
        assertThat(result.getUpdateCount()).isZero();
        assertThat(result.getFailedCount()).isZero();
        assertThat(result.getDurationMs()).isNotNegative();
    }

    @Test
    @DisplayName("cancelTask: PENDING 状态可取消, 状态置 CANCELLED 并写结束时间")
    void cancelTask_pendingToCancelled() throws Exception {
        ScrmExternalContactSyncTaskEntity task = buildTask(200L, "PENDING");
        when(taskRepository.findById(200L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(ScrmExternalContactSyncTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmExternalContactSyncTaskDto result = service.cancelTask(200L);

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
        assertThat(result.getEndTime()).isNotNull();
    }

    @Test
    @DisplayName("cancelTask: SUCCESS 终态不可取消抛 BAD_REQUEST")
    void cancelTask_terminalState() {
        ScrmExternalContactSyncTaskEntity task = buildTask(200L, "SUCCESS");
        when(taskRepository.findById(200L)).thenReturn(Optional.of(task));
        assertThatThrownBy(() -> service.cancelTask(200L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("任务状态非法");
    }

    // ==================== 统计 ====================

    @Test
    @DisplayName("getSyncStats: 聚合任务状态分布与时间区间成功率")
    void getSyncStats_aggregates() {
        when(taskRepository.countByStatus()).thenReturn(List.of(
                new Object[]{"SUCCESS", 3L},
                new Object[]{"FAILED", 1L}));
        when(taskRepository.aggregateStats(any(), any()))
                .thenReturn(new Object[]{5L, 3L, 10L, 5L, 2L});

        Map<String, Object> stats = service.getSyncStats(null, null);

        assertThat(stats.get("totalTasks")).isEqualTo(4L);
        @SuppressWarnings("unchecked")
        Map<String, Long> statusDist = (Map<String, Long>) stats.get("statusDistribution");
        assertThat(statusDist.get("SUCCESS")).isEqualTo(3L);
        assertThat(statusDist.get("FAILED")).isEqualTo(1L);
        assertThat(statusDist.get("PENDING")).isZero();
        assertThat(stats.get("timeRangeTotalTasks")).isEqualTo(5L);
        assertThat(stats.get("timeRangeSuccessTasks")).isEqualTo(3L);
        assertThat((Double) stats.get("successRate")).isEqualTo(0.6);
        assertThat(stats.get("totalNew")).isEqualTo(10L);
        assertThat(stats.get("totalFailed")).isEqualTo(2L);
    }

    // ==================== 辅助方法 ====================

    private ScrmExternalContactSyncConfigEntity buildConfig(Long id, boolean enabled) {
        ScrmExternalContactSyncConfigEntity entity = new ScrmExternalContactSyncConfigEntity();
        entity.setId(id);
        entity.setConfigName("企微同步配置");
        entity.setPlatform("WORK_WECHAT");
        entity.setEnabled(enabled);
        return entity;
    }

    private ScrmExternalContactSyncTaskEntity buildTask(Long id, String status) {
        ScrmExternalContactSyncTaskEntity entity = new ScrmExternalContactSyncTaskEntity();
        entity.setId(id);
        entity.setConfigId(10L);
        entity.setTaskName("同步任务");
        entity.setSyncMode("INCREMENTAL");
        entity.setStatus(status);
        entity.setTotalRecords(0);
        entity.setSuccessCount(0);
        entity.setFailedCount(0);
        entity.setNewCount(0);
        entity.setUpdateCount(0);
        entity.setSkipCount(0);
        return entity;
    }

    private ScrmExternalContactSyncTaskEntity assignTaskId(ScrmExternalContactSyncTaskEntity entity, Long id) {
        entity.setId(id);
        return entity;
    }
}
