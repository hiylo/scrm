/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLifecycleServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmLifecycleStageDto;
import org.hiylo.scrm.dto.ScrmLifecycleTransitionActionDto;
import org.hiylo.scrm.dto.ScrmLifecycleTransitionDto;
import org.hiylo.scrm.entity.ScrmCustomerLifecycleEntity;
import org.hiylo.scrm.entity.ScrmLifecycleHistoryEntity;
import org.hiylo.scrm.entity.ScrmLifecycleStageEntity;
import org.hiylo.scrm.entity.ScrmLifecycleTransitionEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCustomerLifecycleRepository;
import org.hiylo.scrm.repository.ScrmLifecycleHistoryRepository;
import org.hiylo.scrm.repository.ScrmLifecycleStageRepository;
import org.hiylo.scrm.repository.ScrmLifecycleTransitionRepository;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmLifecycleService 单元测试
 * <p>
 * 聚焦阶段 CRUD (默认值填充 / stageCode 唯一性 / stageCategory 枚举校验)、流转规则 CRUD
 * (目标阶段存在性 / 编码一致性 / 转换类型枚举)、客户生命周期转换 (同阶段跳过 / 新客户进入起始阶段 /
 * 无可用规则拒绝 / 冷却期校验 / 历史记录 / 触发次数累加)、批量转换、事件触发转换、
 * 数据隔离与统计 (getLifecycleStats / getStageFunnel) 等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmLifecycleService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmLifecycleServiceTest {

    /** 生命周期阶段仓库 Mock */
    @Mock
    private ScrmLifecycleStageRepository stageRepository;
    /** 生命周期流转规则仓库 Mock */
    @Mock
    private ScrmLifecycleTransitionRepository transitionRepository;
    /** 客户生命周期仓库 Mock */
    @Mock
    private ScrmCustomerLifecycleRepository customerLifecycleRepository;
    /** 生命周期历史记录仓库 Mock */
    @Mock
    private ScrmLifecycleHistoryRepository historyRepository;

    /** 被测服务实例 */
    private ScrmLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new ScrmLifecycleService(stageRepository, transitionRepository,
                customerLifecycleRepository, historyRepository);
    }

    @AfterEach
    void tearDown() {
    }

    // ==================== 阶段 CRUD ====================

    @Test
    @DisplayName("createStage: 成功创建, enabled/isStartStage/isEndStage/isChurnStage 缺省填充默认值")
    void createStage_success() throws Exception {
        ScrmLifecycleStageDto dto = buildStageDto();
        when(stageRepository.findByStageCode("LEAD")).thenReturn(Optional.empty());
        when(stageRepository.save(any(ScrmLifecycleStageEntity.class)))
                .thenAnswer(inv -> assignStageId(inv.getArgument(0), 100L));

        ScrmLifecycleStageEntity result = service.createStage(dto);

        assertThat(result.getId()).isEqualTo(100L);
        ArgumentCaptor<ScrmLifecycleStageEntity> captor = ArgumentCaptor.forClass(ScrmLifecycleStageEntity.class);
        verify(stageRepository).save(captor.capture());
        ScrmLifecycleStageEntity saved = captor.getValue();
        assertThat(saved.getStageName()).isEqualTo("潜客");
        assertThat(saved.getStageCode()).isEqualTo("LEAD");
        assertThat(saved.getStageCategory()).isEqualTo("ACQUISITION");
        assertThat(saved.getStageOrder()).isEqualTo(10);
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getIsStartStage()).isFalse();
        assertThat(saved.getIsEndStage()).isFalse();
        assertThat(saved.getIsChurnStage()).isFalse();
        assertThat(saved.getCustomerCount()).isZero();
        assertThat(saved.getTotalEnteredCount()).isZero();
        assertThat(saved.getAvgDurationDays()).isEqualTo(0.0);
        assertThat(saved.getConversionRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("createStage: stageCode 重复抛 CONFLICT")
    void createStage_duplicateCode() {
        ScrmLifecycleStageDto dto = buildStageDto();
        when(stageRepository.findByStageCode("LEAD"))
                .thenReturn(Optional.of(new ScrmLifecycleStageEntity()));
        assertThatThrownBy(() -> service.createStage(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("阶段编码已存在");
    }

    @Test
    @DisplayName("createStage: stageCategory 非法抛 BAD_REQUEST")
    void createStage_invalidCategory() {
        ScrmLifecycleStageDto dto = buildStageDto();
        dto.setStageCategory("UNKNOWN");
        assertThatThrownBy(() -> service.createStage(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("阶段类别非法");
    }

    @Test
    @DisplayName("createStage: stageName 为空抛 BAD_REQUEST")
    void createStage_blankName() {
        ScrmLifecycleStageDto dto = buildStageDto();
        dto.setStageName("");
        assertThatThrownBy(() -> service.createStage(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("阶段名称不能为空");
    }

    @Test
    @DisplayName("createStage: stageCode 为空抛 BAD_REQUEST")
    void createStage_blankCode() {
        ScrmLifecycleStageDto dto = buildStageDto();
        dto.setStageCode("");
        assertThatThrownBy(() -> service.createStage(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("阶段编码不能为空");
    }

    @Test
    @DisplayName("updateStage: 字段非空才覆盖")
    void updateStage_partialUpdate() throws Exception {
        ScrmLifecycleStageEntity existing = buildStageEntity(100L);
        when(stageRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(stageRepository.save(any(ScrmLifecycleStageEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmLifecycleStageDto dto = new ScrmLifecycleStageDto();
        dto.setStageName("更新后名称");
        dto.setDescription("更新后描述");
        ScrmLifecycleStageEntity result = service.updateStage(100L, dto);

        assertThat(result.getStageName()).isEqualTo("更新后名称");
        assertThat(result.getDescription()).isEqualTo("更新后描述");
        // 未提供字段保留原值
        assertThat(result.getStageCode()).isEqualTo("LEAD");
        assertThat(result.getStageCategory()).isEqualTo("ACQUISITION");
    }

    @Test
    @DisplayName("updateStage: 不存在抛 NOT_FOUND")
    void updateStage_notFound() {
        when(stageRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateStage(999L, new ScrmLifecycleStageDto()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("生命周期阶段不存在");
    }

    
    @Test
    @DisplayName("updateStage: stageCode 变更且与同账号其他阶段冲突抛 CONFLICT")
    void updateStage_codeConflict() {
        ScrmLifecycleStageEntity existing = buildStageEntity(100L);
        ScrmLifecycleStageEntity other = buildStageEntity(101L);
        when(stageRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(stageRepository.findByStageCode("ACTIVE"))
                .thenReturn(Optional.of(other));

        ScrmLifecycleStageDto dto = new ScrmLifecycleStageDto();
        dto.setStageCode("ACTIVE");
        assertThatThrownBy(() -> service.updateStage(100L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("阶段编码已存在");
    }

    @Test
    @DisplayName("deleteStage: 无客户关联且无转换规则时成功删除")
    void deleteStage_success() throws Exception {
        ScrmLifecycleStageEntity existing = buildStageEntity(100L);
        when(stageRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(customerLifecycleRepository.countByCurrentStageId(100L)).thenReturn(0L);
        when(transitionRepository.findByFromStageIdAndIsEnabledOrderByPriorityDesc(eq(100L), eq(Boolean.TRUE))).thenReturn(Collections.emptyList());
        when(transitionRepository.findByFromStageIdAndIsEnabledOrderByPriorityDesc(eq(100L), eq(Boolean.FALSE))).thenReturn(Collections.emptyList());

        service.deleteStage(100L);

        verify(stageRepository).delete(existing);
    }

    @Test
    @DisplayName("deleteStage: 阶段下仍有客户抛 CONFLICT, 不删除")
    void deleteStage_hasCustomers() {
        ScrmLifecycleStageEntity existing = buildStageEntity(100L);
        when(stageRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(customerLifecycleRepository.countByCurrentStageId(100L)).thenReturn(3L);
        assertThatThrownBy(() -> service.deleteStage(100L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("阶段下仍有 3 个客户, 无法删除");
        verify(stageRepository, never()).delete(any(ScrmLifecycleStageEntity.class));
    }

    
    @Test
    @DisplayName("getStage: 存在且同账号返回实体")
    void getStage_success() throws Exception {
        ScrmLifecycleStageEntity existing = buildStageEntity(100L);
        when(stageRepository.findById(100L)).thenReturn(Optional.of(existing));
        ScrmLifecycleStageEntity result = service.getStage(100L);
        assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("getStage: 不存在抛 NOT_FOUND")
    void getStage_notFound() {
        when(stageRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getStage(999L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("生命周期阶段不存在");
    }

    @Test
    @DisplayName("getStageByCode: code 为空抛 BAD_REQUEST")
    void getStageByCode_blank() {
        assertThatThrownBy(() -> service.getStageByCode(""))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("阶段编码不能为空");
    }

    @Test
    @DisplayName("getStageByCode: 不存在抛 NOT_FOUND")
    void getStageByCode_notFound() {
        when(stageRepository.findByStageCode("MISSING"))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getStageByCode("MISSING"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("阶段不存在");
    }

    // ==================== 阶段启停 / 重排 ====================

    @Test
    @DisplayName("enableStage: 成功启用")
    void enableStage_success() throws Exception {
        ScrmLifecycleStageEntity existing = buildStageEntity(100L);
        existing.setEnabled(false);
        when(stageRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(stageRepository.save(any(ScrmLifecycleStageEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        service.enableStage(100L);
        assertThat(existing.getEnabled()).isTrue();
    }

    @Test
    @DisplayName("disableStage: 成功禁用")
    void disableStage_success() throws Exception {
        ScrmLifecycleStageEntity existing = buildStageEntity(100L);
        existing.setEnabled(true);
        when(stageRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(stageRepository.save(any(ScrmLifecycleStageEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        service.disableStage(100L);
        assertThat(existing.getEnabled()).isFalse();
    }

    @Test
    @DisplayName("reorderStages: 映射为空抛 BAD_REQUEST")
    void reorderStages_emptyMap() {
        assertThatThrownBy(() -> service.reorderStages(Collections.emptyMap()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("阶段顺序映射不能为空");
    }

    @Test
    @DisplayName("reorderStages: 成功更新阶段顺序")
    void reorderStages_success() throws Exception {
        ScrmLifecycleStageEntity s1 = buildStageEntity(100L);
        ScrmLifecycleStageEntity s2 = buildStageEntity(101L);
        when(stageRepository.findById(100L)).thenReturn(Optional.of(s1));
        when(stageRepository.findById(101L)).thenReturn(Optional.of(s2));
        when(stageRepository.save(any(ScrmLifecycleStageEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(stageRepository.findAllByOrderByStageOrderAsc())
                .thenReturn(List.of(s1, s2));

        List<ScrmLifecycleStageEntity> result = service.reorderStages(Map.of(100L, 1, 101L, 2));

        assertThat(result).hasSize(2);
        assertThat(s1.getStageOrder()).isEqualTo(1);
        assertThat(s2.getStageOrder()).isEqualTo(2);
    }

    // ==================== 流转规则 CRUD ====================

    @Test
    @DisplayName("createTransition: 成功创建, transitionType/priority/cooldownDays/isEnabled 缺省填充默认值")
    void createTransition_success() throws Exception {
        ScrmLifecycleTransitionDto dto = buildTransitionDto();
        ScrmLifecycleStageEntity fromStage = buildStageEntity(10L);
        fromStage.setStageCode("LEAD");
        ScrmLifecycleStageEntity toStage = buildStageEntity(20L);
        toStage.setStageCode("ACTIVE");
        when(stageRepository.findById(10L)).thenReturn(Optional.of(fromStage));
        when(stageRepository.findById(20L)).thenReturn(Optional.of(toStage));
        when(transitionRepository.save(any(ScrmLifecycleTransitionEntity.class)))
                .thenAnswer(inv -> assignTransitionId(inv.getArgument(0), 200L));

        ScrmLifecycleTransitionEntity result = service.createTransition(dto);

        assertThat(result.getId()).isEqualTo(200L);
        ArgumentCaptor<ScrmLifecycleTransitionEntity> captor
            = ArgumentCaptor.forClass(ScrmLifecycleTransitionEntity.class);
        verify(transitionRepository).save(captor.capture());
        ScrmLifecycleTransitionEntity saved = captor.getValue();
        assertThat(saved.getFromStageId()).isEqualTo(10L);
        assertThat(saved.getToStageId()).isEqualTo(20L);
        assertThat(saved.getTransitionType()).isEqualTo("AUTO");
        assertThat(saved.getPriority()).isZero();
        assertThat(saved.getCooldownDays()).isZero();
        assertThat(saved.getIsEnabled()).isTrue();
        assertThat(saved.getTriggerCount()).isZero();
    }

    @Test
    @DisplayName("createTransition: 目标阶段不存在抛 NOT_FOUND")
    void createTransition_toStageNotFound() {
        ScrmLifecycleTransitionDto dto = buildTransitionDto();
        when(stageRepository.findById(20L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createTransition(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("生命周期阶段不存在");
    }

    @Test
    @DisplayName("createTransition: 目标阶段编码与 ID 不匹配抛 BAD_REQUEST")
    void createTransition_toStageCodeMismatch() {
        ScrmLifecycleTransitionDto dto = buildTransitionDto();
        ScrmLifecycleStageEntity toStage = buildStageEntity(20L);
        toStage.setStageCode("OTHER");
        when(stageRepository.findById(20L)).thenReturn(Optional.of(toStage));
        assertThatThrownBy(() -> service.createTransition(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("目标阶段编码与阶段 ID 不匹配");
    }

    @Test
    @DisplayName("createTransition: 转换类型非法抛 BAD_REQUEST")
    void createTransition_invalidType() {
        ScrmLifecycleTransitionDto dto = buildTransitionDto();
        dto.setTransitionType("UNKNOWN");

        assertThatThrownBy(() -> service.createTransition(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("转换类型非法");
    }

    @Test
    @DisplayName("deleteTransition: 成功删除")
    void deleteTransition_success() throws Exception {
        ScrmLifecycleTransitionEntity existing = buildTransitionEntity(200L);
        when(transitionRepository.findById(200L)).thenReturn(Optional.of(existing));
        service.deleteTransition(200L);
        verify(transitionRepository).delete(existing);
    }

    @Test
    @DisplayName("deleteTransition: 不存在抛 NOT_FOUND")
    void deleteTransition_notFound() {
        when(transitionRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteTransition(999L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("流转规则不存在");
    }

    
    @Test
    @DisplayName("enableTransition: 成功启用")
    void enableTransition_success() throws Exception {
        ScrmLifecycleTransitionEntity existing = buildTransitionEntity(200L);
        existing.setIsEnabled(false);
        when(transitionRepository.findById(200L)).thenReturn(Optional.of(existing));
        when(transitionRepository.save(any(ScrmLifecycleTransitionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        service.enableTransition(200L);
        assertThat(existing.getIsEnabled()).isTrue();
    }

    @Test
    @DisplayName("getAvailableTransitions: 返回阶段启用的转换规则列表")
    void getAvailableTransitions_success() throws Exception {
        ScrmLifecycleStageEntity stage = buildStageEntity(10L);
        when(stageRepository.findById(10L)).thenReturn(Optional.of(stage));
        ScrmLifecycleTransitionEntity rule = buildTransitionEntity(200L);
        when(transitionRepository.findByFromStageIdAndIsEnabledOrderByPriorityDesc(10L, Boolean.TRUE)).thenReturn(List.of(rule));

        List<ScrmLifecycleTransitionEntity> result = service.getAvailableTransitions(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(200L);
    }

    // ==================== 客户生命周期查询 ====================

    @Test
    @DisplayName("getCustomerLifecycle: customerId 为 null 抛 BAD_REQUEST")
    void getCustomerLifecycle_nullCustomerId() {
        assertThatThrownBy(() -> service.getCustomerLifecycle(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户 ID 不能为空");
    }

    @Test
    @DisplayName("getCustomerLifecycle: 不存在抛 NOT_FOUND")
    void getCustomerLifecycle_notFound() {
        when(customerLifecycleRepository.findByCustomerId(500L))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getCustomerLifecycle(500L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户生命周期不存在");
    }

    @Test
    @DisplayName("listCustomersByStage: 阶段不存在抛 NOT_FOUND")
    void listCustomersByStage_notFound() {
        when(stageRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listCustomersByStage(999L, PageRequest.of(0, 10)))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("生命周期阶段不存在");
    }

    @Test
    @DisplayName("listCustomersByStage: 返回分页结果")
    void listCustomersByStage_success() throws Exception {
        ScrmLifecycleStageEntity stage = buildStageEntity(10L);
        when(stageRepository.findById(10L)).thenReturn(Optional.of(stage));
        ScrmCustomerLifecycleEntity customer = buildCustomerLifecycleEntity(500L, 10L);
        Page<ScrmCustomerLifecycleEntity> page = new PageImpl<>(
                List.of(customer), PageRequest.of(0, 10), 1);
        when(customerLifecycleRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<ScrmCustomerLifecycleEntity> result = service.listCustomersByStage(10L, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCustomerId()).isEqualTo(500L);
    }

    // ==================== 客户阶段转换 ====================

    @Test
    @DisplayName("transitionCustomer: actionDto 为 null 抛 BAD_REQUEST")
    void transitionCustomer_nullDto() {
        assertThatThrownBy(() -> service.transitionCustomer(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("转换动作参数不能为空");
    }

    @Test
    @DisplayName("transitionCustomer: customerId 为 null 抛 BAD_REQUEST")
    void transitionCustomer_nullCustomerId() {
        ScrmLifecycleTransitionActionDto action = new ScrmLifecycleTransitionActionDto();
        action.setToStageCode("LEAD");
        assertThatThrownBy(() -> service.transitionCustomer(action))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户 ID 不能为空");
    }

    @Test
    @DisplayName("transitionCustomer: toStageCode 为空抛 BAD_REQUEST")
    void transitionCustomer_blankToStageCode() {
        ScrmLifecycleTransitionActionDto action = new ScrmLifecycleTransitionActionDto();
        action.setCustomerId(500L);
        assertThatThrownBy(() -> service.transitionCustomer(action))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("目标阶段编码不能为空");
    }

    @Test
    @DisplayName("transitionCustomer: 客户已在目标阶段, 跳过转换")
    void transitionCustomer_sameStage_skip() throws Exception {
        ScrmLifecycleStageEntity toStage = buildStageEntity(10L);
        toStage.setStageCode("LEAD");
        ScrmCustomerLifecycleEntity existed = buildCustomerLifecycleEntity(500L, 10L);
        existed.setCurrentStageCode("LEAD");
        when(stageRepository.findByStageCode("LEAD")).thenReturn(Optional.of(toStage));
        when(customerLifecycleRepository.findByCustomerId(500L))
                .thenReturn(Optional.of(existed));

        ScrmLifecycleTransitionActionDto action = new ScrmLifecycleTransitionActionDto();
        action.setCustomerId(500L);
        action.setToStageCode("LEAD");
        ScrmCustomerLifecycleEntity result = service.transitionCustomer(action);

        assertThat(result).isSameAs(existed);
        verify(customerLifecycleRepository, never()).save(any(ScrmCustomerLifecycleEntity.class));
    }

    @Test
    @DisplayName("transitionCustomer: 新客户进入起始阶段允许直接进入, 不需转换规则")
    void transitionCustomer_newCustomerStartStage() throws Exception {
        ScrmLifecycleStageEntity toStage = buildStageEntity(10L);
        toStage.setStageCode("LEAD");
        toStage.setIsStartStage(true);
        when(stageRepository.findByStageCode("LEAD")).thenReturn(Optional.of(toStage));
        when(customerLifecycleRepository.findByCustomerId(500L))
                .thenReturn(Optional.empty());
        when(customerLifecycleRepository.save(any(ScrmCustomerLifecycleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(stageRepository.save(any(ScrmLifecycleStageEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.save(any(ScrmLifecycleHistoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmLifecycleTransitionActionDto action = new ScrmLifecycleTransitionActionDto();
        action.setCustomerId(500L);
        action.setToStageCode("LEAD");
        action.setCustomerName("张三");
        ScrmCustomerLifecycleEntity result = service.transitionCustomer(action);

        assertThat(result.getCurrentStageId()).isEqualTo(10L);
        assertThat(result.getCurrentStageCode()).isEqualTo("LEAD");
        assertThat(result.getCustomerId()).isEqualTo(500L);
        verify(historyRepository).save(any(ScrmLifecycleHistoryEntity.class));
        // 起始阶段 customerCount 累计
        assertThat(toStage.getCustomerCount()).isEqualTo(1);
        assertThat(toStage.getTotalEnteredCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("transitionCustomer: 无匹配转换规则抛 BAD_REQUEST")
    void transitionCustomer_noMatchingRule_throws() {
        ScrmLifecycleStageEntity toStage = buildStageEntity(20L);
        toStage.setStageCode("ACTIVE");
        toStage.setIsStartStage(false);
        ScrmCustomerLifecycleEntity existed = buildCustomerLifecycleEntity(500L, 10L);
        existed.setCurrentStageCode("LEAD");
        when(stageRepository.findByStageCode("ACTIVE")).thenReturn(Optional.of(toStage));
        when(customerLifecycleRepository.findByCustomerId(500L))
                .thenReturn(Optional.of(existed));
        when(transitionRepository.findByFromStageIdAndIsEnabledOrderByPriorityDesc(10L, Boolean.TRUE)).thenReturn(Collections.emptyList());

        ScrmLifecycleTransitionActionDto action = new ScrmLifecycleTransitionActionDto();
        action.setCustomerId(500L);
        action.setToStageCode("ACTIVE");
        assertThatThrownBy(() -> service.transitionCustomer(action))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("无可用转换规则");
    }

    @Test
    @DisplayName("transitionCustomer: 命中转换规则成功转换, 写入历史 / 累加 triggerCount / 更新阶段客户数")
    void transitionCustomer_ruleMatched_success() throws Exception {
        ScrmLifecycleStageEntity fromStage = buildStageEntity(10L);
        fromStage.setStageCode("LEAD");
        fromStage.setCustomerCount(1);
        ScrmLifecycleStageEntity toStage = buildStageEntity(20L);
        toStage.setStageCode("ACTIVE");
        ScrmCustomerLifecycleEntity existed = buildCustomerLifecycleEntity(500L, 10L);
        existed.setCurrentStageCode("LEAD");
        ScrmLifecycleTransitionEntity rule = buildTransitionEntity(200L);
        rule.setFromStageId(10L);
        rule.setToStageId(20L);
        rule.setToStageCode("ACTIVE");
        rule.setTriggerCount(0);

        when(stageRepository.findByStageCode("ACTIVE")).thenReturn(Optional.of(toStage));
        when(customerLifecycleRepository.findByCustomerId(500L))
                .thenReturn(Optional.of(existed));
        when(transitionRepository.findByFromStageIdAndIsEnabledOrderByPriorityDesc(10L, Boolean.TRUE)).thenReturn(List.of(rule));
        when(customerLifecycleRepository.save(any(ScrmCustomerLifecycleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(stageRepository.findById(10L)).thenReturn(Optional.of(fromStage));
        when(stageRepository.save(any(ScrmLifecycleStageEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(transitionRepository.save(any(ScrmLifecycleTransitionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.save(any(ScrmLifecycleHistoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmLifecycleTransitionActionDto action = new ScrmLifecycleTransitionActionDto();
        action.setCustomerId(500L);
        action.setToStageCode("ACTIVE");
        action.setTriggerEvent("PURCHASE");
        ScrmCustomerLifecycleEntity result = service.transitionCustomer(action);

        // 当前阶段切换
        assertThat(result.getCurrentStageId()).isEqualTo(20L);
        assertThat(result.getCurrentStageCode()).isEqualTo("ACTIVE");
        assertThat(result.getPreviousStageId()).isEqualTo(10L);
        assertThat(result.getStageHistoryCount()).isEqualTo(1);
        // 转换规则触发次数累加
        assertThat(rule.getTriggerCount()).isEqualTo(1);
        assertThat(rule.getLastTriggeredAt()).isNotNull();
        // 源阶段客户数减 1, 目标阶段进入数加 1
        assertThat(fromStage.getCustomerCount()).isZero();
        assertThat(toStage.getCustomerCount()).isEqualTo(1);
        // 历史记录
        ArgumentCaptor<ScrmLifecycleHistoryEntity> histCaptor
            = ArgumentCaptor.forClass(ScrmLifecycleHistoryEntity.class);
        verify(historyRepository).save(histCaptor.capture());
        ScrmLifecycleHistoryEntity history = histCaptor.getValue();
        assertThat(history.getCustomerId()).isEqualTo(500L);
        assertThat(history.getFromStageId()).isEqualTo(10L);
        assertThat(history.getToStageId()).isEqualTo(20L);
        assertThat(history.getTransitionId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("transitionCustomer: 冷却期内抛 CONFLICT")
    void transitionCustomer_cooldown_throws() {
        ScrmLifecycleStageEntity toStage = buildStageEntity(20L);
        toStage.setStageCode("ACTIVE");
        ScrmCustomerLifecycleEntity existed = buildCustomerLifecycleEntity(500L, 10L);
        existed.setCurrentStageCode("LEAD");
        existed.setEnteredCurrentStageAt(LocalDateTime.now()); // 刚进入, 冷却期内
        ScrmLifecycleTransitionEntity rule = buildTransitionEntity(200L);
        rule.setFromStageId(10L);
        rule.setToStageId(20L);
        rule.setToStageCode("ACTIVE");
        rule.setCooldownDays(7); // 7 天冷却

        when(stageRepository.findByStageCode("ACTIVE")).thenReturn(Optional.of(toStage));
        when(customerLifecycleRepository.findByCustomerId(500L))
                .thenReturn(Optional.of(existed));
        when(transitionRepository.findByFromStageIdAndIsEnabledOrderByPriorityDesc(10L, Boolean.TRUE)).thenReturn(List.of(rule));

        ScrmLifecycleTransitionActionDto action = new ScrmLifecycleTransitionActionDto();
        action.setCustomerId(500L);
        action.setToStageCode("ACTIVE");
        assertThatThrownBy(() -> service.transitionCustomer(action))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("转换规则冷却期内");
        verify(customerLifecycleRepository, never()).save(any(ScrmCustomerLifecycleEntity.class));
    }

    @Test
    @DisplayName("assignCustomer: 新客户直接初始化到目标阶段")
    void assignCustomer_newCustomer() throws Exception {
        ScrmLifecycleStageEntity targetStage = buildStageEntity(10L);
        targetStage.setStageCode("LEAD");
        when(stageRepository.findByStageCode("LEAD"))
                .thenReturn(Optional.of(targetStage));
        when(customerLifecycleRepository.findByCustomerId(500L))
                .thenReturn(Optional.empty());
        when(customerLifecycleRepository.save(any(ScrmCustomerLifecycleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(stageRepository.save(any(ScrmLifecycleStageEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCustomerLifecycleEntity result = service.assignCustomer(500L, "LEAD");

        assertThat(result.getCustomerId()).isEqualTo(500L);
        assertThat(result.getCurrentStageId()).isEqualTo(10L);
        assertThat(result.getCurrentStageCode()).isEqualTo("LEAD");
        assertThat(targetStage.getCustomerCount()).isEqualTo(1);
        assertThat(targetStage.getTotalEnteredCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("assignCustomer: customerId 为 null 抛 BAD_REQUEST")
    void assignCustomer_nullCustomerId() {
        assertThatThrownBy(() -> service.assignCustomer(null, "LEAD"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户 ID 不能为空");
    }

    @Test
    @DisplayName("assignCustomer: stageCode 为空抛 BAD_REQUEST")
    void assignCustomer_blankStageCode() {
        assertThatThrownBy(() -> service.assignCustomer(500L, ""))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("阶段编码不能为空");
    }

    // ==================== 批量转换 ====================

    @Test
    @DisplayName("bulkTransition: dto 为 null 抛 BAD_REQUEST")
    void bulkTransition_nullDto() {
        assertThatThrownBy(() -> service.bulkTransition(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("批量转换参数不能为空");
    }

    @Test
    @DisplayName("bulkTransition: customerIds 为空抛 BAD_REQUEST")
    void bulkTransition_emptyCustomerIds() {
        org.hiylo.scrm.dto.ScrmLifecycleBulkTransitionDto bulk =
                new org.hiylo.scrm.dto.ScrmLifecycleBulkTransitionDto();
        bulk.setCustomerIds(Collections.emptyList());
        assertThatThrownBy(() -> service.bulkTransition(bulk))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户 ID 列表不能为空");
    }

    @Test
    @DisplayName("bulkTransition: 部分客户转换失败不影响其他客户, 返回成功列表")
    void bulkTransition_partialFailure() throws Exception {
        org.hiylo.scrm.dto.ScrmLifecycleBulkTransitionDto bulk =
                new org.hiylo.scrm.dto.ScrmLifecycleBulkTransitionDto();
        bulk.setCustomerIds(List.of(500L, 501L));
        bulk.setToStageCode("LEAD");
        ScrmLifecycleStageEntity toStage = buildStageEntity(10L);
        toStage.setStageCode("LEAD");
        toStage.setIsStartStage(true);
        // 500 成功 (新客户进入起始阶段), 501 失败 (目标阶段不存在)
        when(stageRepository.findByStageCode("LEAD"))
                .thenReturn(Optional.of(toStage))
                .thenReturn(Optional.of(toStage));
        when(customerLifecycleRepository.findByCustomerId(500L))
                .thenReturn(Optional.empty());
        when(customerLifecycleRepository.findByCustomerId(501L))
                .thenReturn(Optional.empty());
        // 500L 走起始阶段直接进入分支
        when(customerLifecycleRepository.save(any(ScrmCustomerLifecycleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(stageRepository.save(any(ScrmLifecycleStageEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.save(any(ScrmLifecycleHistoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // 501L 没有 start stage 路径 - 实际上 501L 也是新客户且 toStage 为 start stage, 也会成功
        // 修改测试: 501L 设置 existed 不为空, 且无匹配规则使其失败
        ScrmCustomerLifecycleEntity existed501 = buildCustomerLifecycleEntity(501L, 99L);
        existed501.setCurrentStageCode("OTHER");
        when(customerLifecycleRepository.findByCustomerId(501L))
                .thenReturn(Optional.of(existed501));
        when(transitionRepository.findByFromStageIdAndIsEnabledOrderByPriorityDesc(99L, Boolean.TRUE)).thenReturn(Collections.emptyList());

        List<ScrmCustomerLifecycleEntity> results = service.bulkTransition(bulk);

        // 500L 成功, 501L 抛异常被跳过
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCustomerId()).isEqualTo(500L);
    }

    // ==================== 事件触发转换 ====================

    @Test
    @DisplayName("checkAndTransition: event 为空抛 BAD_REQUEST")
    void checkAndTransition_blankEvent() {
        assertThatThrownBy(() -> service.checkAndTransition(500L, ""))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("触发事件不能为空");
    }

    @Test
    @DisplayName("checkAndTransition: 无匹配触发事件的规则, 返回当前生命周期")
    void checkAndTransition_noMatchingEvent() throws Exception {
        ScrmCustomerLifecycleEntity current = buildCustomerLifecycleEntity(500L, 10L);
        current.setCurrentStageCode("LEAD");
        when(customerLifecycleRepository.findByCustomerId(500L))
                .thenReturn(Optional.of(current));
        when(transitionRepository.findByFromStageIdAndIsEnabledOrderByPriorityDesc(10L, Boolean.TRUE)).thenReturn(Collections.emptyList());

        ScrmCustomerLifecycleEntity result = service.checkAndTransition(500L, "PURCHASE");

        assertThat(result).isSameAs(current);
    }

    @Test
    @DisplayName("checkAndTransition: 匹配触发事件时执行转换")
    void checkAndTransition_matchedEvent() throws Exception {
        ScrmCustomerLifecycleEntity current = buildCustomerLifecycleEntity(500L, 10L);
        current.setCurrentStageCode("LEAD");
        ScrmLifecycleTransitionEntity rule = buildTransitionEntity(200L);
        rule.setFromStageId(10L);
        rule.setToStageId(20L);
        rule.setToStageCode("ACTIVE");
        rule.setTriggerEvents("PURCHASE,LOGIN");
        ScrmLifecycleStageEntity toStage = buildStageEntity(20L);
        toStage.setStageCode("ACTIVE");

        when(customerLifecycleRepository.findByCustomerId(500L))
                .thenReturn(Optional.of(current));
        when(transitionRepository.findByFromStageIdAndIsEnabledOrderByPriorityDesc(10L, Boolean.TRUE)).thenReturn(List.of(rule));
        when(stageRepository.findByStageCode("ACTIVE")).thenReturn(Optional.of(toStage));
        when(customerLifecycleRepository.save(any(ScrmCustomerLifecycleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(stageRepository.findById(10L)).thenReturn(Optional.of(buildStageEntity(10L)));
        when(stageRepository.save(any(ScrmLifecycleStageEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(transitionRepository.save(any(ScrmLifecycleTransitionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.save(any(ScrmLifecycleHistoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCustomerLifecycleEntity result = service.checkAndTransition(500L, "PURCHASE");

        assertThat(result.getCurrentStageId()).isEqualTo(20L);
        assertThat(result.getCurrentStageCode()).isEqualTo("ACTIVE");
    }

    // ==================== 转换历史 ====================

    @Test
    @DisplayName("getCustomerHistory: customerId 为 null 抛 BAD_REQUEST")
    void getCustomerHistory_nullCustomerId() {
        assertThatThrownBy(() -> service.getCustomerHistory(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户 ID 不能为空");
    }

    @Test
    @DisplayName("getCustomerHistory: 返回客户转换历史列表")
    void getCustomerHistory_success() throws Exception {
        ScrmLifecycleHistoryEntity history = new ScrmLifecycleHistoryEntity();
        history.setId(300L);
        when(historyRepository.findByCustomerIdOrderByTransitionTimeDesc(500L))
                .thenReturn(List.of(history));

        List<ScrmLifecycleHistoryEntity> result = service.getCustomerHistory(500L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(300L);
    }

    // ==================== 统计 ====================

    @Test
    @DisplayName("getStageFunnel: 返回各阶段漏斗数据")
    void getStageFunnel_success() {
        ScrmLifecycleStageEntity s1 = buildStageEntity(10L);
        s1.setCustomerCount(100);
        s1.setTotalEnteredCount(200);
        s1.setConversionRate(0.5);
        when(stageRepository.findAllByOrderByStageOrderAsc())
                .thenReturn(List.of(s1));

        List<Map<String, Object>> funnel = service.getStageFunnel();

        assertThat(funnel).hasSize(1);
        assertThat(funnel.get(0).get("stageId")).isEqualTo(10L);
        assertThat(funnel.get(0).get("customerCount")).isEqualTo(100);
        assertThat(funnel.get(0).get("totalEnteredCount")).isEqualTo(200);
        assertThat(funnel.get(0).get("conversionRate")).isEqualTo(0.5);
    }

    @Test
    @DisplayName("getLifecycleStats: 返回总客户数与阶段分布")
    void getLifecycleStats_success() {
        ScrmLifecycleStageEntity s1 = buildStageEntity(10L);
        s1.setCustomerCount(60);
        s1.setAvgDurationDays(15.5);
        s1.setConversionRate(0.6);
        when(stageRepository.findAllByOrderByStageOrderAsc())
                .thenReturn(List.of(s1));
        when(customerLifecycleRepository.count()).thenReturn(100L);

        Map<String, Object> stats = service.getLifecycleStats();

        assertThat(stats.get("totalCustomers")).isEqualTo(100L);
        assertThat(stats.get("totalStages")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stageStats = (List<Map<String, Object>>) stats.get("stageStats");
        assertThat(stageStats).hasSize(1);
        assertThat(stageStats.get(0).get("distribution")).isEqualTo(0.6);
        assertThat(stageStats.get(0).get("avgDurationDays")).isEqualTo(15.5);
    }

    @Test
    @DisplayName("updateStageStats: 计算客户数 / 平均停留 / 转化率")
    void updateStageStats_success() throws Exception {
        ScrmLifecycleStageEntity stage = buildStageEntity(10L);
        stage.setTotalEnteredCount(100);
        when(stageRepository.findById(10L)).thenReturn(Optional.of(stage));
        when(customerLifecycleRepository.countByCurrentStageId(10L)).thenReturn(50L);
        when(historyRepository.avgDurationInPreviousStage(10L)).thenReturn(12.5);
        when(historyRepository.countByFromStage(eq(10L), any(), any())).thenReturn(80L);
        when(stageRepository.save(any(ScrmLifecycleStageEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmLifecycleStageEntity result = service.updateStageStats(10L);

        assertThat(result.getCustomerCount()).isEqualTo(50);
        assertThat(result.getAvgDurationDays()).isEqualTo(12.5);
        assertThat(result.getConversionRate()).isEqualTo(0.8);
    }

    // ==================== 辅助方法 ====================

    private ScrmLifecycleStageDto buildStageDto() {
        ScrmLifecycleStageDto dto = new ScrmLifecycleStageDto();
        dto.setStageName("潜客");
        dto.setStageCode("LEAD");
        dto.setStageCategory("ACQUISITION");
        dto.setStageOrder(10);
        dto.setCreatedBy("admin");
        return dto;
    }

    private ScrmLifecycleStageEntity buildStageEntity(Long id) {
        ScrmLifecycleStageEntity entity = new ScrmLifecycleStageEntity();
        entity.setId(id);
        entity.setStageName("潜客");
        entity.setStageCode("LEAD");
        entity.setStageOrder(10);
        entity.setStageCategory("ACQUISITION");
        entity.setIsStartStage(false);
        entity.setIsEndStage(false);
        entity.setIsChurnStage(false);
        entity.setCustomerCount(0);
        entity.setTotalEnteredCount(0);
        entity.setAvgDurationDays(0.0);
        entity.setConversionRate(0.0);
        entity.setEnabled(true);
        return entity;
    }

    private ScrmLifecycleTransitionDto buildTransitionDto() {
        ScrmLifecycleTransitionDto dto = new ScrmLifecycleTransitionDto();
        dto.setFromStageId(10L);
        dto.setFromStageCode("LEAD");
        dto.setToStageId(20L);
        dto.setToStageCode("ACTIVE");
        dto.setTransitionName("潜客转活跃");
        return dto;
    }

    private ScrmLifecycleTransitionEntity buildTransitionEntity(Long id) {
        ScrmLifecycleTransitionEntity entity = new ScrmLifecycleTransitionEntity();
        entity.setId(id);
        entity.setFromStageId(10L);
        entity.setFromStageCode("LEAD");
        entity.setToStageId(20L);
        entity.setToStageCode("ACTIVE");
        entity.setTransitionName("潜客转活跃");
        entity.setTransitionType("AUTO");
        entity.setPriority(0);
        entity.setCooldownDays(0);
        entity.setIsEnabled(true);
        entity.setTriggerCount(0);
        return entity;
    }

    private ScrmCustomerLifecycleEntity buildCustomerLifecycleEntity(Long customerId, Long stageId) {
        ScrmCustomerLifecycleEntity entity = new ScrmCustomerLifecycleEntity();
        entity.setId(1000L);
        entity.setCustomerId(customerId);
        entity.setCurrentStageId(stageId);
        entity.setCurrentStageCode("LEAD");
        entity.setCurrentStageName("潜客");
        entity.setEnteredCurrentStageAt(LocalDateTime.now().minusDays(10));
        entity.setDurationInStageDays(10);
        entity.setStageHistoryCount(0);
        entity.setIsOverdue(false);
        entity.setOverdueDays(0);
        entity.setLastUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private ScrmLifecycleStageEntity assignStageId(ScrmLifecycleStageEntity entity, Long id) {
        entity.setId(id);
        return entity;
    }

    private ScrmLifecycleTransitionEntity assignTransitionId(ScrmLifecycleTransitionEntity entity, Long id) {
        entity.setId(id);
        return entity;
    }
}
