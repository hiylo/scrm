/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLevelServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmCustomerLevelAssignDto;
import org.hiylo.scrm.dto.ScrmCustomerLevelDto;
import org.hiylo.scrm.dto.ScrmCustomerLevelRuleDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerLevelEntity;
import org.hiylo.scrm.entity.ScrmCustomerLevelHistoryEntity;
import org.hiylo.scrm.entity.ScrmCustomerLevelRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCustomerLevelHistoryRepository;
import org.hiylo.scrm.repository.ScrmCustomerLevelRepository;
import org.hiylo.scrm.repository.ScrmCustomerLevelRuleRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
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
 * ScrmCustomerLevelService 单元测试
 * <p>
 * 聚焦客户等级 CRUD (参数校验 / 默认值填充 / 编码唯一性 / 默认等级切换)、
 * 升降级规则 CRUD (目标等级启用校验 / conditions JSON 解析)、等级分配 (首次 INITIAL /
 * 后续 MANUAL / 同等级跳过 / 禁用等级拒绝)、客户当前等级查询 (历史优先 / 默认兜底) 与
 * 自动升降级评估 (升级优先 / 目标 order 校验 / 同等级跳过) 等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmCustomerLevelService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmCustomerLevelServiceTest {

    /** 客户等级数据仓库 Mock 桩 */
    @Mock
    private ScrmCustomerLevelRepository levelRepository;
    /** 客户等级规则数据仓库 Mock 桩 */
    @Mock
    private ScrmCustomerLevelRuleRepository ruleRepository;
    /** 客户等级历史数据仓库 Mock 桩 */
    @Mock
    private ScrmCustomerLevelHistoryRepository historyRepository;
    /** 客户数据仓库 Mock 桩 */
    @Mock
    private ScrmCustomerRepository customerRepository;

    /** 被测服务实例 */
    private ScrmCustomerLevelService service;
    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new ScrmCustomerLevelService(levelRepository, ruleRepository,
                historyRepository, customerRepository, objectMapper);
    }

    @AfterEach
    void tearDown() {
    }

    // ==================== 等级 CRUD ====================

    @Test
    @DisplayName("createLevel: 成功创建, enabled/isDefault 缺省填充默认值")
    void createLevel_success() throws Exception {
        ScrmCustomerLevelDto dto = buildLevelDto();
        when(levelRepository.findByLevelCode("VIP")).thenReturn(Optional.empty());
        when(levelRepository.save(any(ScrmCustomerLevelEntity.class)))
                .thenAnswer(inv -> assignId(inv.getArgument(0), 100L));

        ScrmCustomerLevelEntity result = service.createLevel(dto);

        assertThat(result.getId()).isEqualTo(100L);
        ArgumentCaptor<ScrmCustomerLevelEntity> captor = ArgumentCaptor.forClass(ScrmCustomerLevelEntity.class);
        verify(levelRepository).save(captor.capture());
        ScrmCustomerLevelEntity saved = captor.getValue();
        assertThat(saved.getLevelName()).isEqualTo("VIP会员");
        assertThat(saved.getLevelCode()).isEqualTo("VIP");
        assertThat(saved.getLevelOrder()).isEqualTo(10);
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getIsDefault()).isFalse();
    }

    @Test
    @DisplayName("createLevel: isDefault=true 时先清空同账号其他默认标记")
    void createLevel_isDefault_clearsOthers() throws Exception {
        ScrmCustomerLevelDto dto = buildLevelDto();
        dto.setIsDefault(true);
        when(levelRepository.findByLevelCode("VIP")).thenReturn(Optional.empty());
        when(levelRepository.save(any(ScrmCustomerLevelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.createLevel(dto);

        verify(levelRepository).clearDefaultFlag();
        ArgumentCaptor<ScrmCustomerLevelEntity> captor = ArgumentCaptor.forClass(ScrmCustomerLevelEntity.class);
        verify(levelRepository).save(captor.capture());
        assertThat(captor.getValue().getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("createLevel: dto 为 null 抛 BAD_REQUEST")
    void createLevel_nullDto() {
        assertThatThrownBy(() -> service.createLevel(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("等级参数不能为空");
    }

    @Test
    @DisplayName("createLevel: levelName 为空抛 BAD_REQUEST")
    void createLevel_blankLevelName() {
        ScrmCustomerLevelDto dto = buildLevelDto();
        dto.setLevelName("");
        assertThatThrownBy(() -> service.createLevel(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("等级名称不能为空");
    }

    @Test
    @DisplayName("createLevel: levelCode 为空抛 BAD_REQUEST")
    void createLevel_blankLevelCode() {
        ScrmCustomerLevelDto dto = buildLevelDto();
        dto.setLevelCode("");
        assertThatThrownBy(() -> service.createLevel(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("等级编码不能为空");
    }

    @Test
    @DisplayName("createLevel: levelOrder 为 null 抛 BAD_REQUEST")
    void createLevel_nullLevelOrder() {
        ScrmCustomerLevelDto dto = buildLevelDto();
        dto.setLevelOrder(null);
        assertThatThrownBy(() -> service.createLevel(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("等级排序不能为空");
    }

    @Test
    @DisplayName("createLevel: 等级编码重复抛 CONFLICT")
    void createLevel_duplicateCode() {
        ScrmCustomerLevelDto dto = buildLevelDto();
        when(levelRepository.findByLevelCode("VIP"))
                .thenReturn(Optional.of(new ScrmCustomerLevelEntity()));
        assertThatThrownBy(() -> service.createLevel(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("等级编码已存在");
    }

    @Test
    @DisplayName("updateLevel: 部分更新, levelCode 变更且不冲突时成功")
    void updateLevel_success() throws Exception {
        ScrmCustomerLevelEntity existing = buildLevelEntity(100L);
        when(levelRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(levelRepository.findByLevelCode("DIAMOND")).thenReturn(Optional.empty());
        when(levelRepository.save(any(ScrmCustomerLevelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCustomerLevelDto dto = new ScrmCustomerLevelDto();
        dto.setLevelCode("DIAMOND");
        dto.setLevelName("钻石会员");
        ScrmCustomerLevelEntity result = service.updateLevel(100L, dto);

        assertThat(result.getLevelCode()).isEqualTo("DIAMOND");
        assertThat(result.getLevelName()).isEqualTo("钻石会员");
    }

    @Test
    @DisplayName("updateLevel: levelCode 被其他等级占用抛 CONFLICT")
    void updateLevel_ruleCodeConflict() {
        ScrmCustomerLevelEntity existing = buildLevelEntity(100L);
        ScrmCustomerLevelEntity other = buildLevelEntity(200L);
        when(levelRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(levelRepository.findByLevelCode("DIAMOND")).thenReturn(Optional.of(other));

        ScrmCustomerLevelDto dto = new ScrmCustomerLevelDto();
        dto.setLevelCode("DIAMOND");
        assertThatThrownBy(() -> service.updateLevel(100L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("等级编码已存在");
    }

    
    
    @Test
    @DisplayName("deleteLevel: 无规则与历史引用时成功删除")
    void deleteLevel_success() throws Exception {
        ScrmCustomerLevelEntity existing = buildLevelEntity(100L);
        when(levelRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(ruleRepository.findByTargetLevelId(100L)).thenReturn(Collections.emptyList());
        when(historyRepository.count(any(Specification.class))).thenReturn(0L);

        service.deleteLevel(100L);

        verify(levelRepository).delete(existing);
    }

    @Test
    @DisplayName("deleteLevel: 有规则引用抛 CONFLICT")
    void deleteLevel_hasRuleReference() {
        ScrmCustomerLevelEntity existing = buildLevelEntity(100L);
        when(levelRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(ruleRepository.findByTargetLevelId(100L))
                .thenReturn(List.of(new ScrmCustomerLevelRuleEntity()));

        assertThatThrownBy(() -> service.deleteLevel(100L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仍有 1 条升降级规则引用该等级");
        verify(levelRepository, never()).delete(any(ScrmCustomerLevelEntity.class));
    }

    @Test
    @DisplayName("deleteLevel: 有历史引用抛 CONFLICT")
    void deleteLevel_hasHistoryReference() {
        ScrmCustomerLevelEntity existing = buildLevelEntity(100L);
        when(levelRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(ruleRepository.findByTargetLevelId(100L)).thenReturn(Collections.emptyList());
        when(historyRepository.count(any(Specification.class))).thenReturn(5L);

        assertThatThrownBy(() -> service.deleteLevel(100L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仍有 5 条等级变更历史引用该等级");
        verify(levelRepository, never()).delete(any(ScrmCustomerLevelEntity.class));
    }

    @Test
    @DisplayName("setDefaultLevel: 清空其他默认标记后设置当前等级为默认")
    void setDefaultLevel_success() throws Exception {
        ScrmCustomerLevelEntity existing = buildLevelEntity(100L);
        when(levelRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(levelRepository.save(any(ScrmCustomerLevelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCustomerLevelEntity result = service.setDefaultLevel(100L);

        verify(levelRepository).clearDefaultFlag();
        assertThat(result.getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("enableLevel: 成功启用")
    void enableLevel_success() throws Exception {
        ScrmCustomerLevelEntity existing = buildLevelEntity(100L);
        existing.setEnabled(false);
        when(levelRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(levelRepository.save(any(ScrmCustomerLevelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmCustomerLevelEntity result = service.enableLevel(100L);
        assertThat(result.getEnabled()).isTrue();
    }

    @Test
    @DisplayName("disableLevel: 成功禁用")
    void disableLevel_success() throws Exception {
        ScrmCustomerLevelEntity existing = buildLevelEntity(100L);
        when(levelRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(levelRepository.save(any(ScrmCustomerLevelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmCustomerLevelEntity result = service.disableLevel(100L);
        assertThat(result.getEnabled()).isFalse();
    }

    // ==================== 规则管理 ====================

    @Test
    @DisplayName("createRule: 成功创建, 默认值填充 conditionType/actionType/priority/enabled")
    void createRule_success() throws Exception {
        ScrmCustomerLevelRuleDto dto = buildRuleDto();
        ScrmCustomerLevelEntity targetLevel = buildLevelEntity(10L);
        when(levelRepository.findById(10L)).thenReturn(Optional.of(targetLevel));
        when(ruleRepository.save(any(ScrmCustomerLevelRuleEntity.class)))
                .thenAnswer(inv -> assignRuleId(inv.getArgument(0), 200L));

        ScrmCustomerLevelRuleEntity result = service.createRule(dto);

        assertThat(result.getId()).isEqualTo(200L);
        ArgumentCaptor<ScrmCustomerLevelRuleEntity> captor = ArgumentCaptor.forClass(ScrmCustomerLevelRuleEntity.class);
        verify(ruleRepository).save(captor.capture());
        ScrmCustomerLevelRuleEntity saved = captor.getValue();
        assertThat(saved.getRuleName()).isEqualTo("升级到VIP");
        assertThat(saved.getRuleType()).isEqualTo("UPGRADE");
        assertThat(saved.getConditionType()).isEqualTo("ALL");
        assertThat(saved.getActionType()).isEqualTo("SET_LEVEL");
        assertThat(saved.getPriority()).isEqualTo(0);
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getMatchCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("createRule: 目标等级已禁用抛 BAD_REQUEST")
    void createRule_targetLevelDisabled() {
        ScrmCustomerLevelRuleDto dto = buildRuleDto();
        ScrmCustomerLevelEntity targetLevel = buildLevelEntity(10L);
        targetLevel.setEnabled(false);
        when(levelRepository.findById(10L)).thenReturn(Optional.of(targetLevel));

        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("目标等级已禁用");
    }

    @Test
    @DisplayName("createRule: ruleType 非法抛 BAD_REQUEST")
    void createRule_invalidRuleType() {
        ScrmCustomerLevelRuleDto dto = buildRuleDto();
        dto.setRuleType("INVALID");
        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("规则类型非法");
    }

    @Test
    @DisplayName("createRule: conditions 非合法 JSON 抛 BAD_REQUEST")
    void createRule_invalidConditionsJson() {
        ScrmCustomerLevelRuleDto dto = buildRuleDto();
        dto.setConditions("{invalid");
        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("条件 JSON 解析失败");
    }

    @Test
    @DisplayName("createRule: conditions 操作符非法抛 BAD_REQUEST")
    void createRule_invalidOperator() {
        ScrmCustomerLevelRuleDto dto = buildRuleDto();
        dto.setConditions("[{\"field\":\"totalSpent\",\"operator\":\"gte\",\"value\":1000}]");
        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("操作符非法");
    }

    // ==================== 等级分配 ====================

    @Test
    @DisplayName("assignLevel: 首次入等级 changeType 为 INITIAL, 写入历史")
    void assignLevel_firstTime_initial() throws Exception {
        ScrmCustomerLevelAssignDto assignDto = new ScrmCustomerLevelAssignDto();
        assignDto.setCustomerId(500L);
        assignDto.setLevelId(10L);
        assignDto.setReason("手动分配");
        ScrmCustomerEntity customer = buildCustomerEntity(500L);
        ScrmCustomerLevelEntity targetLevel = buildLevelEntity(10L);
        when(customerRepository.findById(500L)).thenReturn(Optional.of(customer));
        when(levelRepository.findById(10L)).thenReturn(Optional.of(targetLevel));
        when(historyRepository.findFirstByCustomerIdOrderByChangedAtDescIdDesc(500L))
                .thenReturn(Optional.empty());
        when(historyRepository.save(any(ScrmCustomerLevelHistoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCustomerLevelHistoryEntity result = service.assignLevel(assignDto);

        ArgumentCaptor<ScrmCustomerLevelHistoryEntity> captor
            = ArgumentCaptor.forClass(ScrmCustomerLevelHistoryEntity.class);
        verify(historyRepository).save(captor.capture());
        ScrmCustomerLevelHistoryEntity saved = captor.getValue();
        assertThat(saved.getCustomerId()).isEqualTo(500L);
        assertThat(saved.getFromLevelId()).isNull();
        assertThat(saved.getToLevelId()).isEqualTo(10L);
        assertThat(saved.getToLevelName()).isEqualTo("VIP会员");
        assertThat(saved.getChangeType()).isEqualTo("INITIAL");
        assertThat(saved.getChangeReason()).isEqualTo("手动分配");
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("assignLevel: 已有等级时 changeType 为 MANUAL")
    void assignLevel_existing_manual() throws Exception {
        ScrmCustomerLevelAssignDto assignDto = new ScrmCustomerLevelAssignDto();
        assignDto.setCustomerId(500L);
        assignDto.setLevelId(20L);
        ScrmCustomerEntity customer = buildCustomerEntity(500L);
        ScrmCustomerLevelEntity targetLevel = buildLevelEntity(20L);
        targetLevel.setLevelCode("DIAMOND");
        targetLevel.setLevelName("钻石会员");
        ScrmCustomerLevelHistoryEntity prev = new ScrmCustomerLevelHistoryEntity();
        prev.setToLevelId(10L);
        prev.setToLevelName("VIP会员");
        when(customerRepository.findById(500L)).thenReturn(Optional.of(customer));
        when(levelRepository.findById(20L)).thenReturn(Optional.of(targetLevel));
        when(historyRepository.findFirstByCustomerIdOrderByChangedAtDescIdDesc(500L))
                .thenReturn(Optional.of(prev));
        when(historyRepository.save(any(ScrmCustomerLevelHistoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCustomerLevelHistoryEntity result = service.assignLevel(assignDto);

        ArgumentCaptor<ScrmCustomerLevelHistoryEntity> captor
            = ArgumentCaptor.forClass(ScrmCustomerLevelHistoryEntity.class);
        verify(historyRepository).save(captor.capture());
        ScrmCustomerLevelHistoryEntity saved = captor.getValue();
        assertThat(saved.getFromLevelId()).isEqualTo(10L);
        assertThat(saved.getFromLevelName()).isEqualTo("VIP会员");
        assertThat(saved.getToLevelId()).isEqualTo(20L);
        assertThat(saved.getChangeType()).isEqualTo("MANUAL");
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("assignLevel: 同等级跳过, 不写历史")
    void assignLevel_sameLevel_skip() throws Exception {
        ScrmCustomerLevelAssignDto assignDto = new ScrmCustomerLevelAssignDto();
        assignDto.setCustomerId(500L);
        assignDto.setLevelId(10L);
        ScrmCustomerEntity customer = buildCustomerEntity(500L);
        ScrmCustomerLevelEntity targetLevel = buildLevelEntity(10L);
        ScrmCustomerLevelHistoryEntity prev = new ScrmCustomerLevelHistoryEntity();
        prev.setToLevelId(10L);
        prev.setToLevelName("VIP会员");
        when(customerRepository.findById(500L)).thenReturn(Optional.of(customer));
        when(levelRepository.findById(10L)).thenReturn(Optional.of(targetLevel));
        when(historyRepository.findFirstByCustomerIdOrderByChangedAtDescIdDesc(500L))
                .thenReturn(Optional.of(prev));

        ScrmCustomerLevelHistoryEntity result = service.assignLevel(assignDto);

        assertThat(result).isNull();
        verify(historyRepository, never()).save(any(ScrmCustomerLevelHistoryEntity.class));
    }

    @Test
    @DisplayName("assignLevel: 等级已禁用抛 BAD_REQUEST")
    void assignLevel_disabledLevel() {
        ScrmCustomerLevelAssignDto assignDto = new ScrmCustomerLevelAssignDto();
        assignDto.setCustomerId(500L);
        assignDto.setLevelId(10L);
        ScrmCustomerEntity customer = buildCustomerEntity(500L);
        ScrmCustomerLevelEntity targetLevel = buildLevelEntity(10L);
        targetLevel.setEnabled(false);
        when(customerRepository.findById(500L)).thenReturn(Optional.of(customer));
        when(levelRepository.findById(10L)).thenReturn(Optional.of(targetLevel));

        assertThatThrownBy(() -> service.assignLevel(assignDto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("等级已禁用");
    }

    @Test
    @DisplayName("assignLevel: dto 为 null 抛 BAD_REQUEST")
    void assignLevel_nullDto() {
        assertThatThrownBy(() -> service.assignLevel(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("分配参数不能为空");
    }

    // ==================== 客户当前等级查询 ====================

    @Test
    @DisplayName("getCustomerLevel: 有历史记录返回对应等级")
    void getCustomerLevel_withHistory() throws Exception {
        ScrmCustomerEntity customer = buildCustomerEntity(500L);
        ScrmCustomerLevelEntity level = buildLevelEntity(10L);
        ScrmCustomerLevelHistoryEntity hist = new ScrmCustomerLevelHistoryEntity();
        hist.setToLevelId(10L);
        when(customerRepository.findById(500L)).thenReturn(Optional.of(customer));
        when(historyRepository.findFirstByCustomerIdOrderByChangedAtDescIdDesc(500L))
                .thenReturn(Optional.of(hist));
        when(levelRepository.findById(10L)).thenReturn(Optional.of(level));

        ScrmCustomerLevelEntity result = service.getCustomerLevel(500L);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getLevelCode()).isEqualTo("VIP");
    }

    @Test
    @DisplayName("getCustomerLevel: 无历史记录返回默认等级 (若启用)")
    void getCustomerLevel_noHistory_returnsDefault() throws Exception {
        ScrmCustomerEntity customer = buildCustomerEntity(500L);
        ScrmCustomerLevelEntity defaultLevel = buildLevelEntity(5L);
        defaultLevel.setIsDefault(true);
        when(customerRepository.findById(500L)).thenReturn(Optional.of(customer));
        when(historyRepository.findFirstByCustomerIdOrderByChangedAtDescIdDesc(500L))
                .thenReturn(Optional.empty());
        when(levelRepository.findByIsDefaultTrue()).thenReturn(Optional.of(defaultLevel));

        ScrmCustomerLevelEntity result = service.getCustomerLevel(500L);

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("getCustomerLevel: 客户不存在抛 NOT_FOUND")
    void getCustomerLevel_customerNotFound() {
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getCustomerLevel(999L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户不存在");
    }

    // ==================== 自动评估 ====================

    @Test
    @DisplayName("evaluateRules: 升级规则命中且目标 order 高于当前, 写入 AUTO 升级历史")
    void evaluateRules_upgradeMatched() throws Exception {
        ScrmCustomerEntity customer = buildCustomerEntity(500L);
        ScrmCustomerLevelEntity currentLevel = buildLevelEntity(10L);
        currentLevel.setLevelOrder(10);
        ScrmCustomerLevelEntity targetLevel = buildLevelEntity(20L);
        targetLevel.setLevelOrder(20);
        ScrmCustomerLevelHistoryEntity currentHist = new ScrmCustomerLevelHistoryEntity();
        currentHist.setToLevelId(10L);
        ScrmCustomerLevelRuleEntity upgradeRule = buildRuleEntity(200L, "UPGRADE");
        upgradeRule.setTargetLevelId(20L);
        upgradeRule.setConditions("[{\"field\":\"totalSpent\",\"operator\":\"gt\",\"value\":500}]");

        when(customerRepository.findById(500L)).thenReturn(Optional.of(customer));
        when(historyRepository.findFirstByCustomerIdOrderByChangedAtDescIdDesc(500L))
                .thenReturn(Optional.of(currentHist));
        when(levelRepository.findById(10L)).thenReturn(Optional.of(currentLevel));
        when(ruleRepository.findByRuleTypeAndEnabledTrueOrderByPriorityAsc("UPGRADE"))
                .thenReturn(List.of(upgradeRule));
        when(levelRepository.findById(20L)).thenReturn(Optional.of(targetLevel));
        when(historyRepository.save(any(ScrmCustomerLevelHistoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // totalSpent 缺省 0, gt 500 不满足, 改用 registrationDays (客户 createTime 较早, 注册天数 > 0)
        upgradeRule.setConditions("[{\"field\":\"registrationDays\",\"operator\":\"gt\",\"value\":0}]");

        ScrmCustomerLevelHistoryEntity result = service.evaluateRules(500L);

        assertThat(result).isNotNull();
        ArgumentCaptor<ScrmCustomerLevelHistoryEntity> captor
            = ArgumentCaptor.forClass(ScrmCustomerLevelHistoryEntity.class);
        verify(historyRepository).save(captor.capture());
        assertThat(captor.getValue().getChangeType()).isEqualTo("AUTO");
        assertThat(captor.getValue().getToLevelId()).isEqualTo(20L);
        verify(ruleRepository).incrementMatchCount(eq(200L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("evaluateRules: 升级规则命中但目标 order 不高于当前, 跳过不写历史")
    void evaluateRules_upgradeTargetNotHigher_skip() throws Exception {
        ScrmCustomerEntity customer = buildCustomerEntity(500L);
        ScrmCustomerLevelEntity currentLevel = buildLevelEntity(10L);
        currentLevel.setLevelOrder(20);
        ScrmCustomerLevelEntity targetLevel = buildLevelEntity(20L);
        targetLevel.setLevelOrder(10);
        ScrmCustomerLevelHistoryEntity currentHist = new ScrmCustomerLevelHistoryEntity();
        currentHist.setToLevelId(10L);
        ScrmCustomerLevelRuleEntity upgradeRule = buildRuleEntity(200L, "UPGRADE");
        upgradeRule.setTargetLevelId(20L);
        upgradeRule.setConditions("[{\"field\":\"registrationDays\",\"operator\":\"gt\",\"value\":0}]");

        when(customerRepository.findById(500L)).thenReturn(Optional.of(customer));
        when(historyRepository.findFirstByCustomerIdOrderByChangedAtDescIdDesc(500L))
                .thenReturn(Optional.of(currentHist));
        when(levelRepository.findById(10L)).thenReturn(Optional.of(currentLevel));
        when(ruleRepository.findByRuleTypeAndEnabledTrueOrderByPriorityAsc("UPGRADE"))
                .thenReturn(List.of(upgradeRule));
        when(levelRepository.findById(20L)).thenReturn(Optional.of(targetLevel));

        ScrmCustomerLevelHistoryEntity result = service.evaluateRules(500L);

        // 升级目标 order 不高于当前 → 跳过; 无降级规则 → 返回 null
        assertThat(result).isNull();
        verify(historyRepository, never()).save(any(ScrmCustomerLevelHistoryEntity.class));
    }

    @Test
    @DisplayName("evaluateRules: 客户不存在抛 NOT_FOUND")
    void evaluateRules_customerNotFound() {
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.evaluateRules(999L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户不存在");
    }

    // ==================== 等级分布统计 ====================

    @Test
    @DisplayName("getLevelDistribution: 聚合各等级客户数并追加总计行")
    void getLevelDistribution_success() {
        ScrmCustomerLevelEntity level1 = buildLevelEntity(10L);
        level1.setLevelOrder(10);
        ScrmCustomerLevelEntity level2 = buildLevelEntity(20L);
        level2.setLevelOrder(20);
        when(historyRepository.getLevelDistribution())
                .thenReturn(List.of(new Object[]{10L, 5L}, new Object[]{20L, 3L}));
        when(levelRepository.findAllByOrderByLevelOrderAsc())
                .thenReturn(List.of(level1, level2));

        List<java.util.Map<String, Object>> result = service.getLevelDistribution();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).get("levelId")).isEqualTo(10L);
        assertThat(result.get(0).get("count")).isEqualTo(5L);
        assertThat(result.get(1).get("levelId")).isEqualTo(20L);
        assertThat(result.get(1).get("count")).isEqualTo(3L);
        // 最后一行为总计
        assertThat(result.get(2).get("levelName")).isEqualTo("总计");
        assertThat(result.get(2).get("count")).isEqualTo(8L);
    }

    // ==================== 分页查询 ====================

    @Test
    @DisplayName("listLevels: 返回分页结果")
    void listLevels_success() {
        ScrmCustomerLevelEntity level = buildLevelEntity(100L);
        Page<ScrmCustomerLevelEntity> page = new PageImpl<>(List.of(level), PageRequest.of(0, 10), 1);
        when(levelRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<ScrmCustomerLevelEntity> result = service.listLevels(true, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getLevelCode()).isEqualTo("VIP");
    }

    // ==================== 辅助方法 ====================

    private ScrmCustomerLevelDto buildLevelDto() {
        ScrmCustomerLevelDto dto = new ScrmCustomerLevelDto();
        dto.setLevelName("VIP会员");
        dto.setLevelCode("VIP");
        dto.setLevelOrder(10);
        return dto;
    }

    private ScrmCustomerLevelEntity buildLevelEntity(Long id) {
        ScrmCustomerLevelEntity entity = new ScrmCustomerLevelEntity();
        entity.setId(id);
        entity.setLevelName("VIP会员");
        entity.setLevelCode("VIP");
        entity.setLevelOrder(10);
        entity.setIsDefault(false);
        entity.setEnabled(true);
        return entity;
    }

    private ScrmCustomerLevelRuleDto buildRuleDto() {
        ScrmCustomerLevelRuleDto dto = new ScrmCustomerLevelRuleDto();
        dto.setRuleName("升级到VIP");
        dto.setTargetLevelId(10L);
        dto.setRuleType("UPGRADE");
        dto.setConditions("[{\"field\":\"totalSpent\",\"operator\":\"gt\",\"value\":1000}]");
        return dto;
    }

    private ScrmCustomerLevelRuleEntity buildRuleEntity(Long id, String ruleType) {
        ScrmCustomerLevelRuleEntity entity = new ScrmCustomerLevelRuleEntity();
        entity.setId(id);
        entity.setRuleName("升级到VIP");
        entity.setTargetLevelId(10L);
        entity.setRuleType(ruleType);
        entity.setConditionType("ALL");
        entity.setConditions("[]");
        entity.setActionType("SET_LEVEL");
        entity.setPriority(0);
        entity.setEnabled(true);
        entity.setMatchCount(0);
        return entity;
    }

    private ScrmCustomerEntity buildCustomerEntity(Long id) {
        ScrmCustomerEntity entity = new ScrmCustomerEntity();
        entity.setId(id);
        entity.setNickname("客户" + id);
        entity.setLifecycle("ACTIVE");
        entity.setCreateTime(LocalDateTime.now().minusDays(30));
        return entity;
    }

    private ScrmCustomerLevelEntity assignId(ScrmCustomerLevelEntity entity, Long id) {
        entity.setId(id);
        return entity;
    }

    private ScrmCustomerLevelRuleEntity assignRuleId(ScrmCustomerLevelRuleEntity entity, Long id) {
        entity.setId(id);
        return entity;
    }
}
