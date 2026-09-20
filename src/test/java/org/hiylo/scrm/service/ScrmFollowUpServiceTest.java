/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFollowUpServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmFollowUpRecordDto;
import org.hiylo.scrm.dto.ScrmFollowUpTaskDto;
import org.hiylo.scrm.dto.ScrmFollowUpTemplateDto;
import org.hiylo.scrm.entity.ScrmFollowUpTaskEntity;
import org.hiylo.scrm.entity.ScrmFollowUpTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmFollowUpRecordRepository;
import org.hiylo.scrm.repository.ScrmFollowUpTaskRepository;
import org.hiylo.scrm.repository.ScrmFollowUpTemplateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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
 * ScrmFollowUpService 单元测试
 * <p>
 * 聚焦跟进任务创建 (默认值填充)、完成 / 取消 (状态流转校验)、跟进模板维护与应用、
 * 跟进记录与任务联动 (PENDING → IN_PROGRESS)、待提醒任务过滤与越权访问校验。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmFollowUpService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmFollowUpServiceTest {

    /** 跟进任务仓库 Mock */
    @Mock
    private ScrmFollowUpTaskRepository taskRepository;
    /** 跟进模板仓库 Mock */
    @Mock
    private ScrmFollowUpTemplateRepository templateRepository;
    /** 跟进记录仓库 Mock */
    @Mock
    private ScrmFollowUpRecordRepository recordRepository;

    /** 被测服务实例 */
    private ScrmFollowUpService service;

    @BeforeEach
    void setUp() {
        service = new ScrmFollowUpService(taskRepository, templateRepository, recordRepository);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的跟进任务实体 (用于 findById 返回)
     */
    private ScrmFollowUpTaskEntity buildTaskEntity(Long id, String status) {
        ScrmFollowUpTaskEntity entity = new ScrmFollowUpTaskEntity();
        entity.setId(id);
        entity.setCustomerId(100L);
        entity.setCustomerName("张三");
        entity.setAssigneeId("sales01");
        entity.setAssigneeName("销售一号");
        entity.setTaskType("CALL");
        entity.setTitle("首次回访");
        entity.setContent("电话回访客户");
        entity.setPlannedAt(LocalDateTime.now().plusDays(1));
        entity.setStatus(status);
        entity.setPriority("MEDIUM");
        entity.setReminderMinutes(30);
        entity.setReminded(false);
        entity.setCreateTime(LocalDateTime.now().minusDays(1));
        return entity;
    }

    /**
     * 构造已持久化的跟进模板实体
     */
    private ScrmFollowUpTemplateEntity buildTemplateEntity(Long id, Boolean enabled) {
        ScrmFollowUpTemplateEntity entity = new ScrmFollowUpTemplateEntity();
        entity.setId(id);
        entity.setTemplateName("电话回访模板");
        entity.setTaskType("CALL");
        entity.setTitleTemplate("首次回访");
        entity.setContentTemplate("电话回访客户了解需求");
        entity.setDefaultPriority("MEDIUM");
        entity.setDefaultReminderMinutes(30);
        entity.setEnabled(enabled);
        entity.setUseCount(0);
        entity.setCreatedBy("admin01");
        return entity;
    }

    /**
     * 构造任务创建参数
     */
    private ScrmFollowUpTaskDto buildCreateDto() {
        ScrmFollowUpTaskDto dto = new ScrmFollowUpTaskDto();
        dto.setCustomerId(100L);
        dto.setCustomerName("张三");
        dto.setAssigneeId("sales01");
        dto.setAssigneeName("销售一号");
        dto.setTaskType("CALL");
        dto.setTitle("首次回访");
        dto.setContent("电话回访客户");
        dto.setPlannedAt(LocalDateTime.now().plusDays(1));
        return dto;
    }

    @Test
    @DisplayName("createTask: 写入账号 ID 与默认值 (PENDING / MEDIUM / 30min / reminded=false)")
    void createTask_success() throws ScrmException {
        when(taskRepository.save(any(ScrmFollowUpTaskEntity.class)))
                .thenAnswer(inv -> {
                    ScrmFollowUpTaskEntity e = inv.getArgument(0);
                    e.setId(1L);
                    return e;
                });

        ScrmFollowUpTaskDto result = service.createTask(buildCreateDto());

        ArgumentCaptor<ScrmFollowUpTaskEntity> captor =
                ArgumentCaptor.forClass(ScrmFollowUpTaskEntity.class);
        verify(taskRepository, times(1)).save(captor.capture());
        ScrmFollowUpTaskEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getPriority()).isEqualTo("MEDIUM");
        assertThat(saved.getReminderMinutes()).isEqualTo(30);
        assertThat(saved.getReminded()).isFalse();
        assertThat(result.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("createTask: 缺少 customerId 抛 BAD_REQUEST")
    void createTask_missingCustomerId() {
        ScrmFollowUpTaskDto dto = buildCreateDto();
        dto.setCustomerId(null);

        assertThatThrownBy(() -> service.createTask(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户 ID 不能为空");
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("completeTask: PENDING 任务完成后状态置 COMPLETED, 记录完成时间与跟进结果")
    void completeTask_success() throws ScrmException {
        ScrmFollowUpTaskEntity entity = buildTaskEntity(1L, "PENDING");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(taskRepository.save(any(ScrmFollowUpTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmFollowUpTaskDto result = service.completeTask(1L, "客户有意向", LocalDateTime.now().plusDays(3));

        ArgumentCaptor<ScrmFollowUpTaskEntity> captor =
                ArgumentCaptor.forClass(ScrmFollowUpTaskEntity.class);
        verify(taskRepository, times(1)).save(captor.capture());
        ScrmFollowUpTaskEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("COMPLETED");
        assertThat(saved.getCompletedAt()).isNotNull();
        assertThat(saved.getFollowUpResult()).isEqualTo("客户有意向");
        assertThat(saved.getNextFollowUpAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("completeTask: 已取消任务不允许完成抛 BAD_REQUEST")
    void completeTask_cancelledTask() {
        ScrmFollowUpTaskEntity entity = buildTaskEntity(1L, "CANCELLED");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.completeTask(1L, "结果", null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("已取消的任务不允许完成");
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelTask: 已完成任务不允许取消抛 BAD_REQUEST")
    void cancelTask_completedTask() {
        ScrmFollowUpTaskEntity entity = buildTaskEntity(1L, "COMPLETED");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.cancelTask(1L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("已完成的任务不允许取消");
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTemplate: 写入账号 ID 与默认值 (MEDIUM / 30min / enabled=true / useCount=0)")
    void createTemplate_success() throws ScrmException {
        when(templateRepository.save(any(ScrmFollowUpTemplateEntity.class)))
                .thenAnswer(inv -> {
                    ScrmFollowUpTemplateEntity e = inv.getArgument(0);
                    e.setId(1L);
                    return e;
                });

        ScrmFollowUpTemplateDto dto = new ScrmFollowUpTemplateDto();
        dto.setTemplateName("电话回访模板");
        dto.setTaskType("CALL");
        dto.setTitleTemplate("首次回访");
        dto.setContentTemplate("电话回访客户了解需求");
        dto.setCreatedBy("admin01");

        ScrmFollowUpTemplateDto result = service.createTemplate(dto);

        ArgumentCaptor<ScrmFollowUpTemplateEntity> captor =
                ArgumentCaptor.forClass(ScrmFollowUpTemplateEntity.class);
        verify(templateRepository, times(1)).save(captor.capture());
        ScrmFollowUpTemplateEntity saved = captor.getValue();
        assertThat(saved.getDefaultPriority()).isEqualTo("MEDIUM");
        assertThat(saved.getDefaultReminderMinutes()).isEqualTo(30);
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getUseCount()).isZero();
        assertThat(result.getTemplateName()).isEqualTo("电话回访模板");
    }

    @Test
    @DisplayName("applyTemplate: 已禁用模板抛 BAD_REQUEST")
    void applyTemplate_disabled() {
        ScrmFollowUpTemplateEntity template = buildTemplateEntity(1L, false);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.applyTemplate(1L, 100L, "sales01", LocalDateTime.now().plusDays(1)))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("模板已禁用");
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("applyTemplate: 应用启用模板创建任务, assigneeId 写入, 模板使用次数自增")
    void applyTemplate_success() throws ScrmException {
        ScrmFollowUpTemplateEntity template = buildTemplateEntity(1L, true);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(taskRepository.save(any(ScrmFollowUpTaskEntity.class)))
                .thenAnswer(inv -> {
                    ScrmFollowUpTaskEntity e = inv.getArgument(0);
                    e.setId(99L);
                    return e;
                });

        ScrmFollowUpTaskDto result = service.applyTemplate(1L, 100L, "sales01",
                LocalDateTime.now().plusDays(1));

        assertThat(result).isNotNull();
        assertThat(result.getAssigneeId()).isEqualTo("sales01");
        assertThat(result.getTaskType()).isEqualTo("CALL");
        assertThat(result.getStatus()).isEqualTo("PENDING");
        // 模板使用次数自增
        ArgumentCaptor<ScrmFollowUpTemplateEntity> captor =
                ArgumentCaptor.forClass(ScrmFollowUpTemplateEntity.class);
        verify(templateRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getUseCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("createRecord: 关联 PENDING 任务时联动置 IN_PROGRESS 并同步下次跟进时间")
    void createRecord_withTaskLinkage() throws ScrmException {
        ScrmFollowUpTaskEntity task = buildTaskEntity(10L, "PENDING");
        when(recordRepository.save(any()))
                .thenAnswer(inv -> {
                    var e = inv.getArgument(0);
                    try {
                        var idField = e.getClass().getDeclaredField("id");
                        idField.setAccessible(true);
                        idField.set(e, 1L);
                    } catch (Exception ignored) {
                    }
                    return e;
                });
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(ScrmFollowUpTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmFollowUpRecordDto dto = new ScrmFollowUpRecordDto();
        dto.setTaskId(10L);
        dto.setCustomerId(100L);
        dto.setCustomerName("张三");
        dto.setContactMethod("PHONE");
        dto.setContactResult("REACHED");
        dto.setContent("客户有意向, 约下周面谈");
        dto.setRecordedBy("sales01");
        dto.setNextFollowUpAt(LocalDateTime.now().plusDays(3));

        service.createRecord(dto);

        ArgumentCaptor<ScrmFollowUpTaskEntity> taskCaptor =
                ArgumentCaptor.forClass(ScrmFollowUpTaskEntity.class);
        verify(taskRepository, times(1)).save(taskCaptor.capture());
        ScrmFollowUpTaskEntity savedTask = taskCaptor.getValue();
        assertThat(savedTask.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(savedTask.getNextFollowUpAt()).isNotNull();
    }

    @Test
    @DisplayName("getPendingReminders: plannedAt - reminderMinutes <= now 的任务进入待提醒列表")
    void getPendingReminders_filtered() {
        // 任务计划时间在 10 分钟后, 提醒分钟数 30 → reminderAt = plannedAt - 30min = 20 分钟前 <= now → 命中
        ScrmFollowUpTaskEntity hitTask = buildTaskEntity(1L, "PENDING");
        hitTask.setPlannedAt(LocalDateTime.now().plusMinutes(10));
        hitTask.setReminderMinutes(30);
        // 任务计划时间在 2 小时后, 提醒分钟数 30 → reminderAt = plannedAt - 30min = 1.5 小时后 > now → 不命中
        ScrmFollowUpTaskEntity missTask = buildTaskEntity(2L, "PENDING");
        missTask.setPlannedAt(LocalDateTime.now().plusHours(2));
        missTask.setReminderMinutes(30);
        when(taskRepository.findUnremindedTasks()).thenReturn(List.of(hitTask, missTask));

        var result = service.getPendingReminders();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    
}
