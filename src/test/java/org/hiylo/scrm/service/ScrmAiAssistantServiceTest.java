/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiAssistantServiceTest.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmAiChatDto;
import org.hiylo.scrm.entity.ScrmAiConversationEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAiAssistantConfigRepository;
import org.hiylo.scrm.repository.ScrmAiConversationRepository;
import org.hiylo.scrm.repository.ScrmAiIntentRepository;
import org.hiylo.scrm.repository.ScrmAiKnowledgeBaseRepository;
import org.hiylo.scrm.repository.ScrmAiKnowledgeDocumentRepository;
import org.hiylo.scrm.service.evaluator.AiResponseGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
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
 * ScrmAiAssistantService 单元测试
 * <p>
 * 覆盖 AI 对话 ({@code chat}) 与批量对话 ({@code batchChat}) 的参数校验、
 * 正常对话流程 (意图识别 → 情感分析 → AI 回复生成 → 持久化) 与批量部分失败跳过逻辑,
 * 使用 Mockito 隔离 Repository 与 ObjectMapper。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmAiAssistantService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmAiAssistantServiceTest {

    /** AI 助手配置数据访问层 Mock */
    @Mock
    private ScrmAiAssistantConfigRepository configRepository;

    /** AI 意图数据访问层 Mock */
    @Mock
    private ScrmAiIntentRepository intentRepository;

    /** AI 对话记录数据访问层 Mock */
    @Mock
    private ScrmAiConversationRepository conversationRepository;

    /** AI 知识库数据访问层 Mock */
    @Mock
    private ScrmAiKnowledgeBaseRepository knowledgeBaseRepository;

    /** AI 知识库文档数据访问层 Mock */
    @Mock
    private ScrmAiKnowledgeDocumentRepository documentRepository;

    /** AI 回复生成器 Mock (可插拔策略, 对接大模型时替换) */
    @Mock
    private AiResponseGenerator aiResponseGenerator;

    /** JSON 解析器 Mock */
    @Mock
    private ObjectMapper objectMapper;

    /** 被测对象 */
    private ScrmAiAssistantService aiAssistantService;

    /**
     * 测试前手动装配门面与兄弟服务 (拆分后门面注入兄弟服务)。
     */
    @BeforeEach
    void setUp() {
        ScrmAiAssistantConfigService configService = new ScrmAiAssistantConfigService(configRepository);
        ScrmAiAssistantIntentService intentService = new ScrmAiAssistantIntentService(intentRepository, objectMapper);
        ScrmAiAssistantConversationService conversationService = new ScrmAiAssistantConversationService(
                conversationRepository, configRepository, intentService, aiResponseGenerator, objectMapper);
        ScrmAiAssistantKnowledgeService knowledgeService = new ScrmAiAssistantKnowledgeService(
                knowledgeBaseRepository, documentRepository, objectMapper);
        ScrmAiAssistantStatsService statsService = new ScrmAiAssistantStatsService(conversationRepository);
        aiAssistantService = new ScrmAiAssistantService(
                configService, intentService, conversationService, knowledgeService, statsService);
    }

    /**
     * 测试后清理请求上下文
     */
    @AfterEach
    void tearDown() {
    }

    // ============================================================
    // chat
    // ============================================================

    @Test
    @DisplayName("chat_blankMessage: 客户消息为空抛 ScrmException (code=SCRM_BAD_REQUEST)")
    void chat_blankMessage() {
        ScrmAiChatDto dto = new ScrmAiChatDto();
        dto.setCustomerId(1L);
        dto.setMessage("   ");

        assertThatThrownBy(() -> aiAssistantService.chat(dto))
                .isInstanceOf(ScrmException.class)
                .hasFieldOrPropertyWithValue("code", "SCRM_BAD_REQUEST")
                .hasMessageContaining("客户消息不能为空");

        verify(conversationRepository, never()).save(any());
    }

    @Test
    @DisplayName("chat_nullCustomerId: customerId 为空抛 ScrmException (code=SCRM_BAD_REQUEST)")
    void chat_nullCustomerId() {
        ScrmAiChatDto dto = new ScrmAiChatDto();
        dto.setMessage("你好");
        // customerId 为 null

        assertThatThrownBy(() -> aiAssistantService.chat(dto))
                .isInstanceOf(ScrmException.class)
                .hasFieldOrPropertyWithValue("code", "SCRM_BAD_REQUEST")
                .hasMessageContaining("客户 ID 不能为空");

        verify(conversationRepository, never()).save(any());
    }

    @Test
    @DisplayName("chat_success: 正常对话返回含 detectedIntent/aiResponse/responseTimeMs, conversationRepository.save 被调用")
    void chat_success() throws ScrmException {
        // ===== Given =====
        // 无可用默认配置 (configId 为 null, 走默认配置查询), 回退 null 配置使用模拟回复
        when(configRepository.findByIsDefaultTrueAndEnabledTrue())
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(ScrmAiConversationEntity.class)))
                .thenAnswer(invocation -> {
                    ScrmAiConversationEntity entity = invocation.getArgument(0);
                    entity.setId(500L);
                    return entity;
                });
        // intentRepository 返回空列表 (默认), 意图识别结果为 UNKNOWN
        // AI 回复生成器 (可插拔, 此处 mock 返回模拟回复)
        when(aiResponseGenerator.generate(any(), any(), any(), any()))
                .thenReturn("[AI 模拟回复] 已识别您的意图: UNKNOWN, 情感: NEUTRAL。");

        ScrmAiChatDto dto = new ScrmAiChatDto();
        dto.setCustomerId(1L);
        dto.setMessage("你好");

        // ===== When =====
        ScrmAiConversationEntity result = aiAssistantService.chat(dto);

        // ===== Then =====
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(500L);
        assertThat(result.getCustomerId()).isEqualTo(1L);
        assertThat(result.getUserMessage()).isEqualTo("你好");
        // 意图识别 (无启用意图) → UNKNOWN
        assertThat(result.getDetectedIntent()).isEqualTo("UNKNOWN");
        // AI 回复 (模拟实现) 非空
        assertThat(result.getAiResponse()).isNotBlank().contains("AI 模拟回复");
        // 响应耗时已记录
        assertThat(result.getResponseTimeMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.getCreatedAt()).isNotNull();

        verify(conversationRepository, times(1)).save(any(ScrmAiConversationEntity.class));
    }

    // ============================================================
    // batchChat
    // ============================================================

    @Test
    @DisplayName("batchChat_emptyList: 空列表返回空结果")
    void batchChat_emptyList() {
        assertThat(aiAssistantService.batchChat(Collections.emptyList())).isEmpty();
        assertThat(aiAssistantService.batchChat(null)).isEmpty();

        verify(conversationRepository, never()).save(any());
    }

    @Test
    @DisplayName("batchChat_partialFailure: 部分失败跳过, 成功项返回, save 仅对成功项调用")
    void batchChat_partialFailure() throws ScrmException {
        // ===== Given =====
        when(configRepository.findByIsDefaultTrueAndEnabledTrue())
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(ScrmAiConversationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // AI 回复生成器 mock (合法项调用 generate, 非法项在参数校验阶段即抛异常跳过)
        when(aiResponseGenerator.generate(any(), any(), any(), any()))
                .thenReturn("[AI 模拟回复] 已为您处理。");

        // 3 条: 第 1 / 第 3 条合法, 第 2 条消息为空 → 抛异常跳过
        ScrmAiChatDto valid1 = new ScrmAiChatDto();
        valid1.setCustomerId(1L);
        valid1.setMessage("你好");
        ScrmAiChatDto invalid = new ScrmAiChatDto();
        invalid.setCustomerId(2L);
        invalid.setMessage("");
        ScrmAiChatDto valid2 = new ScrmAiChatDto();
        valid2.setCustomerId(3L);
        valid2.setMessage("谢谢");

        // ===== When =====
        List<ScrmAiConversationEntity> results = aiAssistantService.batchChat(List.of(valid1, invalid, valid2));

        // ===== Then =====
        assertThat(results).hasSize(2);
        assertThat(results).extracting(ScrmAiConversationEntity::getCustomerId)
                .containsExactly(1L, 3L);
        // 仅 2 条成功项触发 save
        verify(conversationRepository, times(2)).save(any(ScrmAiConversationEntity.class));
    }
}
