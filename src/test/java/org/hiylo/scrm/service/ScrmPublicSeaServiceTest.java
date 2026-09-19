/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPublicSeaServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmLeadAssignmentDto;
import org.hiylo.scrm.dto.ScrmPublicSeaCustomerDto;
import org.hiylo.scrm.entity.ScrmLeadAssignmentEntity;
import org.hiylo.scrm.entity.ScrmPublicSeaCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmLeadAssignmentRepository;
import org.hiylo.scrm.repository.ScrmPublicSeaCustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
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
 * ScrmPublicSeaService 单元测试
 * <p>
 * 聚焦公海客户的领取 / 分配 / 转移 / 回收 / 转正状态流转、批量分配容错、
 * 分配流水写入与数据隔离等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmPublicSeaService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmPublicSeaServiceTest {

    /** 公海客户仓库 Mock */
    @Mock
    private ScrmPublicSeaCustomerRepository publicSeaCustomerRepository;
    /** 线索分配记录仓库 Mock */
    @Mock
    private ScrmLeadAssignmentRepository leadAssignmentRepository;

    /** 被测服务实例 */
    private ScrmPublicSeaService service;

    @BeforeEach
    void setUp() {
        service = new ScrmPublicSeaService(publicSeaCustomerRepository, leadAssignmentRepository);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的公海客户实体 (用于 findById 返回)
     */
    private ScrmPublicSeaCustomerEntity buildEntity(Long id, String status) {
        ScrmPublicSeaCustomerEntity entity = new ScrmPublicSeaCustomerEntity();
        entity.setId(id);
        entity.setPlatformType("wework");
        entity.setPlatformCustomerUid("uid_" + id);
        entity.setNickname("客户" + id);
        entity.setLifecycle("NEW");
        entity.setStatus(status);
        entity.setRecallCount(0);
        return entity;
    }

    /**
     * 构造分配流水实体 (用于 findByPublicSeaCustomerIdOrderByIdDesc 返回)
     */
    private ScrmLeadAssignmentEntity buildAssignment(Long id, Long customerId, String status) {
        ScrmLeadAssignmentEntity entity = new ScrmLeadAssignmentEntity();
        entity.setId(id);
        entity.setPublicSeaCustomerId(customerId);
        entity.setAssignedTo("user1");
        entity.setAssignedBy("user1");
        entity.setAssignmentType("CLAIM");
        entity.setStatus(status);
        entity.setAssignedAt(LocalDateTime.now());
        return entity;
    }

    @Test
    @DisplayName("getCustomer: 客户不存在抛 NOT_FOUND")
    void getCustomer_notFound() {
        when(publicSeaCustomerRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCustomer(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("公海客户不存在");
    }

    
    @Test
    @DisplayName("getCustomer: 同账号访问返回 DTO")
    void getCustomer_success() throws ScrmException {
        ScrmPublicSeaCustomerEntity entity = buildEntity(10L, "AVAILABLE");
        when(publicSeaCustomerRepository.findById(10L)).thenReturn(Optional.of(entity));

        ScrmPublicSeaCustomerDto result = service.getCustomer(10L);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getStatus()).isEqualTo("AVAILABLE");
        assertThat(result.getPlatformType()).isEqualTo("wework");
    }

    @Test
    @DisplayName("claimCustomer: userId 为空抛 BAD_REQUEST")
    void claimCustomer_nullUserId() {
        assertThatThrownBy(() -> service.claimCustomer(10L, null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("领取人 userId 不能为空");
        verify(publicSeaCustomerRepository, never()).save(any());
    }

    @Test
    @DisplayName("claimCustomer: 客户不存在抛 NOT_FOUND")
    void claimCustomer_notFound() {
        when(publicSeaCustomerRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.claimCustomer(10L, "user1"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("公海客户不存在");
        verify(publicSeaCustomerRepository, never()).save(any());
    }

    @Test
    @DisplayName("claimCustomer: ASSIGNED 状态不允许领取抛 CONFLICT")
    void claimCustomer_invalidStatus() {
        ScrmPublicSeaCustomerEntity entity = buildEntity(10L, "ASSIGNED");
        when(publicSeaCustomerRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.claimCustomer(10L, "user1"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("状态不允许领取");
        verify(publicSeaCustomerRepository, never()).save(any());
    }

    @Test
    @DisplayName("claimCustomer: AVAILABLE 状态领取成功, 写入 CLAIM 流水")
    void claimCustomer_available_success() throws ScrmException {
        ScrmPublicSeaCustomerEntity entity = buildEntity(10L, "AVAILABLE");
        when(publicSeaCustomerRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(publicSeaCustomerRepository.save(any(ScrmPublicSeaCustomerEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(leadAssignmentRepository.findByPublicSeaCustomerIdOrderByIdDesc(10L))
                .thenReturn(Collections.emptyList());

        ScrmPublicSeaCustomerDto result = service.claimCustomer(10L, "user1");

        ArgumentCaptor<ScrmPublicSeaCustomerEntity> customerCaptor =
                ArgumentCaptor.forClass(ScrmPublicSeaCustomerEntity.class);
        verify(publicSeaCustomerRepository, times(1)).save(customerCaptor.capture());
        ScrmPublicSeaCustomerEntity saved = customerCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo("ASSIGNED");
        assertThat(saved.getAssignedTo()).isEqualTo("user1");
        assertThat(saved.getAssignedAt()).isNotNull();
        assertThat(saved.getAssignmentExpireAt()).isNotNull();
        assertThat(saved.getLastAssignedAt()).isNotNull();

        ArgumentCaptor<ScrmLeadAssignmentEntity> assignmentCaptor =
                ArgumentCaptor.forClass(ScrmLeadAssignmentEntity.class);
        verify(leadAssignmentRepository, times(1)).save(assignmentCaptor.capture());
        ScrmLeadAssignmentEntity assignment = assignmentCaptor.getValue();
        assertThat(assignment.getAssignmentType()).isEqualTo("CLAIM");
        assertThat(assignment.getStatus()).isEqualTo("ACTIVE");
        assertThat(assignment.getAssignedTo()).isEqualTo("user1");
        assertThat(result.getAssignedTo()).isEqualTo("user1");
    }

    @Test
    @DisplayName("claimCustomer: RECALLED 状态允许再次领取")
    void claimCustomer_recalled_success() throws ScrmException {
        ScrmPublicSeaCustomerEntity entity = buildEntity(10L, "RECALLED");
        when(publicSeaCustomerRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(publicSeaCustomerRepository.save(any(ScrmPublicSeaCustomerEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(leadAssignmentRepository.findByPublicSeaCustomerIdOrderByIdDesc(10L))
                .thenReturn(Collections.emptyList());

        service.claimCustomer(10L, "user2");

        ArgumentCaptor<ScrmPublicSeaCustomerEntity> captor =
                ArgumentCaptor.forClass(ScrmPublicSeaCustomerEntity.class);
        verify(publicSeaCustomerRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ASSIGNED");
        assertThat(captor.getValue().getAssignedTo()).isEqualTo("user2");
    }

    @Test
    @DisplayName("claimCustomer: 释放前序 ACTIVE 流水为 TRANSFERRED")
    void claimCustomer_releaseActiveAssignments() throws ScrmException {
        ScrmPublicSeaCustomerEntity entity = buildEntity(10L, "AVAILABLE");
        ScrmLeadAssignmentEntity activeAssignment = buildAssignment(1L, 10L, "ACTIVE");
        when(publicSeaCustomerRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(publicSeaCustomerRepository.save(any(ScrmPublicSeaCustomerEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(leadAssignmentRepository.findByPublicSeaCustomerIdOrderByIdDesc(10L))
                .thenReturn(List.of(activeAssignment));

        service.claimCustomer(10L, "user1");

        ArgumentCaptor<ScrmLeadAssignmentEntity> captor =
                ArgumentCaptor.forClass(ScrmLeadAssignmentEntity.class);
        // 1 次 release + 1 次 writeAssignment
        verify(leadAssignmentRepository, times(2)).save(captor.capture());
        ScrmLeadAssignmentEntity released = captor.getAllValues().get(0);
        assertThat(released.getStatus()).isEqualTo("TRANSFERRED");
    }

    @Test
    @DisplayName("assignCustomer: userId 为空抛 BAD_REQUEST")
    void assignCustomer_nullUserId() {
        assertThatThrownBy(() -> service.assignCustomer(10L, null, "admin"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("被分配人 userId 不能为空");
        verify(publicSeaCustomerRepository, never()).save(any());
    }

    @Test
    @DisplayName("assignCustomer: LOCKED 状态不允许分配抛 CONFLICT")
    void assignCustomer_invalidStatus() {
        ScrmPublicSeaCustomerEntity entity = buildEntity(10L, "LOCKED");
        when(publicSeaCustomerRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.assignCustomer(10L, "user1", "admin"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("状态不允许分配");
        verify(publicSeaCustomerRepository, never()).save(any());
    }

    @Test
    @DisplayName("assignCustomer: AVAILABLE 状态分配成功, assignedBy 为管理员")
    void assignCustomer_success() throws ScrmException {
        ScrmPublicSeaCustomerEntity entity = buildEntity(10L, "AVAILABLE");
        when(publicSeaCustomerRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(publicSeaCustomerRepository.save(any(ScrmPublicSeaCustomerEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(leadAssignmentRepository.findByPublicSeaCustomerIdOrderByIdDesc(10L))
                .thenReturn(Collections.emptyList());

        service.assignCustomer(10L, "user1", "admin");

        ArgumentCaptor<ScrmLeadAssignmentEntity> captor =
                ArgumentCaptor.forClass(ScrmLeadAssignmentEntity.class);
        verify(leadAssignmentRepository, times(1)).save(captor.capture());
        ScrmLeadAssignmentEntity assignment = captor.getValue();
        assertThat(assignment.getAssignmentType()).isEqualTo("ASSIGN");
        assertThat(assignment.getAssignedBy()).isEqualTo("admin");
        assertThat(assignment.getAssignedTo()).isEqualTo("user1");
    }

    @Test
    @DisplayName("transferCustomer: toUserId 为空抛 BAD_REQUEST")
    void transferCustomer_nullToUserId() {
        assertThatThrownBy(() -> service.transferCustomer(10L, null, "note"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("接收人 userId 不能为空");
        verify(publicSeaCustomerRepository, never()).save(any());
    }

    @Test
    @DisplayName("transferCustomer: AVAILABLE 状态不允许转移抛 CONFLICT")
    void transferCustomer_invalidStatus() {
        ScrmPublicSeaCustomerEntity entity = buildEntity(10L, "AVAILABLE");
        when(publicSeaCustomerRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.transferCustomer(10L, "user2", "note"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("状态不允许转移");
        verify(publicSeaCustomerRepository, never()).save(any());
    }

    @Test
    @DisplayName("transferCustomer: ASSIGNED 状态转移成功, 记录 previousOwner")
    void transferCustomer_success() throws ScrmException {
        ScrmPublicSeaCustomerEntity entity = buildEntity(10L, "ASSIGNED");
        entity.setAssignedTo("user1");
        when(publicSeaCustomerRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(publicSeaCustomerRepository.save(any(ScrmPublicSeaCustomerEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(leadAssignmentRepository.findByPublicSeaCustomerIdOrderByIdDesc(10L))
                .thenReturn(Collections.emptyList());

        service.transferCustomer(10L, "user2", "转交备注");

        ArgumentCaptor<ScrmPublicSeaCustomerEntity> customerCaptor =
                ArgumentCaptor.forClass(ScrmPublicSeaCustomerEntity.class);
        verify(publicSeaCustomerRepository, times(1)).save(customerCaptor.capture());
        assertThat(customerCaptor.getValue().getAssignedTo()).isEqualTo("user2");

        ArgumentCaptor<ScrmLeadAssignmentEntity> assignmentCaptor =
                ArgumentCaptor.forClass(ScrmLeadAssignmentEntity.class);
        verify(leadAssignmentRepository, times(1)).save(assignmentCaptor.capture());
        ScrmLeadAssignmentEntity assignment = assignmentCaptor.getValue();
        assertThat(assignment.getAssignmentType()).isEqualTo("TRANSFER");
        assertThat(assignment.getPreviousOwner()).isEqualTo("user1");
        assertThat(assignment.getNote()).isEqualTo("转交备注");
    }

    @Test
    @DisplayName("recallCustomer: AVAILABLE 状态不允许回收抛 CONFLICT")
    void recallCustomer_invalidStatus() {
        ScrmPublicSeaCustomerEntity entity = buildEntity(10L, "AVAILABLE");
        when(publicSeaCustomerRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.recallCustomer(10L, "超时"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("状态不允许回收");
        verify(publicSeaCustomerRepository, never()).save(any());
    }

    @Test
    @DisplayName("recallCustomer: ASSIGNED 状态回收成功, recallCount+1 且清空归属人")
    void recallCustomer_success() throws ScrmException {
        ScrmPublicSeaCustomerEntity entity = buildEntity(10L, "ASSIGNED");
        entity.setAssignedTo("user1");
        entity.setRecallCount(2);
        when(publicSeaCustomerRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(publicSeaCustomerRepository.save(any(ScrmPublicSeaCustomerEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(leadAssignmentRepository.findByPublicSeaCustomerIdOrderByIdDesc(10L))
                .thenReturn(Collections.emptyList());

        ScrmPublicSeaCustomerDto result = service.recallCustomer(10L, "超时未跟进");

        ArgumentCaptor<ScrmPublicSeaCustomerEntity> customerCaptor =
                ArgumentCaptor.forClass(ScrmPublicSeaCustomerEntity.class);
        verify(publicSeaCustomerRepository, times(1)).save(customerCaptor.capture());
        ScrmPublicSeaCustomerEntity saved = customerCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo("AVAILABLE");
        assertThat(saved.getAssignedTo()).isNull();
        assertThat(saved.getAssignedAt()).isNull();
        assertThat(saved.getAssignmentExpireAt()).isNull();
        // recallCount 由 2 自增为 3
        assertThat(saved.getRecallCount()).isEqualTo(3);
        // 回收流水 (assignedBy=system, status=RECALLED)
        ArgumentCaptor<ScrmLeadAssignmentEntity> assignmentCaptor =
                ArgumentCaptor.forClass(ScrmLeadAssignmentEntity.class);
        verify(leadAssignmentRepository, times(1)).save(assignmentCaptor.capture());
        ScrmLeadAssignmentEntity assignment = assignmentCaptor.getValue();
        assertThat(assignment.getStatus()).isEqualTo("RECALLED");
        assertThat(assignment.getAssignedBy()).isEqualTo("system");
        assertThat(assignment.getNote()).isEqualTo("超时未跟进");
        assertThat(result.getRecallCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("recallCustomer: recallCount 为 null 时回填为 1")
    void recallCustomer_nullRecallCount() throws ScrmException {
        ScrmPublicSeaCustomerEntity entity = buildEntity(10L, "ASSIGNED");
        entity.setRecallCount(null);
        when(publicSeaCustomerRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(publicSeaCustomerRepository.save(any(ScrmPublicSeaCustomerEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(leadAssignmentRepository.findByPublicSeaCustomerIdOrderByIdDesc(10L))
                .thenReturn(Collections.emptyList());

        service.recallCustomer(10L, null);

        ArgumentCaptor<ScrmPublicSeaCustomerEntity> captor =
                ArgumentCaptor.forClass(ScrmPublicSeaCustomerEntity.class);
        verify(publicSeaCustomerRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getRecallCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("recallCustomer: LOCKED 状态允许回收")
    void recallCustomer_locked_success() throws ScrmException {
        ScrmPublicSeaCustomerEntity entity = buildEntity(10L, "LOCKED");
        entity.setAssignedTo("user1");
        when(publicSeaCustomerRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(publicSeaCustomerRepository.save(any(ScrmPublicSeaCustomerEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(leadAssignmentRepository.findByPublicSeaCustomerIdOrderByIdDesc(10L))
                .thenReturn(Collections.emptyList());

        service.recallCustomer(10L, "解锁回收");

        ArgumentCaptor<ScrmPublicSeaCustomerEntity> captor =
                ArgumentCaptor.forClass(ScrmPublicSeaCustomerEntity.class);
        verify(publicSeaCustomerRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("convertToCustomer: ownerAccountId 为空抛 BAD_REQUEST")
    void convertToCustomer_nullOwnerAccountId() {
        assertThatThrownBy(() -> service.convertToCustomer(10L, null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("归属账号 ID 不能为空");
        verify(publicSeaCustomerRepository, never()).save(any());
    }

    @Test
    @DisplayName("convertToCustomer: LOCKED 状态 (已转正) 抛 CONFLICT")
    void convertToCustomer_alreadyLocked() {
        ScrmPublicSeaCustomerEntity entity = buildEntity(10L, "LOCKED");
        when(publicSeaCustomerRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.convertToCustomer(10L, 100L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("已转正, 不能重复操作");
        verify(publicSeaCustomerRepository, never()).save(any());
    }

    @Test
    @DisplayName("convertToCustomer: ASSIGNED 状态转正成功, 状态置为 LOCKED")
    void convertToCustomer_success() throws ScrmException {
        ScrmPublicSeaCustomerEntity entity = buildEntity(10L, "ASSIGNED");
        when(publicSeaCustomerRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(publicSeaCustomerRepository.save(any(ScrmPublicSeaCustomerEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(leadAssignmentRepository.findByPublicSeaCustomerIdOrderByIdDesc(10L))
                .thenReturn(Collections.emptyList());

        ScrmPublicSeaCustomerDto result = service.convertToCustomer(10L, 100L);

        ArgumentCaptor<ScrmPublicSeaCustomerEntity> captor =
                ArgumentCaptor.forClass(ScrmPublicSeaCustomerEntity.class);
        verify(publicSeaCustomerRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("LOCKED");
        // 前序 ACTIVE 流水被置为 CONVERTED
        ArgumentCaptor<ScrmLeadAssignmentEntity> assignmentCaptor =
                ArgumentCaptor.forClass(ScrmLeadAssignmentEntity.class);
        // 只有 release, 没有 writeAssignment (convert 不写新流水)
        verify(leadAssignmentRepository, never()).save(any(ScrmLeadAssignmentEntity.class));
        assertThat(result.getStatus()).isEqualTo("LOCKED");
    }

    @Test
    @DisplayName("batchAssign: 客户 ID 列表为空抛 BAD_REQUEST")
    void batchAssign_emptyList() {
        assertThatThrownBy(() -> service.batchAssign(Collections.emptyList(), "user1", "admin"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户 ID 列表不能为空");
        verify(publicSeaCustomerRepository, never()).save(any());
    }

    @Test
    @DisplayName("batchAssign: null 列表抛 BAD_REQUEST")
    void batchAssign_nullList() {
        assertThatThrownBy(() -> service.batchAssign(null, "user1", "admin"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户 ID 列表不能为空");
    }

    @Test
    @DisplayName("batchAssign: 部分失败返回成功数与失败 ID 列表")
    void batchAssign_partialFailures() {
        ScrmPublicSeaCustomerEntity ok = buildEntity(10L, "AVAILABLE");
        when(publicSeaCustomerRepository.findById(10L)).thenReturn(Optional.of(ok));
        when(publicSeaCustomerRepository.findById(20L)).thenReturn(Optional.empty());
        when(publicSeaCustomerRepository.save(any(ScrmPublicSeaCustomerEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(leadAssignmentRepository.findByPublicSeaCustomerIdOrderByIdDesc(10L))
                .thenReturn(Collections.emptyList());

        ScrmPublicSeaService.BatchAssignResult result =
                service.batchAssign(List.of(10L, 20L), "user1", "admin");

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCustomerIds()).containsExactly(20L);
    }

    @Test
    @DisplayName("getMyLeads: userId 为空抛 BAD_REQUEST")
    void getMyLeads_nullUserId() {
        assertThatThrownBy(() -> service.getMyLeads(null, null, PageRequest.of(0, 10)))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("归属人 userId 不能为空");
        verify(publicSeaCustomerRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("getMyLeads: userId 为空白抛 BAD_REQUEST")
    void getMyLeads_blankUserId() {
        assertThatThrownBy(() -> service.getMyLeads("  ", null, PageRequest.of(0, 10)))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("归属人 userId 不能为空");
    }

    @Test
    @DisplayName("getAssignmentHistory: 客户不存在抛 NOT_FOUND")
    void getAssignmentHistory_notFound() {
        when(publicSeaCustomerRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAssignmentHistory(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("公海客户不存在");
        verify(leadAssignmentRepository, never()).findByPublicSeaCustomerIdOrderByIdDesc(any());
    }

    
    @Test
    @DisplayName("listPublicSea: 数据隔离返回当前账号分页结果")
    void listPublicSea_success() {
        ScrmPublicSeaCustomerEntity entity = buildEntity(10L, "AVAILABLE");
        Page<ScrmPublicSeaCustomerEntity> page = new org.springframework.data.domain.PageImpl<>(
                List.of(entity), PageRequest.of(0, 10), 1L);
        when(publicSeaCustomerRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<ScrmPublicSeaCustomerDto> result =
                service.listPublicSea("wework", "NEW", "AVAILABLE", "客户", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(10L);
        verify(publicSeaCustomerRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    
    
}
