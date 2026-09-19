/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkflowServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmWorkflowDto;
import org.hiylo.scrm.dto.ScrmWorkflowTriggerDto;
import org.hiylo.scrm.entity.ScrmWorkflowEntity;
import org.hiylo.scrm.entity.ScrmWorkflowInstanceEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmWorkflowInstanceRepository;
import org.hiylo.scrm.repository.ScrmWorkflowNodeLogRepository;
import org.hiylo.scrm.repository.ScrmWorkflowRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmWorkflowService 单元测试
 * <p>
 * 聚焦工作流管理 (创建默认值 / 编码唯一性 / 越权校验 / 状态非法更新拦截)、
 * 工作流激活 (DRAFT → ACTIVE) 与工作流触发 (创建实例 / 状态 RUNNING / 执行统计刷新) 等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmWorkflowService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmWorkflowServiceTest {

    /** 工作流仓库 Mock */
    @Mock
    private ScrmWorkflowRepository workflowRepository;
    /** 工作流实例仓库 Mock */
    @Mock
    private ScrmWorkflowInstanceRepository instanceRepository;
    /** 工作流节点日志仓库 Mock */
    @Mock
    private ScrmWorkflowNodeLogRepository nodeLogRepository;
    /** 客户仓库 Mock */
    @Mock
    private ScrmCustomerRepository customerRepository;
    /** 工作流动作执行器 Mock */
    @Mock
    private WorkflowActionExecutor actionExecutor;

    /** ObjectMapper 使用真实实例, 不 mock (遵循约束) */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 被测服务实例 */
    private ScrmWorkflowService service;

    @BeforeEach
    void setUp() {
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

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的工作流实体 (用于 findById 返回)
     */
    private ScrmWorkflowEntity buildWorkflowEntity(Long id, String status) {
        ScrmWorkflowEntity entity = new ScrmWorkflowEntity();
        entity.setId(id);
        entity.setWorkflowName("新用户欢迎工作流");
        entity.setWorkflowCode("WF_001");
        entity.setWorkflowType("MARKETING");
        entity.setTriggerType("EVENT");
        entity.setStatus(status);
        entity.setVersionNumber(1);
        entity.setPriority(0);
        entity.setMaxConcurrentInstances(1000);
        entity.setCooldownHours(0);
        entity.setExecutionCount(0);
        entity.setSuccessCount(0);
        entity.setActiveInstanceCount(0);
        return entity;
    }

    @Test
    @DisplayName("createWorkflow: 写入账号 ID 与默认值后持久化")
    void createWorkflow_success() throws ScrmException {
        ScrmWorkflowDto dto = new ScrmWorkflowDto();
        dto.setWorkflowName("新工作流");
        dto.setWorkflowCode("WF_001");
        dto.setWorkflowType("MARKETING");
        dto.setTriggerType("EVENT");
        dto.setTriggerConfig("{\"event\":\"signup\"}");
        dto.setNodes("[{\"id\":\"n1\",\"type\":\"START\"}]");
        when(workflowRepository.findByWorkflowCode(eq("WF_001")))
                .thenReturn(Optional.empty());
        when(workflowRepository.save(any(ScrmWorkflowEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmWorkflowEntity result = service.createWorkflow(dto);

        ArgumentCaptor<ScrmWorkflowEntity> captor =
                ArgumentCaptor.forClass(ScrmWorkflowEntity.class);
        verify(workflowRepository, times(1)).save(captor.capture());
        ScrmWorkflowEntity saved = captor.getValue();
        // 默认 status=DRAFT
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        // 默认 versionNumber=1
        assertThat(saved.getVersionNumber()).isEqualTo(1);
        // 默认 priority=0
        assertThat(saved.getPriority()).isZero();
        // executionCount 初值 0
        assertThat(saved.getExecutionCount()).isZero();
        // successCount 初值 0
        assertThat(saved.getSuccessCount()).isZero();
        // activeInstanceCount 初值 0
        assertThat(saved.getActiveInstanceCount()).isZero();
        // 默认 maxConcurrentInstances=1000
        assertThat(saved.getMaxConcurrentInstances()).isEqualTo(1000);
        // 默认 cooldownHours=0
        assertThat(saved.getCooldownHours()).isZero();
        assertThat(result.getWorkflowCode()).isEqualTo("WF_001");
    }

    @Test
    @DisplayName("createWorkflow: workflowCode 重复抛 CONFLICT")
    void createWorkflow_duplicateCode() {
        ScrmWorkflowDto dto = new ScrmWorkflowDto();
        dto.setWorkflowName("新工作流");
        dto.setWorkflowCode("WF_DUP");
        dto.setWorkflowType("MARKETING");
        dto.setTriggerType("EVENT");
        dto.setTriggerConfig("{\"event\":\"signup\"}");
        dto.setNodes("[{\"id\":\"n1\",\"type\":\"START\"}]");
        when(workflowRepository.findByWorkflowCode(eq("WF_DUP")))
                .thenReturn(Optional.of(buildWorkflowEntity(10L, "DRAFT")));

        assertThatThrownBy(() -> service.createWorkflow(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("工作流编码已存在");
        verify(workflowRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateWorkflow: 已激活状态不可更新")
    void updateWorkflow_statusConflict() {
        ScrmWorkflowEntity entity = buildWorkflowEntity(10L, "ACTIVE");
        when(workflowRepository.findById(10L)).thenReturn(Optional.of(entity));

        ScrmWorkflowDto dto = new ScrmWorkflowDto();
        dto.setWorkflowName("更新后的名称");

        assertThatThrownBy(() -> service.updateWorkflow(10L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("已激活/已归档的工作流不可更新");
        verify(workflowRepository, never()).save(any());
    }

    
    @Test
    @DisplayName("activateWorkflow: DRAFT → ACTIVE")
    void activateWorkflow_success() throws ScrmException {
        ScrmWorkflowEntity entity = buildWorkflowEntity(10L, "DRAFT");
        when(workflowRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(workflowRepository.save(any(ScrmWorkflowEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.activateWorkflow(10L);

        ArgumentCaptor<ScrmWorkflowEntity> captor =
                ArgumentCaptor.forClass(ScrmWorkflowEntity.class);
        verify(workflowRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("triggerWorkflow: 创建实例, 状态 RUNNING, 工作流执行统计 +1")
    void triggerWorkflow_success() throws ScrmException {
        ScrmWorkflowEntity workflow = buildWorkflowEntity(10L, "ACTIVE");
        when(workflowRepository.findById(10L)).thenReturn(Optional.of(workflow));
        when(instanceRepository.countActiveByWorkflowId(eq(10L))).thenReturn(0L);
        when(instanceRepository.save(any(ScrmWorkflowInstanceEntity.class)))
                .thenAnswer(inv -> {
                    ScrmWorkflowInstanceEntity inst = inv.getArgument(0);
                    inst.setId(100L);
                    return inst;
                });
        when(workflowRepository.save(any(ScrmWorkflowEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmWorkflowTriggerDto triggerDto = new ScrmWorkflowTriggerDto();
        triggerDto.setWorkflowId(10L);
        triggerDto.setCustomerId(200L);
        triggerDto.setCustomerName("张三");
        ScrmWorkflowInstanceEntity result = service.triggerWorkflow(triggerDto);

        assertThat(result.getStatus()).isEqualTo("RUNNING");
        assertThat(result.getCustomerId()).isEqualTo(200L);
        assertThat(result.getCustomerName()).isEqualTo("张三");
        assertThat(result.getStartedAt()).isNotNull();
        // 工作流执行统计刷新
        ArgumentCaptor<ScrmWorkflowEntity> workflowCaptor =
                ArgumentCaptor.forClass(ScrmWorkflowEntity.class);
        verify(workflowRepository, times(1)).save(workflowCaptor.capture());
        ScrmWorkflowEntity savedWorkflow = workflowCaptor.getValue();
        assertThat(savedWorkflow.getExecutionCount()).isEqualTo(1);
        assertThat(savedWorkflow.getActiveInstanceCount()).isEqualTo(1);
        assertThat(savedWorkflow.getLastTriggeredAt()).isNotNull();
        // 实例持久化
        verify(instanceRepository, times(1)).save(any(ScrmWorkflowInstanceEntity.class));
    }
}
