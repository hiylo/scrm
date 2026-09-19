/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMassSendServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmMassSendTaskDto;
import org.hiylo.scrm.dto.ScrmMassSendTargetDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmMassSendTargetEntity;
import org.hiylo.scrm.entity.ScrmMassSendTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmCustomerTagRepository;
import org.hiylo.scrm.repository.ScrmMassSendTargetRepository;
import org.hiylo.scrm.repository.ScrmMassSendTaskRepository;
import org.hiylo.scrm.repository.ScrmTagCustomerRepository;
import org.hiylo.scrm.vo.MassSendReportVo;
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
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmMassSendService 单元测试
 * <p>
 * 聚焦群发任务生命周期管理 (创建 / 更新草稿 / 发布 / 暂停 / 恢复 / 取消)、
 * 目标客户筛选 (全量 / 标签 / 列表 / 分群)、发送结果报告统计、
 * 参数校验与越权隔离等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmMassSendService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmMassSendServiceTest {

    /** 群发任务仓库 Mock */
    @Mock
    private ScrmMassSendTaskRepository taskRepository;
    /** 群发目标仓库 Mock */
    @Mock
    private ScrmMassSendTargetRepository targetRepository;
    /** 客户档案仓库 Mock */
    @Mock
    private ScrmCustomerRepository customerRepository;
    /** 客户标签仓库 Mock */
    @Mock
    private ScrmCustomerTagRepository customerTagRepository;
    /** 客户标签关联仓库 Mock */
    @Mock
    private ScrmTagCustomerRepository tagCustomerRepository;

    /** 被测服务实例 */
    private ScrmMassSendService service;

    @BeforeEach
    void setUp() {
        service = new ScrmMassSendService(taskRepository, targetRepository,
                customerRepository, customerTagRepository, tagCustomerRepository);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造创建任务参数 DTO
     */
    private ScrmMassSendTaskDto buildCreateDto() {
        ScrmMassSendTaskDto dto = new ScrmMassSendTaskDto();
        dto.setTaskName("双十一群发");
        dto.setPlatformType("WECHAT");
        dto.setContent("促销活动");
        dto.setTargetType("ALL");
        dto.setSenderAccountId(200L);
        return dto;
    }

    /**
     * 构造已持久化的群发任务实体 (用于 findById 返回)
     */
    private ScrmMassSendTaskEntity buildTaskEntity(Long id, String status) {
        ScrmMassSendTaskEntity entity = new ScrmMassSendTaskEntity();
        entity.setId(id);
        entity.setTaskName("双十一群发");
        entity.setPlatformType("WECHAT");
        entity.setContent("促销活动");
        entity.setTargetType("ALL");
        entity.setSenderAccountId(200L);
        entity.setStatus(status);
        entity.setTotalCount(0);
        entity.setSentCount(0);
        entity.setSuccessCount(0);
        entity.setFailCount(0);
        return entity;
    }

    /**
     * 构造已持久化的客户实体 (用于筛选目标客户返回)
     */
    private ScrmCustomerEntity buildCustomerEntity(Long id) {
        ScrmCustomerEntity entity = new ScrmCustomerEntity();
        entity.setId(id);
        entity.setNickname("客户" + id);
        entity.setPlatformType("WECHAT");
        entity.setPlatformCustomerUid("wx_" + id);
        return entity;
    }

    @Test
    @DisplayName("createTask: 写入账号 ID 与默认计数后持久化")
    void createTask_success() throws ScrmException {
        ScrmMassSendTaskDto dto = buildCreateDto();
        when(taskRepository.save(any(ScrmMassSendTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmMassSendTaskDto result = service.createTask(dto);

        ArgumentCaptor<ScrmMassSendTaskEntity> captor =
                ArgumentCaptor.forClass(ScrmMassSendTaskEntity.class);
        verify(taskRepository, times(1)).save(captor.capture());
        ScrmMassSendTaskEntity saved = captor.getValue();
        // status 初值为 DRAFT
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        // 计数器初值为 0
        assertThat(saved.getTotalCount()).isZero();
        assertThat(saved.getSentCount()).isZero();
        assertThat(saved.getSuccessCount()).isZero();
        assertThat(saved.getFailCount()).isZero();
        assertThat(result.getTaskName()).isEqualTo("双十一群发");
    }

    @Test
    @DisplayName("createTask: 任务名称为空抛 BAD_REQUEST")
    void createTask_blankName() {
        ScrmMassSendTaskDto dto = buildCreateDto();
        dto.setTaskName("");

        assertThatThrownBy(() -> service.createTask(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("任务名称不能为空");
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTask: 发送账号 ID 为空抛 BAD_REQUEST")
    void createTask_nullSenderAccountId() {
        ScrmMassSendTaskDto dto = buildCreateDto();
        dto.setSenderAccountId(null);

        assertThatThrownBy(() -> service.createTask(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("发送账号 ID 不能为空");
        verify(taskRepository, never()).save(any());
    }

    
    @Test
    @DisplayName("updateTask: 非 DRAFT 状态不允许更新抛 BAD_REQUEST")
    void updateTask_invalidStatus() {
        ScrmMassSendTaskEntity entity = buildTaskEntity(10L, "RUNNING");
        when(taskRepository.findById(10L)).thenReturn(java.util.Optional.of(entity));

        ScrmMassSendTaskDto dto = new ScrmMassSendTaskDto();
        dto.setTaskName("新名称");
        assertThatThrownBy(() -> service.updateTask(10L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 DRAFT 状态的任务允许更新");
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("publishTask: 按 ALL 筛选目标客户生成明细, 状态流转至 RUNNING")
    void publishTask_allTarget() throws ScrmException {
        ScrmMassSendTaskEntity entity = buildTaskEntity(10L, "DRAFT");
        when(taskRepository.findById(10L)).thenReturn(java.util.Optional.of(entity));
        doNothing().when(targetRepository).deleteByTaskId(10L);
        ScrmCustomerEntity c1 = buildCustomerEntity(100L);
        ScrmCustomerEntity c2 = buildCustomerEntity(101L);
        when(customerRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(c1, c2));
        when(targetRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(taskRepository.save(any(ScrmMassSendTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmMassSendTaskDto result = service.publishTask(10L);

        // 验证生成 target 明细
        ArgumentCaptor<List<ScrmMassSendTargetEntity>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(targetRepository, times(1)).saveAll(captor.capture());
        @SuppressWarnings("unchecked")
        List<ScrmMassSendTargetEntity> saved = (List<ScrmMassSendTargetEntity>) captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getTaskId()).isEqualTo(10L);
        assertThat(saved.get(0).getStatus()).isEqualTo("PENDING");
        // 任务 totalCount=2, status=RUNNING
        assertThat(entity.getTotalCount()).isEqualTo(2);
        assertThat(entity.getStatus()).isEqualTo("RUNNING");
        assertThat(entity.getStartedAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("publishTask: 无目标客户抛 BAD_REQUEST")
    void publishTask_noCustomers() {
        ScrmMassSendTaskEntity entity = buildTaskEntity(10L, "DRAFT");
        when(taskRepository.findById(10L)).thenReturn(java.util.Optional.of(entity));
        doNothing().when(targetRepository).deleteByTaskId(10L);
        when(customerRepository.findAll(any(Specification.class)))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.publishTask(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("未筛选到目标客户");
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("publishTask: RUNNING 状态不可发布抛 BAD_REQUEST")
    void publishTask_invalidStatus() {
        ScrmMassSendTaskEntity entity = buildTaskEntity(10L, "RUNNING");
        when(taskRepository.findById(10L)).thenReturn(java.util.Optional.of(entity));

        assertThatThrownBy(() -> service.publishTask(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("任务状态非法, 仅 DRAFT / PENDING 可发布");
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("pauseTask: RUNNING → PAUSED")
    void pauseTask_success() throws ScrmException {
        ScrmMassSendTaskEntity entity = buildTaskEntity(10L, "RUNNING");
        when(taskRepository.findById(10L)).thenReturn(java.util.Optional.of(entity));
        when(taskRepository.save(any(ScrmMassSendTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.pauseTask(10L);

        ArgumentCaptor<ScrmMassSendTaskEntity> captor =
                ArgumentCaptor.forClass(ScrmMassSendTaskEntity.class);
        verify(taskRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PAUSED");
    }

    @Test
    @DisplayName("resumeTask: PAUSED → RUNNING")
    void resumeTask_success() throws ScrmException {
        ScrmMassSendTaskEntity entity = buildTaskEntity(10L, "PAUSED");
        when(taskRepository.findById(10L)).thenReturn(java.util.Optional.of(entity));
        when(taskRepository.save(any(ScrmMassSendTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.resumeTask(10L);

        ArgumentCaptor<ScrmMassSendTaskEntity> captor =
                ArgumentCaptor.forClass(ScrmMassSendTaskEntity.class);
        verify(taskRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("resumeTask: 非 PAUSED 状态不可恢复抛 BAD_REQUEST")
    void resumeTask_invalidStatus() {
        ScrmMassSendTaskEntity entity = buildTaskEntity(10L, "RUNNING");
        when(taskRepository.findById(10L)).thenReturn(java.util.Optional.of(entity));

        assertThatThrownBy(() -> service.resumeTask(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("任务状态非法, 仅 PAUSED 可恢复");
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelTask: 将未发送目标标记失败, 任务流转至 COMPLETED")
    void cancelTask_success() throws ScrmException {
        ScrmMassSendTaskEntity entity = buildTaskEntity(10L, "RUNNING");
        when(taskRepository.findById(10L)).thenReturn(java.util.Optional.of(entity));
        ScrmMassSendTargetEntity pending = new ScrmMassSendTargetEntity();
        pending.setId(20L);
        pending.setStatus("PENDING");
        ScrmMassSendTargetEntity sent = new ScrmMassSendTargetEntity();
        sent.setId(21L);
        sent.setStatus("SENT");
        when(targetRepository.findByTaskId(10L)).thenReturn(List.of(pending, sent));
        when(targetRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(taskRepository.save(any(ScrmMassSendTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.cancelTask(10L);

        // 仅 PENDING 目标被标记为 FAILED
        ArgumentCaptor<List<ScrmMassSendTargetEntity>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(targetRepository, times(1)).saveAll(captor.capture());
        @SuppressWarnings("unchecked")
        List<ScrmMassSendTargetEntity> saved = (List<ScrmMassSendTargetEntity>) captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getStatus()).isEqualTo("FAILED");
        assertThat(saved.get(0).getErrorMessage()).isEqualTo("任务已取消");
        // 任务状态置 COMPLETED
        assertThat(entity.getStatus()).isEqualTo("COMPLETED");
        assertThat(entity.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("cancelTask: COMPLETED 状态幂等返回")
    void cancelTask_idempotent() throws ScrmException {
        ScrmMassSendTaskEntity entity = buildTaskEntity(10L, "COMPLETED");
        when(taskRepository.findById(10L)).thenReturn(java.util.Optional.of(entity));

        ScrmMassSendTaskDto result = service.cancelTask(10L);

        // 终态幂等返回, 不再 save
        verify(taskRepository, never()).save(any());
        verify(targetRepository, never()).saveAll(any());
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("getTaskReport: 按状态聚合目标明细数, 计算成功率与失败率")
    void getTaskReport_success() throws ScrmException {
        ScrmMassSendTaskEntity entity = buildTaskEntity(10L, "COMPLETED");
        entity.setTotalCount(10);
        when(taskRepository.findById(10L)).thenReturn(java.util.Optional.of(entity));
        // SENT=8, FAILED=2, PENDING=0
        when(targetRepository.countByStatus(10L)).thenReturn(List.<Object[]>of(
                new Object[]{"SENT", 8L},
                new Object[]{"FAILED", 2L}));

        MassSendReportVo report = service.getTaskReport(10L);

        assertThat(report.getTaskId()).isEqualTo(10L);
        assertThat(report.getTotalCount()).isEqualTo(10);
        // sentCount = SENT + FAILED = 10
        assertThat(report.getSentCount()).isEqualTo(10);
        assertThat(report.getSuccessCount()).isEqualTo(8);
        assertThat(report.getFailCount()).isEqualTo(2);
        // successRate = 8/10*100 = 80.0
        assertThat(report.getSuccessRate()).isEqualTo(80.0);
        assertThat(report.getFailureRate()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("getTaskReport: sentCount=0 时 successRate/failureRate 为 null")
    void getTaskReport_noSent() throws ScrmException {
        ScrmMassSendTaskEntity entity = buildTaskEntity(10L, "DRAFT");
        entity.setTotalCount(5);
        when(taskRepository.findById(10L)).thenReturn(java.util.Optional.of(entity));
        // 全部 PENDING, 无 SENT/FAILED
        when(targetRepository.countByStatus(10L)).thenReturn(List.<Object[]>of(
                new Object[]{"PENDING", 5L}));

        MassSendReportVo report = service.getTaskReport(10L);

        assertThat(report.getSentCount()).isZero();
        assertThat(report.getPendingCount()).isEqualTo(5);
        assertThat(report.getSuccessRate()).isNull();
        assertThat(report.getFailureRate()).isNull();
    }

    @Test
    @DisplayName("listTasks: 通过 Specification 分页查询并按 createTime 倒序")
    void listTasks_pagination() {
        ScrmMassSendTaskEntity entity = buildTaskEntity(10L, "RUNNING");
        when(taskRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        Page<ScrmMassSendTaskDto> result = service.listTasks("RUNNING", "WECHAT",
                "双十一", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo("RUNNING");
        // 验证分页参数携带 createTime DESC 排序
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(taskRepository, times(1)).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getSort().toString()).contains("createTime: DESC");
    }

    @Test
    @DisplayName("getTaskTargets: 按状态过滤目标明细分页查询")
    void getTaskTargets_withStatus() throws ScrmException {
        ScrmMassSendTaskEntity entity = buildTaskEntity(10L, "RUNNING");
        when(taskRepository.findById(10L)).thenReturn(java.util.Optional.of(entity));
        ScrmMassSendTargetEntity target = new ScrmMassSendTargetEntity();
        target.setId(20L);
        target.setTaskId(10L);
        target.setCustomerId(100L);
        target.setStatus("SENT");
        when(targetRepository.findByTaskIdAndStatus(eq(10L), eq("SENT"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(target)));

        Page<ScrmMassSendTargetDto> result = service.getTaskTargets(10L, "SENT", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo("SENT");
        verify(targetRepository, times(1)).findByTaskIdAndStatus(eq(10L), eq("SENT"), any(Pageable.class));
    }

    @Test
    @DisplayName("getTaskTargets: status 为空时查询全部目标明细")
    void getTaskTargets_noStatus() throws ScrmException {
        ScrmMassSendTaskEntity entity = buildTaskEntity(10L, "RUNNING");
        when(taskRepository.findById(10L)).thenReturn(java.util.Optional.of(entity));
        ScrmMassSendTargetEntity target = new ScrmMassSendTargetEntity();
        target.setId(20L);
        target.setTaskId(10L);
        target.setStatus("PENDING");
        when(targetRepository.findByTaskId(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(target)));

        Page<ScrmMassSendTargetDto> result = service.getTaskTargets(10L, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        verify(targetRepository, times(1)).findByTaskId(eq(10L), any(Pageable.class));
    }
}
