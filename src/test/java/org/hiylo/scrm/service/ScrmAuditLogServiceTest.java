/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAuditLogServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.entity.ScrmAuditLogEntity;
import org.hiylo.scrm.repository.ScrmAuditLogRepository;
import org.hiylo.scrm.vo.AuditStatsVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmAuditLogService 单元测试
 * <p>
 * 聚焦审计日志保存 (同步/异步)、按用户/资源/时间区间/失败操作分页查询、
 * 最近活动、统计聚合 (总数/成功数/失败数/成功率/资源分布) 与数据隔离等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("ScrmAuditLogService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmAuditLogServiceTest {

    /** 审计日志数据仓库 Mock 桩 */
    @Mock
    private ScrmAuditLogRepository auditLogRepository;

    /** 被测审计日志服务实例 */
    private ScrmAuditLogService service;

    @BeforeEach
    void setUp() {
        service = new ScrmAuditLogService(auditLogRepository);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的审计日志实体
     */
    private ScrmAuditLogEntity buildAuditLogEntity(Long id, String userId, String result) {
        ScrmAuditLogEntity entity = new ScrmAuditLogEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setUsername("tester");
        entity.setResource("scrm_account");
        entity.setAction("create");
        entity.setMethod("POST");
        entity.setRequestUri("/scrm/accounts");
        entity.setResult(result);
        entity.setOperatedAt(LocalDateTime.now());
        entity.setExecutionTime(100L);
        return entity;
    }

    // ==================== 保存 ====================

    /**
     * 验证保存审计日志场景, 期望调用 repository.save 并返回同一实体实例
     */
    @Test
    @DisplayName("save: 调用 repository.save 并返回实体")
    void save_success() {
        ScrmAuditLogEntity entity = buildAuditLogEntity(null, "user1", "SUCCESS");
        when(auditLogRepository.save(any(ScrmAuditLogEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmAuditLogEntity result = service.save(entity);

        verify(auditLogRepository, times(1)).save(entity);
        assertThat(result).isSameAs(entity);
    }

    /**
     * 验证异步保存审计日志场景, 期望正常调用 repository.save 写入实体
     */
    @Test
    @DisplayName("saveAsync: 正常保存调用 repository.save")
    void saveAsync_success() {
        ScrmAuditLogEntity entity = buildAuditLogEntity(null, "user1", "SUCCESS");
        when(auditLogRepository.save(any(ScrmAuditLogEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.saveAsync(entity);

        ArgumentCaptor<ScrmAuditLogEntity> captor =
                ArgumentCaptor.forClass(ScrmAuditLogEntity.class);
        verify(auditLogRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(entity);
    }

    /**
     * 验证异步保存时 repository 抛异常场景, 期望异常被吞掉而不向外传播
     */
    @Test
    @DisplayName("saveAsync: repository 抛异常时不传播, 吞掉异常")
    void saveAsync_swallowsException() {
        ScrmAuditLogEntity entity = buildAuditLogEntity(null, "user1", "FAILED");
        when(auditLogRepository.save(any(ScrmAuditLogEntity.class)))
                .thenThrow(new RuntimeException("DB connection lost"));

        // 异步保存不应传播异常
        service.saveAsync(entity);

        verify(auditLogRepository, times(1)).save(entity);
    }

    // ==================== 查询 ====================

    /**
     * 验证按用户查询审计日志场景, 期望按当前账号与用户 ID 分页查询并返回结果
     */
    @Test
    @DisplayName("getByUser: 按当前账号与用户 ID 查询审计日志")
    void getByUser_success() {
        ScrmAuditLogEntity log = buildAuditLogEntity(1L, "user1", "SUCCESS");
        Page<ScrmAuditLogEntity> page = new PageImpl<>(List.of(log), PageRequest.of(0, 10), 1L);
        when(auditLogRepository.findByUserIdOrderByOperatedAtDesc(eq("user1"), any(Pageable.class)))
                .thenReturn(page);

        Page<ScrmAuditLogEntity> result = service.getByUser("user1", 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUserId()).isEqualTo("user1");
        verify(auditLogRepository, times(1))
                .findByUserIdOrderByOperatedAtDesc(eq("user1"), any(Pageable.class));
    }

    /**
     * 验证传入负数页码与页大小场景, 期望页码归零且页大小取最小值 1
     */
    @Test
    @DisplayName("getByUser: 负数页码/大小归零与取 1")
    void getByUser_negativePaging() {
        Page<ScrmAuditLogEntity> emptyPage = new PageImpl<>(Collections.emptyList());
        when(auditLogRepository.findByUserIdOrderByOperatedAtDesc(eq("user1"), any(Pageable.class)))
                .thenReturn(emptyPage);

        service.getByUser("user1", -1, -5);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository, times(1))
                .findByUserIdOrderByOperatedAtDesc(eq("user1"), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
    }

    /**
     * 验证按资源查询审计日志场景, 期望按当前账号与资源名称分页查询并返回结果
     */
    @Test
    @DisplayName("getByResource: 按当前账号与资源查询审计日志")
    void getByResource_success() {
        ScrmAuditLogEntity log = buildAuditLogEntity(1L, "user1", "SUCCESS");
        Page<ScrmAuditLogEntity> page = new PageImpl<>(List.of(log), PageRequest.of(0, 10), 1L);
        when(auditLogRepository.findByResource(eq("scrm_account"), any(Pageable.class)))
                .thenReturn(page);

        Page<ScrmAuditLogEntity> result = service.getByResource("scrm_account", 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getResource()).isEqualTo("scrm_account");
    }

    /**
     * 验证分页查询审计日志场景, 期望按当前账号返回分页结果
     */
    @Test
    @DisplayName("list: 按当前账号分页查询审计日志")
    void list_success() {
        ScrmAuditLogEntity log1 = buildAuditLogEntity(1L, "user1", "SUCCESS");
        ScrmAuditLogEntity log2 = buildAuditLogEntity(2L, "user2", "FAILED");
        Page<ScrmAuditLogEntity> page = new PageImpl<>(Arrays.asList(log1, log2), PageRequest.of(0, 10), 2L);
        when(auditLogRepository.findAllByOrderByOperatedAtDesc(any(Pageable.class)))
                .thenReturn(page);

        Page<ScrmAuditLogEntity> result = service.list(0, 10);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2L);
    }

    /**
     * 验证按时间区间查询审计日志场景, 期望按账号与起止时间分页查询并返回结果
     */
    @Test
    @DisplayName("getByTimeRange: 按账号与时间区间查询")
    void getByTimeRange_success() {
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();
        ScrmAuditLogEntity log = buildAuditLogEntity(1L, "user1", "SUCCESS");
        Page<ScrmAuditLogEntity> page = new PageImpl<>(List.of(log), PageRequest.of(0, 10), 1L);
        when(auditLogRepository.findByOperatedAtBetween(eq(start), eq(end), any(Pageable.class)))
                .thenReturn(page);

        Page<ScrmAuditLogEntity> result = service.getByTimeRange(start, end, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        verify(auditLogRepository, times(1))
                .findByOperatedAtBetween(eq(start), eq(end), any(Pageable.class));
    }

    /**
     * 验证查询失败操作场景, 期望仅返回 result 为 FAILED 的审计日志
     */
    @Test
    @DisplayName("getFailedOperations: 按当前账号查询失败操作 (result=FAILED)")
    void getFailedOperations_success() {
        ScrmAuditLogEntity failed = buildAuditLogEntity(1L, "user1", "FAILED");
        Page<ScrmAuditLogEntity> page = new PageImpl<>(List.of(failed), PageRequest.of(0, 10), 1L);
        when(auditLogRepository.findByResultOrderByOperatedAtDesc(eq(ScrmAuditLogService.RESULT_FAILED), any(Pageable.class)))
                .thenReturn(page);

        Page<ScrmAuditLogEntity> result = service.getFailedOperations(0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getResult()).isEqualTo("FAILED");
    }

    /**
     * 验证获取最近活动场景, 期望返回最近 N 条审计日志
     */
    @Test
    @DisplayName("getRecentActivities: 返回最近 N 条审计日志")
    void getRecentActivities_success() {
        ScrmAuditLogEntity log1 = buildAuditLogEntity(1L, "user1", "SUCCESS");
        ScrmAuditLogEntity log2 = buildAuditLogEntity(2L, "user2", "SUCCESS");
        Page<ScrmAuditLogEntity> page = new PageImpl<>(Arrays.asList(log1, log2), PageRequest.of(0, 5), 2L);
        when(auditLogRepository.findAllByOrderByOperatedAtDesc(any(Pageable.class)))
                .thenReturn(page);

        List<ScrmAuditLogEntity> result = service.getRecentActivities(5);

        assertThat(result).hasSize(2);
    }

    /**
     * 验证传入负数条数限制场景, 期望页大小归为最小值 1 而不返回负数
     */
    @Test
    @DisplayName("getRecentActivities: limit 归 1, 不返回负数大小")
    void getRecentActivities_negativeLimit() {
        Page<ScrmAuditLogEntity> emptyPage = new PageImpl<>(Collections.emptyList());
        when(auditLogRepository.findAllByOrderByOperatedAtDesc(any(Pageable.class)))
                .thenReturn(emptyPage);

        service.getRecentActivities(-3);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository, times(1))
                .findAllByOrderByOperatedAtDesc(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
    }

    // ==================== 统计 ====================

    /**
     * 验证审计统计聚合场景, 期望汇总资源分布与成功失败计数并计算成功率
     */
    @Test
    @DisplayName("getAuditStats: 聚合资源分布与成功/失败计数并计算成功率")
    void getAuditStats_success() {
        when(auditLogRepository.countGroupByResource()).thenReturn(Arrays.asList(
                new Object[]{"scrm_account", 6L},
                new Object[]{"scrm_customer", 4L}));
        when(auditLogRepository.countByResult(ScrmAuditLogService.RESULT_SUCCESS))
                .thenReturn(8L);
        when(auditLogRepository.countByResult(ScrmAuditLogService.RESULT_FAILED))
                .thenReturn(2L);

        AuditStatsVo vo = service.getAuditStats();

        assertThat(vo.getTotalOperations()).isEqualTo(10L);
        assertThat(vo.getSuccessCount()).isEqualTo(8L);
        assertThat(vo.getFailedCount()).isEqualTo(2L);
        assertThat(vo.getSuccessRate()).isEqualTo(0.8d);
        assertThat(vo.getResourceDistribution())
                .containsEntry("scrm_account", 6L)
                .containsEntry("scrm_customer", 4L);
    }

    /**
     * 验证无任何审计记录场景, 期望总数与成功率均为 0 且资源分布为空
     */
    @Test
    @DisplayName("getAuditStats: 无记录时总数 0 成功率 0")
    void getAuditStats_empty() {
        when(auditLogRepository.countGroupByResource()).thenReturn(Collections.emptyList());
        when(auditLogRepository.countByResult(ScrmAuditLogService.RESULT_SUCCESS))
                .thenReturn(0L);
        when(auditLogRepository.countByResult(ScrmAuditLogService.RESULT_FAILED))
                .thenReturn(0L);

        AuditStatsVo vo = service.getAuditStats();

        assertThat(vo.getTotalOperations()).isZero();
        assertThat(vo.getSuccessCount()).isZero();
        assertThat(vo.getFailedCount()).isZero();
        assertThat(vo.getSuccessRate()).isEqualTo(0.0d);
        assertThat(vo.getResourceDistribution()).isEmpty();
    }

    /**
     * 验证分组聚合返回 null 行或 null 元素场景, 期望跳过空数据仅统计有效行
     */
    @Test
    @DisplayName("getAuditStats: countGroupByResource 返回 null 行或 null 元素时跳过")
    void getAuditStats_nullRowsSafe() {
        when(auditLogRepository.countGroupByResource()).thenReturn(Arrays.asList(
                null,
                new Object[]{null, 3L},
                new Object[]{"scrm_account", 5L},
                new Object[]{"scrm_customer"}));
        when(auditLogRepository.countByResult(ScrmAuditLogService.RESULT_SUCCESS))
                .thenReturn(5L);
        when(auditLogRepository.countByResult(ScrmAuditLogService.RESULT_FAILED))
                .thenReturn(0L);

        AuditStatsVo vo = service.getAuditStats();

        // 仅 scrm_account 计入总数 (5), 其他行被跳过
        assertThat(vo.getTotalOperations()).isEqualTo(5L);
        assertThat(vo.getResourceDistribution()).containsOnlyKeys("scrm_account");
    }

    /**
     * 验证分组聚合整体返回 null 场景, 期望安全处理且不抛出异常
     */
    @Test
    @DisplayName("getAuditStats: countGroupByResource 返回 null 时安全处理")
    void getAuditStats_nullResultSafe() {
        when(auditLogRepository.countGroupByResource()).thenReturn(null);
        when(auditLogRepository.countByResult(ScrmAuditLogService.RESULT_SUCCESS))
                .thenReturn(0L);
        when(auditLogRepository.countByResult(ScrmAuditLogService.RESULT_FAILED))
                .thenReturn(0L);

        AuditStatsVo vo = service.getAuditStats();

        assertThat(vo.getTotalOperations()).isZero();
        assertThat(vo.getResourceDistribution()).isEmpty();
    }

    /**
     * 验证分组聚合返回 Integer 类型计数场景, 期望正确转换为 Long 参与统计
     */
    @Test
    @DisplayName("getAuditStats: 聚合返回 Number 类型 (Integer) 正确转换为 Long")
    void getAuditStats_numberTypeConversion() {
        when(auditLogRepository.countGroupByResource()).thenReturn(
                Collections.singletonList(new Object[]{"scrm_account", 3}));
        when(auditLogRepository.countByResult(ScrmAuditLogService.RESULT_SUCCESS))
                .thenReturn(3L);
        when(auditLogRepository.countByResult(ScrmAuditLogService.RESULT_FAILED))
                .thenReturn(0L);

        AuditStatsVo vo = service.getAuditStats();

        assertThat(vo.getTotalOperations()).isEqualTo(3L);
        assertThat(vo.getResourceDistribution()).containsEntry("scrm_account", 3L);
    }
}
