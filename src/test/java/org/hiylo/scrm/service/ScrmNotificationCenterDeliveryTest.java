/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationCenterDeliveryTest.java
 * Date : 2026/09/19 21:20:19
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmBatchSendDto;
import org.hiylo.scrm.dto.ScrmNotificationBatchDto;
import org.hiylo.scrm.dto.ScrmNotificationDto;
import org.hiylo.scrm.dto.ScrmNotificationSendDto;
import org.hiylo.scrm.entity.ScrmNotificationBatchEntity;
import org.hiylo.scrm.entity.ScrmNotificationEntity;
import org.hiylo.scrm.entity.ScrmNotificationTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmNotificationBatchRepository;
import org.hiylo.scrm.repository.ScrmNotificationPreferenceRepository;
import org.hiylo.scrm.repository.ScrmNotificationRepository;
import org.hiylo.scrm.repository.ScrmNotificationTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmNotificationCenterService} 投递链路单元测试。
 * <p>
 * 覆盖按渠道分派的真实语义:
 * </p>
 * <ul>
 *   <li>IN_APP 持久化即送达 (DELIVERED), 不触碰任何外部通道</li>
 *   <li>PUSH 复用既有 {@link PushNotificationService} 通道下发到用户设备, 状态保持 SENT
 *       (推送无设备端回执, 不冒充 DELIVERED); 个推未启用时注入
 *       {@link NoOpPushNotificationService} 也不报错</li>
 *   <li>EMAIL / SMS 仓库内无网关实现: 明确置 FAILED 并在 errorMessage 标明通道未接入,
 *       不伪造发送成功; 批量发送的成功 / 失败计数按最终状态统计</li>
 *   <li>未知渠道同样失败, 避免拼错的渠道静默通过</li>
 * </ul>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmNotificationCenterService 渠道投递单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmNotificationCenterDeliveryTest {

    @Mock
    private ScrmNotificationTemplateRepository templateRepository;
    @Mock
    private ScrmNotificationRepository notificationRepository;
    @Mock
    private ScrmNotificationPreferenceRepository preferenceRepository;
    @Mock
    private ScrmNotificationBatchRepository batchRepository;
    @Mock
    private PushNotificationService pushNotificationService;

    /** 被测服务 (门面, 按拆分后的子域服务组装) */
    private ScrmNotificationCenterService service;

    /**
     * 按拆分后的依赖图组装门面 (模板 / 偏好零依赖, 发送依赖模板+偏好, 查询依赖模板+发送)。
     */
    @BeforeEach
    void setUp() {
        service = buildFacade(pushNotificationService);
    }

    /**
     * 组装通知中心门面: 构造子域服务并注入到门面。
     *
     * @param pushService 推送通道实现 (真实 / NoOp)
     * @return 组装完成的门面
     */
    private ScrmNotificationCenterService buildFacade(PushNotificationService pushService) {
        ScrmNotificationCenterTemplateService templateService =
                new ScrmNotificationCenterTemplateService(templateRepository);
        ScrmNotificationCenterPreferenceService preferenceService =
                new ScrmNotificationCenterPreferenceService(preferenceRepository, notificationRepository);
        ScrmNotificationCenterSendService sendService = new ScrmNotificationCenterSendService(
                templateRepository, notificationRepository, batchRepository,
                templateService, preferenceService, pushService);
        ScrmNotificationCenterQueryService queryService = new ScrmNotificationCenterQueryService(
                notificationRepository, templateService, sendService);
        return new ScrmNotificationCenterService(templateService, sendService, queryService, preferenceService);
    }

    /**
     * 构造指定渠道的启用模板 (标题 / 正文含 {name} 占位符)
     */
    private ScrmNotificationTemplateEntity template(String code, String channel) {
        ScrmNotificationTemplateEntity template = new ScrmNotificationTemplateEntity();
        template.setId(10L);
        template.setTemplateName("模板");
        template.setTemplateCode(code);
        template.setCategory("SYSTEM");
        template.setChannel(channel);
        template.setTitle("你好{name}");
        template.setContent("欢迎{name}");
        template.setEnabled(true);
        template.setSenderName("系统");
        return template;
    }

    /**
     * 让 notificationRepository.save 回填 ID 并返回入参
     */
    private void stubNotificationSave() {
        when(notificationRepository.save(any(ScrmNotificationEntity.class)))
                .thenAnswer(invocation -> {
                    ScrmNotificationEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(100L);
                    }
                    return entity;
                });
    }

    /**
     * 构造发送参数
     */
    private ScrmNotificationSendDto sendDto(String templateCode, String... recipients) {
        ScrmNotificationSendDto dto = new ScrmNotificationSendDto();
        dto.setTemplateCode(templateCode);
        dto.setRecipients(List.of(recipients));
        dto.setRecipientType("USER");
        dto.setVariables(Map.of("name", "张三"));
        dto.setRelatedType("WORKFLOW_INSTANCE");
        dto.setRelatedId("900");
        return dto;
    }

    /**
     * 断言推送通道收到的载荷 (接收者 / 标题 / 正文 / 类型 / 附加数据)
     */
    private void verifyPushCalledFor(String userId) {
        ArgumentMatcher<Map<String, Object>> extraMatcher = extra -> extra != null
                && "PUSH".equals(extra.get("channel"))
                && "SYSTEM".equals(extra.get("category"))
                && "WORKFLOW_INSTANCE".equals(extra.get("relatedType"))
                && "900".equals(extra.get("relatedId"))
                && Long.valueOf(100L).equals(extra.get("notificationId"));
        verify(pushNotificationService).pushToUser(eq(userId), eq("你好张三"), eq("欢迎张三"),
                eq("SYSTEM"), ArgumentMatchers.argThat(extraMatcher));
    }

    // ============================================================
    // PUSH 渠道: 复用既有推送通道
    // ============================================================

    @Test
    @DisplayName("sendNotification PUSH: 经 PushNotificationService 下发, 状态 SENT 而非 DELIVERED")
    void sendNotification_pushChannelsGoesThroughPushService() throws ScrmException {
        when(templateRepository.findByTemplateCode("TPL_PUSH"))
                .thenReturn(Optional.of(template("TPL_PUSH", "PUSH")));
        stubNotificationSave();

        List<ScrmNotificationDto> result = service.sendNotification(sendDto("TPL_PUSH", "user-1"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("SENT");
        assertThat(result.get(0).getSentAt()).isNotNull();
        // 推送无设备端送达回执, 不得冒充 DELIVERED
        assertThat(result.get(0).getDeliveredAt()).isNull();
        verifyPushCalledFor("user-1");
    }

    @Test
    @DisplayName("sendNotification PUSH: 每个接收者各下发一次推送")
    void sendNotification_pushPerRecipient() throws ScrmException {
        when(templateRepository.findByTemplateCode("TPL_PUSH"))
                .thenReturn(Optional.of(template("TPL_PUSH", "PUSH")));
        stubNotificationSave();

        service.sendNotification(sendDto("TPL_PUSH", "user-1", "user-2"));

        verify(pushNotificationService).pushToUser(eq("user-1"), any(), any(), any(), any());
        verify(pushNotificationService).pushToUser(eq("user-2"), any(), any(), any(), any());
        verify(templateRepository).incrementUsageCount(10L);
    }

    @Test
    @DisplayName("sendNotification PUSH: 个推未启用 (NoOp 实现) 时不报错, 记录仍为 SENT")
    void sendNotification_pushWithNoOpImplementationDoesNotFail() throws ScrmException {
        ScrmNotificationCenterService noOpService = buildFacade(new NoOpPushNotificationService());
        when(templateRepository.findByTemplateCode("TPL_PUSH"))
                .thenReturn(Optional.of(template("TPL_PUSH", "PUSH")));
        stubNotificationSave();

        List<ScrmNotificationDto> result = noOpService.sendNotification(sendDto("TPL_PUSH", "user-1"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("SENT");
    }

    @Test
    @DisplayName("sendNotification PUSH: 推送通道抛异常时该记录置 FAILED, 不影响其他接收者")
    void sendNotification_pushFailureMarksFailed() throws ScrmException {
        when(templateRepository.findByTemplateCode("TPL_PUSH"))
                .thenReturn(Optional.of(template("TPL_PUSH", "PUSH")));
        stubNotificationSave();
        doThrow(new IllegalStateException("个推鉴权失败")).when(pushNotificationService)
                .pushToUser(eq("user-1"), any(), any(), any(), any());

        List<ScrmNotificationDto> result = service.sendNotification(sendDto("TPL_PUSH", "user-1", "user-2"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStatus()).isEqualTo("FAILED");
        assertThat(result.get(0).getErrorMessage()).contains("个推鉴权失败");
        assertThat(result.get(1).getStatus()).isEqualTo("SENT");
    }

    // ============================================================
    // EMAIL / SMS: 无网关, 显式未接入
    // ============================================================

    @Test
    @DisplayName("sendNotification EMAIL: 不伪造发送成功, 记录置 FAILED 并说明通道未接入")
    void sendNotification_emailChannelIsNotIntegrated() throws ScrmException {
        when(templateRepository.findByTemplateCode("TPL_EMAIL"))
                .thenReturn(Optional.of(template("TPL_EMAIL", "EMAIL")));
        stubNotificationSave();

        List<ScrmNotificationDto> result = service.sendNotification(sendDto("TPL_EMAIL", "user-1"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("FAILED");
        assertThat(result.get(0).getErrorMessage()).contains("未接入").contains("EMAIL");
        // 站内信记录照常持久化, 且不会误走推送通道
        verify(notificationRepository, times(2)).save(any(ScrmNotificationEntity.class));
        verifyNoInteractions(pushNotificationService);
    }

    @Test
    @DisplayName("sendNotification SMS: 同样显式未接入")
    void sendNotification_smsChannelIsNotIntegrated() throws ScrmException {
        when(templateRepository.findByTemplateCode("TPL_SMS"))
                .thenReturn(Optional.of(template("TPL_SMS", "SMS")));
        stubNotificationSave();

        List<ScrmNotificationDto> result = service.sendNotification(sendDto("TPL_SMS", "user-1"));

        assertThat(result.get(0).getStatus()).isEqualTo("FAILED");
        assertThat(result.get(0).getErrorMessage()).contains("未接入");
        verifyNoInteractions(pushNotificationService);
    }

    @Test
    @DisplayName("sendNotification 未知渠道: 置 FAILED, 不因渠道名拼错而静默放行")
    void sendNotification_unknownChannelFails() throws ScrmException {
        when(templateRepository.findByTemplateCode("TPL_X"))
                .thenReturn(Optional.of(template("TPL_X", "WECHAT")));
        stubNotificationSave();

        List<ScrmNotificationDto> result = service.sendNotification(sendDto("TPL_X", "user-1"));

        assertThat(result.get(0).getStatus()).isEqualTo("FAILED");
        assertThat(result.get(0).getErrorMessage()).contains("未知的通知渠道");
    }

    @Test
    @DisplayName("retryNotification EMAIL: 重试后仍为 FAILED 并保留未接入原因 (重试不伪造成功)")
    void retryNotification_emailStillNotIntegrated() throws ScrmException {
        ScrmNotificationEntity entity = new ScrmNotificationEntity();
        entity.setId(201L);
        entity.setChannel("EMAIL");
        entity.setCategory("SYSTEM");
        entity.setTitle("你好");
        entity.setContent("欢迎");
        entity.setStatus("FAILED");
        entity.setRetryCount(0);
        entity.setMaxRetries(3);
        when(notificationRepository.findById(201L)).thenReturn(Optional.of(entity));
        stubNotificationSave();

        ScrmNotificationDto result = service.retryNotification(201L);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(result.getErrorMessage()).contains("未接入");
        verifyNoInteractions(pushNotificationService);
    }

    // ============================================================
    // IN_APP: 持久化即送达, 不触碰外部通道
    // ============================================================

    @Test
    @DisplayName("sendNotification IN_APP: 站内信持久化即 DELIVERED, 不调用推送通道")
    void sendNotification_inAppStaysInternal() throws ScrmException {
        when(templateRepository.findByTemplateCode("TPL_IN_APP"))
                .thenReturn(Optional.of(template("TPL_IN_APP", "IN_APP")));
        stubNotificationSave();

        List<ScrmNotificationDto> result = service.sendNotification(sendDto("TPL_IN_APP", "user-1"));

        assertThat(result.get(0).getStatus()).isEqualTo("DELIVERED");
        assertThat(result.get(0).getDeliveredAt()).isNotNull();
        assertThat(result.get(0).getTitle()).isEqualTo("你好张三");
        verifyNoInteractions(pushNotificationService);
    }

    // ============================================================
    // 批量发送计数
    // ============================================================

    /**
     * 让 batchRepository.save 回填 ID 并返回入参
     */
    private void stubBatchSave() {
        when(batchRepository.save(any(ScrmNotificationBatchEntity.class)))
                .thenAnswer(invocation -> {
                    ScrmNotificationBatchEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(300L);
                    }
                    return entity;
                });
    }

    /**
     * 构造批量发送参数
     */
    private ScrmBatchSendDto batchDto(String templateCode, String... recipientIds) {
        ScrmBatchSendDto dto = new ScrmBatchSendDto();
        dto.setBatchName("批次");
        dto.setTemplateCode(templateCode);
        dto.setRecipientIds(List.of(recipientIds));
        dto.setVariables(Map.of("name", "张三"));
        return dto;
    }

    @Test
    @DisplayName("sendBatch IN_APP: 全部送达时 successCount 正确, 批次 COMPLETED")
    void sendBatch_inAppCountsSuccess() throws ScrmException {
        when(templateRepository.findByTemplateCode("TPL_IN_APP"))
                .thenReturn(Optional.of(template("TPL_IN_APP", "IN_APP")));
        stubBatchSave();
        stubNotificationSave();

        ScrmNotificationBatchDto batch = service.sendBatch(batchDto("TPL_IN_APP", "user-1", "user-2"));

        assertThat(batch.getTotalCount()).isEqualTo(2);
        assertThat(batch.getSuccessCount()).isEqualTo(2);
        assertThat(batch.getFailedCount()).isZero();
        assertThat(batch.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("sendBatch EMAIL: 未接入渠道不得计入成功, 批次按最终状态判 FAILED")
    void sendBatch_notIntegratedChannelIsNotCountedAsSuccess() throws ScrmException {
        when(templateRepository.findByTemplateCode("TPL_EMAIL"))
                .thenReturn(Optional.of(template("TPL_EMAIL", "EMAIL")));
        stubBatchSave();
        stubNotificationSave();

        ScrmNotificationBatchDto batch = service.sendBatch(batchDto("TPL_EMAIL", "user-1", "user-2"));

        assertThat(batch.getSuccessCount()).isZero();
        assertThat(batch.getFailedCount()).isEqualTo(2);
        assertThat(batch.getStatus()).isEqualTo("FAILED");
        // 全部失败时不刷新模板使用次数
        verify(templateRepository, never()).incrementUsageCount(any());
    }

    @Test
    @DisplayName("sendBatch PUSH: 逐接收者下发一次推送并计入成功")
    void sendBatch_pushCallsChannelPerRecipient() throws ScrmException {
        when(templateRepository.findByTemplateCode("TPL_PUSH"))
                .thenReturn(Optional.of(template("TPL_PUSH", "PUSH")));
        stubBatchSave();
        stubNotificationSave();

        ScrmNotificationBatchDto batch = service.sendBatch(batchDto("TPL_PUSH", "user-1", "user-2"));

        verify(pushNotificationService, times(2)).pushToUser(any(), any(), any(), any(), any());
        assertThat(batch.getSuccessCount()).isEqualTo(2);
        assertThat(batch.getStatus()).isEqualTo("COMPLETED");
    }
}
