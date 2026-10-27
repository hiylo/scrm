/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFinanceHotLeadPushServiceTest.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.config.ScrmFinanceLeadPushProperties;
import org.hiylo.scrm.dto.ScrmNotificationSendDto;
import org.hiylo.scrm.entity.ScrmAiConversationEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmLeadScoreEntity;
import org.hiylo.scrm.entity.ScrmLeadScoringModelEntity;
import org.hiylo.scrm.repository.ScrmAiConversationRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 财税模块⑤ 高意向线索推送服务单元测试。
 * <p>
 * 聚焦「grade=SUPER_HOT/HOT 客户 → NotificationCenter 推送销售」核心逻辑:
 * 等级命中推送、非高意向不推送、非财税模型不推送、已推送幂等去重,
 * 以及推送载荷携带意向理由 + 对话摘要 + 联系方式。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmFinanceHotLeadPushService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmFinanceHotLeadPushServiceTest {

    /** JSON 解析器 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 通知中心门面 Mock (验证推送调用) */
    @Mock
    private ScrmNotificationCenterService notificationCenterService;

    /** 财税评分模型管理服务 Mock (按编码解析模型 ID) */
    @Mock
    private ScrmLeadScoringModelService modelService;

    /** AI 对话记录仓库 Mock (对话摘要) */
    @Mock
    private ScrmAiConversationRepository conversationRepository;

    /** 客户仓库 Mock (联系方式) */
    @Mock
    private ScrmCustomerRepository customerRepository;

    /** 通知记录仓库 Mock (幂等去重) */
    @Mock
    private ScrmNotificationRepository notificationRepository;

    /** 被测服务实例 */
    private ScrmFinanceHotLeadPushService service;

    /** 测试配置: 默认推送配置 (模板 lead-hot-push / 接收人 admin / 模型 FIN_LEAD_SCORE_V1) */
    private ScrmFinanceLeadPushProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ScrmFinanceLeadPushProperties();
        service = new ScrmFinanceHotLeadPushService(notificationCenterService, properties, modelService,
                conversationRepository, customerRepository, notificationRepository, objectMapper);
    }

    /**
     * 构造财税评分模型实体 (modelId=92001, code=FIN_LEAD_SCORE_V1)。
     */
    private ScrmLeadScoringModelEntity buildFinanceModel() {
        ScrmLeadScoringModelEntity entity = new ScrmLeadScoringModelEntity();
        entity.setId(92001L);
        entity.setModelCode("FIN_LEAD_SCORE_V1");
        entity.setModelName("财税线索意向分级模型");
        return entity;
    }

    /**
     * 构造评分记录实体。
     *
     * @param id      评分记录 ID
     * @param modelId 模型 ID
     * @param grade   等级
     */
    private ScrmLeadScoreEntity buildScore(Long id, Long modelId, String grade) {
        ScrmLeadScoreEntity entity = new ScrmLeadScoreEntity();
        entity.setId(id);
        entity.setCustomerId(100L);
        entity.setCustomerName("杭州某某贸易有限公司");
        entity.setModelId(modelId);
        entity.setTotalScore(88.0);
        entity.setScorePercent(88.0);
        entity.setGrade(grade);
        entity.setGradeLabel(grade);
        entity.setDimensionScores(
                "[{\"dimension\":\"price_inquiry\",\"score\":40,\"maxScore\":40,"
                        + "\"details\":\"精准问价：明确到具体服务\"},"
                        + "{\"dimension\":\"company_info_given\",\"score\":30,\"maxScore\":30,"
                        + "\"details\":\"完整留资\"}]");
        return entity;
    }

    @Test
    @DisplayName("pushIfHot: HOT 等级 + 财税模型 → NotificationCenter 推送, 载荷含意向理由/摘要/联系方式")
    void pushIfHot_hotGrade_pushWithReason() throws Exception {
        ScrmLeadScoreEntity score = buildScore(5001L, 92001L, "HOT");
        when(modelService.getModelByCode("FIN_LEAD_SCORE_V1")).thenReturn(buildFinanceModel());
        when(notificationRepository.existsByRelatedTypeAndRelatedIdAndTemplateCode(
                "LEAD_SCORE", "5001", "lead-hot-push")).thenReturn(false);
        ScrmAiConversationEntity conv = new ScrmAiConversationEntity();
        conv.setUserMessage("代理记账多少钱一个月？我们是小规模纳税人，在杭州。");
        when(conversationRepository.findFirstByCustomerIdOrderByCreatedAtDesc(100L))
                .thenReturn(Optional.of(conv));
        ScrmCustomerEntity customer = new ScrmCustomerEntity();
        customer.setId(100L);
        customer.setRemark("李经理 138****0000");
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));

        service.pushIfHot(score);

        ArgumentCaptor<ScrmNotificationSendDto> captor =
                ArgumentCaptor.forClass(ScrmNotificationSendDto.class);
        verify(notificationCenterService, times(1)).sendNotification(captor.capture());
        ScrmNotificationSendDto dto = captor.getValue();
        assertThat(dto.getTemplateCode()).isEqualTo("lead-hot-push");
        assertThat(dto.getRecipients()).containsExactly("admin");
        assertThat(dto.getRelatedType()).isEqualTo("LEAD_SCORE");
        assertThat(dto.getRelatedId()).isEqualTo("5001");
        assertThat(dto.getVariables().get("grade")).isEqualTo("HOT");
        assertThat(dto.getVariables().get("customerName")).isEqualTo("杭州某某贸易有限公司");
        // 意向理由来自 dimensionScores 明细
        assertThat(dto.getVariables().get("reason")).contains("price_inquiry 40/40")
                .contains("company_info_given 30/30");
        // 对话摘要 + 联系方式
        assertThat(dto.getVariables().get("conversationSummary")).contains("代理记账多少钱一个月");
        assertThat(dto.getVariables().get("contact")).contains("138");
    }

    @Test
    @DisplayName("pushIfHot: SUPER_HOT 等级同样推送 (含 A_PLUS)")
    void pushIfHot_superHot_push() {
        ScrmLeadScoreEntity score = buildScore(5002L, 92001L, "SUPER_HOT");
        when(modelService.getModelByCode("FIN_LEAD_SCORE_V1")).thenReturn(buildFinanceModel());
        when(notificationRepository.existsByRelatedTypeAndRelatedIdAndTemplateCode(
                "LEAD_SCORE", "5002", "lead-hot-push")).thenReturn(false);

        service.pushIfHot(score);

        verify(notificationCenterService, times(1)).sendNotification(any(ScrmNotificationSendDto.class));
    }

    @Test
    @DisplayName("pushIfHot: COLD 等级不推送")
    void pushIfHot_coldGrade_skip() {
        ScrmLeadScoreEntity score = buildScore(5003L, 92001L, "COLD");

        service.pushIfHot(score);

        verify(notificationCenterService, never()).sendNotification(any(ScrmNotificationSendDto.class));
    }

    @Test
    @DisplayName("pushIfHot: 非财税模型的热线索不推送 (不干扰其他评分模型)")
    void pushIfHot_nonFinanceModel_skip() {
        ScrmLeadScoreEntity score = buildScore(5004L, 10L, "HOT");
        when(modelService.getModelByCode("FIN_LEAD_SCORE_V1")).thenReturn(buildFinanceModel());

        service.pushIfHot(score);

        verify(notificationCenterService, never()).sendNotification(any(ScrmNotificationSendDto.class));
        verify(notificationRepository, never())
                .existsByRelatedTypeAndRelatedIdAndTemplateCode(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("pushIfHot: 已推送过的评分记录幂等去重, 不重复推送")
    void pushIfHot_alreadyPushed_skip() {
        ScrmLeadScoreEntity score = buildScore(5005L, 92001L, "HOT");
        when(modelService.getModelByCode("FIN_LEAD_SCORE_V1")).thenReturn(buildFinanceModel());
        when(notificationRepository.existsByRelatedTypeAndRelatedIdAndTemplateCode(
                "LEAD_SCORE", "5005", "lead-hot-push")).thenReturn(true);

        service.pushIfHot(score);

        verify(notificationCenterService, never()).sendNotification(any(ScrmNotificationSendDto.class));
    }

    @Test
    @DisplayName("pushIfHot: 推送失败只记日志不抛出, 不阻断评分主流程")
    void pushIfHot_pushFailure_silent() throws Exception {
        ScrmLeadScoreEntity score = buildScore(5006L, 92001L, "HOT");
        when(modelService.getModelByCode("FIN_LEAD_SCORE_V1")).thenReturn(buildFinanceModel());
        when(notificationRepository.existsByRelatedTypeAndRelatedIdAndTemplateCode(
                "LEAD_SCORE", "5006", "lead-hot-push")).thenReturn(false);
        when(notificationCenterService.sendNotification(any(ScrmNotificationSendDto.class)))
                .thenThrow(new IllegalStateException("模拟推送失败"));

        // 不抛异常
        service.pushIfHot(score);
    }

    @Test
    @DisplayName("pushIfHot: 总开关关闭时仅记录不发送")
    void pushIfHot_disabled_skip() {
        properties.setEnabled(false);
        ScrmLeadScoreEntity score = buildScore(5007L, 92001L, "HOT");

        service.pushIfHot(score);

        verify(notificationCenterService, never()).sendNotification(any(ScrmNotificationSendDto.class));
    }
}
