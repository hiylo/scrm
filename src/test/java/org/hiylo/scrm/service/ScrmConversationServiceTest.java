/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmConversationServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmConversationDto;
import org.hiylo.scrm.entity.ScrmConversationEntity;
import org.hiylo.scrm.entity.ScrmConversationMessageEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.feign.AiChatClient;
import org.hiylo.scrm.feign.dto.AiChatRequest;
import org.hiylo.scrm.feign.dto.AiChatResponse;

import org.hiylo.scrm.repository.ScrmAccountRepository;
import org.hiylo.scrm.repository.ScrmConversationMessageRepository;
import org.hiylo.scrm.repository.ScrmConversationRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.vo.ConversationSummaryVo;
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
import org.springframework.test.util.ReflectionTestUtils;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmConversationService 单元测试
 * <p>
 * 聚焦会话创建去重、状态校验、未读数 / 最后消息更新、关键词内存过滤与
 * AI 总结 (空消息 / 降级 / 异常 / 成功) 等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmConversationService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmConversationServiceTest {

    /** 会话数据仓库 Mock 桩 */
    @Mock
    private ScrmConversationRepository repository;
    /** 会话消息数据仓库 Mock 桩 */
    @Mock
    private ScrmConversationMessageRepository messageRepository;
    /** 客户数据仓库 Mock 桩 */
    @Mock
    private ScrmCustomerRepository customerRepository;
    /** 账户数据仓库 Mock 桩 */
    @Mock
    private ScrmAccountRepository accountRepository;
    /** AI 服务远程调用客户端 Mock 桩 */
    @Mock
    private AiChatClient aiChatClient;

    /** 被测服务实例 */
    private ScrmConversationService service;

    @BeforeEach
    void setUp() {
        service = new ScrmConversationService(repository, messageRepository, customerRepository,
                accountRepository, aiChatClient);
        ReflectionTestUtils.setField(service, "aiModel", "gpt-4o-mini");
        ReflectionTestUtils.setField(service, "aiTemperature", 0.7);
        ReflectionTestUtils.setField(service, "aiMaxTokens", 500);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造会话实体 (用于 findById 返回)
     */
    private ScrmConversationEntity buildEntity(Long id) {
        ScrmConversationEntity entity = new ScrmConversationEntity();
        entity.setId(id);
        entity.setPlatformType("wework");
        entity.setAccountId(100L);
        entity.setCustomerId(200L);
        entity.setConversationType("SINGLE");
        entity.setPlatformConversationId("plat_" + id);
        entity.setStatus("ACTIVE");
        entity.setUnreadCount(0L);
        return entity;
    }

    /**
     * 构造消息实体
     */
private ScrmConversationMessageEntity buildMessage(Long id, String direction, String content,
         LocalDateTime sentAt) {
        ScrmConversationMessageEntity msg = new ScrmConversationMessageEntity();
        msg.setId(id);
        msg.setMessageId("msg_" + id);
        msg.setConversationId(10L);
        msg.setMessageType("TEXT");
        msg.setDirection(direction);
        msg.setContent(content);
        msg.setSentAt(sentAt);
        return msg;
    }

    /**
     * 构造 AI 响应
     */
    private AiChatResponse buildAiResponse(String content, String model) {
        AiChatResponse response = new AiChatResponse();
        response.setId("resp_1");
        response.setModel(model);
        AiChatResponse.AiChatChoice choice = new AiChatResponse.AiChatChoice();
        choice.setMessage(new AiChatRequest.AiChatMessage("assistant", content));
        choice.setFinishReason("stop");
        response.setChoices(Collections.singletonList(choice));
        return response;
    }

    @Test
    @DisplayName("createConversation: platformConversationId 不冲突时创建成功并写入归属账号")
    void createConversation_success() throws ScrmException {
        ScrmConversationDto dto = new ScrmConversationDto();
        dto.setPlatformType("wework");
        dto.setAccountId(100L);
        dto.setCustomerId(200L);
        dto.setConversationType("SINGLE");
        dto.setPlatformConversationId("plat_new");
        when(repository.findByPlatformConversationId("plat_new")).thenReturn(Optional.empty());
        when(repository.save(any(ScrmConversationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ScrmConversationDto result = service.createConversation(dto);

        ArgumentCaptor<ScrmConversationEntity> captor =
                ArgumentCaptor.forClass(ScrmConversationEntity.class);
        verify(repository, times(1)).save(captor.capture());
        // 新建场景强制清空 ID
        assertThat(captor.getValue().getId()).isNull();
        // 默认 unreadCount 填 0
        assertThat(captor.getValue().getUnreadCount()).isZero();
        assertThat(result.getPlatformConversationId()).isEqualTo("plat_new");
    }

    @Test
    @DisplayName("createConversation: platformConversationId 重复抛 SCRM_CONVERSATION_DUPLICATED")
    void createConversation_duplicate() {
        ScrmConversationDto dto = new ScrmConversationDto();
        dto.setPlatformType("wework");
        dto.setAccountId(100L);
        dto.setCustomerId(200L);
        dto.setConversationType("SINGLE");
        dto.setPlatformConversationId("plat_dup");
        when(repository.findByPlatformConversationId("plat_dup"))
                .thenReturn(Optional.of(buildEntity(10L)));

        assertThatThrownBy(() -> service.createConversation(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("会话已存在");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("createConversation: platformConversationId 为空时跳过唯一性校验")
    void createConversation_blankPlatformId_skipUniqueCheck() throws ScrmException {
        ScrmConversationDto dto = new ScrmConversationDto();
        dto.setPlatformType("wework");
        dto.setAccountId(100L);
        dto.setCustomerId(200L);
        dto.setConversationType("SINGLE");
        when(repository.save(any(ScrmConversationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createConversation(dto);

        verify(repository, never()).findByPlatformConversationId(any());
        verify(repository, times(1)).save(any(ScrmConversationEntity.class));
    }

    @Test
    @DisplayName("getConversation: 不存在抛 SCRM_CONVERSATION_NOT_FOUND")
    void getConversation_notFound() {
        when(repository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getConversation(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("会话不存在");
    }

    @Test
    @DisplayName("getConversation: 同账号访问返回 DTO 并填充客户昵称")
    void getConversation_success() throws ScrmException {
        ScrmConversationEntity entity = buildEntity(10L);
        when(repository.findById(10L)).thenReturn(Optional.of(entity));
        org.hiylo.scrm.entity.ScrmCustomerEntity customer
            = new org.hiylo.scrm.entity.ScrmCustomerEntity();
        customer.setId(200L);
        customer.setNickname("张三");
        customer.setAvatarUrl("http://avatar");
        when(customerRepository.findById(200L)).thenReturn(Optional.of(customer));
        org.hiylo.scrm.entity.ScrmAccountEntity account
            = new org.hiylo.scrm.entity.ScrmAccountEntity();
        account.setId(100L);
        account.setDisplayName("销售账号");
        when(accountRepository.findById(100L)).thenReturn(Optional.of(account));
        when(messageRepository.countByConversationId(10L)).thenReturn(5L);

        ScrmConversationDto result = service.getConversation(10L);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getCustomerNickname()).isEqualTo("张三");
        assertThat(result.getCustomerAvatarUrl()).isEqualTo("http://avatar");
        assertThat(result.getAccountName()).isEqualTo("销售账号");
        assertThat(result.getMessageCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("getConversationByPlatformId: 空白 ID 返回 null")
    void getConversationByPlatformId_blank() {
        assertThat(service.getConversationByPlatformId("  ")).isNull();
        verify(repository, never()).findByPlatformConversationId(any());
    }

    @Test
    @DisplayName("getConversationByPlatformId: 不存在返回 null")
    void getConversationByPlatformId_notFound() {
        when(repository.findByPlatformConversationId("plat_x")).thenReturn(Optional.empty());

        assertThat(service.getConversationByPlatformId("plat_x")).isNull();
    }

    @Test
    @DisplayName("updateStatus: 状态为空抛 BAD_REQUEST")
    void updateStatus_blankStatus() {
        assertThatThrownBy(() -> service.updateStatus(10L, "  "))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("状态不能为空");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("updateStatus: 状态非法抛 BAD_REQUEST")
    void updateStatus_invalidStatus() {
        assertThatThrownBy(() -> service.updateStatus(10L, "INVALID"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("状态非法");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("updateStatus: 小写状态被归一化为大写并持久化")
    void updateStatus_normalized() throws ScrmException {
        ScrmConversationEntity entity = buildEntity(10L);
        when(repository.findById(10L)).thenReturn(Optional.of(entity));
        when(repository.save(any(ScrmConversationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateStatus(10L, "closed");

        ArgumentCaptor<ScrmConversationEntity> captor =
                ArgumentCaptor.forClass(ScrmConversationEntity.class);
        verify(repository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("incrementUnreadCount: null 未读数变 1")
    void incrementUnreadCount_nullBecomesOne() {
        ScrmConversationEntity entity = buildEntity(10L);
        entity.setUnreadCount(null);
        when(repository.findById(10L)).thenReturn(Optional.of(entity));
        when(repository.save(any(ScrmConversationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.incrementUnreadCount(10L);

        ArgumentCaptor<ScrmConversationEntity> captor =
                ArgumentCaptor.forClass(ScrmConversationEntity.class);
        verify(repository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getUnreadCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("incrementUnreadCount: 现有未读数自增")
    void incrementUnreadCount_increment() {
        ScrmConversationEntity entity = buildEntity(10L);
        entity.setUnreadCount(5L);
        when(repository.findById(10L)).thenReturn(Optional.of(entity));
        when(repository.save(any(ScrmConversationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.incrementUnreadCount(10L);

        ArgumentCaptor<ScrmConversationEntity> captor =
                ArgumentCaptor.forClass(ScrmConversationEntity.class);
        verify(repository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getUnreadCount()).isEqualTo(6L);
    }

    @Test
    @DisplayName("incrementUnreadCount: 会话不存在时吞异常不抛出")
    void incrementUnreadCount_notFound_swallowed() {
        when(repository.findById(10L)).thenReturn(Optional.empty());

        // 不抛异常
        service.incrementUnreadCount(10L);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("resetUnreadCount: 设置未读数为 0")
    void resetUnreadCount_success() {
        ScrmConversationEntity entity = buildEntity(10L);
        entity.setUnreadCount(8L);
        when(repository.findById(10L)).thenReturn(Optional.of(entity));
        when(repository.save(any(ScrmConversationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.resetUnreadCount(10L);

        ArgumentCaptor<ScrmConversationEntity> captor =
                ArgumentCaptor.forClass(ScrmConversationEntity.class);
        verify(repository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getUnreadCount()).isZero();
    }

    @Test
    @DisplayName("updateLastMessage: 摘要超过 500 字符被截断")
    void updateLastMessage_truncates() throws ScrmException {
        ScrmConversationEntity entity = buildEntity(10L);
        when(repository.findById(10L)).thenReturn(Optional.of(entity));
        when(repository.save(any(ScrmConversationEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        String longSummary = "x".repeat(600);

        service.updateLastMessage(10L, longSummary, LocalDateTime.now());

        ArgumentCaptor<ScrmConversationEntity> captor =
                ArgumentCaptor.forClass(ScrmConversationEntity.class);
        verify(repository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getLastMessageSummary()).hasSize(500);
    }

    @Test
    @DisplayName("updateLastMessage: 不存在抛 NOT_FOUND")
    void updateLastMessage_notFound() {
        when(repository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateLastMessage(10L, "summary", LocalDateTime.now()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("会话不存在");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("getConversationByAccountAndCustomer: 任一参数为 null 返回 null")
    void getConversationByAccountAndCustomer_nullArgs() {
        assertThat(service.getConversationByAccountAndCustomer(null, 200L)).isNull();
        assertThat(service.getConversationByAccountAndCustomer(100L, null)).isNull();
        verify(repository, never()).findByAccountIdAndCustomerId(any(), any());
    }

    @Test
    @DisplayName("getConversationByAccountAndCustomer: 命中返回 DTO")
    void getConversationByAccountAndCustomer_success() {
        ScrmConversationEntity entity = buildEntity(10L);
        when(repository.findByAccountIdAndCustomerId(100L, 200L)).thenReturn(Optional.of(entity));
        when(messageRepository.countByConversationId(10L)).thenReturn(0L);

        ScrmConversationDto result = service.getConversationByAccountAndCustomer(100L, 200L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("listConversations: 关键词在内存中过滤匹配客户昵称")
    void listConversations_keywordFilter() {
        ScrmConversationEntity entity = buildEntity(10L);
        entity.setLastMessageAt(LocalDateTime.now());
        Page<ScrmConversationEntity> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 10), 1L);
        when(repository.findAllByOrderByLastMessageAtDesc(any(Pageable.class))).thenReturn(page);
        org.hiylo.scrm.entity.ScrmCustomerEntity customer
            = new org.hiylo.scrm.entity.ScrmCustomerEntity();
        customer.setId(200L);
        customer.setNickname("张三丰");
        when(customerRepository.findById(200L)).thenReturn(Optional.of(customer));
        org.hiylo.scrm.entity.ScrmAccountEntity account
            = new org.hiylo.scrm.entity.ScrmAccountEntity();
        account.setId(100L);
        account.setDisplayName("账号");
        when(accountRepository.findById(100L)).thenReturn(Optional.of(account));
        when(messageRepository.countByConversationId(10L)).thenReturn(0L);

        Page<ScrmConversationDto> result =
                service.listConversations(null, null, null, null, "张三", 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCustomerNickname()).contains("张三");
    }

    @Test
    @DisplayName("listConversations: 关键词不匹配时返回空列表")
    void listConversations_keywordNoMatch() {
        ScrmConversationEntity entity = buildEntity(10L);
        entity.setLastMessageAt(LocalDateTime.now());
        Page<ScrmConversationEntity> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 10), 1L);
        when(repository.findAllByOrderByLastMessageAtDesc(any(Pageable.class))).thenReturn(page);
        org.hiylo.scrm.entity.ScrmCustomerEntity customer
            = new org.hiylo.scrm.entity.ScrmCustomerEntity();
        customer.setId(200L);
        customer.setNickname("张三");
        when(customerRepository.findById(200L)).thenReturn(Optional.of(customer));
        org.hiylo.scrm.entity.ScrmAccountEntity account
            = new org.hiylo.scrm.entity.ScrmAccountEntity();
        account.setId(100L);
        account.setDisplayName("账号");
        when(accountRepository.findById(100L)).thenReturn(Optional.of(account));
        when(messageRepository.countByConversationId(10L)).thenReturn(0L);

        Page<ScrmConversationDto> result =
                service.listConversations(null, null, null, null, "李四", 0, 10);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("listConversations: 带 status 走状态分支查询")
    void listConversations_withStatus() {
        ScrmConversationEntity entity = buildEntity(10L);
        entity.setLastMessageAt(LocalDateTime.now());
        Page<ScrmConversationEntity> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 10), 1L);
        when(repository.findByStatusAndLastMessageAtBetweenOrderByLastMessageAtDesc(eq("ACTIVE"), any(), any(), any(Pageable.class))).thenReturn(page);
        when(messageRepository.countByConversationId(10L)).thenReturn(0L);

        Page<ScrmConversationDto> result = service.listConversations(null, null, null, "active", null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        verify(repository, times(1)).findByStatusAndLastMessageAtBetweenOrderByLastMessageAtDesc(any(), any(), any(), any());
    }

    @Test
    @DisplayName("summarizeConversation: 会话不存在抛 NOT_FOUND")
    void summarizeConversation_notFound() {
        when(repository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.summarizeConversation(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("会话不存在");
    }

    @Test
    @DisplayName("summarizeConversation: 无消息返回空消息兜底总结")
    void summarizeConversation_emptyMessages() throws ScrmException {
        ScrmConversationEntity entity = buildEntity(10L);
        when(repository.findById(10L)).thenReturn(Optional.of(entity));
        Page<ScrmConversationMessageEntity> emptyPage = new PageImpl<>(Collections.emptyList());
        when(messageRepository.findByConversationIdOrderBySentAtDesc(eq(10L), any(Pageable.class)))
                .thenReturn(emptyPage);

        ConversationSummaryVo result = service.summarizeConversation(10L);

        assertThat(result.getSummary()).isEqualTo("该会话暂无消息记录。");
        assertThat(result.getMessageCount()).isZero();
        assertThat(result.getModel()).isEqualTo("gpt-4o-mini");
        // 空消息不调用 AI
        verify(aiChatClient, never()).chatCompletion(any());
    }

    @Test
    @DisplayName("summarizeConversation: AI 调用异常返回兜底总结")
    void summarizeConversation_aiCallException() throws ScrmException {
        ScrmConversationEntity entity = buildEntity(10L);
        when(repository.findById(10L)).thenReturn(Optional.of(entity));
        ScrmConversationMessageEntity msg = buildMessage(1L, "IN", "你好", LocalDateTime.now());
        Page<ScrmConversationMessageEntity> msgPage = new PageImpl<>(List.of(msg));
        when(messageRepository.findByConversationIdOrderBySentAtDesc(eq(10L), any(Pageable.class)))
                .thenReturn(msgPage);
        when(aiChatClient.chatCompletion(any(AiChatRequest.class)))
                .thenThrow(new RuntimeException("ai-server 不可达"));
        when(repository.save(any(ScrmConversationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ConversationSummaryVo result = service.summarizeConversation(10L);

        assertThat(result.getSummary()).isEqualTo("会话总结生成失败，请稍后重试。");
        assertThat(result.getMessageCount()).isEqualTo(1);
        // 兜底总结仍持久化
        ArgumentCaptor<ScrmConversationEntity> captor =
                ArgumentCaptor.forClass(ScrmConversationEntity.class);
        verify(repository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getLastMessageSummary()).isEqualTo("会话总结生成失败，请稍后重试。");
    }

    @Test
    @DisplayName("summarizeConversation: AI 触发降级标记返回兜底总结")
    void summarizeConversation_fallbackContent() throws ScrmException {
        ScrmConversationEntity entity = buildEntity(10L);
        when(repository.findById(10L)).thenReturn(Optional.of(entity));
        ScrmConversationMessageEntity msg = buildMessage(1L, "IN", "你好", LocalDateTime.now());
        Page<ScrmConversationMessageEntity> msgPage = new PageImpl<>(List.of(msg));
        when(messageRepository.findByConversationIdOrderBySentAtDesc(eq(10L), any(Pageable.class)))
                .thenReturn(msgPage);
        when(aiChatClient.chatCompletion(any(AiChatRequest.class)))
                .thenReturn(buildAiResponse(AiChatClient.FALLBACK_CONTENT, "fallback"));
        when(repository.save(any(ScrmConversationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ConversationSummaryVo result = service.summarizeConversation(10L);

        assertThat(result.getSummary()).isEqualTo("会话总结生成失败，请稍后重试。");
    }

    @Test
    @DisplayName("summarizeConversation: 正常返回 AI 生成的总结并持久化")
    void summarizeConversation_success() throws ScrmException {
        ScrmConversationEntity entity = buildEntity(10L);
        when(repository.findById(10L)).thenReturn(Optional.of(entity));
        ScrmConversationMessageEntity msg1 = buildMessage(1L, "IN", "你好", LocalDateTime.now().minusMinutes(2));
        ScrmConversationMessageEntity msg2 = buildMessage(2L, "OUT", "您好, 请问有什么可以帮您"
            , LocalDateTime.now().minusMinutes(1));
        Page<ScrmConversationMessageEntity> msgPage = new PageImpl<>(List.of(msg2, msg1));
        when(messageRepository.findByConversationIdOrderBySentAtDesc(eq(10L), any(Pageable.class)))
                .thenReturn(msgPage);
        when(aiChatClient.chatCompletion(any(AiChatRequest.class)))
                .thenReturn(buildAiResponse("客户咨询产品问题", "gpt-4o-mini"));
        when(repository.save(any(ScrmConversationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ConversationSummaryVo result = service.summarizeConversation(10L);

        assertThat(result.getSummary()).isEqualTo("客户咨询产品问题");
        assertThat(result.getMessageCount()).isEqualTo(2);
        assertThat(result.getModel()).isEqualTo("gpt-4o-mini");
        ArgumentCaptor<ScrmConversationEntity> captor =
                ArgumentCaptor.forClass(ScrmConversationEntity.class);
        verify(repository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getLastMessageSummary()).isEqualTo("客户咨询产品问题");
    }

    @Test
    @DisplayName("getConversationsByAccount: 按账号分页查询返回 DTO")
    void getConversationsByAccount_success() {
        ScrmConversationEntity entity = buildEntity(10L);
        Page<ScrmConversationEntity> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 10), 1L);
        when(repository.findByAccountIdOrderByLastMessageAtDesc(eq(100L), any(Pageable.class))).thenReturn(page);
        when(messageRepository.countByConversationId(10L)).thenReturn(0L);

        Page<ScrmConversationDto> result = service.getConversationsByAccount(100L, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAccountId()).isEqualTo(100L);
    }
}
