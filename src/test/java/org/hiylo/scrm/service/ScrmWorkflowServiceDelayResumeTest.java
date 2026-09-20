/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkflowServiceDelayResumeTest.java
 * Date : 2026/09/19 21:20:19
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.entity.ScrmWorkflowEntity;
import org.hiylo.scrm.entity.ScrmWorkflowInstanceEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmWorkflowInstanceRepository;
import org.hiylo.scrm.repository.ScrmWorkflowNodeLogRepository;
import org.hiylo.scrm.repository.ScrmWorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmWorkflowService#resumeDelayedInstance} 单元测试。
 * <p>
 * 多节点部署下同一到期实例会被多个调度器同时扫到, 恢复入口用
 * {@code status = WAITING AND version = 期望版本} 的比较并交换 (claimWaitingInstance) 抢锁:
 * </p>
 * <ul>
 *   <li>CAS 返回 0 (被其他节点抢走) → 直接返回 null, 不再读实例也不再流转, 保证延迟节点不被重复执行</li>
 *   <li>CAS 返回 1 → 从当前节点的下一条边继续推进实例</li>
 *   <li>抢占成功但实例已不存在 → 抛 ScrmException, 不静默吞掉</li>
 * </ul>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmWorkflowService 延迟恢复 CAS 语义单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmWorkflowServiceDelayResumeTest {

    /** 实例 ID */
    private static final Long INSTANCE_ID = 900L;
    /** 工作流 ID */
    private static final Long WORKFLOW_ID = 800L;
    /** 扫描时读到的乐观锁版本 */
    private static final Long EXPECTED_VERSION = 7L;

    @Mock
    private ScrmWorkflowRepository workflowRepository;
    @Mock
    private ScrmWorkflowInstanceRepository instanceRepository;
    @Mock
    private ScrmWorkflowNodeLogRepository nodeLogRepository;
    @Mock
    private ScrmCustomerRepository customerRepository;
    @Mock
    private WorkflowActionExecutor actionExecutor;

    /** 被测服务 (条件评估器用真实实例) */
    private ScrmWorkflowService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        ScrmWorkflowDefinitionService definitionService =
                new ScrmWorkflowDefinitionService(workflowRepository, instanceRepository, nodeLogRepository,
                        objectMapper);
        ScrmWorkflowNodeService nodeService = new ScrmWorkflowNodeService(workflowRepository, instanceRepository,
                nodeLogRepository, customerRepository, new CustomerConditionEvaluator(objectMapper),
                actionExecutor, objectMapper, definitionService);
        ScrmWorkflowInstanceService instanceService = new ScrmWorkflowInstanceService(workflowRepository,
                instanceRepository, nodeLogRepository, definitionService, nodeService);
        ScrmWorkflowStatsService statsService = new ScrmWorkflowStatsService(workflowRepository,
                instanceRepository, nodeLogRepository, definitionService);
        service = new ScrmWorkflowService(definitionService, instanceService, nodeService, statsService);
    }

    /**
     * 构造处于 RUNNING (已被 CAS 抢占) 的延迟实例
     */
    private ScrmWorkflowInstanceEntity runningInstance() {
        ScrmWorkflowInstanceEntity instance = new ScrmWorkflowInstanceEntity();
        instance.setId(INSTANCE_ID);
        instance.setWorkflowId(WORKFLOW_ID);
        instance.setWorkflowName("唤醒工作流");
        instance.setCustomerId(500L);
        instance.setStatus("RUNNING");
        instance.setCurrentNodeId("delay_1");
        instance.setCurrentNodeType("DELAY");
        instance.setVersion(EXPECTED_VERSION + 1);
        instance.setStartedAt(LocalDateTime.now().minusDays(2));
        return instance;
    }

    /**
     * 构造无出边的工作流 (流转至当前节点后无可达节点即完成实例)
     */
    private ScrmWorkflowEntity workflow(String edges) {
        ScrmWorkflowEntity workflow = new ScrmWorkflowEntity();
        workflow.setId(WORKFLOW_ID);
        workflow.setWorkflowName("唤醒工作流");
        workflow.setNodes("[{\"id\":\"delay_1\",\"type\":\"DELAY\"}]");
        workflow.setEdges(edges);
        workflow.setExecutionCount(0);
        workflow.setSuccessCount(0);
        workflow.setActiveInstanceCount(1);
        return workflow;
    }

    @Test
    @DisplayName("resumeDelayedInstance: CAS 返回 0 (被其他节点抢走) 时返回 null 且不重复执行流转")
    void resumeDelayedInstance_lostClaimSkips() throws Exception {
        when(instanceRepository.claimWaitingInstance(eq(INSTANCE_ID), eq(EXPECTED_VERSION),
                any(LocalDateTime.class))).thenReturn(0);

        ScrmWorkflowInstanceEntity result = service.resumeDelayedInstance(INSTANCE_ID, EXPECTED_VERSION);

        assertThat(result).isNull();
        // 未抢到锁就不该再读实例 / 查工作流 / 落库, 否则延迟节点会被执行两次
        verify(instanceRepository, never()).findById(any());
        verify(instanceRepository, never()).save(any());
        verifyNoInteractions(workflowRepository, nodeLogRepository, actionExecutor);
    }

    @Test
    @DisplayName("resumeDelayedInstance: CAS 按 status+version 抢占, 参数原样透传")
    void resumeDelayedInstance_passesExpectedVersion() throws Exception {
        when(instanceRepository.claimWaitingInstance(eq(INSTANCE_ID), eq(3L),
                any(LocalDateTime.class))).thenReturn(0);

        service.resumeDelayedInstance(INSTANCE_ID, 3L);

        verify(instanceRepository).claimWaitingInstance(eq(INSTANCE_ID), eq(3L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("resumeDelayedInstance: CAS 返回 1 时继续推进实例直到完成")
    void resumeDelayedInstance_claimedAdvancesInstance() throws Exception {
        ScrmWorkflowInstanceEntity instance = runningInstance();
        ScrmWorkflowEntity workflow = workflow("[]");
        when(instanceRepository.claimWaitingInstance(eq(INSTANCE_ID), eq(EXPECTED_VERSION),
                any(LocalDateTime.class))).thenReturn(1);
        when(instanceRepository.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));
        when(nodeLogRepository.findByInstanceIdOrderBySequence(INSTANCE_ID)).thenReturn(List.of());
        when(instanceRepository.save(any(ScrmWorkflowInstanceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(workflowRepository.save(any(ScrmWorkflowEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScrmWorkflowInstanceEntity result = service.resumeDelayedInstance(INSTANCE_ID, EXPECTED_VERSION);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        // 当前节点的下一条边被走过: 无出边即完成实例, 并回写统计
        verify(workflowRepository, atLeastOnce()).findById(WORKFLOW_ID);
        verify(instanceRepository).save(instance);
        verify(workflowRepository).save(workflow);
        assertThat(workflow.getSuccessCount()).isEqualTo(1);
        assertThat(workflow.getActiveInstanceCount()).isZero();
    }

    @Test
    @DisplayName("resumeDelayedInstance: 沿下一条边继续执行下一个节点 (不重复执行延迟节点)")
    void resumeDelayedInstance_followsNextEdge() throws Exception {
        ScrmWorkflowInstanceEntity instance = runningInstance();
        ScrmWorkflowEntity workflow = workflow("[{\"from\":\"delay_1\",\"to\":\"end_1\"}]");
        workflow.setNodes("[{\"id\":\"delay_1\",\"type\":\"DELAY\"},"
                + "{\"id\":\"end_1\",\"type\":\"END\",\"name\":\"结束\"}]");
        when(instanceRepository.claimWaitingInstance(eq(INSTANCE_ID), eq(EXPECTED_VERSION),
                any(LocalDateTime.class))).thenReturn(1);
        when(instanceRepository.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));
        when(nodeLogRepository.findByInstanceIdOrderBySequence(INSTANCE_ID)).thenReturn(List.of());
        when(nodeLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(instanceRepository.save(any(ScrmWorkflowInstanceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(workflowRepository.save(any(ScrmWorkflowEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScrmWorkflowInstanceEntity result = service.resumeDelayedInstance(INSTANCE_ID, EXPECTED_VERSION);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        // 下一个节点 (END) 被登记执行, 说明流转确实往下走了一步
        assertThat(result.getCurrentNodeId()).isEqualTo("end_1");
        verify(nodeLogRepository, atLeastOnce()).save(any());
        verify(actionExecutor, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("resumeDelayedInstance: 抢占成功但实例已不存在时抛 ScrmException (不静默成功)")
    void resumeDelayedInstance_instanceMissingAfterClaim() {
        when(instanceRepository.claimWaitingInstance(eq(INSTANCE_ID), eq(EXPECTED_VERSION),
                any(LocalDateTime.class))).thenReturn(1);
        when(instanceRepository.findById(INSTANCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resumeDelayedInstance(INSTANCE_ID, EXPECTED_VERSION))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("工作流实例不存在");
    }

    @Test
    @DisplayName("resumeDelayedInstance: 实例已非 WAITING/RUNNING (被取消) 时停止流转不误推进")
    void resumeDelayedInstance_stopsWhenInstanceNotRunning() throws Exception {
        ScrmWorkflowInstanceEntity instance = runningInstance();
        instance.setStatus("CANCELLED");
        when(instanceRepository.claimWaitingInstance(eq(INSTANCE_ID), eq(EXPECTED_VERSION),
                any(LocalDateTime.class))).thenReturn(1);
        when(instanceRepository.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));

        ScrmWorkflowInstanceEntity result = service.resumeDelayedInstance(INSTANCE_ID, EXPECTED_VERSION);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("CANCELLED");
        // processNextNode 在非 RUNNING 时直接返回: 不查工作流也不落库, 已取消实例不会被复活
        verifyNoInteractions(workflowRepository, nodeLogRepository, actionExecutor);
        verify(instanceRepository, never()).save(any());
    }
}
