/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerTagServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.hiylo.scrm.dto.ScrmCustomerTagDto;
import org.hiylo.scrm.dto.ScrmTagCustomerDto;
import org.hiylo.scrm.dto.ScrmTagRuleDto;
import org.hiylo.scrm.dto.ScrmTagRuleEvaluateDto;
import org.hiylo.scrm.dto.ScrmTagRuleTestDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerTagEntity;
import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.hiylo.scrm.entity.ScrmTagRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmCustomerTagRepository;
import org.hiylo.scrm.repository.ScrmTagCustomerRepository;
import org.hiylo.scrm.repository.ScrmTagRuleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmCustomerTagService 单元测试
 * <p>
 * 聚焦客户标签画像模块核心业务逻辑: 标签定义 CRUD / 启用禁用 / 越权访问 /
 * 客户打标 (覆盖更新 / 自动标签) / 去标 (自动标签不可手动去标) / 标签规则 CRUD /
 * 规则条件评估 (ALL/ANY/NONE + eq/ne/gt/lt/contains/between) / 规则测试 / 手动评估 /
 * 批量执行 / 标签删除级联清理。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmCustomerTagService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmCustomerTagServiceTest {

    /** 客户标签仓库 Mock */
    @Mock
    private ScrmCustomerTagRepository tagRepository;
    /** 标签规则仓库 Mock */
    @Mock
    private ScrmTagRuleRepository ruleRepository;
    /** 客户标签关联仓库 Mock */
    @Mock
    private ScrmTagCustomerRepository tagCustomerRepository;
    /** 客户档案仓库 Mock */
    @Mock
    private ScrmCustomerRepository customerRepository;
    /** JPA 实体管理器 Mock */
    @Mock
    private EntityManager entityManager;

    /** ObjectMapper 使用真实实例, 不 mock (遵循约束) */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 被测服务实例 */
    private ScrmCustomerTagService service;

    @BeforeEach
    void setUp() {
        service = new ScrmCustomerTagService(tagRepository, ruleRepository,
                tagCustomerRepository, customerRepository, objectMapper, entityManager);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造标签 DTO
     */
    private ScrmCustomerTagDto buildTagDto() {
        ScrmCustomerTagDto dto = new ScrmCustomerTagDto();
        dto.setTagName("高价值客户");
        dto.setTagCode("VIP_001");
        dto.setTagType("MANUAL");
        dto.setCategory("VALUE");
        return dto;
    }

    /**
     * 构造已持久化的标签实体
     */
    private ScrmCustomerTagEntity buildTagEntity(Long id) {
        ScrmCustomerTagEntity entity = new ScrmCustomerTagEntity();
        entity.setId(id);
        entity.setTagName("高价值客户");
        entity.setTagCode("VIP_001");
        entity.setTagType("MANUAL");
        entity.setValueType("BOOLEAN");
        entity.setRuleLogic("AND");
        entity.setEvaluationFrequency("DAILY");
        entity.setEnabled(Boolean.TRUE);
        entity.setCustomerCount(0);
        return entity;
    }

    /**
     * 构造已持久化的禁用标签实体
     */
    private ScrmCustomerTagEntity buildDisabledTagEntity(Long id) {
        ScrmCustomerTagEntity entity = buildTagEntity(id);
        entity.setEnabled(Boolean.FALSE);
        return entity;
    }

    /**
     * 构造规则 DTO
     */
    private ScrmTagRuleDto buildRuleDto(Long tagId) {
        ScrmTagRuleDto dto = new ScrmTagRuleDto();
        dto.setRuleName("活跃客户规则");
        dto.setTagId(tagId);
        dto.setConditionType("ALL");
        dto.setConditions("[{\"field\":\"lifecycle\",\"operator\":\"eq\",\"value\":\"ACTIVE\"}]");
        dto.setExecutionFrequency("DAILY");
        return dto;
    }

    /**
     * 构造已持久化的规则实体
     */
    private ScrmTagRuleEntity buildRuleEntity(Long id, Long tagId, String status) {
        ScrmTagRuleEntity entity = new ScrmTagRuleEntity();
        entity.setId(id);
        entity.setRuleName("活跃客户规则");
        entity.setTagId(tagId);
        entity.setConditionType("ALL");
        entity.setConditions("[{\"field\":\"lifecycle\",\"operator\":\"eq\",\"value\":\"ACTIVE\"}]");
        entity.setExecutionFrequency("DAILY");
        entity.setStatus(status);
        entity.setMatchedCount(0);
        return entity;
    }

    /**
     * 构造客户实体
     */
    private ScrmCustomerEntity buildCustomerEntity(Long id, String lifecycle) {
        ScrmCustomerEntity entity = new ScrmCustomerEntity();
        entity.setId(id);
        entity.setNickname("张三");
        entity.setLifecycle(lifecycle);
        entity.setPlatformType("WECHAT");
        entity.setCreateTime(LocalDateTime.now().minusDays(30));
        entity.setLastInteractionAt(LocalDateTime.now().minusDays(1));
        return entity;
    }

    /**
     * 构造打标 DTO
     */
    private ScrmTagCustomerDto buildTagCustomerDto(Long customerId, Long tagId) {
        ScrmTagCustomerDto dto = new ScrmTagCustomerDto();
        dto.setCustomerId(customerId);
        dto.setTagId(tagId);
        dto.setTagValue("true");
        dto.setAssignedBy("tester");
        dto.setAssignedByName("测试员");
        return dto;
    }

    // ==================== 标签管理 ====================

    @Test
    @DisplayName("createTag: 写入归属账号与默认值 (valueType/ruleLogic/evaluationFrequency)")
    void createTag_success() throws ScrmException {
        ScrmCustomerTagDto dto = buildTagDto();
        when(tagRepository.findByTagCode("VIP_001")).thenReturn(Optional.empty());
        when(tagRepository.save(any(ScrmCustomerTagEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.createTag(dto);

        ArgumentCaptor<ScrmCustomerTagEntity> captor =
                ArgumentCaptor.forClass(ScrmCustomerTagEntity.class);
        verify(tagRepository, times(1)).save(captor.capture());
        ScrmCustomerTagEntity saved = captor.getValue();
        assertThat(saved.getValueType()).isEqualTo("BOOLEAN");
        assertThat(saved.getRuleLogic()).isEqualTo("AND");
        assertThat(saved.getEvaluationFrequency()).isEqualTo("DAILY");
        assertThat(saved.getTagCode()).isEqualTo("VIP_001");
    }

    @Test
    @DisplayName("createTag: 标签编码重复抛 CONFLICT")
    void createTag_duplicateCode() {
        ScrmCustomerTagDto dto = buildTagDto();
        when(tagRepository.findByTagCode("VIP_001"))
                .thenReturn(Optional.of(buildTagEntity(10L)));

        assertThatThrownBy(() -> service.createTag(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("标签编码已存在");
        verify(tagRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTag: 标签名称为空抛 BAD_REQUEST")
    void createTag_blankName() {
        ScrmCustomerTagDto dto = buildTagDto();
        dto.setTagName(" ");

        assertThatThrownBy(() -> service.createTag(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("标签名称不能为空");
        verify(tagRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTag: 标签类型为空抛 BAD_REQUEST")
    void createTag_blankType() {
        ScrmCustomerTagDto dto = buildTagDto();
        dto.setTagType(" ");

        assertThatThrownBy(() -> service.createTag(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("标签类型不能为空");
        verify(tagRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateTag: tagCode 改为其他已存在编码抛 CONFLICT")
    void updateTag_duplicateCode() {
        ScrmCustomerTagEntity entity = buildTagEntity(10L);
        when(tagRepository.findById(10L)).thenReturn(Optional.of(entity));
        ScrmCustomerTagEntity existing = buildTagEntity(20L);
        when(tagRepository.findByTagCode("VIP_002")).thenReturn(Optional.of(existing));
        ScrmCustomerTagDto dto = new ScrmCustomerTagDto();
        dto.setTagCode("VIP_002");

        assertThatThrownBy(() -> service.updateTag(10L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("标签编码已存在");
        verify(tagRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateTag: 部分更新仅覆盖非空字段")
    void updateTag_partial() throws ScrmException {
        ScrmCustomerTagEntity entity = buildTagEntity(10L);
        when(tagRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(tagRepository.save(any(ScrmCustomerTagEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmCustomerTagDto dto = new ScrmCustomerTagDto();
        dto.setTagName("新标签名");

        service.updateTag(10L, dto);

        ArgumentCaptor<ScrmCustomerTagEntity> captor =
                ArgumentCaptor.forClass(ScrmCustomerTagEntity.class);
        verify(tagRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getTagName()).isEqualTo("新标签名");
        // 其他字段保留
        assertThat(captor.getValue().getTagCode()).isEqualTo("VIP_001");
    }

    @Test
    @DisplayName("deleteTag: 存在规则引用抛 CONFLICT")
    void deleteTag_hasRules() {
        ScrmCustomerTagEntity entity = buildTagEntity(10L);
        when(tagRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(ruleRepository.findByTagId(10L))
                .thenReturn(List.of(buildRuleEntity(1L, 10L, "ACTIVE")));

        assertThatThrownBy(() -> service.deleteTag(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("无法删除标签");
        verify(tagCustomerRepository, never()).deleteByTagId(anyLong());
        verify(tagRepository, never()).delete(any(ScrmCustomerTagEntity.class));
    }

    @Test
    @DisplayName("deleteTag: 无规则引用时级联清理并删除")
    void deleteTag_success() throws ScrmException {
        ScrmCustomerTagEntity entity = buildTagEntity(10L);
        when(tagRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(ruleRepository.findByTagId(10L)).thenReturn(Collections.emptyList());
        when(tagCustomerRepository.deleteByTagId(10L)).thenReturn(5);

        service.deleteTag(10L);

        verify(tagCustomerRepository, times(1)).deleteByTagId(10L);
        verify(tagRepository, times(1)).delete(entity);
    }

    @Test
    @DisplayName("enableTag: 置 enabled=true 并持久化")
    void enableTag_success() throws ScrmException {
        ScrmCustomerTagEntity entity = buildDisabledTagEntity(10L);
        when(tagRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(tagRepository.save(any(ScrmCustomerTagEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.enableTag(10L);

        ArgumentCaptor<ScrmCustomerTagEntity> captor =
                ArgumentCaptor.forClass(ScrmCustomerTagEntity.class);
        verify(tagRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isTrue();
    }

    @Test
    @DisplayName("disableTag: 置 enabled=false 并持久化")
    void disableTag_success() throws ScrmException {
        ScrmCustomerTagEntity entity = buildTagEntity(10L);
        when(tagRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(tagRepository.save(any(ScrmCustomerTagEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.disableTag(10L);

        ArgumentCaptor<ScrmCustomerTagEntity> captor =
                ArgumentCaptor.forClass(ScrmCustomerTagEntity.class);
        verify(tagRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isFalse();
    }

    @Test
    @DisplayName("getTag: 不存在抛 NOT_FOUND")
    void getTag_notFound() {
        when(tagRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTag(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户标签不存在");
    }

    
    @Test
    @DisplayName("getTagByCode: 不存在抛 NOT_FOUND")
    void getTagByCode_notFound() {
        when(tagRepository.findByTagCode("VIP_X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTagByCode("VIP_X"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("标签不存在");
    }

    // ==================== 客户打标 ====================

    @Test
    @DisplayName("assignTag: 新关联设置 is_auto=false 并刷新标签客户数")
    void assignTag_newAssignment() throws ScrmException {
        ScrmCustomerTagEntity tag = buildTagEntity(10L);
        when(tagRepository.findById(10L)).thenReturn(Optional.of(tag));
        when(tagCustomerRepository.findByCustomerIdAndTagId(100L, 10L))
                .thenReturn(Optional.empty());
        when(tagCustomerRepository.save(any(ScrmTagCustomerEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(tagCustomerRepository.countByTagId(10L)).thenReturn(1L);
        when(tagRepository.save(any(ScrmCustomerTagEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.assignTag(buildTagCustomerDto(100L, 10L));

        ArgumentCaptor<ScrmTagCustomerEntity> captor =
                ArgumentCaptor.forClass(ScrmTagCustomerEntity.class);
        verify(tagCustomerRepository, times(1)).save(captor.capture());
        ScrmTagCustomerEntity saved = captor.getValue();
        assertThat(saved.getCustomerId()).isEqualTo(100L);
        assertThat(saved.getTagId()).isEqualTo(10L);
        assertThat(saved.getTagSource()).isEqualTo("MANUAL");
        assertThat(saved.getIsAuto()).isFalse();
        assertThat(saved.getAssignedBy()).isEqualTo("tester");
        // 新增关联后刷新标签客户数
        verify(tagCustomerRepository, times(1)).countByTagId(10L);
    }

    @Test
    @DisplayName("assignTag: 已存在关联覆盖更新 tagValue 且不刷新客户数")
    void assignTag_overwrite() throws ScrmException {
        ScrmCustomerTagEntity tag = buildTagEntity(10L);
        when(tagRepository.findById(10L)).thenReturn(Optional.of(tag));
        ScrmTagCustomerEntity existing = new ScrmTagCustomerEntity();
        existing.setId(5L);
        existing.setCustomerId(100L);
        existing.setTagId(10L);
        existing.setTagSource("MANUAL");
        existing.setIsAuto(false);
        when(tagCustomerRepository.findByCustomerIdAndTagId(100L, 10L))
                .thenReturn(Optional.of(existing));
        when(tagCustomerRepository.save(any(ScrmTagCustomerEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.assignTag(buildTagCustomerDto(100L, 10L));

        ArgumentCaptor<ScrmTagCustomerEntity> captor =
                ArgumentCaptor.forClass(ScrmTagCustomerEntity.class);
        verify(tagCustomerRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(5L);
        // 已存在关联不刷新客户数
        verify(tagCustomerRepository, never()).countByTagId(anyLong());
    }

    @Test
    @DisplayName("assignTag: 已禁用标签抛 BAD_REQUEST")
    void assignTag_disabledTag() {
        ScrmCustomerTagEntity tag = buildDisabledTagEntity(10L);
        when(tagRepository.findById(10L)).thenReturn(Optional.of(tag));

        assertThatThrownBy(() -> service.assignTag(buildTagCustomerDto(100L, 10L)))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("标签已禁用");
        verify(tagCustomerRepository, never()).save(any());
    }

    @Test
    @DisplayName("assignTag: customerId 为空抛 BAD_REQUEST")
    void assignTag_missingCustomerId() {
        ScrmTagCustomerDto dto = new ScrmTagCustomerDto();
        dto.setTagId(10L);

        assertThatThrownBy(() -> service.assignTag(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户 ID 不能为空");
        verify(tagRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("batchAssignTags: 部分失败时返回成功数")
    void batchAssignTags_partialFailure() throws ScrmException {
        ScrmCustomerTagEntity tag = buildTagEntity(10L);
        // 第一条成功 (tagId=10), 第二条失败 (tagId=20 标签不存在)
        when(tagRepository.findById(10L)).thenReturn(Optional.of(tag));
        when(tagRepository.findById(20L)).thenReturn(Optional.empty());
        when(tagCustomerRepository.findByCustomerIdAndTagId(eq(100L), eq(10L)))
                .thenReturn(Optional.empty());
        when(tagCustomerRepository.save(any(ScrmTagCustomerEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(tagCustomerRepository.countByTagId(10L)).thenReturn(1L);
        when(tagRepository.save(any(ScrmCustomerTagEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        int success = service.batchAssignTags(List.of(
                buildTagCustomerDto(100L, 10L),
                buildTagCustomerDto(100L, 20L)));

        assertThat(success).isEqualTo(1);
    }

    @Test
    @DisplayName("batchAssignTags: 空列表抛 BAD_REQUEST")
    void batchAssignTags_empty() {
        assertThatThrownBy(() -> service.batchAssignTags(Collections.emptyList()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("批量打标参数列表不能为空");
    }

    @Test
    @DisplayName("removeTag: 自动标签不可手动去标 (BAD_REQUEST)")
    void removeTag_autoTagRejects() {
        ScrmTagCustomerEntity entity = new ScrmTagCustomerEntity();
        entity.setId(5L);
        entity.setCustomerId(100L);
        entity.setTagId(10L);
        entity.setIsAuto(true);
        when(tagCustomerRepository.findByCustomerIdAndTagId(100L, 10L))
                .thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.removeTag(100L, 10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("自动打标标签不可手动去标");
        verify(tagCustomerRepository, never()).delete(any(ScrmTagCustomerEntity.class));
    }

    @Test
    @DisplayName("removeTag: 手动标签可移除并刷新客户数")
    void removeTag_manualSuccess() throws ScrmException {
        ScrmTagCustomerEntity entity = new ScrmTagCustomerEntity();
        entity.setId(5L);
        entity.setCustomerId(100L);
        entity.setTagId(10L);
        entity.setIsAuto(false);
        when(tagCustomerRepository.findByCustomerIdAndTagId(100L, 10L))
                .thenReturn(Optional.of(entity));
        when(tagRepository.findById(10L)).thenReturn(Optional.of(buildTagEntity(10L)));
        when(tagCustomerRepository.countByTagId(10L)).thenReturn(0L);
        when(tagRepository.save(any(ScrmCustomerTagEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.removeTag(100L, 10L);

        verify(tagCustomerRepository, times(1)).delete(entity);
        verify(tagRepository, times(1)).save(any(ScrmCustomerTagEntity.class));
    }

    @Test
    @DisplayName("removeTag: 关联不存在抛 NOT_FOUND")
    void removeTag_notFound() {
        when(tagCustomerRepository.findByCustomerIdAndTagId(100L, 10L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeTag(100L, 10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户标签关联不存在");
    }

    @Test
    @DisplayName("getCustomerTags: 返回客户的所有标签关联")
    void getCustomerTags_success() {
        ScrmTagCustomerEntity tc = new ScrmTagCustomerEntity();
        tc.setId(1L);
        tc.setCustomerId(100L);
        tc.setTagId(10L);
        when(tagCustomerRepository.findByCustomerId(100L))
                .thenReturn(List.of(tc));

        List<ScrmTagCustomerEntity> result = service.getCustomerTags(100L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTagId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("getTagCustomers: 分页查询标签下客户")
    void getTagCustomers_success() {
        ScrmTagCustomerEntity tc = new ScrmTagCustomerEntity();
        tc.setId(1L);
        tc.setTagId(10L);
        when(tagCustomerRepository.findByTagId(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tc), PageRequest.of(0, 10), 1L));

        var page = service.getTagCustomers(10L, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(1L);
    }

    // ==================== 规则管理 ====================

    @Test
    @DisplayName("createRule: 写入默认状态 ACTIVE 与 matchedCount=0")
    void createRule_success() throws ScrmException {
        when(tagRepository.findById(10L)).thenReturn(Optional.of(buildTagEntity(10L)));
        when(ruleRepository.save(any(ScrmTagRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmTagRuleDto dto = buildRuleDto(10L);

        service.createRule(dto);

        ArgumentCaptor<ScrmTagRuleEntity> captor =
                ArgumentCaptor.forClass(ScrmTagRuleEntity.class);
        verify(ruleRepository, times(1)).save(captor.capture());
        ScrmTagRuleEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getMatchedCount()).isZero();
        assertThat(saved.getTagId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("createRule: 关联标签已禁用抛 BAD_REQUEST")
    void createRule_disabledTag() {
        when(tagRepository.findById(10L)).thenReturn(Optional.of(buildDisabledTagEntity(10L)));
        ScrmTagRuleDto dto = buildRuleDto(10L);

        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("标签已禁用");
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("createRule: 条件组合类型非法抛 BAD_REQUEST")
    void createRule_invalidConditionType() {
        ScrmTagRuleDto dto = buildRuleDto(10L);
        dto.setConditionType("INVALID");

        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("条件组合类型非法");
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("createRule: 条件 JSON 非法抛 BAD_REQUEST")
    void createRule_invalidConditionsJson() {
        ScrmTagRuleDto dto = buildRuleDto(10L);
        dto.setConditions("{invalid json}");

        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("条件 JSON 解析失败");
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("createRule: 规则名称为空抛 BAD_REQUEST")
    void createRule_blankName() {
        ScrmTagRuleDto dto = buildRuleDto(10L);
        dto.setRuleName(" ");

        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("规则名称不能为空");
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateRule: 切换到禁用标签抛 BAD_REQUEST")
    void updateRule_switchToDisabledTag() {
        ScrmTagRuleEntity entity = buildRuleEntity(1L, 10L, "ACTIVE");
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(tagRepository.findById(20L)).thenReturn(Optional.of(buildDisabledTagEntity(20L)));
        ScrmTagRuleDto dto = new ScrmTagRuleDto();
        dto.setTagId(20L);

        assertThatThrownBy(() -> service.updateRule(1L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("标签已禁用");
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteRule: 调用 repository.delete")
    void deleteRule_success() throws ScrmException {
        ScrmTagRuleEntity entity = buildRuleEntity(1L, 10L, "ACTIVE");
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(entity));

        service.deleteRule(1L);

        verify(ruleRepository, times(1)).delete(entity);
    }

    @Test
    @DisplayName("enableRule: 置 status=ACTIVE")
    void enableRule_success() throws ScrmException {
        ScrmTagRuleEntity entity = buildRuleEntity(1L, 10L, "INACTIVE");
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(ruleRepository.save(any(ScrmTagRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.enableRule(1L);

        ArgumentCaptor<ScrmTagRuleEntity> captor =
                ArgumentCaptor.forClass(ScrmTagRuleEntity.class);
        verify(ruleRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("disableRule: 置 status=INACTIVE")
    void disableRule_success() throws ScrmException {
        ScrmTagRuleEntity entity = buildRuleEntity(1L, 10L, "ACTIVE");
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(ruleRepository.save(any(ScrmTagRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.disableRule(1L);

        ArgumentCaptor<ScrmTagRuleEntity> captor =
                ArgumentCaptor.forClass(ScrmTagRuleEntity.class);
        verify(ruleRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("INACTIVE");
    }

    
    @Test
    @DisplayName("listRules: 按条件分页查询规则")
    void listRules_filter() {
        ScrmTagRuleEntity rule = buildRuleEntity(1L, 10L, "ACTIVE");
        when(ruleRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(rule)));

        var page = service.listRules(10L, "ACTIVE", null, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        verify(ruleRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    // ==================== 规则评估 ====================

    @Test
    @DisplayName("testRule: 命中返回 matchedCustomerIds 与详情")
    void testRule_matched() throws ScrmException {
        ScrmTagRuleEntity rule = buildRuleEntity(1L, 10L, "ACTIVE");
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));
        // 客户 lifecycle=ACTIVE 命中条件
        ScrmCustomerEntity customer = buildCustomerEntity(100L, "ACTIVE");
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));

        ScrmTagRuleTestDto testDto = new ScrmTagRuleTestDto();
        testDto.setRuleId(1L);
        testDto.setCustomerIds(List.of(100L));

        Map<String, Object> result = service.testRule(testDto);

        assertThat(result.get("matchedCount")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Long> matchedIds = (List<Long>) result.get("matchedCustomerIds");
        assertThat(matchedIds).containsExactly(100L);
        // 测试不实际打标
        verify(tagCustomerRepository, never()).save(any());
    }

    @Test
    @DisplayName("testRule: 未命中返回空 matchedCustomerIds")
    void testRule_notMatched() throws ScrmException {
        ScrmTagRuleEntity rule = buildRuleEntity(1L, 10L, "ACTIVE");
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));
        // 客户 lifecycle=DORMANT 未命中 ACTIVE 条件
        ScrmCustomerEntity customer = buildCustomerEntity(100L, "DORMANT");
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));

        ScrmTagRuleTestDto testDto = new ScrmTagRuleTestDto();
        testDto.setRuleId(1L);
        testDto.setCustomerIds(List.of(100L));

        Map<String, Object> result = service.testRule(testDto);

        assertThat(result.get("matchedCount")).isEqualTo(0);
        @SuppressWarnings("unchecked")
        List<Long> matchedIds = (List<Long>) result.get("matchedCustomerIds");
        assertThat(matchedIds).isEmpty();
    }

    @Test
    @DisplayName("testRule: 客户不存在时记为未命中且带 error")
    void testRule_customerNotFound() throws ScrmException {
        ScrmTagRuleEntity rule = buildRuleEntity(1L, 10L, "ACTIVE");
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));
        when(customerRepository.findById(100L)).thenReturn(Optional.empty());

        ScrmTagRuleTestDto testDto = new ScrmTagRuleTestDto();
        testDto.setRuleId(1L);
        testDto.setCustomerIds(List.of(100L));

        Map<String, Object> result = service.testRule(testDto);

        assertThat(result.get("matchedCount")).isEqualTo(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) result.get("details");
        assertThat(details.get(0).get("matched")).isEqualTo(false);
        assertThat(details.get(0).get("error")).isEqualTo("客户不存在");
    }

    @Test
    @DisplayName("evaluateRule: 命中规则后自动打标 (tag_source=AUTO, is_auto=true)")
    void evaluateRule_matchedAutoAssign() throws ScrmException {
        ScrmCustomerEntity customer = buildCustomerEntity(100L, "ACTIVE");
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        ScrmTagRuleEntity rule = buildRuleEntity(1L, 10L, "ACTIVE");
        when(ruleRepository.findByStatus("ACTIVE")).thenReturn(List.of(rule));
        ScrmCustomerTagEntity tag = buildTagEntity(10L);
        when(tagRepository.findById(10L)).thenReturn(Optional.of(tag));
        when(tagCustomerRepository.findByCustomerIdAndTagId(100L, 10L))
                .thenReturn(Optional.empty());
        when(tagCustomerRepository.save(any(ScrmTagCustomerEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(tagCustomerRepository.countByTagId(10L)).thenReturn(1L);
        when(tagRepository.save(any(ScrmCustomerTagEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmTagRuleEvaluateDto dto = new ScrmTagRuleEvaluateDto();
        dto.setCustomerId(100L);
        dto.setTriggerEvent("CUSTOMER_UPDATED");
        dto.setCustomerContext(Map.of("lifecycle", "ACTIVE"));

        Map<String, Object> result = service.evaluateRule(dto);

        assertThat(result.get("matchedCount")).isEqualTo(1);
        ArgumentCaptor<ScrmTagCustomerEntity> captor =
                ArgumentCaptor.forClass(ScrmTagCustomerEntity.class);
        verify(tagCustomerRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getTagSource()).isEqualTo("AUTO");
        assertThat(captor.getValue().getIsAuto()).isTrue();
    }

    @Test
    @DisplayName("evaluateRule: 未命中不打标")
    void evaluateRule_notMatched() throws ScrmException {
        ScrmCustomerEntity customer = buildCustomerEntity(100L, "DORMANT");
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        ScrmTagRuleEntity rule = buildRuleEntity(1L, 10L, "ACTIVE");
        when(ruleRepository.findByStatus("ACTIVE")).thenReturn(List.of(rule));

        ScrmTagRuleEvaluateDto dto = new ScrmTagRuleEvaluateDto();
        dto.setCustomerId(100L);
        dto.setTriggerEvent("CUSTOMER_UPDATED");
        dto.setCustomerContext(Map.of("lifecycle", "DORMANT"));

        Map<String, Object> result = service.evaluateRule(dto);

        assertThat(result.get("matchedCount")).isEqualTo(0);
        verify(tagCustomerRepository, never()).save(any());
    }

    @Test
    @DisplayName("evaluateRule: 关联标签已禁用跳过打标")
    void evaluateRule_disabledTagSkip() throws ScrmException {
        ScrmCustomerEntity customer = buildCustomerEntity(100L, "ACTIVE");
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        ScrmTagRuleEntity rule = buildRuleEntity(1L, 10L, "ACTIVE");
        when(ruleRepository.findByStatus("ACTIVE")).thenReturn(List.of(rule));
        // 标签已禁用 → 跳过
        when(tagRepository.findById(10L)).thenReturn(Optional.of(buildDisabledTagEntity(10L)));

        ScrmTagRuleEvaluateDto dto = new ScrmTagRuleEvaluateDto();
        dto.setCustomerId(100L);
        dto.setTriggerEvent("CUSTOMER_UPDATED");
        dto.setCustomerContext(Map.of("lifecycle", "ACTIVE"));

        Map<String, Object> result = service.evaluateRule(dto);

        assertThat(result.get("matchedCount")).isEqualTo(0);
        verify(tagCustomerRepository, never()).save(any());
    }

    @Test
    @DisplayName("evaluateRule: 客户不存在抛 NOT_FOUND")
    void evaluateRule_customerNotFound() {
        when(customerRepository.findById(100L)).thenReturn(Optional.empty());

        ScrmTagRuleEvaluateDto dto = new ScrmTagRuleEvaluateDto();
        dto.setCustomerId(100L);
        dto.setTriggerEvent("CUSTOMER_UPDATED");
        dto.setCustomerContext(Map.of());

        assertThatThrownBy(() -> service.evaluateRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户不存在");
    }

    @Test
    @DisplayName("evaluateRule: dto 为空抛 BAD_REQUEST")
    void evaluateRule_nullDto() {
        assertThatThrownBy(() -> service.evaluateRule(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("评估参数不能为空");
    }

    @Test
    @DisplayName("evaluateRule: customerId 为空抛 BAD_REQUEST")
    void evaluateRule_missingCustomerId() {
        ScrmTagRuleEvaluateDto dto = new ScrmTagRuleEvaluateDto();
        dto.setTriggerEvent("CUSTOMER_UPDATED");

        assertThatThrownBy(() -> service.evaluateRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户 ID 不能为空");
    }

    @Test
    @DisplayName("executeRule: 遍历客户并对命中者自动打标, 增量更新执行统计")
    void executeRule_success() throws ScrmException {
        ScrmTagRuleEntity rule = buildRuleEntity(1L, 10L, "ACTIVE");
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));
        ScrmCustomerTagEntity tag = buildTagEntity(10L);
        when(tagRepository.findById(10L)).thenReturn(Optional.of(tag));
        // 两个客户: 一个 ACTIVE 命中, 一个 DORMANT 未命中
        ScrmCustomerEntity c1 = buildCustomerEntity(100L, "ACTIVE");
        ScrmCustomerEntity c2 = buildCustomerEntity(101L, "DORMANT");
        when(customerRepository.findAll(any(Specification.class))).thenReturn(List.of(c1, c2));
        // c1 打标相关 mock
        when(tagCustomerRepository.findByCustomerIdAndTagId(100L, 10L))
                .thenReturn(Optional.empty());
        when(tagCustomerRepository.save(any(ScrmTagCustomerEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(tagCustomerRepository.countByTagId(10L)).thenReturn(1L);
        when(tagRepository.save(any(ScrmCustomerTagEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        int matched = service.executeRule(1L);

        assertThat(matched).isEqualTo(1);
        verify(ruleRepository, times(1)).updateExecutionStats(eq(1L), any(LocalDateTime.class), eq(1));
        // 只为命中的客户打标
        verify(tagCustomerRepository, times(1)).save(any(ScrmTagCustomerEntity.class));
    }

    @Test
    @DisplayName("runAllRules: 遍历活跃规则并返回每条结果")
    void runAllRules_success() throws ScrmException {
        ScrmTagRuleEntity rule1 = buildRuleEntity(1L, 10L, "ACTIVE");
        ScrmTagRuleEntity rule2 = buildRuleEntity(2L, 20L, "ACTIVE");
        when(ruleRepository.findByStatus("ACTIVE"))
                .thenReturn(List.of(rule1, rule2));
        // executeRule 调用链
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule1));
        when(ruleRepository.findById(2L)).thenReturn(Optional.of(rule2));
        ScrmCustomerTagEntity tag1 = buildTagEntity(10L);
        ScrmCustomerTagEntity tag2 = buildTagEntity(20L);
        when(tagRepository.findById(10L)).thenReturn(Optional.of(tag1));
        when(tagRepository.findById(20L)).thenReturn(Optional.of(tag2));
        when(customerRepository.findAll(any(Specification.class))).thenReturn(Collections.emptyList());
        when(ruleRepository.updateExecutionStats(anyLong(), any(LocalDateTime.class), eq(0)))
                .thenReturn(1);
        when(tagCustomerRepository.countByTagId(anyLong())).thenReturn(0L);
        when(tagRepository.save(any(ScrmCustomerTagEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        List<Map<String, Object>> results = service.runAllRules();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).get("ruleId")).isEqualTo(1L);
        assertThat(results.get(1).get("ruleId")).isEqualTo(2L);
    }

    @Test
    @DisplayName("runAllRules: 无活跃规则返回空结果")
    void runAllRules_empty() {
        when(ruleRepository.findByStatus("ACTIVE"))
                .thenReturn(Collections.emptyList());

        List<Map<String, Object>> results = service.runAllRules();

        assertThat(results).isEmpty();
    }
}
