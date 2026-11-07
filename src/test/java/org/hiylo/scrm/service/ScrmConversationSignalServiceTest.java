/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmConversationSignalServiceTest.java
 * Date : 2026/10/24 10:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.entity.ScrmConversationEntity;
import org.hiylo.scrm.entity.ScrmConversationMessageEntity;
import org.hiylo.scrm.repository.ScrmConversationMessageRepository;
import org.hiylo.scrm.repository.ScrmConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmConversationSignalService} 单元测试。
 * <p>
 * 锁定评分 4 个会话维度键的提取语义：
 * 问价次数分档、企业信息维度计分、紧迫度次数分档、对话深度为有效客户消息数；
 * 无会话/无客户消息/异常一律降级为 0（绝不编造，也不阻断评分）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("会话意向信号提取")
class ScrmConversationSignalServiceTest {

    /** 客户 ID */
    private static final long CUSTOMER_ID = 5001L;

    /** 会话 ID */
    private static final long CONVERSATION_ID = 7001L;

    @Mock
    private ScrmConversationRepository conversationRepository;

    @Mock
    private ScrmConversationMessageRepository messageRepository;

    private ScrmConversationSignalService service;

    @BeforeEach
    void setUp() {
        service = new ScrmConversationSignalService(conversationRepository, messageRepository);
    }

    @Test
    @DisplayName("无会话：全部为 0")
    void noConversation_returnsZeros() {
        when(conversationRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of());

        Map<String, Object> signals = service.extractSignals(CUSTOMER_ID);

        assertThat(signals).containsEntry("priceInquiry", 0)
                .containsEntry("companyInfoGiven", 0)
                .containsEntry("urgencyLevel", 0)
                .containsEntry("conversationDepth", 0);
    }

    @Test
    @DisplayName("入参为空：全部为 0，不查库")
    void nullCustomerId_returnsZeros() {
        Map<String, Object> signals = service.extractSignals(null);

        assertThat(signals).containsEntry("priceInquiry", 0)
                .containsEntry("conversationDepth", 0);
    }

    @Test
    @DisplayName("只有坐席消息（OUT）：客户消息为 0，深度为 0")
    void onlyOutboundMessages_depthZero() {
        stubConversation();
        when(messageRepository.findByConversationIdOrderBySentAtDesc(CONVERSATION_ID))
                .thenReturn(List.of(message("OUT", "您好，请问有什么可以帮您")));

        Map<String, Object> signals = service.extractSignals(CUSTOMER_ID);

        assertThat(signals).containsEntry("conversationDepth", 0)
                .containsEntry("priceInquiry", 0);
    }

    @Test
    @DisplayName("问价 1/2/3 次分别记为 1/2/3 档")
    void priceInquiryLevels() {
        stubConversation();
        when(messageRepository.findByConversationIdOrderBySentAtDesc(CONVERSATION_ID))
                .thenReturn(List.of(
                        message("IN", "你们代账多少钱"),
                        message("IN", "小规模怎么收费"),
                        message("IN", "有没有优惠")));

        assertThat(service.extractSignals(CUSTOMER_ID)).containsEntry("priceInquiry", 3);
    }

    @Test
    @DisplayName("企业信息：仅提交主体 → 2 分；主体 + 纳税人身份 → 3 分")
    void companyInfoLevels() {
        stubConversation();
        when(messageRepository.findByConversationIdOrderBySentAtDesc(CONVERSATION_ID))
                .thenReturn(List.of(message("IN", "我们公司刚注册，营业执照下来了")));
        assertThat(service.extractSignals(CUSTOMER_ID)).containsEntry("companyInfoGiven", 2);

        when(messageRepository.findByConversationIdOrderBySentAtDesc(CONVERSATION_ID))
                .thenReturn(List.of(
                        message("IN", "我们公司刚注册，营业执照下来了"),
                        message("IN", "我们是一般纳税人，需要开票")));
        assertThat(service.extractSignals(CUSTOMER_ID)).containsEntry("companyInfoGiven", 3);
    }

    @Test
    @DisplayName("紧迫度：命中次数分档，最多 3")
    void urgencyLevels() {
        stubConversation();
        when(messageRepository.findByConversationIdOrderBySentAtDesc(CONVERSATION_ID))
                .thenReturn(List.of(
                        message("IN", "这个很着急"),
                        message("IN", "月底前必须办完"),
                        message("IN", "已经逾期了会罚款吗"),
                        message("IN", "尽快回复我")));

        assertThat(service.extractSignals(CUSTOMER_ID)).containsEntry("urgencyLevel", 3);
    }

    @Test
    @DisplayName("对话深度 = 有效客户消息条数（空内容不计）")
    void conversationDepthCountsInboundOnly() {
        stubConversation();
        when(messageRepository.findByConversationIdOrderBySentAtDesc(CONVERSATION_ID))
                .thenReturn(List.of(
                        message("IN", "你好"),
                        message("OUT", "您好"),
                        message("IN", "想了解一下代账"),
                        message("IN", "   "),
                        message("IN", "谢谢")));

        assertThat(service.extractSignals(CUSTOMER_ID)).containsEntry("conversationDepth", 3);
    }

    @Test
    @DisplayName("真实财税对话：刷脸/添加方式场景能识别紧迫与企业信息信号")
    void realScenario_signals() {
        stubConversation();
        when(messageRepository.findByConversationIdOrderBySentAtDesc(CONVERSATION_ID))
                .thenReturn(List.of(
                        message("IN", "应该要在我手机上刷脸"),
                        message("IN", "你看看你的添加方式"),
                        message("IN", "我们这个个体户急着办，越快越好"),
                        message("IN", "费用多少")));

        Map<String, Object> signals = service.extractSignals(CUSTOMER_ID);

        assertThat((Integer) signals.get("conversationDepth")).isEqualTo(4);
        assertThat((Integer) signals.get("companyInfoGiven")).isGreaterThanOrEqualTo(2);
        assertThat((Integer) signals.get("urgencyLevel")).isGreaterThanOrEqualTo(1);
        assertThat((Integer) signals.get("priceInquiry")).isEqualTo(1);
    }

    @Test
    @DisplayName("仓储异常：降级为全 0，不抛异常")
    void repositoryError_degradesToZeros() {
        when(conversationRepository.findByCustomerId(anyLong()))
                .thenThrow(new RuntimeException("db down"));

        Map<String, Object> signals = service.extractSignals(CUSTOMER_ID);

        assertThat(signals).containsEntry("priceInquiry", 0)
                .containsEntry("companyInfoGiven", 0)
                .containsEntry("urgencyLevel", 0)
                .containsEntry("conversationDepth", 0);
    }

    // ==================== 测试夹具 ====================

    /**
     * 桩：客户有一个会话。
     */
    private void stubConversation() {
        ScrmConversationEntity conversation = new ScrmConversationEntity();
        conversation.setId(CONVERSATION_ID);
        conversation.setCustomerId(CUSTOMER_ID);
        when(conversationRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of(conversation));
    }

    /**
     * 构造一条会话消息。
     *
     * @param direction 方向（IN 客户 / OUT 坐席）
     * @param content   内容
     * @return 消息实体
     */
    private ScrmConversationMessageEntity message(String direction, String content) {
        ScrmConversationMessageEntity entity = new ScrmConversationMessageEntity();
        entity.setConversationId(CONVERSATION_ID);
        entity.setDirection(direction);
        entity.setContent(content);
        return entity;
    }
}
