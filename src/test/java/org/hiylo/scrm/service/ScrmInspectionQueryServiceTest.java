/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmInspectionQueryServiceTest.java
 * Date : 2026/10/24 15:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.component.InspectionRateLimiter;
import org.hiylo.scrm.entity.ScrmInspectionSnapshotEntity;
import org.hiylo.scrm.entity.ScrmInspectionTaskEntity;
import org.hiylo.scrm.feign.QixinApiConfig;
import org.hiylo.scrm.feign.QixinApiException;
import org.hiylo.scrm.feign.QixinEnterpriseApi;
import org.hiylo.scrm.feign.dto.QixinEnterpriseProfile;
import org.hiylo.scrm.repository.ScrmInspectionTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmInspectionQueryService} 编排层单元测试。
 * <p>
 * <b>为什么需要这层测试</b>：启信宝客户端本身已有契约测试（{@code QixinEnterpriseApiClientTest}），
 * 但「巡检怎么编排」一直没测——正是这里藏着两个会真实伤到生产的缺陷：
 * </p>
 * <ol>
 *   <li><b>未启用却逐任务刷失败</b>：默认 {@code enabled=false} 时仍遍历所有任务调 API，
 *       每次抛「未启用」→ 写失败快照 → 任务标 FAILED。合约未签约的过渡期会每天刷一批假失败。</li>
 *   <li><b>单次失败写两次快照</b>：{@code inspectOne} 已记录失败，外层 catch 又记一次，
 *       快照重复且 {@code runCount} 加两次。</li>
 * </ol>
 * <p>
 * 本测试同时覆盖正常的「照面 + 经营异常 → 归一化 → 快照 → 变化才告警」主链路。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("工商巡检编排")
class ScrmInspectionQueryServiceTest {

    /** 任务 ID */
    private static final long TASK_ID = 9001L;

    /** 企业名称 */
    private static final String ENTERPRISE = "示例企业有限公司";

    /** 统一社会信用代码 */
    private static final String CREDIT_CODE = "91310115MA1K3WXY7X";

    @Mock
    private ScrmInspectionTaskRepository taskRepository;

    @Mock
    private QixinEnterpriseApi qixinEnterpriseApi;

    @Mock
    private ScrmInspectionSnapshotService snapshotService;

    @Mock
    private ScrmInspectionAlertService alertService;

    @Mock
    private InspectionRateLimiter rateLimiter;

    private QixinApiConfig config;

    private ScrmInspectionQueryService service;

