/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApprovalServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmApprovalActionDto;
import org.hiylo.scrm.dto.ScrmApprovalFlowDto;
import org.hiylo.scrm.entity.ScrmApprovalFlowEntity;
import org.hiylo.scrm.entity.ScrmApprovalInstanceEntity;
import org.hiylo.scrm.entity.ScrmApprovalLogEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmApprovalFlowRepository;
import org.hiylo.scrm.repository.ScrmApprovalInstanceRepository;
import org.hiylo.scrm.repository.ScrmApprovalLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
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
 * ScrmApprovalService 单元测试
 * <p>
 * 聚焦审批流程定义管理 (创建 / 默认值填充 / 编码唯一性校验)、审批操作
 * (同意 / 驳回 / 状态机校验) 与越权访问校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("ScrmApprovalService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmApprovalServiceTest {

    /** JSON 序列化器, 用于解析审批流程节点定义等 JSON 字段 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 审批流数据仓库 Mock 桩 */
    @Mock
    private ScrmApprovalFlowRepository flowRepository;
    /** 审批实例数据仓库 Mock 桩 */
    @Mock
    private ScrmApprovalInstanceRepository instanceRepository;
    /** 审批日志数据仓库 Mock 桩 */
    @Mock
    private ScrmApprovalLogRepository logRepository;

    /** 被测审批服务实例 */
    private ScrmApprovalService service;

    @BeforeEach
    void setUp() {
        ScrmApprovalFlowService flowService = new ScrmApprovalFlowService(objectMapper, flowRepository);
        ScrmApprovalLogService logService = new ScrmApprovalLogService(instanceRepository, logRepository);
        ScrmApprovalInstanceService instanceService =
                new ScrmApprovalInstanceService(instanceRepository, objectMapper, flowService, logService);
        ScrmApprovalActionService actionService =
                new ScrmApprovalActionService(instanceRepository, flowService, instanceService, logService);
        ScrmApprovalStatsService statsService =
                new ScrmApprovalStatsService(instanceRepository, logRepository, flowService);
        service = new ScrmApprovalService(flowService, instanceService, actionService, logService, statsService);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的审批流程实体 (用于 findById 返回)
     */
    private ScrmApprovalFlowEntity buildFlowEntity(Long id, String status) {
        ScrmApprovalFlowEntity entity = new ScrmApprovalFlowEntity();
        entity.setId(id);
        entity.setFlowName("合同审批流程");
        entity.setFlowCode("FLOW001");
        entity.setFlowType("CONTRACT");
        entity.setNodes("[{\"nodeId\":\"n1\",\"nodeName\":\"开始\",\"nodeType\":\"START\"},"
                + "{\"nodeId\":\"n2\",\"nodeName\":\"审批\",\"nodeType\":\"APPROVE\",\"approverType\":\"USER\",\"approverIds\":\"u1\"},"
                + "{\"nodeId\":\"n3\",\"nodeName\":\"结束\",\"nodeType\":\"END\"}]");
        entity.setStartNode("n1");
        entity.setEndNodes("n3");
        entity.setVersionNumber(1);
        entity.setStatus(status);
        entity.setIsDefault(Boolean.FALSE);
        entity.setUsageCount(0);
        entity.setAllowDelegation(Boolean.TRUE);
        entity.setAllowCountersign(Boolean.FALSE);
        entity.setAllowUrgent(Boolean.TRUE);
        entity.setMaxDurationDays(30);
        return entity;
    }

    /**
     * 构造已持久化的审批实例实体 (用于 findById 返回)
     */
    private ScrmApprovalInstanceEntity buildInstanceEntity(Long id, String status) {
        ScrmApprovalInstanceEntity entity = new ScrmApprovalInstanceEntity();
        entity.setId(id);
        entity.setInstanceNo("AP20260805000001");
        entity.setFlowId(10L);
        entity.setFlowName("合同审批流程");
        entity.setFlowType("CONTRACT");
        entity.setBusinessType("CONTRACT");
        entity.setApplicantId("applicant01");
        entity.setApplicantName("张三");
        entity.setStatus(status);
        entity.setCurrentNodeId("n2");
        entity.setCurrentNodeName("审批");
        entity.setCurrentNodeType("APPROVE");
        entity.setCurrentApproverIds("u1");
        entity.setStartedAt(LocalDateTime.now().minusHours(2));
        entity.setIsUrgent(Boolean.FALSE);
        return entity;
    }

    /**
     * 验证创建审批流程成功场景, 期望写入归属账号并填充默认值后持久化实体
     */
    @Test
    @DisplayName("createFlow: 写入归属账号与默认值后持久化")
    void createFlow_success() throws ScrmException {
        when(flowRepository.findByFlowCode(eq("FLOW001")))
                .thenReturn(Optional.empty());
        when(flowRepository.save(any(ScrmApprovalFlowEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmApprovalFlowDto dto = new ScrmApprovalFlowDto();
        dto.setFlowName("合同审批流程");
        dto.setFlowCode("FLOW001");
        dto.setFlowType("CONTRACT");
        dto.setNodes("[{\"nodeId\":\"n1\",\"nodeType\":\"START\"},"
                + "{\"nodeId\":\"n2\",\"nodeType\":\"APPROVE\",\"approverType\":\"USER\",\"approverIds\":\"u1\"},"
                + "{\"nodeId\":\"n3\",\"nodeType\":\"END\"}]");
        dto.setStartNode("n1");
        dto.setEndNodes("n3");
        dto.setCreatedBy("admin01");

        ScrmApprovalFlowEntity result = service.createFlow(dto);

        ArgumentCaptor<ScrmApprovalFlowEntity> captor =
                ArgumentCaptor.forClass(ScrmApprovalFlowEntity.class);
        verify(flowRepository, times(1)).save(captor.capture());
        ScrmApprovalFlowEntity saved = captor.getValue();
        assertThat(saved.getVersionNumber()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getIsDefault()).isFalse();
        assertThat(saved.getUsageCount()).isZero();
        assertThat(saved.getAllowDelegation()).isTrue();
        assertThat(saved.getAllowCountersign()).isFalse();
        assertThat(saved.getAllowUrgent()).isTrue();
        assertThat(saved.getMaxDurationDays()).isEqualTo(30);
        assertThat(saved.getCreatedBy()).isEqualTo("admin01");
        assertThat(result.getFlowName()).isEqualTo("合同审批流程");
    }

    /**
     * 验证流程编码重复场景, 期望抛出 CONFLICT 异常且不执行保存
     */
    @Test
    @DisplayName("createFlow: 流程编码重复时抛 CONFLICT")
    void createFlow_codeConflict() {
        when(flowRepository.findByFlowCode(eq("FLOW001")))
                .thenReturn(Optional.of(buildFlowEntity(10L, "ACTIVE")));

        ScrmApprovalFlowDto dto = new ScrmApprovalFlowDto();
        dto.setFlowName("合同审批流程");
        dto.setFlowCode("FLOW001");
        dto.setFlowType("CONTRACT");
        dto.setNodes("[{\"nodeId\":\"n1\",\"nodeType\":\"START\"}]");

        assertThatThrownBy(() -> service.createFlow(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("流程编码已存在");
        verify(flowRepository, never()).save(any());
    }

    /**
     * 验证流程类型非法场景, 期望抛出 BAD_REQUEST 异常且不执行保存
     */
    @Test
    @DisplayName("createFlow: 流程类型非法时抛 BAD_REQUEST")
    void createFlow_invalidFlowType() {
        ScrmApprovalFlowDto dto = new ScrmApprovalFlowDto();
        dto.setFlowName("非法流程");
        dto.setFlowCode("FLOW002");
        dto.setFlowType("INVALID_TYPE");
        dto.setNodes("[{\"nodeId\":\"n1\",\"nodeType\":\"START\"}]");

        assertThatThrownBy(() -> service.createFlow(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("流程类型非法");
        verify(flowRepository, never()).save(any());
    }

    /**
     * 验证驳回审批成功场景, 期望状态置为 REJECTED 并记录驳回人与驳回原因
     */
    @Test
    @DisplayName("reject: 驳回审批后状态置 REJECTED 并记录驳回人")
    void reject_success() throws ScrmException {
        ScrmApprovalInstanceEntity instance = buildInstanceEntity(50L, "APPROVING");
        when(instanceRepository.findById(50L)).thenReturn(Optional.of(instance));
        when(instanceRepository.save(any(ScrmApprovalInstanceEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(logRepository.findByInstanceIdOrderBySequence(eq(50L)))
                .thenReturn(Collections.emptyList());
        when(logRepository.save(any(ScrmApprovalLogEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmApprovalActionDto actionDto = new ScrmApprovalActionDto();
        actionDto.setInstanceId(50L);
        actionDto.setAction("REJECT");
        actionDto.setOperatorId("u1");
        actionDto.setOperatorName("李四");
        actionDto.setOperatorRole("MANAGER");
        actionDto.setComment("金额超出预算");

        ScrmApprovalInstanceEntity result = service.reject(actionDto);

        ArgumentCaptor<ScrmApprovalInstanceEntity> captor =
                ArgumentCaptor.forClass(ScrmApprovalInstanceEntity.class);
        verify(instanceRepository, times(2)).save(captor.capture());
        ScrmApprovalInstanceEntity firstSave = captor.getAllValues().get(0);
        assertThat(firstSave.getStatus()).isEqualTo("REJECTED");
        assertThat(firstSave.getRejectedBy()).isEqualTo("u1");
        assertThat(firstSave.getRejectedReason()).isEqualTo("金额超出预算");
        assertThat(firstSave.getCompletedAt()).isNotNull();
        assertThat(firstSave.getApprovedAt()).isNotNull();
        assertThat(firstSave.getDurationHours()).isNotNull();
        assertThat(result.getStatus()).isEqualTo("REJECTED");
        // 记录驳回日志
        verify(logRepository, times(1)).save(any(ScrmApprovalLogEntity.class));
    }

    /**
     * 验证实例已处于 APPROVED 状态时再次审批场景, 期望抛出实例当前状态不可审批的异常
     */
    @Test
    @DisplayName("approve: 实例已 APPROVED 时抛 CONFLICT")
    void approve_wrongStatus() {
        ScrmApprovalInstanceEntity instance = buildInstanceEntity(50L, "APPROVED");
        when(instanceRepository.findById(50L)).thenReturn(Optional.of(instance));

        ScrmApprovalActionDto actionDto = new ScrmApprovalActionDto();
        actionDto.setInstanceId(50L);
        actionDto.setAction("APPROVE");
        actionDto.setOperatorId("u1");
        actionDto.setOperatorName("李四");

        assertThatThrownBy(() -> service.approve(actionDto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("实例当前状态不可审批");
        verify(instanceRepository, never()).save(any());
    }

    /**
     * 验证越权访问审批流程场景, 期望按不存在处理抛出 NOT_FOUND 异常
     */
    
}
