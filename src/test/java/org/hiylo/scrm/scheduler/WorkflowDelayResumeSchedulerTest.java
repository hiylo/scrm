/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WorkflowDelayResumeSchedulerTest.java
 * Date : 2026-09-19 10:12:40
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.scheduler;

import org.hiylo.scrm.entity.ScrmWorkflowInstanceEntity;
import org.hiylo.scrm.repository.ScrmWorkflowInstanceRepository;
import org.hiylo.scrm.service.ScrmWorkflowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowDelayResumeScheduler} 单元测试。
 * <p>
 * 覆盖调度器三条关键保证:
 * </p>
 * <ul>
 *   <li>单轮内某个实例恢复失败只记日志, 不影响其余实例继续恢复</li>
 *   <li>{@code AtomicBoolean} 重入保护: 上一轮未完成时本轮直接返回 0, 不重叠执行</li>
 *   <li>恢复返回 null (被其他节点 CAS 抢走) 不计入本轮成功数</li>
 * </ul>
 *
 * @author Hsi Chu
 */
@DisplayName("WorkflowDelayResumeScheduler 单元测试")
@ExtendWith(MockitoExtension.class)
class WorkflowDelayResumeSchedulerTest {

    @Mock
    private ScrmWorkflowService workflowService;
    @Mock
    private ScrmWorkflowInstanceRepository instanceRepository;

    @InjectMocks
    private WorkflowDelayResumeScheduler scheduler;

    /**
     * 构造到期实例
     */
    private ScrmWorkflowInstanceEntity dueInstance(Long id, Long version) {
        ScrmWorkflowInstanceEntity instance = new ScrmWorkflowInstanceEntity();
        instance.setId(id);
        instance.setVersion(version);
        instance.setStatus("WAITING");
        instance.setCurrentNodeId("delay_1");
        instance.setNextExecutionAt(LocalDateTime.now().minusMinutes(1));
        return instance;
    }

    @Test
    @DisplayName("resumeDueInstances: 无到期实例时返回 0 且不调用恢复逻辑")
    void resumeDueInstances_noDueInstances() {
        when(instanceRepository.findDueWaitingInstances(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(scheduler.resumeDueInstances()).isZero();
        verifyNoInteractions(workflowService);
    }

    @Test
    @DisplayName("resumeDueInstances: 逐个恢复到期实例并返回成功数, 按到期时间限量扫描")
    void resumeDueInstances_resumesAllDueInstances() throws Exception {
        when(instanceRepository.findDueWaitingInstances(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(dueInstance(1L, 10L), dueInstance(2L, 3L), dueInstance(3L, 8L)));
        when(workflowService.resumeDelayedInstance(anyLong(), anyLong()))
                .thenReturn(new ScrmWorkflowInstanceEntity());

        int resumed = scheduler.resumeDueInstances();

        assertThat(resumed).isEqualTo(3);
        verify(workflowService).resumeDelayedInstance(1L, 10L);
        verify(workflowService).resumeDelayedInstance(2L, 3L);
        verify(workflowService).resumeDelayedInstance(3L, 8L);
        // 扫描带单轮上限, 避免延迟实例集中到期时单轮跑不完
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(instanceRepository).findDueWaitingInstances(any(LocalDateTime.class), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getPageNumber()).isZero();
    }

    @Test
    @DisplayName("resumeDueInstances: 某实例恢复抛异常只记日志, 其余实例继续恢复")
    void resumeDueInstances_oneFailureDoesNotBlockOthers() throws Exception {
        when(instanceRepository.findDueWaitingInstances(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(dueInstance(1L, 1L), dueInstance(2L, 2L), dueInstance(3L, 3L)));
        when(workflowService.resumeDelayedInstance(eq(1L), anyLong()))
                .thenReturn(new ScrmWorkflowInstanceEntity());
        when(workflowService.resumeDelayedInstance(eq(2L), anyLong()))
                .thenThrow(new IllegalStateException("节点执行失败"));
        when(workflowService.resumeDelayedInstance(eq(3L), anyLong()))
                .thenReturn(new ScrmWorkflowInstanceEntity());

        assertThat(scheduler.resumeDueInstances()).isEqualTo(2);
        verify(workflowService, times(3)).resumeDelayedInstance(anyLong(), anyLong());
    }

    @Test
    @DisplayName("resumeDueInstances: 恢复返回 null (被其他节点 CAS 抢走) 不计入成功数")
    void resumeDueInstances_skipsInstancesClaimedByOtherNodes() throws Exception {
        when(instanceRepository.findDueWaitingInstances(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(dueInstance(1L, 1L), dueInstance(2L, 2L)));
        when(workflowService.resumeDelayedInstance(eq(1L), anyLong())).thenReturn(null);
        when(workflowService.resumeDelayedInstance(eq(2L), anyLong()))
                .thenReturn(new ScrmWorkflowInstanceEntity());

        assertThat(scheduler.resumeDueInstances()).isEqualTo(1);
    }

    @Test
    @DisplayName("resumeDueInstances: 扫描异常时返回 0 并释放重入标记, 下一轮仍可正常扫描")
    void resumeDueInstances_scanFailureReleasesGuard() {
        when(instanceRepository.findDueWaitingInstances(any(LocalDateTime.class), any(Pageable.class)))
                .thenThrow(new RuntimeException("数据库不可用"))
                .thenReturn(List.of());

        assertThat(scheduler.resumeDueInstances()).isZero();
        // 若异常路径没有把 running 置回 false, 这里会被重入保护挡住而不调用仓库
        assertThat(scheduler.resumeDueInstances()).isZero();
        verify(instanceRepository, times(2))
                .findDueWaitingInstances(any(LocalDateTime.class), any(Pageable.class));
    }

    @Test
    @DisplayName("resumeDueInstances: 上一轮未结束时重入调用直接返回 0, 不重叠扫描")
    void resumeDueInstances_reentrancyGuardSkipsOverlappingRun() throws Exception {
        AtomicInteger innerResult = new AtomicInteger(-1);
        AtomicInteger calls = new AtomicInteger();
        when(instanceRepository.findDueWaitingInstances(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(dueInstance(1L, 1L)));
        when(workflowService.resumeDelayedInstance(eq(1L), anyLong())).thenAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) {
                // 模拟上一轮尚未结束时调度线程再次触发
                innerResult.set(scheduler.resumeDueInstances());
            }
            return new ScrmWorkflowInstanceEntity();
        });

        assertThat(scheduler.resumeDueInstances()).isEqualTo(1);
        assertThat(innerResult.get()).isZero();
        // 重入的那一轮没有触碰仓库
        verify(instanceRepository, times(1))
                .findDueWaitingInstances(any(LocalDateTime.class), any(Pageable.class));
    }
}