    @BeforeEach
    void setUp() {
        config = new QixinApiConfig();
        config.setEnabled(true);
        config.setToken("test-token");
        service = new ScrmInspectionQueryService(taskRepository, qixinEnterpriseApi, config,
                snapshotService, alertService, rateLimiter, new ObjectMapper());
        when(rateLimiter.tryAcquire(anyString(), anyInt())).thenReturn(true);
        when(taskRepository.save(any(ScrmInspectionTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ==================== 启用闸门（本轮修复的核心） ====================

    @Test
    @DisplayName("未启用：整体跳过，不写任何快照、不改任务状态")
    void notEnabled_skipsEntireRound() {
        config.setEnabled(false);
        when(taskRepository.findEnabledBusinessTasks())
                .thenReturn(List.of(task()));

        ScrmInspectionQueryService.BusinessSummary summary = service.inspectAll();

        assertThat(summary.taskScanned()).isZero();
        assertThat(summary.skipReason()).contains("未启用");
        verify(snapshotService, never()).saveSnapshot(anyLong(), any(), any(), any(),
                any(), any(), any(), any());
        verify(taskRepository, never()).save(any());
        verify(qixinEnterpriseApi, never()).queryProfile(any(), any());
    }

    @Test
    @DisplayName("已启用但 token 为空：整体跳过，不刷失败快照")
    void enabledButNoToken_skipsEntireRound() {
        config.setToken("   ");
        when(taskRepository.findEnabledBusinessTasks()).thenReturn(List.of(task()));

        ScrmInspectionQueryService.BusinessSummary summary = service.inspectAll();

        assertThat(summary.taskScanned()).isZero();
        assertThat(summary.skipReason()).contains("token");
        verify(snapshotService, never()).saveSnapshot(anyLong(), any(), any(), any(),
                any(), any(), any(), any());
        verify(qixinEnterpriseApi, never()).queryProfile(any(), any());
    }

    @Test
    @DisplayName("无启用任务：正常返回 0，不算跳过")
    void noTasks_returnsZero() {
        when(taskRepository.findEnabledBusinessTasks()).thenReturn(List.of());

        ScrmInspectionQueryService.BusinessSummary summary = service.inspectAll();

        assertThat(summary.taskScanned()).isZero();
        assertThat(summary.skipReason()).isNull();
    }

    // ==================== 正常主链路 ====================

    @Test
    @DisplayName("正常巡检：状态归一化为「正常」并更新工商游标")
    void inspect_success_normalizesStatus() {
        ScrmInspectionTaskEntity task = task();
        when(taskRepository.findEnabledBusinessTasks()).thenReturn(List.of(task));
        when(qixinEnterpriseApi.queryProfile(any(), any())).thenReturn(profile("存续", false));
        when(qixinEnterpriseApi.queryAbnormal(any(), any())).thenReturn(profile("存续", false));
        when(snapshotService.saveSnapshot(anyLong(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(snapshot(false));

        ScrmInspectionQueryService.BusinessSummary summary = service.inspectAll();

        assertThat(summary.taskScanned()).isEqualTo(1);
        assertThat(summary.success()).isEqualTo(1);
        assertThat(summary.failed()).isZero();
        assertThat(summary.changed()).isZero();
        assertThat(task.getLastResult()).isEqualTo("SUCCESS");
        assertThat(task.getLastBusinessRunAt()).isNotNull();
        assertThat(task.getRunCount()).isEqualTo(1);
        // 状态归一化: "存续" → "正常"
        verify(snapshotService).saveSnapshot(anyLong(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.eq("正常"), any(), any());
    }

    @Test
    @DisplayName("经营异常优先于工商状态：状态取「经营异常」并合并异常原因")
    void inspect_abnormalOverridesStatus() {
        ScrmInspectionTaskEntity task = task();
        when(taskRepository.findEnabledBusinessTasks()).thenReturn(List.of(task));
        when(qixinEnterpriseApi.queryProfile(any(), any())).thenReturn(profile("存续", false));
        QixinEnterpriseProfile abnormal = profile("存续", true);
        abnormal.setAbnormalReason("未按规定期限公示年度报告");
        when(qixinEnterpriseApi.queryAbnormal(any(), any())).thenReturn(abnormal);
        when(snapshotService.saveSnapshot(anyLong(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(snapshot(false));

        service.inspectAll();

        verify(snapshotService).saveSnapshot(anyLong(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.eq("经营异常"), any(), any());
    }

    @Test
    @DisplayName("注销状态原样保留（不误判为正常）")
    void inspect_cancelledKeptAsIs() {
        ScrmInspectionTaskEntity task = task();
        when(taskRepository.findEnabledBusinessTasks()).thenReturn(List.of(task));
        when(qixinEnterpriseApi.queryProfile(any(), any())).thenReturn(profile("注销", false));
        when(qixinEnterpriseApi.queryAbnormal(any(), any())).thenReturn(profile("注销", false));
        when(snapshotService.saveSnapshot(anyLong(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(snapshot(false));

        service.inspectAll();

        verify(snapshotService).saveSnapshot(anyLong(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.eq("注销"), any(), any());
    }

    @Test
    @DisplayName("状态变化才告警：changed=true 触发通知并把任务标 ALERTED")
    void inspect_changedAlertsAndMarksAlerted() {
        ScrmInspectionTaskEntity task = task();
        when(taskRepository.findEnabledBusinessTasks()).thenReturn(List.of(task));
        when(qixinEnterpriseApi.queryProfile(any(), any())).thenReturn(profile("存续", false));
        when(qixinEnterpriseApi.queryAbnormal(any(), any())).thenReturn(profile("存续", false));
        when(snapshotService.saveSnapshot(anyLong(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(snapshot(true));
        when(snapshotService.latest(anyLong(), any())).thenReturn(Optional.of(snapshot(false)));

        ScrmInspectionQueryService.BusinessSummary summary = service.inspectAll();

        assertThat(summary.changed()).isEqualTo(1);
        assertThat(task.getLastResult()).isEqualTo("ALERTED");
        verify(alertService, times(1)).notifyBusinessChange(any(), any(), any());
    }

    @Test
    @DisplayName("无变化不告警，任务标 SUCCESS")
    void inspect_unchangedNoAlert() {
        ScrmInspectionTaskEntity task = task();
        when(taskRepository.findEnabledBusinessTasks()).thenReturn(List.of(task));
        when(qixinEnterpriseApi.queryProfile(any(), any())).thenReturn(profile("存续", false));
        when(qixinEnterpriseApi.queryAbnormal(any(), any())).thenReturn(profile("存续", false));
        when(snapshotService.saveSnapshot(anyLong(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(snapshot(false));

        service.inspectAll();

        verify(alertService, never()).notifyBusinessChange(any(), any(), any());
        assertThat(task.getLastResult()).isEqualTo("SUCCESS");
    }

    // ==================== 失败路径（本轮修复的重复写入） ====================

    @Test
    @DisplayName("单企业查询失败：只写一条失败快照（不重复写两条）")
    void inspect_apiError_writesSingleFailureSnapshot() {
        ScrmInspectionTaskEntity task = task();
        when(taskRepository.findEnabledBusinessTasks()).thenReturn(List.of(task));
        when(qixinEnterpriseApi.queryProfile(any(), any()))
                .thenThrow(new QixinApiException("业务失败: code=401", false));
        when(snapshotService.saveSnapshot(anyLong(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(snapshot(false));

        ScrmInspectionQueryService.BusinessSummary summary = service.inspectAll();

        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.success()).isZero();
        // 关键断言: 失败快照只写 1 次（修复前是 2 次）
        verify(snapshotService, times(1)).saveSnapshot(anyLong(), any(), any(), any(),
                any(), any(), any(), any());
        assertThat(task.getLastResult()).isEqualTo("FAILED");
        assertThat(task.getLastErrorMessage()).contains("401");
        // runCount 只加一次（修复前加两次）
        assertThat(task.getRunCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("日预算用尽：计失败且只写一条失败快照，不调第三方")
    void inspect_budgetExhausted_singleFailure() {
        ScrmInspectionTaskEntity task = task();
        when(taskRepository.findEnabledBusinessTasks()).thenReturn(List.of(task));
        when(rateLimiter.tryAcquire(anyString(), anyInt())).thenReturn(false);
        when(snapshotService.saveSnapshot(anyLong(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(snapshot(false));

        ScrmInspectionQueryService.BusinessSummary summary = service.inspectAll();

        assertThat(summary.failed()).isEqualTo(1);
        verify(qixinEnterpriseApi, never()).queryProfile(any(), any());
        verify(snapshotService, times(1)).saveSnapshot(anyLong(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("单企业失败不中断整轮：其余企业继续巡检")
    void inspect_oneFailureDoesNotAbortRound() {
        ScrmInspectionTaskEntity bad = task();
        bad.setId(9002L);
        bad.setEnterpriseName("失败企业有限公司");
        ScrmInspectionTaskEntity good = task();
        when(taskRepository.findEnabledBusinessTasks()).thenReturn(List.of(bad, good));
        when(qixinEnterpriseApi.queryProfile(any(), any()))
                .thenThrow(new QixinApiException("网络超时", true))
                .thenReturn(profile("存续", false));
        when(qixinEnterpriseApi.queryAbnormal(any(), any())).thenReturn(profile("存续", false));
        when(snapshotService.saveSnapshot(anyLong(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(snapshot(false));

        ScrmInspectionQueryService.BusinessSummary summary = service.inspectAll();

        assertThat(summary.taskScanned()).isEqualTo(2);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.success()).isEqualTo(1);
    }

    @Test
    @DisplayName("非预期异常（非 QixinApiException）：外层兜底记失败")
    void inspect_unexpectedException_caughtByOuter() {
        ScrmInspectionTaskEntity task = task();
        when(taskRepository.findEnabledBusinessTasks()).thenReturn(List.of(task));
        when(qixinEnterpriseApi.queryProfile(any(), any()))
                .thenThrow(new IllegalStateException("boom"));
        when(snapshotService.saveSnapshot(anyLong(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(snapshot(false));

        ScrmInspectionQueryService.BusinessSummary summary = service.inspectAll();

        assertThat(summary.failed()).isEqualTo(1);
        // 外层兜底会写一条失败快照
        verify(snapshotService, times(1)).saveSnapshot(anyLong(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("inspectOne 直接调用：预算用尽抛 QixinApiException")
    void inspectOne_budgetExhaustedThrows() {
        when(rateLimiter.tryAcquire(anyString(), anyInt())).thenReturn(false);
        when(snapshotService.saveSnapshot(anyLong(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(snapshot(false));

        assertThatThrownBy(() -> service.inspectOne(task()))
                .isInstanceOf(QixinApiException.class)
                .hasMessageContaining("预算");
    }

    // ==================== 运维自检 ====================

    @Test
    @DisplayName("apiStatus：未配置时 ready=false，且不回显 token")
    void apiStatus_reportsReadinessWithoutLeakingToken() {
        config.setEnabled(true);
        config.setToken("super-secret-token");

        Map<String, Object> status = service.apiStatus();

        assertThat(status.get("enabled")).isEqualTo(true);
        assertThat(status.get("tokenConfigured")).isEqualTo(true);
        assertThat(status.get("ready")).isEqualTo(true);
        assertThat(status.get("baseUrl")).isEqualTo("https://api.qixin.com");
        assertThat(status.values().toString()).doesNotContain("super-secret-token");
    }

    @Test
    @DisplayName("apiStatus：未启用时 ready=false")
    void apiStatus_notReadyWhenDisabled() {
        config.setEnabled(false);
        config.setToken("t");

        Map<String, Object> status = service.apiStatus();

        assertThat(status.get("ready")).isEqualTo(false);
    }

    // ==================== 测试夹具 ====================

    /**
     * 构造巡检任务。
     *
     * @return 任务实体
     */
    private ScrmInspectionTaskEntity task() {
        ScrmInspectionTaskEntity task = new ScrmInspectionTaskEntity();
        task.setId(TASK_ID);
        task.setEnterpriseName(ENTERPRISE);
        task.setCreditCode(CREDIT_CODE);
        task.setIsEnabled(true);
        task.setInspectBusiness(true);
        task.setRunCount(0);
        return task;
    }

    /**
     * 构造工商快照。
     *
     * @param status   经营状态
     * @param abnormal 是否经营异常
     * @return 快照
     */
    private QixinEnterpriseProfile profile(String status, boolean abnormal) {
        QixinEnterpriseProfile profile = new QixinEnterpriseProfile();
        profile.setEnterpriseName(ENTERPRISE);
        profile.setCreditCode(CREDIT_CODE);
        profile.setBusinessStatus(status);
        profile.setAbnormal(abnormal);
        profile.setLegalPerson("张三");
        return profile;
    }

    /**
     * 构造快照实体。
     *
     * @param changed 是否变化
     * @return 快照实体
     */
    private ScrmInspectionSnapshotEntity snapshot(boolean changed) {
        ScrmInspectionSnapshotEntity snapshot = new ScrmInspectionSnapshotEntity();
        snapshot.setChanged(changed);
        snapshot.setStatus("正常");
        snapshot.setDimension(ScrmInspectionSnapshotService.DIM_BUSINESS);
        return snapshot;
    }
}
