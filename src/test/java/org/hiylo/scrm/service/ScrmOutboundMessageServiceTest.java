/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOutboundMessageServiceTest.java
 * Date : 2026/10/07 18:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.component.ScrmOutboundGuardComponent;
import org.hiylo.scrm.dto.ScrmOutboundMessageDto;
import org.hiylo.scrm.dto.callback.OutboundAckCallbackDto;
import org.hiylo.scrm.entity.ScrmConversationMessageEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmOutboundMessageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmOutboundMessageService 单元测试。
 * <p>
 * 覆盖出站消息入队（防重复）、拉取置 IN_PROGRESS、成功/失败回执、
 * 失败重试指数退避、超时回收等核心逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmOutboundMessageService 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScrmOutboundMessageServiceTest {

    /** 出站队列仓库 Mock */
    @Mock
    private ScrmOutboundMessageRepository outboundRepository;
    /** 客户仓库 Mock */
    @Mock
    private org.hiylo.scrm.repository.ScrmCustomerRepository customerRepository;
    /** 会话服务 Mock */
    @Mock
    private ScrmConversationService conversationService;
    /** 账号服务 Mock */
    @Mock
    private ScrmAccountService accountService;

    /** 事件发布器 Mock（入队后发布 OutboundEnqueuedEvent 触发实时代发） */
    @Mock
    private ApplicationEventPublisher eventPublisher;

    /** 出站发送前守卫 Mock（默认放行，需在用例中单独 stub） */
    @Mock
    private ScrmOutboundGuardComponent outboundGuard;

    /** 被测服务 */
    @InjectMocks
    private ScrmOutboundMessageService outboundMessageService;

    private ScrmConversationMessageEntity message(Long id, String messageId, String content) {
        ScrmConversationMessageEntity m = new ScrmConversationMessageEntity();
        m.setId(id);
        m.setMessageId(messageId);
        m.setMessageType("TEXT");
        m.setContent(content);
        return m;
    }

    private org.hiylo.scrm.dto.ScrmConversationDto conversation() {
        org.hiylo.scrm.dto.ScrmConversationDto c = new org.hiylo.scrm.dto.ScrmConversationDto();
        c.setId(50L);
        c.setAccountId(100L);
        c.setCustomerId(200L);
        return c;
    }

    @Test
    @DisplayName("enqueueFromMessage: 首次入队写入 PENDING 并解析目标平台 ID")
    void enqueueFromMessage_success() {
        when(outboundGuard.guard(anyString(), anyLong(), anyString(), any()))
                .thenReturn(ScrmOutboundGuardComponent.GuardResult.allow());
        when(outboundRepository.findFirstByConversationIdAndBusinessMessageId(50L, "m-1"))
                .thenReturn(Optional.empty());
        when(conversationService.getConversation(50L)).thenReturn(conversation());
        org.hiylo.scrm.entity.ScrmCustomerEntity customer = new org.hiylo.scrm.entity.ScrmCustomerEntity();
        customer.setPlatformCustomerUid("wxid_peer");
        when(customerRepository.findById(200L)).thenReturn(Optional.of(customer));
        when(outboundRepository.save(any())).thenAnswer(inv -> {
            org.hiylo.scrm.entity.ScrmOutboundMessageEntity e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        org.hiylo.scrm.entity.ScrmOutboundMessageEntity saved =
                outboundMessageService.enqueueFromMessage("wechat_personal", "AUTO_REPLY",
                        50L, message(9L, "m-1", "你好"));

        ArgumentCaptor<org.hiylo.scrm.entity.ScrmOutboundMessageEntity> captor =
                ArgumentCaptor.forClass(org.hiylo.scrm.entity.ScrmOutboundMessageEntity.class);
        verify(outboundRepository, times(1)).save(captor.capture());
        org.hiylo.scrm.entity.ScrmOutboundMessageEntity e = captor.getValue();
        assertThat(e.getStatus()).isEqualTo("PENDING");
        assertThat(e.getPlatformType()).isEqualTo("wechat_personal");
        assertThat(e.getTargetPlatformId()).isEqualTo("wxid_peer");
        assertThat(e.getContent()).isEqualTo("你好");
        assertThat(e.getRetryCount()).isZero();
        assertThat(saved.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("enqueueFromMessage: 同会话同业务消息重复入队直接返回已有记录")
    void enqueueFromMessage_duplicate() {
        org.hiylo.scrm.entity.ScrmOutboundMessageEntity existed =
                new org.hiylo.scrm.entity.ScrmOutboundMessageEntity();
        existed.setId(99L);
        when(outboundRepository.findFirstByConversationIdAndBusinessMessageId(50L, "m-1"))
                .thenReturn(Optional.of(existed));

        org.hiylo.scrm.entity.ScrmOutboundMessageEntity result =
                outboundMessageService.enqueueFromMessage("wechat_personal", "AUTO_REPLY",
                        50L, message(9L, "m-1", "你好"));

        assertThat(result.getId()).isEqualTo(99L);
        verify(outboundRepository, never()).save(any());
    }

    @Test
    @DisplayName("takePending: 拉取置 IN_PROGRESS 并返回快照")
    void takePending_success() {
        org.hiylo.scrm.entity.ScrmOutboundMessageEntity e =
                new org.hiylo.scrm.entity.ScrmOutboundMessageEntity();
        e.setId(7L);
        e.setPlatformType("wechat_personal");
        e.setStatus("PENDING");
        when(outboundRepository.findByPlatformTypeAndStatusAndNextRetryAtIsNullOrNextRetryAtBeforeOrderByCreateTimeAsc(
                eq("wechat_personal"), eq("PENDING"), any(), any())).thenReturn(List.of(e));
        when(outboundRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ScrmOutboundMessageDto> items = outboundMessageService.takePending("wechat_personal", 10);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getId()).isEqualTo(7L);
        assertThat(e.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(e.getInProgressAt()).isNotNull();
    }

    @Test
    @DisplayName("takePending: 无待发送消息返回空列表")
    void takePending_empty() {
        when(outboundRepository.findByPlatformTypeAndStatusAndNextRetryAtIsNullOrNextRetryAtBeforeOrderByCreateTimeAsc(
                anyString(), anyString(), any(), any())).thenReturn(List.of());
        assertThat(outboundMessageService.takePending("wechat_personal", 10)).isEmpty();
        verify(outboundRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("ack: SENT 终态写入 completedAt 并清空 errorMessage")
    void ack_sent() throws ScrmException {
        org.hiylo.scrm.entity.ScrmOutboundMessageEntity e =
                new org.hiylo.scrm.entity.ScrmOutboundMessageEntity();
        e.setId(7L);
        e.setStatus("IN_PROGRESS");
        e.setRetryCount(0);
        e.setMaxRetries(3);
        when(outboundRepository.findById(7L)).thenReturn(Optional.of(e));

        OutboundAckCallbackDto dto = new OutboundAckCallbackDto();
        dto.setOutboundId(7L);
        dto.setStatus("SENT");
        outboundMessageService.ack(dto);

        assertThat(e.getStatus()).isEqualTo("SENT");
        assertThat(e.getCompletedAt()).isNotNull();
        assertThat(e.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("ack: FAILED 未超上限退回 PENDING 并累加重试与退避时间")
    void ack_failed_requeue() throws ScrmException {
        org.hiylo.scrm.entity.ScrmOutboundMessageEntity e =
                new org.hiylo.scrm.entity.ScrmOutboundMessageEntity();
        e.setId(7L);
        e.setStatus("IN_PROGRESS");
        e.setRetryCount(0);
        e.setMaxRetries(3);
        when(outboundRepository.findById(7L)).thenReturn(Optional.of(e));

        OutboundAckCallbackDto dto = new OutboundAckCallbackDto();
        dto.setOutboundId(7L);
        dto.setStatus("FAILED");
        dto.setErrorMessage("微信输入框未找到");
        outboundMessageService.ack(dto);

        assertThat(e.getStatus()).isEqualTo("PENDING");
        assertThat(e.getRetryCount()).isEqualTo(1);
        assertThat(e.getNextRetryAt()).isNotNull();
        assertThat(e.getErrorMessage()).isEqualTo("微信输入框未找到");
    }

    @Test
    @DisplayName("ack: FAILED 达到上限进入终态")
    void ack_failed_terminal() throws ScrmException {
        org.hiylo.scrm.entity.ScrmOutboundMessageEntity e =
                new org.hiylo.scrm.entity.ScrmOutboundMessageEntity();
        e.setId(7L);
        e.setStatus("IN_PROGRESS");
        e.setRetryCount(2);
        e.setMaxRetries(3);
        when(outboundRepository.findById(7L)).thenReturn(Optional.of(e));

        OutboundAckCallbackDto dto = new OutboundAckCallbackDto();
        dto.setOutboundId(7L);
        dto.setStatus("FAILED");
        dto.setErrorMessage("多次失败");
        outboundMessageService.ack(dto);

        assertThat(e.getStatus()).isEqualTo("FAILED");
        assertThat(e.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("ack: outboundId 缺失抛参数异常, 非法状态抛参数异常")
    void ack_invalid() {
        OutboundAckCallbackDto missing = new OutboundAckCallbackDto();
        assertThatThrownBy(() -> outboundMessageService.ack(missing))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("outboundId");

        org.hiylo.scrm.entity.ScrmOutboundMessageEntity e =
                new org.hiylo.scrm.entity.ScrmOutboundMessageEntity();
        e.setId(7L);
        when(outboundRepository.findById(7L)).thenReturn(Optional.of(e));
        OutboundAckCallbackDto bad = new OutboundAckCallbackDto();
        bad.setOutboundId(7L);
        bad.setStatus("WHATEVER");
        assertThatThrownBy(() -> outboundMessageService.ack(bad))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("非法回执状态");
    }

    @Test
    @DisplayName("recoverTimedOut: 超时 IN_PROGRESS 退回 PENDING")
    void recoverTimedOut_requeue() {
        org.hiylo.scrm.entity.ScrmOutboundMessageEntity e =
                new org.hiylo.scrm.entity.ScrmOutboundMessageEntity();
        e.setId(7L);
        e.setStatus("IN_PROGRESS");
        e.setRetryCount(0);
        e.setMaxRetries(3);
        when(outboundRepository.findByPlatformTypeAndStatusAndInProgressAtBefore(
                eq("wechat_personal"), eq("IN_PROGRESS"), any(), any())).thenReturn(List.of(e));
        when(outboundRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        int recovered = outboundMessageService.recoverTimedOut("wechat_personal", 120);

        assertThat(recovered).isEqualTo(1);
        assertThat(e.getStatus()).isEqualTo("PENDING");
        assertThat(e.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("recoverTimedOut: 重试耗尽直接终态 FAILED")
    void recoverTimedOut_terminal() {
        org.hiylo.scrm.entity.ScrmOutboundMessageEntity e =
                new org.hiylo.scrm.entity.ScrmOutboundMessageEntity();
        e.setId(7L);
        e.setStatus("IN_PROGRESS");
        e.setRetryCount(2);
        e.setMaxRetries(3);
        when(outboundRepository.findByPlatformTypeAndStatusAndInProgressAtBefore(
                eq("wechat_personal"), eq("IN_PROGRESS"), any(), any())).thenReturn(List.of(e));
        when(outboundRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(outboundMessageService.recoverTimedOut("wechat_personal", 120)).isEqualTo(1);
        assertThat(e.getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("claimForPush: PENDING 抢占成功置 IN_PROGRESS")
    void claimForPush_success() {
        org.hiylo.scrm.entity.ScrmOutboundMessageEntity e =
                new org.hiylo.scrm.entity.ScrmOutboundMessageEntity();
        e.setId(7L);
        e.setStatus("PENDING");
        when(outboundRepository.findById(7L)).thenReturn(Optional.of(e));
        when(outboundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(outboundMessageService.claimForPush(7L)).isTrue();
        assertThat(e.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(e.getInProgressAt()).isNotNull();
    }

    @Test
    @DisplayName("claimForPush: 非 PENDING（已被轮询取走/终态）抢占失败")
    void claimForPush_conflict() {
        org.hiylo.scrm.entity.ScrmOutboundMessageEntity e =
                new org.hiylo.scrm.entity.ScrmOutboundMessageEntity();
        e.setId(7L);
        e.setStatus("IN_PROGRESS");
        when(outboundRepository.findById(7L)).thenReturn(Optional.of(e));

        assertThat(outboundMessageService.claimForPush(7L)).isFalse();
        verify(outboundRepository, never()).save(any());
    }

    @Test
    @DisplayName("claimForPush: 记录不存在返回 false")
    void claimForPush_missing() {
        when(outboundRepository.findById(999L)).thenReturn(Optional.empty());
        assertThat(outboundMessageService.claimForPush(999L)).isFalse();
    }

    @Test
    @DisplayName("ackByOutboundId: ok=true → 回执 SENT")
    void ackByOutboundId_sent() {
        org.hiylo.scrm.entity.ScrmOutboundMessageEntity e =
                new org.hiylo.scrm.entity.ScrmOutboundMessageEntity();
        e.setId(7L);
        e.setStatus("IN_PROGRESS");
        e.setRetryCount(0);
        e.setMaxRetries(3);
        when(outboundRepository.findById(7L)).thenReturn(Optional.of(e));

        outboundMessageService.ackByOutboundId(7L, true, null, null);

        assertThat(e.getStatus()).isEqualTo("SENT");
        assertThat(e.getCompletedAt()).isNotNull();
        assertThat(e.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("ackByOutboundId: ok=false → 回执 FAILED 透传错误码/消息，未超上限退回 PENDING")
    void ackByOutboundId_failed() {
        org.hiylo.scrm.entity.ScrmOutboundMessageEntity e =
                new org.hiylo.scrm.entity.ScrmOutboundMessageEntity();
        e.setId(7L);
        e.setStatus("IN_PROGRESS");
        e.setRetryCount(0);
        e.setMaxRetries(3);
        when(outboundRepository.findById(7L)).thenReturn(Optional.of(e));

        outboundMessageService.ackByOutboundId(7L, false, "E_NO_SESSION", "会话未找到");

        assertThat(e.getStatus()).isEqualTo("PENDING");
        assertThat(e.getRetryCount()).isEqualTo(1);
        assertThat(e.getErrorMessage()).isEqualTo("会话未找到");
    }

    @Test
    @DisplayName("enqueueFromMessage 入队后发布 OutboundEnqueuedEvent")
    void enqueuePublishesEvent() {
        when(outboundGuard.guard(anyString(), anyLong(), anyString(), any()))
                .thenReturn(ScrmOutboundGuardComponent.GuardResult.allow());
        when(outboundRepository.findFirstByConversationIdAndBusinessMessageId(50L, "m-1"))
                .thenReturn(Optional.empty());
        when(conversationService.getConversation(50L)).thenReturn(conversation());
        org.hiylo.scrm.entity.ScrmCustomerEntity customer = new org.hiylo.scrm.entity.ScrmCustomerEntity();
        customer.setPlatformCustomerUid("wxid_peer");
        when(customerRepository.findById(200L)).thenReturn(Optional.of(customer));
        when(outboundRepository.save(any())).thenAnswer(inv -> {
            org.hiylo.scrm.entity.ScrmOutboundMessageEntity e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        outboundMessageService.enqueueFromMessage("wechat_personal", "AUTO_REPLY",
                50L, message(9L, "m-1", "你好"));

        ArgumentCaptor<OutboundEnqueuedEvent> captor =
                ArgumentCaptor.forClass(OutboundEnqueuedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        OutboundEnqueuedEvent event = captor.getValue();
        assertThat(event.getOutboundId()).isEqualTo(1L);
        assertThat(event.getAccountId()).isEqualTo(100L);
        assertThat(event.getTargetPlatformId()).isEqualTo("wxid_peer");
        assertThat(event.getContent()).isEqualTo("你好");
    }
}