/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AgentHubWebSocketHandlerTest.java
 * Date : 2026/10/08 11:40:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmAccountDto;
import org.hiylo.scrm.dto.callback.ConversationEventCallbackDto;
import org.hiylo.scrm.dto.callback.OutboundAckCallbackDto;
import org.hiylo.scrm.dto.ScrmConversationMessageDto;
import org.hiylo.scrm.service.ConversationMediaService;
import org.hiylo.scrm.service.OutboundEnqueuedEvent;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.service.ScrmAccountService;
import org.hiylo.scrm.service.ScrmAutoReplyService;
import org.hiylo.scrm.service.ScrmConversationMessageService;
import org.hiylo.scrm.service.ScrmOutboundMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentHub WebSocket 处理器单元测试。
 * <p>
 * 覆盖协议核心路径：hello 握手（令牌正确/错误/缺失）、心跳 ping/pong、
 * wechat.new_message 事件落库、sessions.snapshot 快照回填、未握手帧拒绝。
 * 通过 {@link #capturedFrames} 捕获服务端发出的帧验证握手结果，
 * 通过 {@link ArgumentCaptor} 验证落库 DTO 映射。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentHubWebSocketHandlerTest {

    /** 测试用握手令牌 */
    private static final String TOKEN = "test-agent-secret-0123456789";

    /** 设备 ID */
    private static final String DEVICE_ID = "device-abc-123";

    /** 微信登录账号（hello.account 缺省时回退 deviceId） */
    private static final String ACCOUNT = "wxid_test_180";

    /** 消息服务 mock */
    @Mock
    private ScrmConversationMessageService conversationMessageService;

    /** 出站队列服务 mock（实时代发抢占 + 应答回填） */
    @Mock
    private ScrmOutboundMessageService outboundMessageService;

    /** 账号服务 mock（账号 ID → platformAccountUid 匹配设备） */
    @Mock
    private ScrmAccountService accountService;

    /** 自动回复服务 mock（实时入站新消息命中规则） */
    @Mock
    private ScrmAutoReplyService autoReplyService;

    /** 会话媒体存储服务 mock（媒体出站消息生成预签名URL，IMAGE/FILE 分派测试） */
    @Mock
    private ConversationMediaService conversationMediaService;

    /** 客户仓库 mock（好友列表幂等建档） */
    @Mock
    private ScrmCustomerRepository customerRepository;

    /** 处理器（手动反射注入 private 字段，避免依赖 Spring） */
    private AgentHubWebSocketHandler handler;

    /** 捕获服务端发出帧的列表 */
    private final java.util.List<TextMessage> capturedFrames = new java.util.ArrayList<>();

    /** session mock */
    private WebSocketSession session;

    /**
     * 初始化：构造 handler 并反射注入 agentSecret，准备 session mock。
     *
     * @throws Exception 反射注入失败
     */
    @BeforeEach
    void setUp() throws Exception {
        handler = new AgentHubWebSocketHandler(
                new ObjectMapper(), conversationMessageService,
                outboundMessageService, accountService, autoReplyService,
                conversationMediaService);
        Field secretField = AgentHubWebSocketHandler.class.getDeclaredField("agentSecret");
        secretField.setAccessible(true);
        secretField.set(handler, TOKEN);
        Field customerRepoField = AgentHubWebSocketHandler.class.getDeclaredField("customerRepository");
        customerRepoField.setAccessible(true);
        customerRepoField.set(handler, customerRepository);

        session = org.mockito.Mockito.mock(WebSocketSession.class);
        Map<String, Object> attrs = new ConcurrentHashMap<>();
        when(session.getAttributes()).thenReturn(attrs);
        when(session.getId()).thenReturn("sess-1");
        when(session.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            capturedFrames.add(invocation.getArgument(0));
            return null;
        }).when(session).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("hello 令牌正确 → welcome，设备注册，可接收事件")
    void helloWithValidTokenWelcomes() throws Exception {
        send(helloFrame(DEVICE_ID, ACCOUNT, TOKEN));
        assertThat(capturedFrames).hasSize(1);
        Map<String, Object> welcome = parse(capturedFrames.get(0));
        assertThat(welcome).containsEntry("type", "welcome");

        // 已授权后可收事件
        send(eventFrame("wechat.new_message", newMessageData()));
        ArgumentCaptor<ConversationEventCallbackDto> captor =
                ArgumentCaptor.forClass(ConversationEventCallbackDto.class);
        verify(conversationMessageService, times(1)).saveMessageFromCallback(captor.capture());
        ConversationEventCallbackDto dto = captor.getValue();
        assertThat(dto.getPlatformType()).isEqualTo("wechat_personal");
        assertThat(dto.getPlatformAccountUid()).isEqualTo(ACCOUNT);
        assertThat(dto.getPlatformCustomerUid()).isEqualTo("wxid_friend_001");
        assertThat(dto.getDirection()).isEqualTo("INBOUND");
        assertThat(dto.getMessageType()).isEqualTo("TEXT");
        assertThat(dto.getContent()).isEqualTo("你好，测试");
        assertThat(dto.getPlatformMessageId()).isEqualTo("12345");
    }

    @Test
    @DisplayName("hello account 缺省 → welcome，账号降级为 deviceId（尤其不 NPE）")
    void helloWithoutAccountFallsBackToDeviceId() throws Exception {
        Map<String, Object> frame = helloFrame(DEVICE_ID, ACCOUNT, TOKEN);
        ((Map<String, Object>) frame.get("data")).remove("account");
        send(frame);

        assertThat(capturedFrames).hasSize(1);
        Map<String, Object> welcome = parse(capturedFrames.get(0));
        assertThat(welcome).containsEntry("type", "welcome");

        // 会话 attributes 中 ATTR_ACCOUNT 应降级为 deviceId（不允许 null）
        assertThat(session.getAttributes().get(AgentHubWebSocketHandler.ATTR_ACCOUNT)).isEqualTo(DEVICE_ID);
        // 已授权后可收事件，落库账号也走 deviceId
        send(eventFrame("wechat.new_message", newMessageData()));
        ArgumentCaptor<ConversationEventCallbackDto> captor =
                ArgumentCaptor.forClass(ConversationEventCallbackDto.class);
        verify(conversationMessageService, times(1)).saveMessageFromCallback(captor.capture());
        assertThat(captor.getValue().getPlatformAccountUid()).isEqualTo(DEVICE_ID);
    }

    @Test
    @DisplayName("事件 create_time 为秒级时间戳 → sentAt 自动放大到毫秒")
    void eventWithSecondTimestampConvertsToMillis() throws Exception {
        send(helloFrame(DEVICE_ID, ACCOUNT, TOKEN));
        Map<String, Object> data = newMessageData();
        long seconds = 1791434462L; // 2026-10-08 前后（秒级）
        data.put("create_time", seconds);
        send(eventFrame("wechat.new_message", data));
        ArgumentCaptor<ConversationEventCallbackDto> captor =
                ArgumentCaptor.forClass(ConversationEventCallbackDto.class);
        verify(conversationMessageService, times(1)).saveMessageFromCallback(captor.capture());
        java.time.LocalDateTime sentAt = captor.getValue().getSentAt();
        assertThat(sentAt).isNotNull();
        // 换算回 epoch millis 应在秒*1000 附近（允许 1 秒误差内的毫秒偏移）
        long epochMillis = sentAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        assertThat(epochMillis).isEqualTo(seconds * 1000L);
    }

    @Test
    @DisplayName("hello 令牌错误 → close(401) 且断开")
    void helloWithBadTokenRejected() throws Exception {
        send(helloFrame(DEVICE_ID, ACCOUNT, "wrong-token"));
        assertThat(capturedFrames).hasSize(1);
        Map<String, Object> close = parse(capturedFrames.get(0));
        assertThat(close).containsEntry("type", "close");
        assertThat(close.get("code")).isEqualTo(401);
        verify(session).close(any());
    }

    @Test
    @DisplayName("hello 缺令牌 → close(400)")
    void helloMissingTokenRejected() throws Exception {
        Map<String, Object> frame = helloFrame(DEVICE_ID, ACCOUNT, TOKEN);
        Map<String, Object> data = (Map<String, Object>) frame.get("data");
        data.remove("token");
        send(frame);
        assertThat(capturedFrames).hasSize(1);
        Map<String, Object> close = parse(capturedFrames.get(0));
        assertThat(close.get("code")).isEqualTo(400);
    }

    @Test
    @DisplayName("未握手直接发事件 → close(401)")
    void eventBeforeHelloRejected() throws Exception {
        send(eventFrame("wechat.new_message", newMessageData()));
        assertThat(capturedFrames).hasSize(1);
        assertThat(parse(capturedFrames.get(0))).containsEntry("type", "close");
        verify(conversationMessageService, never()).saveMessageFromCallback(any());
    }

    @Test
    @DisplayName("握手后 ping → pong")
    void pingGetsPong() throws Exception {
        send(helloFrame(DEVICE_ID, ACCOUNT, TOKEN));
        capturedFrames.clear();
        send(newFrame("ping", null));
        assertThat(capturedFrames).hasSize(1);
        assertThat(parse(capturedFrames.get(0))).containsEntry("type", "pong");
    }

    @Test
    @DisplayName("sessions.snapshot 快照逐条落库（is_self 区分方向）")
    void sessionsSnapshotPersistsMessages() throws Exception {
        send(helloFrame(DEVICE_ID, ACCOUNT, TOKEN));
        capturedFrames.clear();

        Map<String, Object> msgIn = newMessageData();
        msgIn.put("local_id", 1001);
        msgIn.put("text", "客户消息");
        Map<String, Object> msgOut = newMessageData();
        msgOut.put("local_id", 1002);
        msgOut.put("is_self", true);
        msgOut.put("text", "我发的");

        Map<String, Object> snapshot = eventFrame("sessions.snapshot", Map.of(
                "sessions", java.util.List.of(Map.of("username", "wxid_friend_001", "summary", "客户消息")),
                "messages", java.util.List.of(msgIn, msgOut),
                "seq", 1));
        send(snapshot);

        ArgumentCaptor<ConversationEventCallbackDto> captor =
                ArgumentCaptor.forClass(ConversationEventCallbackDto.class);
        verify(conversationMessageService, times(2)).saveMessageFromCallback(captor.capture());
        java.util.List<ConversationEventCallbackDto> all = captor.getAllValues();
        assertThat(all).hasSize(2);
        assertThat(all.get(0).getDirection()).isEqualTo("INBOUND");
        assertThat(all.get(1).getDirection()).isEqualTo("OUTBOUND");
    }

    @Test
    @DisplayName("会话快照无消息 → 不落库")
    void snapshotWithoutMessagesNoop() throws Exception {
        send(helloFrame(DEVICE_ID, ACCOUNT, TOKEN));
        capturedFrames.clear();
        send(eventFrame("sessions.snapshot",
                Map.of("sessions", java.util.List.of(), "messages", java.util.List.of(), "seq", 1)));
        verify(conversationMessageService, never()).saveMessageFromCallback(any());
    }

    @Test
    @DisplayName("消息正文为空 → [kind] 占位，仍落库")
    void emptyTextFallsBackToKindPlaceholder() throws Exception {
        send(helloFrame(DEVICE_ID, ACCOUNT, TOKEN));
        capturedFrames.clear();
        Map<String, Object> data = newMessageData();
        data.put("text", "");
        data.put("kind", "image");
        send(eventFrame("wechat.new_message", data));
        ArgumentCaptor<ConversationEventCallbackDto> captor =
                ArgumentCaptor.forClass(ConversationEventCallbackDto.class);
        verify(conversationMessageService, times(1)).saveMessageFromCallback(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("[image]");
    }

    @Test
    @DisplayName("实时新消息（IN 方向）落库后触发自动回复匹配")
    void newInboundMessageTriggersAutoReply() throws Exception {
        send(helloFrame(DEVICE_ID, ACCOUNT, TOKEN));
        capturedFrames.clear();

        ScrmConversationMessageDto saved = new ScrmConversationMessageDto();
        saved.setConversationId(555L);
        when(conversationMessageService.saveMessageFromCallback(any()))
                .thenReturn(saved);

        send(eventFrame("wechat.new_message", newMessageData()));

        verify(autoReplyService, times(1))
                .matchInboundMessage(eq("wechat_personal"), eq(555L), eq("你好，测试"));
    }

    @Test
    @DisplayName("实时新消息（OUT 方向/自己发出）不触发自动回复")
    void outboundSelfMessageDoesNotTriggerAutoReply() throws Exception {
        send(helloFrame(DEVICE_ID, ACCOUNT, TOKEN));
        capturedFrames.clear();

        ScrmConversationMessageDto saved = new ScrmConversationMessageDto();
        saved.setConversationId(555L);
        when(conversationMessageService.saveMessageFromCallback(any()))
                .thenReturn(saved);

        Map<String, Object> data = newMessageData();
        data.put("is_self", true);
        send(eventFrame("wechat.new_message", data));

        verify(autoReplyService, never())
                .matchInboundMessage(any(), any(), any());
    }

    @Test
    @DisplayName("会话快照回填（历史消息）不触发自动回复")
    void snapshotDoesNotTriggerAutoReply() throws Exception {
        send(helloFrame(DEVICE_ID, ACCOUNT, TOKEN));
        capturedFrames.clear();

        when(conversationMessageService.saveMessageFromCallback(any()))
                .thenReturn(new ScrmConversationMessageDto());

        Map<String, Object> msgIn = newMessageData();
        msgIn.put("local_id", 1001);
        msgIn.put("text", "客户消息");
        Map<String, Object> snapshot = eventFrame("sessions.snapshot", Map.of(
                "sessions", java.util.List.of(Map.of("username", "wxid_friend_001")),
                "messages", java.util.List.of(msgIn),
                "seq", 1));
        send(snapshot);

        verify(autoReplyService, never())
                .matchInboundMessage(any(), any(), any());
    }

    // ------------------------------------------------------------------ 发送路径

    @Test
    @DisplayName("出站入队事件 + 账号在线 → 抢占并下发 wechat.send_text 指令")
    void outboundEnqueuedPushesSendTextToOnlineDevice() throws Exception {
        // 握手登记设备（account = ACCOUNT）
        send(helloFrame(DEVICE_ID, ACCOUNT, TOKEN));
        capturedFrames.clear();

        // 账号 platformAccountUid 与设备 hello.account 一致
        ScrmAccountDto account = new ScrmAccountDto();
        account.setPlatformAccountUid(ACCOUNT);
        when(accountService.getAccountInternal(100L)).thenReturn(account);
        when(outboundMessageService.claimForPush(7L)).thenReturn(true);

        handler.onOutboundEnqueued(new OutboundEnqueuedEvent(
                this, 7L, 100L, "wxid_friend_001", "您好，有什么可以帮您",
                "TEXT", null, null, "wechat_personal", "biz-1"));

        verify(outboundMessageService, times(1)).claimForPush(7L);
        assertThat(capturedFrames).hasSize(1);
        Map<String, Object> command = parse(capturedFrames.get(0));
        assertThat(command).containsEntry("id", "ob-7");
        assertThat(command).containsEntry("method", "wechat.send_text");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) command.get("params");
        assertThat(params).containsEntry("session", "wxid_friend_001");
        assertThat(params).containsEntry("text", "您好，有什么可以帮您");
        assertThat(params).containsEntry("verify", true);
    }

    @Test
    @DisplayName("出站入队事件 + 设备离线 → 不抢占、不下发（保持 PENDING 待轮询）")
    void outboundEnqueuedSkipsWhenDeviceOffline() {
        ScrmAccountDto account = new ScrmAccountDto();
        account.setPlatformAccountUid("wxid_not_online");
        when(accountService.getAccountInternal(100L)).thenReturn(account);

        handler.onOutboundEnqueued(new OutboundEnqueuedEvent(
                this, 7L, 100L, "wxid_friend_001", "测试", "TEXT", null, null, "wechat_personal", "biz-1"));

        verify(outboundMessageService, never()).claimForPush(any());
        assertThat(capturedFrames).isEmpty();
    }

    @Test
    @DisplayName("出站入队事件 + 抢占失败（已被轮询取走）→ 不下发，避免双发")
    void outboundEnqueuedSkipsWhenClaimFails() throws Exception {
        send(helloFrame(DEVICE_ID, ACCOUNT, TOKEN));
        capturedFrames.clear();

        ScrmAccountDto account = new ScrmAccountDto();
        account.setPlatformAccountUid(ACCOUNT);
        when(accountService.getAccountInternal(100L)).thenReturn(account);
        when(outboundMessageService.claimForPush(7L)).thenReturn(false);

        handler.onOutboundEnqueued(new OutboundEnqueuedEvent(
                this, 7L, 100L, "wxid_friend_001", "测试", "TEXT", null, null, "wechat_personal", "biz-1"));

        verify(outboundMessageService, times(1)).claimForPush(7L);
        assertThat(capturedFrames).isEmpty();
    }

    @Test
    @DisplayName("指令应答 ok=true → 回填 SENT")
    void commandReplyOkAcksSent() throws Exception {
        send(helloFrame(DEVICE_ID, ACCOUNT, TOKEN));
        capturedFrames.clear();

        Map<String, Object> reply = new HashMap<>();
        reply.put("id", "ob-7");
        reply.put("ok", true);
        reply.put("result", Map.of("sent", true, "verified", true));
        send(reply);

        verify(outboundMessageService, times(1))
                .ackByOutboundId(eq(7L), eq(true), any(), any());
    }

    @Test
    @DisplayName("指令应答 ok=false → 回填 FAILED 并透传 error.code/message")
    void commandReplyFailureAcksFailed() throws Exception {
        send(helloFrame(DEVICE_ID, ACCOUNT, TOKEN));
        capturedFrames.clear();

        Map<String, Object> reply = new HashMap<>();
        reply.put("id", "ob-9");
        reply.put("ok", false);
        reply.put("error", Map.of("code", "E_NO_SESSION", "message", "会话未找到"));
        send(reply);

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboundMessageService, times(1))
                .ackByOutboundId(eq(9L), eq(false), codeCaptor.capture(), msgCaptor.capture());
        assertThat(codeCaptor.getValue()).isEqualTo("E_NO_SESSION");
        assertThat(msgCaptor.getValue()).isEqualTo("会话未找到");
    }

    @Test
    @DisplayName("非出站前缀指令应答 → 不回填出站队列")
    void commandReplyNonOutboundIgnored() throws Exception {
        send(helloFrame(DEVICE_ID, ACCOUNT, TOKEN));
        capturedFrames.clear();

        Map<String, Object> reply = new HashMap<>();
        reply.put("id", "r1");
        reply.put("ok", true);
        send(reply);

        verify(outboundMessageService, never()).ackByOutboundId(any(), any(Boolean.class), any(), any());
    }

    @Test
    @DisplayName("IMAGE 出站入队事件 → 下发 wechat.send_image 指令（含 path=presigned URL）")
    void outboundImageEnqueuedPushesSendImage() throws Exception {
        send(helloFrame(DEVICE_ID, ACCOUNT, TOKEN));
        capturedFrames.clear();

        ScrmAccountDto account = new ScrmAccountDto();
        account.setPlatformAccountUid(ACCOUNT);
        when(accountService.getAccountInternal(100L)).thenReturn(account);
        when(outboundMessageService.claimForPush(7L)).thenReturn(true);
        when(conversationMediaService.getMediaUrl("scrm/conversation/202610/abc_pic.png", 60))
                .thenReturn("https://minio.local/scrm/presigned-pic.png");

        handler.onOutboundEnqueued(new OutboundEnqueuedEvent(
                this, 7L, 100L, "wxid_friend_001", null,
                "IMAGE", "scrm/conversation/202610/abc_pic.png", "abc_pic.png",
                "wechat_personal", "biz-img-1"));

        verify(outboundMessageService, times(1)).claimForPush(7L);
        assertThat(capturedFrames).hasSize(1);
        Map<String, Object> command = parse(capturedFrames.get(0));
        assertThat(command).containsEntry("id", "ob-7");
        assertThat(command).containsEntry("method", "wechat.send_image");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) command.get("params");
        assertThat(params).containsEntry("session", "wxid_friend_001");
        assertThat(params).containsEntry("path", "https://minio.local/scrm/presigned-pic.png");
        assertThat(params).containsEntry("verify", true);
    }

    @Test
    @DisplayName("FILE 出站入队事件 → 下发 wechat.send_file 指令")
    void outboundFileEnqueuedPushesSendFile() throws Exception {
        send(helloFrame(DEVICE_ID, ACCOUNT, TOKEN));
        capturedFrames.clear();

        ScrmAccountDto account = new ScrmAccountDto();
        account.setPlatformAccountUid(ACCOUNT);
        when(accountService.getAccountInternal(100L)).thenReturn(account);
        when(outboundMessageService.claimForPush(7L)).thenReturn(true);
        when(conversationMediaService.getMediaUrl("scrm/conversation/202610/doc.pdf", 60))
                .thenReturn("https://minio.local/scrm/presigned-doc.pdf");

        handler.onOutboundEnqueued(new OutboundEnqueuedEvent(
                this, 7L, 100L, "wxid_friend_001", null,
                "FILE", "scrm/conversation/202610/doc.pdf", "doc.pdf",
                "wechat_personal", "biz-file-1"));

        Map<String, Object> command = parse(capturedFrames.get(0));
        assertThat(command).containsEntry("method", "wechat.send_file");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) command.get("params");
        assertThat(params).containsEntry("path", "https://minio.local/scrm/presigned-doc.pdf");
    }

    @Test
    @DisplayName("媒体预签名 URL 生成失败 → 回退对象 key 作为 path 下发")
    void outboundMediaUrlFallbackToObjectKey() throws Exception {
        send(helloFrame(DEVICE_ID, ACCOUNT, TOKEN));
        capturedFrames.clear();

        ScrmAccountDto account = new ScrmAccountDto();
        account.setPlatformAccountUid(ACCOUNT);
        when(accountService.getAccountInternal(100L)).thenReturn(account);
        when(outboundMessageService.claimForPush(7L)).thenReturn(true);
        when(conversationMediaService.getMediaUrl(any(), anyInt()))
                .thenThrow(new org.hiylo.scrm.exception.ScrmException("SCRM_MEDIA_PRESIGN_FAILED", "presign err"));

        handler.onOutboundEnqueued(new OutboundEnqueuedEvent(
                this, 7L, 100L, "wxid_friend_001", null,
                "IMAGE", "scrm/conversation/202610/fallback.png", "fallback.png",
                "wechat_personal", "biz-img-2"));

        Map<String, Object> command = parse(capturedFrames.get(0));
        assertThat(command).containsEntry("method", "wechat.send_image");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) command.get("params");
        assertThat(params).containsEntry("path", "scrm/conversation/202610/fallback.png");
    }

    @Test
    @DisplayName("sessions.snapshot 携带 contacts[] → 好友幂等建档为潜在客户")
    void snapshotContactsProvisionsCustomers() throws Exception {
        send(helloFrame(DEVICE_ID, ACCOUNT, TOKEN));
        capturedFrames.clear();

        ScrmAccountDto account = new ScrmAccountDto();
        account.setId(100L);
        when(accountService.getAccountByPlatform(eq("wechat_personal"), eq(ACCOUNT)))
                .thenReturn(account);
        // 好友1不存在 → 新建；好友2已存在 → 跳过
        when(customerRepository.findByPlatformTypeAndPlatformCustomerUidAndOwnerAccountId(
                eq("wechat_personal"), eq("wxid_f1"), eq(100L)))
                .thenReturn(java.util.Optional.empty());
        when(customerRepository.findByPlatformTypeAndPlatformCustomerUidAndOwnerAccountId(
                eq("wechat_personal"), eq("wxid_f2"), eq(100L)))
                .thenReturn(java.util.Optional.of(new ScrmCustomerEntity()));
        when(customerRepository.save(any(ScrmCustomerEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        send(eventFrame("sessions.snapshot", Map.of(
                "sessions", java.util.List.of(),
                "messages", java.util.List.of(),
                "contacts", java.util.List.of(
                        Map.of("username", "wxid_f1", "nick_name", "好友一", "remark", ""),
                        Map.of("username", "wxid_f2", "nick_name", "", "remark", "同事")),
                "seq", 1)));

        org.mockito.ArgumentCaptor<ScrmCustomerEntity> captor =
                org.mockito.ArgumentCaptor.forClass(ScrmCustomerEntity.class);
        verify(customerRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getPlatformCustomerUid()).isEqualTo("wxid_f1");
        assertThat(captor.getValue().getNickname()).isEqualTo("好友一");
        assertThat(captor.getValue().getOwnerAccountId()).isEqualTo(100L);
        assertThat(captor.getValue().getLifecycle()).isEqualTo("NEW");
    }

    @Test
    @DisplayName("sessions.snapshot 无 contacts / 账号未建档 → 不建档不报错")
    void snapshotWithoutContactsNoop() throws Exception {
        send(helloFrame(DEVICE_ID, ACCOUNT, TOKEN));
        capturedFrames.clear();
        when(accountService.getAccountByPlatform(any(), any())).thenReturn(null);

        send(eventFrame("sessions.snapshot", Map.of(
                "sessions", java.util.List.of(),
                "messages", java.util.List.of(),
                "contacts", java.util.List.of(Map.of("username", "wxid_x")),
                "seq", 2)));

        verify(customerRepository, never()).save(any(ScrmCustomerEntity.class));
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 构造 hello 帧。
     *
     * @param deviceId 设备 ID
     * @param account  微信账号
     * @param token    握手令牌
     * @return hello 帧
     */
    private Map<String, Object> helloFrame(String deviceId, String account, String token) {
        Map<String, Object> data = new HashMap<>();
        data.put("device_id", deviceId);
        data.put("version", "1.0.0");
        data.put("token", token);
        data.put("capabilities", java.util.List.of("agent.ping", "wechat.*"));
        data.put("wechat_running", true);
        data.put("account", account);
        return newFrame("hello", data);
    }

    /**
     * 构造一条新消息数据。
     *
     * @return 消息 data
     */
    private Map<String, Object> newMessageData() {
        Map<String, Object> data = new HashMap<>();
        data.put("session", "wxid_friend_001");
        data.put("session_name", "好友001");
        data.put("is_self", false);
        data.put("kind", "text");
        data.put("text", "你好，测试");
        data.put("local_id", 12345);
        data.put("sender_wxid", "wxid_friend_001");
        data.put("sender_name", "好友001");
        data.put("create_time", System.currentTimeMillis());
        data.put("sort_seq", 1L);
        return data;
    }

    /**
     * 构造通用帧。
     *
     * @param type 帧类型
     * @param data 帧 data（可空）
     * @return 帧
     */
    private Map<String, Object> newFrame(String type, Map<String, Object> data) {
        Map<String, Object> frame = new HashMap<>();
        frame.put("type", type);
        if (data != null) {
            frame.put("data", data);
        }
        return frame;
    }

    /**
     * 构造事件帧（顶层平铺 type/event/data 三键，与 REMOTE-AGENT §3 一致）。
     *
     * @param event 事件名
     * @param data  事件 data
     * @return 事件帧
     */
    private Map<String, Object> eventFrame(String event, Map<String, Object> data) {
        Map<String, Object> frame = new HashMap<>();
        frame.put("type", "event");
        frame.put("event", event);
        frame.put("data", data);
        return frame;
    }

    /**
     * 通过 handler 发送一帧 JSON。
     *
     * @param frame 帧对象
     * @throws Exception 发送失败
     */
    private void send(Map<String, Object> frame) throws Exception {
        handler.handleTextMessage(session, new TextMessage(new ObjectMapper().writeValueAsString(frame)));
    }

    /**
     * 解析捕获的帧。
     *
     * @param message 文本消息
     * @return 解析后的帧
     * @throws Exception 解析失败
     */
    private Map<String, Object> parse(TextMessage message) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = new ObjectMapper().readValue(message.getPayload(), Map.class);
        return parsed;
    }
}
