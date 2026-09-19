/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingTriggerServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.entity.ScrmConversationMessageEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmMarketingTriggerEventEntity;
import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.hiylo.scrm.repository.ScrmConversationMessageRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmJourneyEnrollmentRepository;
import org.hiylo.scrm.repository.ScrmMarketingTriggerEventRepository;
import org.hiylo.scrm.repository.ScrmMarketingTriggerRepository;
import org.hiylo.scrm.repository.ScrmMassSendTaskRepository;
import org.hiylo.scrm.repository.ScrmNotificationRepository;
import org.hiylo.scrm.repository.ScrmTagCustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmMarketingTriggerService 单元测试
 * <p>
 * 聚焦触发式营销动作执行 (sendMessage / addTag / setLifecycle) 的真实执行逻辑,
 * 通过反射调用 private 方法验证。sendMessage 验证出站消息持久化, addTag 验证标签
 * 幂等赋值, setLifecycle 验证客户生命周期更新。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmMarketingTriggerService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmMarketingTriggerServiceTest {

    /** 营销触发器仓库 Mock */
    @Mock
    private ScrmMarketingTriggerRepository triggerRepository;
    /** 营销触发事件仓库 Mock */
    @Mock
    private ScrmMarketingTriggerEventRepository eventRepository;
    /** 客户档案仓库 Mock */
    @Mock
    private ScrmCustomerRepository customerRepository;
    /** 客户标签关联仓库 Mock */
    @Mock
    private ScrmTagCustomerRepository tagCustomerRepository;
    /** 会话消息仓库 Mock */
    @Mock
    private ScrmConversationMessageRepository conversationMessageRepository;
    /** 旅程参与记录仓库 Mock */
    @Mock
    private ScrmJourneyEnrollmentRepository journeyEnrollmentRepository;
    /** 通知仓库 Mock */
    @Mock
    private ScrmNotificationRepository notificationRepository;
    /** 群发任务仓库 Mock */
    @Mock
    private ScrmMassSendTaskRepository massSendTaskRepository;

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 被测服务实例 */
    private ScrmMarketingTriggerService service;

    @BeforeEach
    void setUp() {
        service = new ScrmMarketingTriggerService(triggerRepository, eventRepository, customerRepository,
                tagCustomerRepository, conversationMessageRepository, journeyEnrollmentRepository,
                notificationRepository, massSendTaskRepository, objectMapper);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造事件记录
     */
    private ScrmMarketingTriggerEventEntity buildEvent(Long customerId, String actionType) {
        ScrmMarketingTriggerEventEntity event = new ScrmMarketingTriggerEventEntity();
        event.setId(10L);
        event.setTriggerId(20L);
        event.setCustomerId(customerId);
        event.setEventType("PAGE_VIEW");
        event.setStatus("PENDING");
        event.setActionType(actionType);
        event.setTriggerCount(1);
        return event;
    }

    @Test
    @DisplayName("sendMessage: 持久化出站会话消息, direction=OUTBOUND")
    void sendMessage_success() {
        ScrmMarketingTriggerEventEntity event = buildEvent(100L, "SEND_MESSAGE");
        Map<String, Object> params = Map.of(
                "conversationId", 200,
                "content", "您好, 欢迎咨询",
                "messageType", "TEXT");

        String result = ReflectionTestUtils.invokeMethod(service, "sendMessage", event, params);

        ArgumentCaptor<ScrmConversationMessageEntity> captor =
                ArgumentCaptor.forClass(ScrmConversationMessageEntity.class);
        verify(conversationMessageRepository, times(1)).save(captor.capture());
        ScrmConversationMessageEntity saved = captor.getValue();
        assertThat(saved.getDirection()).isEqualTo("OUTBOUND");
        assertThat(saved.getConversationId()).isEqualTo(200L);
        assertThat(saved.getContent()).isEqualTo("您好, 欢迎咨询");
        assertThat(saved.getMessageType()).isEqualTo("TEXT");
        assertThat(saved.getSentAt()).isNotNull();
        assertThat(result).contains("200");
    }

    @Test
    @DisplayName("addTag: 标签不存在时创建赋值记录, save 被调用")
    void addTag_success() {
        ScrmMarketingTriggerEventEntity event = buildEvent(100L, "ADD_TAG");
        Map<String, Object> params = Map.of("tagIds", "100,200");
        // 标签赋值均不存在 → 应新增
        when(tagCustomerRepository.findByCustomerIdAndTagId(eq(100L), eq(100L)))
                .thenReturn(Optional.empty());
        when(tagCustomerRepository.findByCustomerIdAndTagId(eq(100L), eq(200L)))
                .thenReturn(Optional.empty());

        String result = ReflectionTestUtils.invokeMethod(service, "addTag", event, params);

        ArgumentCaptor<ScrmTagCustomerEntity> captor =
                ArgumentCaptor.forClass(ScrmTagCustomerEntity.class);
        verify(tagCustomerRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ScrmTagCustomerEntity::getTagId)
                .containsExactlyInAnyOrder(100L, 200L);
        captor.getAllValues().forEach(tc -> {
            assertThat(tc.getCustomerId()).isEqualTo(100L);
            assertThat(tc.getTagSource()).isEqualTo("TRIGGER");
            assertThat(tc.getIsAuto()).isTrue();
        });
        assertThat(result).contains("新增=2");
    }

    @Test
    @DisplayName("setLifecycle: 更新客户 lifecycle 字段并持久化")
    void setLifecycle_success() {
        ScrmMarketingTriggerEventEntity event = buildEvent(100L, "SET_LIFECYCLE");
        Map<String, Object> params = Map.of("lifecycle", "ACTIVE");
        ScrmCustomerEntity customer = new ScrmCustomerEntity();
        customer.setId(100L);
        customer.setLifecycle("NEW");
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));

        String result = ReflectionTestUtils.invokeMethod(service, "setLifecycle", event, params);

        ArgumentCaptor<ScrmCustomerEntity> captor =
                ArgumentCaptor.forClass(ScrmCustomerEntity.class);
        verify(customerRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getLifecycle()).isEqualTo("ACTIVE");
        assertThat(result).contains("NEW").contains("ACTIVE");
    }
}
