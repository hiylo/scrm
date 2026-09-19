/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationCenterServiceTest.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmNotificationDto;
import org.hiylo.scrm.dto.ScrmNotificationSendDto;
import org.hiylo.scrm.entity.ScrmNotificationEntity;
import org.hiylo.scrm.entity.ScrmNotificationTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmNotificationBatchRepository;
import org.hiylo.scrm.repository.ScrmNotificationPreferenceRepository;
import org.hiylo.scrm.repository.ScrmNotificationRepository;
import org.hiylo.scrm.repository.ScrmNotificationTemplateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmNotificationCenterService 单元测试
 * <p>
 * 覆盖通知发送 ({@code sendNotification}) 与失败重试 ({@code retryNotification}) 关键流程,
 * 验证 IN_APP 渠道渲染 → 持久化 → deliver → DELIVERED 状态流转及异常分支,
 * 使用 Mockito 隔离 Repository。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmNotificationCenterService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmNotificationCenterServiceTest {

    /** 通知模板数据访问层 Mock */
    @Mock
    private ScrmNotificationTemplateRepository templateRepository;

    /** 通知记录数据访问层 Mock */
    @Mock
    private ScrmNotificationRepository notificationRepository;

    /** 通知偏好数据访问层 Mock */
    @Mock
    private ScrmNotificationPreferenceRepository preferenceRepository;

    /** 通知批次数据访问层 Mock */
    @Mock
    private ScrmNotificationBatchRepository batchRepository;

    /** 推送通道 Mock (PUSH 渠道投递, 详见 ScrmNotificationCenterDeliveryTest) */
    @Mock
    private PushNotificationService pushNotificationService;

    /** 被测对象 (门面, 按拆分后的子域服务组装) */
    private ScrmNotificationCenterService notificationCenterService;

    /**
     * 测试前设置请求上下文, 并按拆分后的依赖图组装门面
     * (模板 / 偏好零依赖, 发送依赖模板+偏好, 查询依赖模板+发送)。
     */
    @BeforeEach
    void setUp() {
        ScrmNotificationCenterTemplateService templateService =
                new ScrmNotificationCenterTemplateService(templateRepository);
        ScrmNotificationCenterPreferenceService preferenceService =
                new ScrmNotificationCenterPreferenceService(preferenceRepository, notificationRepository);
        ScrmNotificationCenterSendService sendService = new ScrmNotificationCenterSendService(
                templateRepository, notificationRepository, batchRepository,
                templateService, preferenceService, pushNotificationService);
        ScrmNotificationCenterQueryService queryService = new ScrmNotificationCenterQueryService(
                notificationRepository, templateService, sendService);
        notificationCenterService = new ScrmNotificationCenterService(
                templateService, sendService, queryService, preferenceService);
    }

    /**
     * 测试后清理请求上下文
     */
    @AfterEach
    void tearDown() {
    }

    /**
     * 构造启用的 IN_APP 渠道模板 (title / content 含 {name} 占位符)
     */
    private ScrmNotificationTemplateEntity buildInAppTemplate() {
        ScrmNotificationTemplateEntity template = new ScrmNotificationTemplateEntity();
        template.setId(10L);
        template.setTemplateName("站内信模板");
        template.setTemplateCode("TPL_IN_APP");
        template.setCategory("SYSTEM");
        template.setChannel("IN_APP");
        template.setTitle("你好{name}");
        template.setContent("欢迎{name}");
        template.setEnabled(true);
        template.setSenderName("系统");
        return template;
    }

    /**
     * 构造通知实体 (已设置账号归属与渠道)
     */
    private ScrmNotificationEntity buildNotification(Long id, String status) {
        ScrmNotificationEntity entity = new ScrmNotificationEntity();
        entity.setId(id);
        entity.setChannel("IN_APP");
        entity.setCategory("SYSTEM");
        entity.setStatus(status);
        entity.setRetryCount(0);
        entity.setMaxRetries(3);
        return entity;
    }

    // ============================================================
    // sendNotification
    // ============================================================

    @Test
    @DisplayName("sendNotification_inApp_success: 模板存在+启用 → 渲染 → 持久化 → deliver → 状态 DELIVERED")
    void sendNotification_inApp_success() throws ScrmException {
        // ===== Given =====
        ScrmNotificationTemplateEntity template = buildInAppTemplate();
        when(templateRepository.findByTemplateCode("TPL_IN_APP"))
                .thenReturn(Optional.of(template));
        // 偏好检查: 无偏好记录默认允许发送
        when(preferenceRepository.findByUserIdAndChannelAndCategory("user-1", "IN_APP", "SYSTEM"))
                .thenReturn(Optional.empty());
        // save 返回带 ID 的实体
        when(notificationRepository.save(any(ScrmNotificationEntity.class)))
                .thenAnswer(invocation -> {
                    ScrmNotificationEntity entity = invocation.getArgument(0);
                    entity.setId(100L);
                    return entity;
                });
        when(templateRepository.incrementUsageCount(10L)).thenReturn(1);

        ScrmNotificationSendDto dto = new ScrmNotificationSendDto();
        dto.setTemplateCode("TPL_IN_APP");
        dto.setRecipients(List.of("user-1"));
        dto.setRecipientType("USER");
        dto.setVariables(Map.of("name", "张三"));

        // ===== When =====
        List<ScrmNotificationDto> result = notificationCenterService.sendNotification(dto);

        // ===== Then =====
        assertThat(result).hasSize(1);
        ScrmNotificationDto dtoResult = result.get(0);
        assertThat(dtoResult.getStatus()).isEqualTo("DELIVERED");
        // 模板渲染: {name} → 张三
        assertThat(dtoResult.getTitle()).isEqualTo("你好张三");
        assertThat(dtoResult.getContent()).isEqualTo("欢迎张三");
        assertThat(dtoResult.getChannel()).isEqualTo("IN_APP");

        // 验证 save 调用 2 次 (持久化 + deliver 后刷新), 最终状态 DELIVERED
        ArgumentCaptor<ScrmNotificationEntity> entityCaptor = ArgumentCaptor.forClass(ScrmNotificationEntity.class);
        verify(notificationRepository, times(2)).save(entityCaptor.capture());
        ScrmNotificationEntity saved = entityCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo("DELIVERED");
        assertThat(saved.getSentAt()).isNotNull();
        assertThat(saved.getDeliveredAt()).isNotNull();
        // 模板使用次数 +1
        verify(templateRepository, times(1)).incrementUsageCount(10L);
    }

    @Test
    @DisplayName("sendNotification_templateNotFound: 模板不存在抛 ScrmException (code=SCRM_NOT_FOUND)")
    void sendNotification_templateNotFound() {
        // ===== Given =====
        when(templateRepository.findByTemplateCode("MISSING"))
                .thenReturn(Optional.empty());
        ScrmNotificationSendDto dto = new ScrmNotificationSendDto();
        dto.setTemplateCode("MISSING");
        dto.setRecipients(List.of("user-1"));

        // ===== When / Then =====
        assertThatThrownBy(() -> notificationCenterService.sendNotification(dto))
                .isInstanceOf(ScrmException.class)
                .hasFieldOrPropertyWithValue("code", "SCRM_NOT_FOUND")
                .hasMessageContaining("通知模板不存在");

        verify(notificationRepository, never()).save(any(ScrmNotificationEntity.class));
        verify(templateRepository, never()).incrementUsageCount(any());
    }

    @Test
    @DisplayName("sendNotification_templateDisabled: 模板禁用抛 ScrmException (code=SCRM_BAD_REQUEST)")
    void sendNotification_templateDisabled() {
        // ===== Given =====
        ScrmNotificationTemplateEntity template = buildInAppTemplate();
        template.setEnabled(false);
        when(templateRepository.findByTemplateCode("TPL_IN_APP"))
                .thenReturn(Optional.of(template));
        ScrmNotificationSendDto dto = new ScrmNotificationSendDto();
        dto.setTemplateCode("TPL_IN_APP");
        dto.setRecipients(List.of("user-1"));

        // ===== When / Then =====
        assertThatThrownBy(() -> notificationCenterService.sendNotification(dto))
                .isInstanceOf(ScrmException.class)
                .hasFieldOrPropertyWithValue("code", "SCRM_BAD_REQUEST")
                .hasMessageContaining("通知模板已禁用");

        verify(notificationRepository, never()).save(any(ScrmNotificationEntity.class));
    }

    @Test
    @DisplayName("sendNotification_emptyRecipients: 接收者列表为空抛 ScrmException (code=SCRM_BAD_REQUEST)")
    void sendNotification_emptyRecipients() {
        // ===== Given =====
        ScrmNotificationSendDto dto = new ScrmNotificationSendDto();
        dto.setTemplateCode("TPL_IN_APP");
        dto.setRecipients(Collections.emptyList());

        // ===== When / Then =====
        assertThatThrownBy(() -> notificationCenterService.sendNotification(dto))
                .isInstanceOf(ScrmException.class)
                .hasFieldOrPropertyWithValue("code", "SCRM_BAD_REQUEST")
                .hasMessageContaining("接收者列表不能为空");

        // 参数校验在模板查询之前, 不应访问 Repository
        verify(templateRepository, never()).findByTemplateCode(any());
        verify(notificationRepository, never()).save(any(ScrmNotificationEntity.class));
    }

    // ============================================================
    // retryNotification
    // ============================================================

    @Test
    @DisplayName("retryNotification_nonFailedStatus: 非 FAILED 状态抛 ScrmException (code=SCRM_BAD_REQUEST)")
    void retryNotification_nonFailedStatus() {
        // ===== Given =====
        ScrmNotificationEntity entity = buildNotification(200L, "SENT");
        when(notificationRepository.findById(200L)).thenReturn(Optional.of(entity));

        // ===== When / Then =====
        assertThatThrownBy(() -> notificationCenterService.retryNotification(200L))
                .isInstanceOf(ScrmException.class)
                .hasFieldOrPropertyWithValue("code", "SCRM_BAD_REQUEST")
                .hasMessageContaining("仅失败状态的通知可重试");

        verify(notificationRepository, never()).save(any(ScrmNotificationEntity.class));
    }

    @Test
    @DisplayName("retryNotification_failedStatus: FAILED 重试后状态置 DELIVERED, retryCount +1")
    void retryNotification_failedStatus() throws ScrmException {
        // ===== Given =====
        ScrmNotificationEntity entity = buildNotification(201L, "FAILED");
        when(notificationRepository.findById(201L)).thenReturn(Optional.of(entity));
        when(notificationRepository.save(any(ScrmNotificationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // ===== When =====
        ScrmNotificationDto result = notificationCenterService.retryNotification(201L);

        // ===== Then =====
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("DELIVERED");
        assertThat(result.getRetryCount()).isEqualTo(1);

        // 验证 save 调用 2 次 (置 PENDING + deliver 后刷新), 最终状态 DELIVERED, 错误信息清空
        ArgumentCaptor<ScrmNotificationEntity> entityCaptor = ArgumentCaptor.forClass(ScrmNotificationEntity.class);
        verify(notificationRepository, times(2)).save(entityCaptor.capture());
        ScrmNotificationEntity saved = entityCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo("DELIVERED");
        assertThat(saved.getRetryCount()).isEqualTo(1);
        assertThat(saved.getErrorMessage()).isNull();
        assertThat(saved.getDeliveredAt()).isNotNull();
    }
}
