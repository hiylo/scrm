/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractChangeServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmContractChangeDto;
import org.hiylo.scrm.entity.ScrmContractChangeEntity;
import org.hiylo.scrm.entity.ScrmContractEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmContractChangeRepository;
import org.hiylo.scrm.repository.ScrmContractRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmContractChangeService 单元测试
 * <p>
 * 聚焦合同变更 CRUD、状态机流转 (PENDING → APPROVED → EXECUTED / REJECTED / CANCELLED)、
 * 状态校验 (仅 PENDING/IN_REVIEW 可审批/驳回/更新, 仅 APPROVED 可执行, 已执行/已取消不可取消)、
 * 变更执行 (金额变化 + newValue JSON 字段应用)、数据隔离与参数校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmContractChangeService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmContractChangeServiceTest {

    /** 合同变更数据仓库 Mock 桩 */
    @Mock
    private ScrmContractChangeRepository changeRepository;
    /** 合同数据仓库 Mock 桩 */
    @Mock
    private ScrmContractRepository contractRepository;

    /** 被测服务实例 */
    private ScrmContractChangeService service;
    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new ScrmContractChangeService(objectMapper, changeRepository, contractRepository);
    }

    @AfterEach
    void tearDown() {
    }

    // ==================== 变更 CRUD ====================

    @Test
    @DisplayName("createChange: 成功创建, 状态缺省 PENDING, changeDate 缺省今天, 金额缺省 0")
    void createChange_success() throws Exception {
        ScrmContractChangeDto dto = buildChangeDto();
        ScrmContractEntity contract = buildContractEntity(10L);
        when(contractRepository.findById(10L)).thenReturn(Optional.of(contract));
        when(changeRepository.findByChangeNo("CHG_001")).thenReturn(Optional.empty());
        when(changeRepository.save(any(ScrmContractChangeEntity.class)))
                .thenAnswer(inv -> assignId(inv.getArgument(0), 100L));

        ScrmContractChangeDto result = service.createChange(dto);

        assertThat(result.getId()).isEqualTo(100L);
        ArgumentCaptor<ScrmContractChangeEntity> captor = ArgumentCaptor.forClass(ScrmContractChangeEntity.class);
        verify(changeRepository).save(captor.capture());
        ScrmContractChangeEntity saved = captor.getValue();
        assertThat(saved.getContractId()).isEqualTo(10L);
        assertThat(saved.getContractNo()).isEqualTo("HT_001");
        assertThat(saved.getChangeStatus()).isEqualTo("PENDING");
        assertThat(saved.getChangeDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getValueChange()).isEqualTo(0d);
        assertThat(saved.getValueBefore()).isEqualTo(0d);
        assertThat(saved.getValueAfter()).isEqualTo(0d);
    }

    @Test
    @DisplayName("createChange: dto 为 null 抛 BAD_REQUEST")
    void createChange_nullDto() {
        assertThatThrownBy(() -> service.createChange(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("变更参数不能为空");
    }

    @Test
    @DisplayName("createChange: contractId 为 null 抛 BAD_REQUEST")
    void createChange_nullContractId() {
        ScrmContractChangeDto dto = buildChangeDto();
        dto.setContractId(null);
        assertThatThrownBy(() -> service.createChange(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("合同 ID 不能为空");
    }

    @Test
    @DisplayName("createChange: changeNo 为空抛 BAD_REQUEST")
    void createChange_blankChangeNo() {
        ScrmContractChangeDto dto = buildChangeDto();
        dto.setChangeNo("");
        assertThatThrownBy(() -> service.createChange(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("变更编号不能为空");
    }

    @Test
    @DisplayName("createChange: changeType 为空抛 BAD_REQUEST")
    void createChange_blankChangeType() {
        ScrmContractChangeDto dto = buildChangeDto();
        dto.setChangeType("");
        assertThatThrownBy(() -> service.createChange(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("变更类型不能为空");
    }

    @Test
    @DisplayName("createChange: changeReason 为空抛 BAD_REQUEST")
    void createChange_blankChangeReason() {
        ScrmContractChangeDto dto = buildChangeDto();
        dto.setChangeReason("");
        assertThatThrownBy(() -> service.createChange(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("变更原因不能为空");
    }

    @Test
    @DisplayName("createChange: 合同不存在抛 NOT_FOUND")
    void createChange_contractNotFound() {
        ScrmContractChangeDto dto = buildChangeDto();
        when(contractRepository.findById(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createChange(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("合同不存在");
    }

    
    @Test
    @DisplayName("createChange: 变更编号重复抛 CONFLICT")
    void createChange_duplicateChangeNo() {
        ScrmContractChangeDto dto = buildChangeDto();
        ScrmContractEntity contract = buildContractEntity(10L);
        when(contractRepository.findById(10L)).thenReturn(Optional.of(contract));
        when(changeRepository.findByChangeNo("CHG_001")).thenReturn(Optional.of(new ScrmContractChangeEntity()));
        assertThatThrownBy(() -> service.createChange(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("变更编号已存在");
    }

    @Test
    @DisplayName("updateChange: 字段非空才覆盖")
    void updateChange_partialUpdate() throws Exception {
        ScrmContractChangeEntity existing = buildChangeEntity(100L, "PENDING");
        when(changeRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(changeRepository.save(any(ScrmContractChangeEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmContractChangeDto dto = new ScrmContractChangeDto();
        dto.setChangeType("SUPPLEMENT");
        dto.setChangeReason("更新原因");
        ScrmContractChangeDto result = service.updateChange(100L, dto);

        assertThat(result.getChangeType()).isEqualTo("SUPPLEMENT");
        assertThat(result.getChangeReason()).isEqualTo("更新原因");
    }

    @Test
    @DisplayName("updateChange: 仅 PENDING/IN_REVIEW 状态可更新, APPROVED 抛 BAD_REQUEST")
    void updateChange_invalidStatus() {
        ScrmContractChangeEntity existing = buildChangeEntity(100L, "APPROVED");
        when(changeRepository.findById(100L)).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.updateChange(100L, new ScrmContractChangeDto()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 PENDING/IN_REVIEW 可更新");
    }

    @Test
    @DisplayName("updateChange: 不存在抛 NOT_FOUND")
    void updateChange_notFound() {
        when(changeRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateChange(999L, new ScrmContractChangeDto()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("合同变更不存在");
    }

    @Test
    @DisplayName("deleteChange: PENDING 状态可删除")
    void deleteChange_pending_success() throws Exception {
        ScrmContractChangeEntity existing = buildChangeEntity(100L, "PENDING");
        when(changeRepository.findById(100L)).thenReturn(Optional.of(existing));
        service.deleteChange(100L);
        verify(changeRepository).delete(existing);
    }

    @Test
    @DisplayName("deleteChange: CANCELLED 状态可删除")
    void deleteChange_cancelled_success() throws Exception {
        ScrmContractChangeEntity existing = buildChangeEntity(100L, "CANCELLED");
        when(changeRepository.findById(100L)).thenReturn(Optional.of(existing));
        service.deleteChange(100L);
        verify(changeRepository).delete(existing);
    }

    @Test
    @DisplayName("deleteChange: REJECTED 状态可删除")
    void deleteChange_rejected_success() throws Exception {
        ScrmContractChangeEntity existing = buildChangeEntity(100L, "REJECTED");
        when(changeRepository.findById(100L)).thenReturn(Optional.of(existing));
        service.deleteChange(100L);
        verify(changeRepository).delete(existing);
    }

    @Test
    @DisplayName("deleteChange: APPROVED 状态不可删除抛 BAD_REQUEST")
    void deleteChange_approved_throws() {
        ScrmContractChangeEntity existing = buildChangeEntity(100L, "APPROVED");
        when(changeRepository.findById(100L)).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.deleteChange(100L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 PENDING/CANCELLED/REJECTED 可删除");
        verify(changeRepository, never()).delete(any(ScrmContractChangeEntity.class));
    }

    
    @Test
    @DisplayName("getChangeByNo: 不存在抛 NOT_FOUND")
    void getChangeByNo_notFound() {
        when(changeRepository.findByChangeNo("MISSING")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getChangeByNo("MISSING"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("合同变更不存在");
    }

    // ==================== 变更审批与执行 ====================

    @Test
    @DisplayName("approveChange: PENDING → APPROVED, 记录审批人与审批时间")
    void approveChange_success() throws Exception {
        ScrmContractChangeEntity existing = buildChangeEntity(100L, "PENDING");
        when(changeRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(changeRepository.save(any(ScrmContractChangeEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmContractChangeDto result = service.approveChange(100L, 200L);

        assertThat(result.getChangeStatus()).isEqualTo("APPROVED");
        assertThat(result.getApproverId()).isEqualTo(200L);
        assertThat(result.getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("approveChange: APPROVED 状态不可审批抛 BAD_REQUEST")
    void approveChange_invalidStatus() {
        ScrmContractChangeEntity existing = buildChangeEntity(100L, "APPROVED");
        when(changeRepository.findById(100L)).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.approveChange(100L, 200L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 PENDING/IN_REVIEW 可审批");
    }

    @Test
    @DisplayName("rejectChange: IN_REVIEW → REJECTED, 记录驳回原因")
    void rejectChange_success() throws Exception {
        ScrmContractChangeEntity existing = buildChangeEntity(100L, "IN_REVIEW");
        when(changeRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(changeRepository.save(any(ScrmContractChangeEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmContractChangeDto result = service.rejectChange(100L, 200L, "金额不合理");

        assertThat(result.getChangeStatus()).isEqualTo("REJECTED");
        assertThat(result.getApproverId()).isEqualTo(200L);
        assertThat(result.getApprovalNotes()).isEqualTo("金额不合理");
    }

    @Test
    @DisplayName("rejectChange: EXECUTED 状态不可驳回抛 BAD_REQUEST")
    void rejectChange_invalidStatus() {
        ScrmContractChangeEntity existing = buildChangeEntity(100L, "EXECUTED");
        when(changeRepository.findById(100L)).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.rejectChange(100L, 200L, "原因"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 PENDING/IN_REVIEW 可驳回");
    }

    @Test
    @DisplayName("executeChange: APPROVED → EXECUTED, valueChange 应用到合同金额")
    void executeChange_valueChangeApplied() throws Exception {
        ScrmContractChangeEntity existing = buildChangeEntity(100L, "APPROVED");
        existing.setContractId(10L);
        existing.setValueChange(500.0);
        ScrmContractEntity contract = buildContractEntity(10L);
        contract.setContractAmount(1000.0);
        when(changeRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(contractRepository.findById(10L)).thenReturn(Optional.of(contract));
        when(changeRepository.save(any(ScrmContractChangeEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmContractChangeDto result = service.executeChange(100L);

        assertThat(result.getChangeStatus()).isEqualTo("EXECUTED");
        assertThat(result.getEffectiveDate()).isEqualTo(LocalDate.now());
        assertThat(contract.getContractAmount()).isEqualTo(1500.0);
        verify(contractRepository).save(contract);
    }

    @Test
    @DisplayName("executeChange: newValue JSON 字段应用到合同 (contractName / contractType)")
    void executeChange_newValueJsonApplied() throws Exception {
        ScrmContractChangeEntity existing = buildChangeEntity(100L, "APPROVED");
        existing.setContractId(10L);
        existing.setValueChange(0d);
        existing.setNewValue("{\"contractName\":\"新名称\",\"contractType\":\"SERVICE\"}");
        ScrmContractEntity contract = buildContractEntity(10L);
        contract.setContractAmount(1000.0);
        when(changeRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(contractRepository.findById(10L)).thenReturn(Optional.of(contract));
        when(changeRepository.save(any(ScrmContractChangeEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.executeChange(100L);

        assertThat(contract.getContractName()).isEqualTo("新名称");
        assertThat(contract.getContractType()).isEqualTo("SERVICE");
    }

    @Test
    @DisplayName("executeChange: 非 APPROVED 状态抛 BAD_REQUEST")
    void executeChange_invalidStatus() {
        ScrmContractChangeEntity existing = buildChangeEntity(100L, "PENDING");
        when(changeRepository.findById(100L)).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.executeChange(100L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 APPROVED 可执行");
    }

    @Test
    @DisplayName("cancelChange: PENDING → CANCELLED")
    void cancelChange_success() throws Exception {
        ScrmContractChangeEntity existing = buildChangeEntity(100L, "PENDING");
        when(changeRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(changeRepository.save(any(ScrmContractChangeEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmContractChangeDto result = service.cancelChange(100L);
        assertThat(result.getChangeStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("cancelChange: EXECUTED 状态不可取消抛 BAD_REQUEST")
    void cancelChange_executed_throws() {
        ScrmContractChangeEntity existing = buildChangeEntity(100L, "EXECUTED");
        when(changeRepository.findById(100L)).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.cancelChange(100L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("已执行/已取消的变更不允许取消");
    }

    @Test
    @DisplayName("cancelChange: CANCELLED 状态不可取消抛 BAD_REQUEST")
    void cancelChange_cancelled_throws() {
        ScrmContractChangeEntity existing = buildChangeEntity(100L, "CANCELLED");
        when(changeRepository.findById(100L)).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.cancelChange(100L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("已执行/已取消的变更不允许取消");
    }

    @Test
    @DisplayName("getChangesByContract: 返回指定合同的变更列表")
    void getChangesByContract_success() {
        ScrmContractChangeEntity e1 = buildChangeEntity(100L, "PENDING");
        ScrmContractChangeEntity e2 = buildChangeEntity(101L, "APPROVED");
        when(changeRepository.findByContractIdOrderByCreateTimeAsc(10L))
                .thenReturn(List.of(e1, e2));

        List<ScrmContractChangeDto> result = service.getChangesByContract(10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("getChangeHistory: 返回变更历史列表")
    void getChangeHistory_success() {
        when(changeRepository.findByContractIdOrderByCreateTimeAsc(10L))
                .thenReturn(Collections.emptyList());
        List<ScrmContractChangeDto> result = service.getChangeHistory(10L);
        assertThat(result).isEmpty();
    }

    // ==================== 辅助方法 ====================

    private ScrmContractChangeDto buildChangeDto() {
        ScrmContractChangeDto dto = new ScrmContractChangeDto();
        dto.setContractId(10L);
        dto.setChangeNo("CHG_001");
        dto.setChangeType("AMENDMENT");
        dto.setChangeReason("合同条款修订");
        return dto;
    }

    private ScrmContractEntity buildContractEntity(Long id) {
        ScrmContractEntity entity = new ScrmContractEntity();
        entity.setId(id);
        entity.setContractNo("HT_001");
        entity.setContractName("测试合同");
        entity.setContractType("SALES");
        entity.setContractAmount(1000.0);
        entity.setCurrency("CNY");
        entity.setStatus("ACTIVE");
        return entity;
    }

    private ScrmContractChangeEntity buildChangeEntity(Long id, String status) {
        ScrmContractChangeEntity entity = new ScrmContractChangeEntity();
        entity.setId(id);
        entity.setContractId(10L);
        entity.setContractNo("HT_001");
        entity.setChangeNo("CHG_001");
        entity.setChangeType("AMENDMENT");
        entity.setChangeReason("原因");
        entity.setChangeStatus(status);
        entity.setChangeDate(LocalDate.now());
        entity.setValueChange(0d);
        entity.setValueBefore(0d);
        entity.setValueAfter(0d);
        return entity;
    }

    private ScrmContractChangeEntity assignId(ScrmContractChangeEntity entity, Long id) {
        entity.setId(id);
        return entity;
    }
}
