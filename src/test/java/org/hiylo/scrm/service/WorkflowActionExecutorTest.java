/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WorkflowActionExecutorTest.java
 * Date : 2026/09/19 21:20:19
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmCustomerDto;
import org.hiylo.scrm.dto.ScrmFollowUpTaskDto;
import org.hiylo.scrm.dto.ScrmLifecycleTransitionRequestDto;
import org.hiylo.scrm.dto.ScrmNotificationDto;
import org.hiylo.scrm.dto.ScrmNotificationSendDto;
import org.hiylo.scrm.dto.ScrmTagCustomerDto;
import org.hiylo.scrm.dto.ScrmTicketDto;
import org.hiylo.scrm.dto.ScrmWebhookEventDto;
import org.hiylo.scrm.dto.ScrmWebhookLogDto;
import org.hiylo.scrm.entity.ScrmCustomerTagEntity;
import org.hiylo.scrm.entity.ScrmSegmentEntity;
import org.hiylo.scrm.entity.ScrmWorkflowInstanceEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowActionExecutor} 单元测试。
 * <p>
 * 重点是「动作真的落到既有业务服务上」与「不会假装做了」两件事:
 * </p>
 * <ul>
 *   <li>ADD_TAG / REMOVE_TAG、ADD_TO_SEGMENT / REMOVE_FROM_SEGMENT、UPDATE_LIFECYCLE、
 *       CREATE_TASK 等动作逐一 verify 下游服务被正确调用, 并核对透传的参数</li>
 *   <li>未知动作类型、必填配置缺失、UPDATE_FIELD 白名单外字段均抛 {@link ScrmException}
 *       且不产生任何副作用</li>
 *   <li>无内部真实通道的动作 (SEND_MESSAGE / SEND_EMAIL / SEND_SMS / CALL_API / ASSIGN_OWNER)
 *       标记 {@code simulated=true} 并给出原因, 不调用任何服务</li>
 * </ul>
 *
 * @author Hsi Chu
 */
@DisplayName("WorkflowActionExecutor 单元测试")
@ExtendWith(MockitoExtension.class)
class WorkflowActionExecutorTest {

    /** 实例所属客户 ID */
    private static final Long CUSTOMER_ID = 500L;

    @Mock
    private ScrmCustomerTagService customerTagService;
    @Mock
    private ScrmSegmentService segmentService;
    @Mock
    private ScrmCustomerLifecycleService lifecycleService;
    @Mock
    private ScrmNotificationCenterService notificationCenterService;
    @Mock
    private ScrmWebhookService webhookService;
    @Mock
    private ScrmFollowUpService followUpService;
    @Mock
    private ScrmTicketService ticketService;
    @Mock
    private ScrmCustomerService customerService;

