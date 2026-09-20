/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerCareServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmCareExecuteDto;
import org.hiylo.scrm.dto.ScrmCareRuleDto;
import org.hiylo.scrm.dto.ScrmCareTaskDto;
import org.hiylo.scrm.dto.ScrmFestivalDto;
import org.hiylo.scrm.entity.ScrmCareRecordEntity;
import org.hiylo.scrm.entity.ScrmCareRuleEntity;
import org.hiylo.scrm.entity.ScrmCareTaskEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmFestivalEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCareRecordRepository;
import org.hiylo.scrm.repository.ScrmCareRuleRepository;
import org.hiylo.scrm.repository.ScrmCareTaskRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmFestivalRepository;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmCustomerCareService 单元测试
 * <p>
 * 聚焦客户关怀规则管理 (创建 / 校验 / 默认值填充)、任务管理 (创建 / 执行 / 取消 / 终态保护)、
 * 规则调度生成 (CUSTOM 类型匹配全部客户 / 同规则同客户同日去重)、节日配置管理 (默认值填充)
 * 与关怀效果统计 (类型分布 / 结果分布 / 成功率 / 回应率) 等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmCustomerCareService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmCustomerCareServiceTest {

    /** 客户关怀规则数据仓库 Mock 桩 */
    @Mock
    private ScrmCareRuleRepository ruleRepository;
    /** 关怀任务数据仓库 Mock 桩 */
    @Mock
    private ScrmCareTaskRepository taskRepository;
    /** 节日数据仓库 Mock 桩 */
    @Mock
    private ScrmFestivalRepository festivalRepository;
    /** 关怀记录数据仓库 Mock 桩 */
    @Mock
    private ScrmCareRecordRepository recordRepository;
    /** 客户数据仓库 Mock 桩 */
    @Mock
    private ScrmCustomerRepository customerRepository;

    /** 被测服务实例 */
    private ScrmCustomerCareService service;
    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ScrmCustomerCareRuleService ruleService =
                new ScrmCustomerCareRuleService(ruleRepository, objectMapper);
        ScrmCustomerCareFestivalService festivalService =
                new ScrmCustomerCareFestivalService(festivalRepository, ruleRepository,
                        taskRepository, customerRepository, objectMapper);
        ScrmCustomerCareTaskService taskService =
                new ScrmCustomerCareTaskService(taskRepository, recordRepository, ruleRepository,
                        customerRepository, objectMapper, festivalService);
        ScrmCustomerCareRecordService recordService =
                new ScrmCustomerCareRecordService(recordRepository, customerRepository);
        service = new ScrmCustomerCareService(ruleService, taskService, festivalService, recordService);
    }

    @AfterEach
    void tearDown() {
    }

    // ==================== 规则管理 ====================

    @Test
    @DisplayName("createRule: 成功创建, priority/enabled/executionCount 缺省填充默认值")
    void createRule_success_defaultsFilled() throws Exception {
        ScrmCareRuleDto dto = buildRuleDto();
        when(ruleRepository.save(any(ScrmCareRuleEntity.class)))
                .thenAnswer(inv -> assignRuleId(inv.getArgument(0), 100L));

        ScrmCareRuleEntity result = service.createRule(dto);

        assertThat(result.getId()).isEqualTo(100L);
        ArgumentCaptor<ScrmCareRuleEntity> captor = ArgumentCaptor.forClass(ScrmCareRuleEntity.class);
        verify(ruleRepository).save(captor.capture());
        ScrmCareRuleEntity saved = captor.getValue();
        assertThat(saved.getRuleName()).isEqualTo("生日关怀");
        assertThat(saved.getCareType()).isEqualTo("BIRTHDAY");
        assertThat(saved.getActionType()).isEqualTo("SEND_MESSAGE");
        assertThat(saved.getTriggerCondition()).isEqualTo("{\"daysBefore\":3,\"time\":\"09:00\"}");
        assertThat(saved.getActionContent()).isEqualTo("{\"messageTemplateId\":\"tpl_001\"}");
        assertThat(saved.getPriority()).isZero();
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getExecutionCount()).isZero();
    }

    @Test
    @DisplayName("createRule: careType 非法时抛 BAD_REQUEST, 不写入仓库")
    void createRule_invalidCareType_badRequest() {
        ScrmCareRuleDto dto = buildRuleDto();
        dto.setCareType("INVALID_TYPE");

        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("关怀类型非法");
        verify(ruleRepository, never()).save(any(ScrmCareRuleEntity.class));
    }

    // ==================== 任务管理 ====================

    @Test
    @DisplayName("createTask: 成功创建, status 缺省 PENDING, customerName 缺省取客户昵称")
    void createTask_success_defaultsPendingAndCustomerName() throws Exception {
        ScrmCareTaskDto dto = buildTaskDto();
        dto.setCustomerName(null);
        ScrmCustomerEntity customer = buildCustomerEntity(500L);
        customer.setNickname("张三");
        when(customerRepository.findById(500L)).thenReturn(Optional.of(customer));
        when(taskRepository.save(any(ScrmCareTaskEntity.class)))
                .thenAnswer(inv -> assignTaskId(inv.getArgument(0), 200L));

        ScrmCareTaskEntity result = service.createTask(dto);

        assertThat(result.getId()).isEqualTo(200L);
        ArgumentCaptor<ScrmCareTaskEntity> captor = ArgumentCaptor.forClass(ScrmCareTaskEntity.class);
        verify(taskRepository).save(captor.capture());
        ScrmCareTaskEntity saved = captor.getValue();
        assertThat(saved.getCustomerId()).isEqualTo(500L);
        assertThat(saved.getCustomerName()).isEqualTo("张三");
        assertThat(saved.getCareType()).isEqualTo("BIRTHDAY");
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getRuleId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("executeTask: 成功执行, 更新状态 SUCCESS / 生成回执 / 写入关怀记录 / 累加规则执行统计")
    void executeTask_success_updatesStatusAndCreatesRecord() throws Exception {
        ScrmCareTaskEntity task = buildTaskEntity(200L);
        task.setStatus("PENDING");
        task.setActionType("SEND_MESSAGE");
        task.setRuleId(10L);
        when(taskRepository.findById(200L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(ScrmCareTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCareExecuteDto executeDto = new ScrmCareExecuteDto();
        executeDto.setTaskId(200L);
        executeDto.setResult("SUCCESS");
        executeDto.setResponse("谢谢");
        ScrmCareTaskEntity result = service.executeTask(executeDto);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getExecutedAt()).isNotNull();
        assertThat(result.getActionResult()).contains("已发送关怀消息");
        assertThat(result.getCustomerResponse()).isEqualTo("谢谢");
        assertThat(result.getResponseAt()).isNotNull();
        ArgumentCaptor<ScrmCareRecordEntity> recordCaptor =
                ArgumentCaptor.forClass(ScrmCareRecordEntity.class);
        verify(recordRepository).save(recordCaptor.capture());
        ScrmCareRecordEntity record = recordCaptor.getValue();
        assertThat(record.getCustomerId()).isEqualTo(500L);
        assertThat(record.getCareResult()).isEqualTo("SUCCESS");
        assertThat(record.getCustomerResponse()).isEqualTo("谢谢");
        verify(ruleRepository).incrementExecutionCount(eq(10L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("executeTask: 任务已终态 (SUCCESS) 时抛 CONFLICT, 不写入任何变更")
    void executeTask_terminalState_throwsConflict() {
        ScrmCareTaskEntity task = buildTaskEntity(200L);
        task.setStatus("SUCCESS");
        when(taskRepository.findById(200L)).thenReturn(Optional.of(task));

        ScrmCareExecuteDto executeDto = new ScrmCareExecuteDto();
        executeDto.setTaskId(200L);
        executeDto.setResult("SUCCESS");

        assertThatThrownBy(() -> service.executeTask(executeDto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("任务已终态");
        verify(taskRepository, never()).save(any(ScrmCareTaskEntity.class));
        verify(recordRepository, never()).save(any(ScrmCareRecordEntity.class));
    }

    @Test
    @DisplayName("cancelTask: PENDING 状态可取消, 取消原因写入 notes")
    void cancelTask_success_updatesStatusAndNotes() throws Exception {
        ScrmCareTaskEntity task = buildTaskEntity(200L);
        task.setStatus("PENDING");
        when(taskRepository.findById(200L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(ScrmCareTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCareTaskEntity result = service.cancelTask(200L, "客户不在");

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
        assertThat(result.getNotes()).isEqualTo("客户不在");
    }

    // ==================== 调度生成 ====================

    @Test
    @DisplayName("generateTasksFromDate: CUSTOM 类型匹配全部客户, 同规则同客户同日去重")
    void generateTasksFromDate_customType_dedup() throws Exception {
        ScrmCareRuleEntity rule = buildRuleEntity(10L);
        rule.setCareType("CUSTOM");
        rule.setEnabled(true);
        rule.setTriggerCondition("{\"time\":\"10:30\"}");
        rule.setActionType("SEND_MESSAGE");
        rule.setActionContent("{\"messageTemplateId\":\"tpl_001\"}");
        ScrmCustomerEntity c1 = buildCustomerEntity(500L);
        c1.setNickname("张三");
        ScrmCustomerEntity c2 = buildCustomerEntity(501L);
        c2.setNickname("李四");
        LocalDate careDate = LocalDate.of(2026, 8, 5);
        when(ruleRepository.findById(10L)).thenReturn(Optional.of(rule));
        when(customerRepository.findAll()).thenReturn(List.of(c1, c2));
        when(taskRepository.findByRuleIdAndCustomerIdAndCareDate(10L, 500L, careDate))
                .thenReturn(List.of(buildTaskEntity(999L)));
        when(taskRepository.findByRuleIdAndCustomerIdAndCareDate(10L, 501L, careDate))
                .thenReturn(Collections.emptyList());
        when(taskRepository.save(any(ScrmCareTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        int created = service.generateTasksFromDate(10L, careDate);

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<ScrmCareTaskEntity> taskCaptor =
                ArgumentCaptor.forClass(ScrmCareTaskEntity.class);
        verify(taskRepository).save(taskCaptor.capture());
        ScrmCareTaskEntity saved = taskCaptor.getValue();
        assertThat(saved.getCustomerId()).isEqualTo(501L);
        assertThat(saved.getCustomerName()).isEqualTo("李四");
        assertThat(saved.getRuleId()).isEqualTo(10L);
        assertThat(saved.getCareType()).isEqualTo("CUSTOM");
        assertThat(saved.getCareDate()).isEqualTo(careDate);
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        verify(ruleRepository).incrementExecutionCount(eq(10L), any(LocalDateTime.class));
    }

    // ==================== 节日配置 ====================

    @Test
    @DisplayName("createFestival: 成功创建, applicable/enabled 缺省填充默认值")
    void createFestival_success_defaultsFilled() throws Exception {
        ScrmFestivalDto dto = buildFestivalDto();
        dto.setApplicable(null);
        dto.setEnabled(null);
        when(festivalRepository.save(any(ScrmFestivalEntity.class)))
                .thenAnswer(inv -> assignFestivalId(inv.getArgument(0), 300L));

        ScrmFestivalEntity result = service.createFestival(dto);

        assertThat(result.getId()).isEqualTo(300L);
        ArgumentCaptor<ScrmFestivalEntity> captor =
                ArgumentCaptor.forClass(ScrmFestivalEntity.class);
        verify(festivalRepository).save(captor.capture());
        ScrmFestivalEntity saved = captor.getValue();
        assertThat(saved.getFestivalName()).isEqualTo("春节");
        assertThat(saved.getFestivalType()).isEqualTo("LUNAR");
        assertThat(saved.getFestivalDate()).isEqualTo("01-01");
        assertThat(saved.getLunarMonth()).isEqualTo(1);
        assertThat(saved.getLunarDay()).isEqualTo(1);
        assertThat(saved.getApplicable()).isEqualTo("ALL");
        assertThat(saved.getEnabled()).isTrue();
    }

    // ==================== 统计 ====================

    @Test
    @DisplayName("getCareStats: 聚合关怀类型与结果分布, 计算成功率和回应率")
    void getCareStats_success_calculatesDistribution() {
        when(recordRepository.countByCareType(any(), any()))
                .thenReturn(List.of(
                        new Object[]{"CUSTOM", 5L},
                        new Object[]{"BIRTHDAY", 3L}));
        when(recordRepository.countByCareResult(any(), any()))
                .thenReturn(List.of(
                        new Object[]{"SUCCESS", 4L},
                        new Object[]{"NO_RESPONSE", 2L},
                        new Object[]{"REJECTED", 1L},
                        new Object[]{"FAILED", 1L}));

        Map<String, Object> stats = service.getCareStats(null, null);

        assertThat(stats.get("total")).isEqualTo(8L);
        @SuppressWarnings("unchecked")
        Map<String, Long> careTypeCount = (Map<String, Long>) stats.get("careTypeCount");
        assertThat(careTypeCount.get("CUSTOM")).isEqualTo(5L);
        assertThat(careTypeCount.get("BIRTHDAY")).isEqualTo(3L);
        assertThat(careTypeCount.get("FESTIVAL")).isZero();
        @SuppressWarnings("unchecked")
        Map<String, Long> resultCount = (Map<String, Long>) stats.get("careResultCount");
        assertThat(resultCount.get("SUCCESS")).isEqualTo(4L);
        assertThat(resultCount.get("NO_RESPONSE")).isEqualTo(2L);
        assertThat(resultCount.get("REJECTED")).isEqualTo(1L);
        assertThat(resultCount.get("FAILED")).isEqualTo(1L);
        assertThat((Double) stats.get("successRate")).isEqualTo(0.5);
        assertThat(stats.get("respondedCount")).isEqualTo(5L);
        assertThat((Double) stats.get("responseRate")).isEqualTo(5.0 / 7.0);
    }

    // ==================== 辅助构建方法 ====================

    private ScrmCareRuleDto buildRuleDto() {
        ScrmCareRuleDto dto = new ScrmCareRuleDto();
        dto.setRuleName("生日关怀");
        dto.setCareType("BIRTHDAY");
        dto.setDescription("客户生日当天发送祝福");
        dto.setTriggerCondition("{\"daysBefore\":3,\"time\":\"09:00\"}");
        dto.setActionType("SEND_MESSAGE");
        dto.setActionContent("{\"messageTemplateId\":\"tpl_001\"}");
        dto.setCreatedBy("admin");
        return dto;
    }

    private ScrmCareTaskDto buildTaskDto() {
        ScrmCareTaskDto dto = new ScrmCareTaskDto();
        dto.setRuleId(10L);
        dto.setCustomerId(500L);
        dto.setCareType("BIRTHDAY");
        dto.setCareDate(LocalDate.of(2026, 8, 5));
        dto.setScheduledAt(LocalDateTime.of(2026, 8, 5, 9, 0));
        dto.setActionType("SEND_MESSAGE");
        dto.setActionContent("{\"messageTemplateId\":\"tpl_001\"}");
        return dto;
    }

    private ScrmFestivalDto buildFestivalDto() {
        ScrmFestivalDto dto = new ScrmFestivalDto();
        dto.setFestivalName("春节");
        dto.setFestivalType("LUNAR");
        dto.setFestivalDate("01-01");
        dto.setLunarMonth(1);
        dto.setLunarDay(1);
        dto.setDefaultGreeting("新年快乐");
        dto.setDefaultActionType("SEND_MESSAGE");
        dto.setCreatedBy("admin");
        return dto;
    }

    private ScrmCareRuleEntity buildRuleEntity(Long id) {
        ScrmCareRuleEntity entity = new ScrmCareRuleEntity();
        entity.setId(id);
        entity.setRuleName("生日关怀");
        entity.setCareType("BIRTHDAY");
        entity.setTriggerCondition("{\"daysBefore\":3,\"time\":\"09:00\"}");
        entity.setActionType("SEND_MESSAGE");
        entity.setActionContent("{\"messageTemplateId\":\"tpl_001\"}");
        entity.setPriority(0);
        entity.setEnabled(true);
        entity.setExecutionCount(0);
        return entity;
    }

    private ScrmCareTaskEntity buildTaskEntity(Long id) {
        ScrmCareTaskEntity entity = new ScrmCareTaskEntity();
        entity.setId(id);
        entity.setCustomerId(500L);
        entity.setCustomerName("张三");
        entity.setCareType("BIRTHDAY");
        entity.setCareDate(LocalDate.of(2026, 8, 5));
        entity.setScheduledAt(LocalDateTime.of(2026, 8, 5, 9, 0));
        entity.setActionType("SEND_MESSAGE");
        entity.setActionContent("{\"messageTemplateId\":\"tpl_001\"}");
        entity.setStatus("PENDING");
        return entity;
    }

    private ScrmCustomerEntity buildCustomerEntity(Long id) {
        ScrmCustomerEntity entity = new ScrmCustomerEntity();
        entity.setId(id);
        entity.setPlatformType("WECHAT");
        entity.setPlatformCustomerUid("wx_001");
        entity.setNickname("张三");
        entity.setOwnerAccountId(1L);
        entity.setLifecycle("ACTIVE");
        return entity;
    }

    private ScrmCareRuleEntity assignRuleId(ScrmCareRuleEntity entity, Long id) {
        entity.setId(id);
        return entity;
    }

    private ScrmCareTaskEntity assignTaskId(ScrmCareTaskEntity entity, Long id) {
        entity.setId(id);
        return entity;
    }

    private ScrmFestivalEntity assignFestivalId(ScrmFestivalEntity entity, Long id) {
        entity.setId(id);
        return entity;
    }
}
