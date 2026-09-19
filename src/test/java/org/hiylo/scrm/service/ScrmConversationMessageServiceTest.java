/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmConversationMessageServiceTest.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.callback.ConversationEventCallbackDto;
import org.hiylo.scrm.dto.ScrmConversationDto;
import org.hiylo.scrm.dto.ScrmConversationMessageDto;
import org.hiylo.scrm.entity.ScrmConversationEntity;
import org.hiylo.scrm.entity.ScrmConversationMessageEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmConversationMessageRepository;
import org.hiylo.scrm.repository.ScrmConversationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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
 * ScrmConversationMessageService 单元测试
 * <p>
 * 验证文本/媒体消息保存、回调消息按 platformMessageId 去重等核心逻辑,
 * 使用 Mockito 隔离 Repository 与 ConversationMediaService。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmConversationMessageService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmConversationMessageServiceTest {

    /** 消息数据访问层 Mock */
    @Mock
    private ScrmConversationMessageRepository messageRepository;

    /** 会话数据访问层 Mock */
    @Mock
    private ScrmConversationRepository conversationRepository;

    /** 会话服务 Mock (消息保存后联动更新会话最后消息) */
    @Mock
    private ScrmConversationService conversationService;

    /** 媒体存储服务 Mock */
    @Mock
    private ConversationMediaService mediaService;

    /** 被测对象 */
    @InjectMocks
    private ScrmConversationMessageService messageService;

    /**
     * 测试前设置请求上下文
     */
    @BeforeEach
    void setUp() {
    }

    /**
     * 测试后清理请求上下文
     */
    @AfterEach
    void tearDown() {
    }

    /**
     * 构造会话实体 (id + 归属账号=3003, 用于 findById 校验)
     */
    private ScrmConversationEntity buildConversationEntity(Long id) {
        ScrmConversationEntity entity = new ScrmConversationEntity();
        entity.setId(id);
        return entity;
    }

    /**
     * 构造文本消息测试 DTO
     */
    private ScrmConversationMessageDto buildTextDto() {
        ScrmConversationMessageDto dto = new ScrmConversationMessageDto();
        dto.setMessageId("msg-text-001");
        dto.setConversationId(10L);
        dto.setMessageType("TEXT");
        dto.setDirection("IN");
        dto.setContent("你好, 这是一条文本消息");
        dto.setSentAt(LocalDateTime.now());
        return dto;
    }

    /**
     * 构造图片消息测试 DTO
     */
    private ScrmConversationMessageDto buildImageDto() {
        ScrmConversationMessageDto dto = new ScrmConversationMessageDto();
        dto.setMessageId("msg-image-001");
        dto.setConversationId(10L);
        dto.setMessageType("IMAGE");
        dto.setDirection("OUT");
        dto.setMediaObjectKey("scrm/conversation/3003/202607/abc_image_001.jpg");
        dto.setMediaSize(102400L);
        dto.setSentAt(LocalDateTime.now());
        return dto;
    }

    @Test
    @DisplayName("saveMessage_text: 文本消息直接存 content, mediaObjectKey 置空")
    void saveMessage_text() throws ScrmException {
        // ===== Given =====
        ScrmConversationMessageDto dto = buildTextDto();
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(buildConversationEntity(10L)));
        // save 返回带 id 的实体
        when(messageRepository.save(any(ScrmConversationMessageEntity.class))).thenAnswer(invocation -> {
            ScrmConversationMessageEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        // ===== When =====
        ScrmConversationMessageDto result = messageService.saveMessage(dto);

        // ===== Then =====
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isEqualTo("msg-text-001");
        assertThat(result.getMessageType()).isEqualTo("TEXT");
        assertThat(result.getDirection()).isEqualTo("IN");
        assertThat(result.getContent()).isEqualTo("你好, 这是一条文本消息");
        // 文本消息 mediaObjectKey 应为 null
        assertThat(result.getMediaObjectKey()).isNull();
        assertThat(result.getMediaSize()).isNull();

        // 验证 save 调用, 实体字段正确
        ArgumentCaptor<ScrmConversationMessageEntity> entityCaptor
            = ArgumentCaptor.forClass(ScrmConversationMessageEntity.class);
        verify(messageRepository, times(1)).save(entityCaptor.capture());
        ScrmConversationMessageEntity saved = entityCaptor.getValue();
        assertThat(saved.getContent()).isEqualTo("你好, 这是一条文本消息");
        assertThat(saved.getMediaObjectKey()).isNull();
        assertThat(saved.getMessageType()).isEqualTo("TEXT");
        assertThat(saved.getDirection()).isEqualTo("IN");

        // 验证联动更新会话最后消息
        verify(conversationService, times(1)).updateLastMessage(eq(10L), anyString(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("saveMessage_image: 图片消息存 mediaObjectKey, content 置空")
    void saveMessage_image() throws ScrmException {
        // ===== Given =====
        ScrmConversationMessageDto dto = buildImageDto();
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(buildConversationEntity(10L)));
        when(messageRepository.save(any(ScrmConversationMessageEntity.class))).thenAnswer(invocation -> {
            ScrmConversationMessageEntity entity = invocation.getArgument(0);
            entity.setId(2L);
            return entity;
        });

        // ===== When =====
        ScrmConversationMessageDto result = messageService.saveMessage(dto);

        // ===== Then =====
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isEqualTo("msg-image-001");
        assertThat(result.getMessageType()).isEqualTo("IMAGE");
        assertThat(result.getDirection()).isEqualTo("OUT");
        // 图片消息 content 应为 null
        assertThat(result.getContent()).isNull();
        // mediaObjectKey 已保存
        assertThat(result.getMediaObjectKey()).isEqualTo("scrm/conversation/3003/202607/abc_image_001.jpg");
        assertThat(result.getMediaSize()).isEqualTo(102400L);

        // 验证 save 调用, 实体字段正确
        ArgumentCaptor<ScrmConversationMessageEntity> entityCaptor
            = ArgumentCaptor.forClass(ScrmConversationMessageEntity.class);
        verify(messageRepository, times(1)).save(entityCaptor.capture());
        ScrmConversationMessageEntity saved = entityCaptor.getValue();
        assertThat(saved.getMediaObjectKey()).isEqualTo("scrm/conversation/3003/202607/abc_image_001.jpg");
        assertThat(saved.getMediaSize()).isEqualTo(102400L);
        assertThat(saved.getContent()).isNull();
        assertThat(saved.getMessageType()).isEqualTo("IMAGE");

        // 验证联动更新会话最后消息 (图片消息摘要应为 "[图片]")
        verify(conversationService, times(1)).updateLastMessage(eq(10L), eq("[图片]"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("saveMessage_text_missingContent: 文本消息内容为空时抛 ScrmException (code=SCRM_MESSAGE_CONTENT_MISSING)")
    void saveMessage_text_missingContent() {
        // ===== Given =====
        ScrmConversationMessageDto dto = buildTextDto();
        dto.setContent("   "); // 空白内容
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(buildConversationEntity(10L)));

        // ===== When / Then =====
        assertThatThrownBy(() -> messageService.saveMessage(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("文本消息内容不能为空")
                .hasFieldOrPropertyWithValue("code", "SCRM_MESSAGE_CONTENT_MISSING");

        // 不应保存消息
        verify(messageRepository, never()).save(any(ScrmConversationMessageEntity.class));
    }

    @Test
    @DisplayName("saveMessageFromCallback_success: 回调消息按 platformMessageId 未重复时正常保存")
    void saveMessageFromCallback_success() throws ScrmException {
        // ===== Given =====
        ConversationEventCallbackDto callback = new ConversationEventCallbackDto();
        callback.setPlatformType("DOUYIN");
        callback.setAccountId("100");
        callback.setCustomerId("200");
        // platformConversationId 为空, 走 accountId + customerId 查找
        callback.setConversationId(null);
        callback.setMessageType("text");
        callback.setDirection("INBOUND");
        callback.setContent("callback hello");
        callback.setPlatformMessageId("pm-001");
        callback.setSentAt(LocalDateTime.now());

        // platformMessageId 不存在 (非重复)
        when(messageRepository.findByPlatformMessageId("pm-001")).thenReturn(Optional.empty());

        // getConversationByPlatformId 不被调用 (conversationId 为空)
        // getConversationByAccountAndCustomer 返回已有会话
        ScrmConversationDto conversation = new ScrmConversationDto();
        conversation.setId(50L);
        conversation.setPlatformType("DOUYIN");
        conversation.setAccountId(100L);
        conversation.setCustomerId(200L);
        conversation.setConversationType("SINGLE");
        when(conversationService.getConversationByAccountAndCustomer(100L, 200L)).thenReturn(conversation);

        // saveMessage 内部校验会话存在性
        when(conversationRepository.findById(50L)).thenReturn(Optional.of(buildConversationEntity(50L)));
        when(messageRepository.save(any(ScrmConversationMessageEntity.class))).thenAnswer(invocation -> {
            ScrmConversationMessageEntity entity = invocation.getArgument(0);
            entity.setId(7L);
            return entity;
        });

        // ===== When =====
        ScrmConversationMessageDto result = messageService.saveMessageFromCallback(callback);

        // ===== Then =====
        assertThat(result).isNotNull();
        // messageId 应等于 platformMessageId
        assertThat(result.getMessageId()).isEqualTo("pm-001");
        assertThat(result.getPlatformMessageId()).isEqualTo("pm-001");
        assertThat(result.getConversationId()).isEqualTo(50L);
        // messageType / direction 规范化 (text→TEXT, INBOUND→IN)
        assertThat(result.getMessageType()).isEqualTo("TEXT");
        assertThat(result.getDirection()).isEqualTo("IN");
        assertThat(result.getContent()).isEqualTo("callback hello");

        // 验证 save 调用一次
        verify(messageRepository, times(1)).save(any(ScrmConversationMessageEntity.class));
        // 验证去重查询调用
        verify(messageRepository, times(1)).findByPlatformMessageId("pm-001");
    }

    @Test
    @DisplayName("saveMessageFromCallback_duplicate: 重复 platformMessageId 不保存, 直接返回已有消息")
    void saveMessageFromCallback_duplicate() throws ScrmException {
        // ===== Given =====
        ConversationEventCallbackDto callback = new ConversationEventCallbackDto();
        callback.setPlatformType("DOUYIN");
        callback.setAccountId("100");
        callback.setCustomerId("200");
        callback.setMessageType("text");
        callback.setDirection("INBOUND");
        callback.setContent("callback hello duplicate");
        callback.setPlatformMessageId("pm-001");
        callback.setSentAt(LocalDateTime.now());

        // platformMessageId 已存在 (重复消息)
        ScrmConversationMessageEntity existing = new ScrmConversationMessageEntity();
        existing.setId(999L);
        existing.setMessageId("pm-001");
        existing.setConversationId(50L);
        existing.setMessageType("TEXT");
        existing.setDirection("IN");
        existing.setContent("callback hello");
        existing.setPlatformMessageId("pm-001");
        existing.setSentAt(LocalDateTime.now().minusMinutes(1));
        when(messageRepository.findByPlatformMessageId("pm-001")).thenReturn(Optional.of(existing));

        // ===== When =====
        ScrmConversationMessageDto result = messageService.saveMessageFromCallback(callback);

        // ===== Then =====
        assertThat(result).isNotNull();
        // 返回的是已有消息 (内容为之前的 "callback hello", 而非本次传入的 "callback hello duplicate")
        assertThat(result.getMessageId()).isEqualTo("pm-001");
        assertThat(result.getContent()).isEqualTo("callback hello");

        // 验证 save 未被调用 (重复消息不保存)
        verify(messageRepository, never()).save(any(ScrmConversationMessageEntity.class));
        // 验证去重查询调用一次
        verify(messageRepository, times(1)).findByPlatformMessageId("pm-001");
        // 不应触发会话查找 (因为已提前返回)
        verify(conversationService, never()).getConversationByPlatformId(anyString());
        verify(conversationService, never()).getConversationByAccountAndCustomer(anyLong(), anyLong());
    }
}