    /** 被测执行器 (ObjectMapper 用真实实例) */
    private WorkflowActionExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new WorkflowActionExecutor(new ObjectMapper(), customerTagService, segmentService,
                lifecycleService, notificationCenterService, webhookService, followUpService,
                ticketService, customerService);
    }

    /**
     * 构造工作流实例
     */
    private ScrmWorkflowInstanceEntity instance() {
        ScrmWorkflowInstanceEntity instance = new ScrmWorkflowInstanceEntity();
        instance.setId(900L);
        instance.setWorkflowId(800L);
        instance.setWorkflowName("高价值客户唤醒");
        instance.setCustomerId(CUSTOMER_ID);
        instance.setCustomerName("张三");
        instance.setVariables("{\"orderAmount\":1200}");
        return instance;
    }

    /**
     * 断言结果为真实执行 (executed=true / simulated=false)
     */
    private void assertExecuted(Map<String, Object> result, String actionType) {
        assertThat(result).containsEntry("actionType", actionType)
                .containsEntry("executed", true)
                .containsEntry("simulated", false);
        assertThat(result.get("message")).isNotNull();
    }

    // ============================================================
    // 标签动作
    // ============================================================

    @Test
    @DisplayName("ADD_TAG: 按 tagId 调用标签服务打标, 来源与工作流上下文一并透传")
    void execute_addTagByTagId() throws Exception {
        Map<String, Object> result = executor.execute("ADD_TAG",
                "{\"tagId\":7,\"tagValue\":\"高\"}", instance());

        ArgumentCaptor<ScrmTagCustomerDto> captor = ArgumentCaptor.forClass(ScrmTagCustomerDto.class);
        verify(customerTagService).assignTag(captor.capture());
        ScrmTagCustomerDto dto = captor.getValue();
        assertThat(dto.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(dto.getTagId()).isEqualTo(7L);
        assertThat(dto.getTagValue()).isEqualTo("高");
        assertThat(dto.getTagSource()).isEqualTo("WORKFLOW");
        assertThat(dto.getNote()).contains("工作流实例 900");
        assertExecuted(result, "ADD_TAG");
        assertThat(result).containsEntry("tagId", 7L);
    }

    @Test
    @DisplayName("ADD_TAG: 仅给 tagCode 时先反查标签 ID 再打标 (数字与字符串写法均可)")
    void execute_addTagByTagCode() throws Exception {
        ScrmCustomerTagEntity tag = new ScrmCustomerTagEntity();
        tag.setId(42L);
        when(customerTagService.getTagByCode("VIP")).thenReturn(tag);

        Map<String, Object> result = executor.execute("ADD_TAG", "{\"tagCode\":\"VIP\"}", instance());

        verify(customerTagService).getTagByCode("VIP");
        ArgumentCaptor<ScrmTagCustomerDto> captor = ArgumentCaptor.forClass(ScrmTagCustomerDto.class);
        verify(customerTagService).assignTag(captor.capture());
        assertThat(captor.getValue().getTagId()).isEqualTo(42L);
        assertThat(result).containsEntry("tagId", 42L);
    }

    @Test
    @DisplayName("REMOVE_TAG: 调用标签服务按客户+标签移除")
    void execute_removeTag() throws Exception {
        Map<String, Object> result = executor.execute("REMOVE_TAG", "{\"tagId\":7}", instance());

        verify(customerTagService).removeTag(CUSTOMER_ID, 7L);
        assertExecuted(result, "REMOVE_TAG");
    }

    @Test
    @DisplayName("ADD_TAG: 既无 tagId 又无 tagCode 抛 ScrmException 且不打标")
    void execute_addTagMissingIdentifier() {
        assertThatThrownBy(() -> executor.execute("ADD_TAG", "{}", instance()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("缺少 tagId 或 tagCode");
        verifyNoInteractions(customerTagService);
    }

    // ============================================================
    // 客群动作
    // ============================================================

    @Test
    @DisplayName("ADD_TO_SEGMENT: 调用分群服务加成员, 来源标记 WORKFLOW")
    void execute_addToSegment() throws Exception {
        Map<String, Object> result = executor.execute("ADD_TO_SEGMENT",
                "{\"segmentId\":3}", instance());

        verify(segmentService).addMember(3L, CUSTOMER_ID, "WORKFLOW");
        assertExecuted(result, "ADD_TO_SEGMENT");
        assertThat(result).containsEntry("segmentId", 3L);
    }

    @Test
    @DisplayName("ADD_TO_SEGMENT: 仅给 segmentCode 时先反查分群 ID")
    void execute_addToSegmentByCode() throws Exception {
        ScrmSegmentEntity segment = new ScrmSegmentEntity();
        segment.setId(11L);
        when(segmentService.getSegmentByCode("HIGH_VALUE")).thenReturn(segment);

        Map<String, Object> result = executor.execute("ADD_TO_SEGMENT",
                "{\"segmentCode\":\"HIGH_VALUE\"}", instance());

        verify(segmentService).addMember(11L, CUSTOMER_ID, "WORKFLOW");
        assertThat(result).containsEntry("segmentId", 11L);
    }

    @Test
    @DisplayName("REMOVE_FROM_SEGMENT: 调用分群服务移除成员")
    void execute_removeFromSegment() throws Exception {
        Map<String, Object> result = executor.execute("REMOVE_FROM_SEGMENT",
                "{\"segmentId\":3}", instance());

        verify(segmentService).removeMember(3L, CUSTOMER_ID);
        assertExecuted(result, "REMOVE_FROM_SEGMENT");
    }

    @Test
    @DisplayName("ADD_TO_SEGMENT: 客群标识缺失抛 ScrmException 且不改成员")
    void execute_addToSegmentMissingIdentifier() {
        assertThatThrownBy(() -> executor.execute("ADD_TO_SEGMENT", null, instance()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("缺少 segmentId 或 segmentCode");
        verifyNoInteractions(segmentService);
    }

    // ============================================================
    // 生命周期流转
    // ============================================================

    @Test
    @DisplayName("UPDATE_LIFECYCLE: 调用生命周期服务流转, trigger 缺省为 WORKFLOW")
    void execute_updateLifecycle() throws Exception {
        Map<String, Object> result = executor.execute("UPDATE_LIFECYCLE",
                "{\"toStage\":\"ACTIVE\",\"notes\":\"工作流唤醒\"}", instance());

        ArgumentCaptor<ScrmLifecycleTransitionRequestDto> captor =
                ArgumentCaptor.forClass(ScrmLifecycleTransitionRequestDto.class);
        verify(lifecycleService).transitionCustomer(captor.capture());
        ScrmLifecycleTransitionRequestDto request = captor.getValue();
        assertThat(request.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(request.getToStage()).isEqualTo("ACTIVE");
        assertThat(request.getTrigger()).isEqualTo("WORKFLOW");
        assertThat(request.getNotes()).isEqualTo("工作流唤醒");
        assertThat(request.getOperatorId()).isEqualTo("WORKFLOW");
        assertThat(request.getOperatorName()).isEqualTo("工作流 高价值客户唤醒");
        assertExecuted(result, "UPDATE_LIFECYCLE");
        assertThat(result).containsEntry("toStage", "ACTIVE");
    }

    @Test
    @DisplayName("UPDATE_LIFECYCLE: 目标阶段缺失抛 ScrmException 且不流转")
    void execute_updateLifecycleMissingStage() {
        assertThatThrownBy(() -> executor.execute("UPDATE_LIFECYCLE", "{\"notes\":\"x\"}", instance()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("toStage");
        verifyNoInteractions(lifecycleService);
    }

    // ============================================================
    // 待办 / 工单
    // ============================================================

    @Test
    @DisplayName("CREATE_TASK: 调用跟进服务建待办, plannedAt 按默认 24 小时后")
    void execute_createTask() throws Exception {
        ScrmFollowUpTaskDto created = new ScrmFollowUpTaskDto();
        created.setId(6001L);
        when(followUpService.createTask(any(ScrmFollowUpTaskDto.class))).thenReturn(created);

        LocalDateTime before = LocalDateTime.now();
        Map<String, Object> result = executor.execute("CREATE_TASK",
                "{\"taskType\":\"CALL\",\"title\":\"回访张三\",\"assigneeId\":\"u1\","
                        + "\"assigneeName\":\"销售一\",\"plannedInHours\":2}", instance());

        ArgumentCaptor<ScrmFollowUpTaskDto> captor = ArgumentCaptor.forClass(ScrmFollowUpTaskDto.class);
        verify(followUpService).createTask(captor.capture());
        ScrmFollowUpTaskDto dto = captor.getValue();
        assertThat(dto.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(dto.getCustomerName()).isEqualTo("张三");
        assertThat(dto.getTaskType()).isEqualTo("CALL");
        assertThat(dto.getTitle()).isEqualTo("回访张三");
        assertThat(dto.getAssigneeId()).isEqualTo("u1");
        assertThat(dto.getCreatedBy()).isEqualTo("WORKFLOW");
        assertThat(dto.getPlannedAt()).isAfterOrEqualTo(before.plusHours(2));
        assertExecuted(result, "CREATE_TASK");
        assertThat(result).containsEntry("taskId", 6001L);
    }

    @Test
    @DisplayName("CREATE_TASK: 未配置 plannedInHours 时按默认 24 小时排期")
    void execute_createTaskDefaultPlannedAt() throws Exception {
        ScrmFollowUpTaskDto created = new ScrmFollowUpTaskDto();
        created.setId(6002L);
        when(followUpService.createTask(any(ScrmFollowUpTaskDto.class))).thenReturn(created);

        LocalDateTime before = LocalDateTime.now();
        executor.execute("CREATE_TASK",
                "{\"taskType\":\"VISIT\",\"title\":\"上门\",\"assigneeId\":\"u1\"}", instance());

        ArgumentCaptor<ScrmFollowUpTaskDto> captor = ArgumentCaptor.forClass(ScrmFollowUpTaskDto.class);
        verify(followUpService).createTask(captor.capture());
        assertThat(captor.getValue().getPlannedAt())
                .isAfterOrEqualTo(before.plusHours(23))
                .isBeforeOrEqualTo(LocalDateTime.now().plusHours(24).plusMinutes(5));
    }

    @Test
    @DisplayName("CREATE_TASK: 必填项缺失抛 ScrmException 且不建待办")
    void execute_createTaskMissingRequired() {
        assertThatThrownBy(() -> executor.execute("CREATE_TASK",
                "{\"taskType\":\"CALL\",\"title\":\"回访\"}", instance()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("assigneeId");
        verifyNoInteractions(followUpService);
    }

    @Test
    @DisplayName("CREATE_TICKET: 调用工单服务建单并回填工单号")
    void execute_createTicket() throws Exception {
        ScrmTicketDto created = new ScrmTicketDto();
        created.setId(7001L);
        created.setTicketNo("TK202609190001");
        when(ticketService.createTicket(any(ScrmTicketDto.class))).thenReturn(created);

        Map<String, Object> result = executor.execute("CREATE_TICKET",
                "{\"title\":\"客户投诉\",\"category\":\"COMPLAINT\",\"priority\":\"HIGH\"}", instance());

        ArgumentCaptor<ScrmTicketDto> captor = ArgumentCaptor.forClass(ScrmTicketDto.class);
        verify(ticketService).createTicket(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("客户投诉");
        assertThat(captor.getValue().getCategory()).isEqualTo("COMPLAINT");
        assertThat(captor.getValue().getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(captor.getValue().getCreatedBy()).isEqualTo("WORKFLOW");
        assertExecuted(result, "CREATE_TICKET");
        assertThat(result).containsEntry("ticketId", 7001L)
                .containsEntry("ticketNo", "TK202609190001");
    }

    // ============================================================
    // 通知 / Webhook
    // ============================================================

    @Test
    @DisplayName("NOTIFY: 调用通知中心发送并按实际通知条数回填")
    void execute_notify() throws Exception {
        when(notificationCenterService.sendNotification(any()))
                .thenReturn(List.of(new ScrmNotificationDto(), new ScrmNotificationDto()));

        Map<String, Object> result = executor.execute("NOTIFY",
                "{\"templateCode\":\"TPL1\",\"recipients\":[\"u1\",\"u2\"],"
                        + "\"variables\":{\"name\":\"张三\"},\"priority\":5}", instance());

        ArgumentCaptor<ScrmNotificationSendDto> captor =
                ArgumentCaptor.forClass(ScrmNotificationSendDto.class);
        verify(notificationCenterService).sendNotification(captor.capture());
        assertThat(captor.getValue().getTemplateCode()).isEqualTo("TPL1");
        assertThat(captor.getValue().getRecipients()).containsExactly("u1", "u2");
        assertThat(captor.getValue().getVariables()).containsEntry("name", "张三");
        assertThat(captor.getValue().getPriority()).isEqualTo(5);
        assertThat(captor.getValue().getRelatedId()).isEqualTo("900");
        assertThat(result).containsEntry("notificationCount", 2);
        assertExecuted(result, "NOTIFY");
    }

    @Test
    @DisplayName("NOTIFY: 按记录最终状态拆分送达与失败, 不把失败计入送达")
    void execute_notifyCountsFailedSeparately() throws Exception {
        ScrmNotificationDto delivered = new ScrmNotificationDto();
        delivered.setStatus("DELIVERED");
        ScrmNotificationDto failed = new ScrmNotificationDto();
        failed.setStatus("FAILED");
        when(notificationCenterService.sendNotification(any())).thenReturn(List.of(delivered, failed));

        Map<String, Object> result = executor.execute("NOTIFY",
                "{\"templateCode\":\"TPL_EMAIL\",\"recipients\":[\"u1\",\"u2\"]}", instance());

        assertThat(result).containsEntry("notificationCount", 2)
                .containsEntry("deliveredCount", 1L)
                .containsEntry("failedCount", 1L);
        assertThat((String) result.get("message")).contains("创建 2 条").contains("送达 1 条").contains("失败 1 条");
    }

    @Test
    @DisplayName("NOTIFY: 接收者缺失抛 ScrmException 且不发通知")
    void execute_notifyMissingRecipients() {
        assertThatThrownBy(() -> executor.execute("NOTIFY",
                "{\"templateCode\":\"TPL1\",\"recipients\":[]}", instance()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("接收者");
        verifyNoInteractions(notificationCenterService);
    }

    @Test
    @DisplayName("WEBHOOK: 发布事件并把实例变量作为事件数据透传")
    void execute_webhook() throws Exception {
        when(webhookService.publishEvent(any(ScrmWebhookEventDto.class)))
                .thenReturn(List.of(new ScrmWebhookLogDto()));

        Map<String, Object> result = executor.execute("WEBHOOK",
                "{\"eventType\":\"workflow.vip\"}", instance());

        ArgumentCaptor<ScrmWebhookEventDto> captor = ArgumentCaptor.forClass(ScrmWebhookEventDto.class);
        verify(webhookService).publishEvent(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("workflow.vip");
        assertThat(captor.getValue().getEntityId()).isEqualTo(CUSTOMER_ID);
        assertThat(captor.getValue().getEventData()).contains("orderAmount");
        assertThat(result).containsEntry("webhookLogCount", 1);
    }

    @Test
    @DisplayName("WEBHOOK: 无匹配订阅时如实说明 (0 投递仍算事件已发布)")
    void execute_webhookWithoutSubscription() throws Exception {
        when(webhookService.publishEvent(any(ScrmWebhookEventDto.class))).thenReturn(List.of());

        Map<String, Object> result = executor.execute("WEBHOOK",
                "{\"eventType\":\"workflow.none\"}", instance());

        assertThat((String) result.get("message")).contains("无匹配的活跃 Webhook 订阅");
        assertThat(result).containsEntry("webhookLogCount", 0);
    }

    @Test
    @DisplayName("WEBHOOK: 事件类型缺失抛 ScrmException 且不发布事件")
    void execute_webhookMissingEventType() {
        assertThatThrownBy(() -> executor.execute("WEBHOOK", "{}", instance()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("eventType");
        verifyNoInteractions(webhookService);
    }

    // ============================================================
    // UPDATE_FIELD
    // ============================================================

    @Test
    @DisplayName("UPDATE_FIELD: 白名单字段调用客户服务更新")
    void execute_updateFieldAllowed() throws Exception {
        Map<String, Object> result = executor.execute("UPDATE_FIELD",
                "{\"field\":\"nickname\",\"value\":\"新昵称\"}", instance());

        ArgumentCaptor<ScrmCustomerDto> captor = ArgumentCaptor.forClass(ScrmCustomerDto.class);
        verify(customerService).updateCustomer(eq(CUSTOMER_ID), captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo("新昵称");
        assertExecuted(result, "UPDATE_FIELD");
        assertThat(result).containsEntry("field", "nickname");
    }

    @Test
    @DisplayName("UPDATE_FIELD nextFollowUpAt: ISO-8601 文本被解析为时间后更新")
    void execute_updateFieldNextFollowUpAt() throws Exception {
        executor.execute("UPDATE_FIELD",
                "{\"field\":\"nextFollowUpAt\",\"value\":\"2026-10-01T09:30:00\"}", instance());

        ArgumentCaptor<ScrmCustomerDto> captor = ArgumentCaptor.forClass(ScrmCustomerDto.class);
        verify(customerService).updateCustomer(eq(CUSTOMER_ID), captor.capture());
        assertThat(captor.getValue().getNextFollowUpAt()).isEqualTo(LocalDateTime.parse("2026-10-01T09:30:00"));
    }

    @Test
    @DisplayName("UPDATE_FIELD: 白名单外字段抛 ScrmException 且不更新客户")
    void execute_updateFieldUnsupportedField() {
        assertThatThrownBy(() -> executor.execute("UPDATE_FIELD",
                "{\"field\":\"ownerAccountId\",\"value\":1}", instance()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("UPDATE_FIELD 不支持的字段");
        verifyNoInteractions(customerService);
    }

    @Test
    @DisplayName("UPDATE_FIELD: 取值缺失或时间格式非法抛 ScrmException 且不更新客户")
    void execute_updateFieldInvalidValue() {
        assertThatThrownBy(() -> executor.execute("UPDATE_FIELD", "{\"field\":\"remark\"}", instance()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("缺少 value");
        assertThatThrownBy(() -> executor.execute("UPDATE_FIELD",
                "{\"field\":\"nextFollowUpAt\",\"value\":\"明天下午\"}", instance()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("ISO-8601");
        verifyNoInteractions(customerService);
    }

    // ============================================================
    // 非法输入与显式降级
    // ============================================================

    @Test
    @DisplayName("execute: 未知动作类型抛 ScrmException 且不触碰任何下游服务")
    void execute_unknownActionType() {
        assertThatThrownBy(() -> executor.execute("DROP_ALL_CUSTOMERS", "{}", instance()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("动作类型不支持");
        verifyNoInteractions(customerTagService, segmentService, lifecycleService,
                notificationCenterService, webhookService, followUpService, ticketService, customerService);
    }

    @Test
    @DisplayName("execute: actionType 为空白抛 ScrmException")
    void execute_blankActionType() {
        assertThatThrownBy(() -> executor.execute("  ", "{}", instance()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("未配置 actionType");
        assertThatThrownBy(() -> executor.execute(null, "{}", instance()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("未配置 actionType");
    }

    @Test
    @DisplayName("execute: 缺少客户上下文 (instance 为空或无 customerId) 抛 ScrmException")
    void execute_missingCustomerContext() {
        assertThatThrownBy(() -> executor.execute("ADD_TAG", "{\"tagId\":7}", null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("缺少客户上下文");
        ScrmWorkflowInstanceEntity withoutCustomer = new ScrmWorkflowInstanceEntity();
        withoutCustomer.setId(901L);
        assertThatThrownBy(() -> executor.execute("ADD_TAG", "{\"tagId\":7}", withoutCustomer))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("缺少客户上下文");
        verifyNoInteractions(customerTagService);
    }

    @Test
    @DisplayName("execute: 配置 JSON 非法抛 ScrmException 且不调用下游服务")
    void execute_invalidConfigJson() {
        assertThatThrownBy(() -> executor.execute("ADD_TAG", "{\"tagId\":", instance()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("动作配置 JSON 解析失败");
        verifyNoInteractions(customerTagService);
    }

    @Test
    @DisplayName("无真实通道的动作: 标记 simulated=true 并说明原因, 不伪造成功也不产生副作用")
    void execute_degradesWithoutRealChannel() throws Exception {
        for (String actionType : List.of("SEND_MESSAGE", "SEND_EMAIL", "SEND_SMS", "CALL_API",
                "ASSIGN_OWNER")) {
            Map<String, Object> result = executor.execute(actionType, "{}", instance());
            assertThat(result).as("actionType=%s", actionType)
                    .containsEntry("actionType", actionType)
                    .containsEntry("executed", false)
                    .containsEntry("simulated", true);
            assertThat((String) result.get("reason")).as("actionType=%s 需给出降级原因", actionType)
                    .isNotBlank();
            assertThat((String) result.get("message")).contains("未真实执行");
        }
        verifyNoInteractions(customerTagService, segmentService, lifecycleService,
                notificationCenterService, webhookService, followUpService, ticketService, customerService);
    }

    @Test
    @DisplayName("execute: 下游服务异常原样抛出, 由调用方把节点置为失败")
    void execute_propagatesDownstreamFailure() {
        doThrow(ScrmException.notFound("标签关联不存在"))
                .when(customerTagService).removeTag(CUSTOMER_ID, 7L);

        assertThatThrownBy(() -> executor.execute("REMOVE_TAG", "{\"tagId\":7}", instance()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("标签关联不存在");
    }
}
