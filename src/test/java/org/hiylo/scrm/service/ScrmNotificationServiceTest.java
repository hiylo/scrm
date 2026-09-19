/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.websocket.ScrmNotification;
import org.hiylo.scrm.websocket.ScrmNotificationWebSocketHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * ScrmNotificationService 单元测试
 * <p>
 * 聚焦各类通知的构建 (type / level / title / content / data / accountId)、
 * 状态/风险等级/健康度到通知级别的映射、看板统计与跟进提醒的显式账号传递、
 * 新消息内容截断 (50 字符) 与异步发送异常容错等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmNotificationService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmNotificationServiceTest {

    /** WebSocket 通知推送处理器 Mock */
    @Mock
    private ScrmNotificationWebSocketHandler webSocketHandler;

    /** 被测服务实例 */
    private ScrmNotificationService service;

    @BeforeEach
    void setUp() {
        service = new ScrmNotificationService(webSocketHandler);
    }

    @AfterEach
    void tearDown() {
    }

    // ==================== 任务状态通知 ====================

    @Test
    @DisplayName("notifyTaskStatus: SUCCESS 映射 INFO 并写入 campaignId / status / detail")
    void notifyTaskStatus_success() {
        service.notifyTaskStatus(100L, "SUCCESS", "任务完成");

        ArgumentCaptor<ScrmNotification> captor = ArgumentCaptor.forClass(ScrmNotification.class);
        verify(webSocketHandler, times(1)).sendNotification(captor.capture());
        ScrmNotification n = captor.getValue();
        assertThat(n.getType()).isEqualTo("TASK_STATUS");
        assertThat(n.getLevel()).isEqualTo("INFO");
        assertThat(n.getTitle()).isEqualTo("任务状态更新");
        assertThat(n.getContent()).contains("100").contains("SUCCESS").contains("任务完成");
        assertThat(n.getTimestamp()).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) n.getData();
        assertThat(data).containsEntry("campaignId", 100L)
                .containsEntry("status", "SUCCESS")
                .containsEntry("detail", "任务完成");
    }

    @Test
    @DisplayName("notifyTaskStatus: FAILED 映射 ERROR")
    void notifyTaskStatus_failed() {
        service.notifyTaskStatus(101L, "FAILED", null);

        ArgumentCaptor<ScrmNotification> captor = ArgumentCaptor.forClass(ScrmNotification.class);
        verify(webSocketHandler, times(1)).sendNotification(captor.capture());
        assertThat(captor.getValue().getLevel()).isEqualTo("ERROR");
        // detail 为 null 时正文不含 ":"
        assertThat(captor.getValue().getContent()).doesNotContain(":");
    }

    @Test
    @DisplayName("notifyTaskStatus: RUNNING 映射 INFO")
    void notifyTaskStatus_running() {
        service.notifyTaskStatus(102L, "RUNNING", "执行中");

        ArgumentCaptor<ScrmNotification> captor = ArgumentCaptor.forClass(ScrmNotification.class);
        verify(webSocketHandler, times(1)).sendNotification(captor.capture());
        assertThat(captor.getValue().getLevel()).isEqualTo("INFO");
    }

    // ==================== 风控告警通知 ====================

    @Test
    @DisplayName("notifyRiskSignal: HIGH 风险等级映射 ERROR")
    void notifyRiskSignal_high() {
        service.notifyRiskSignal("R001", "P001", "HIGH", "敏感词命中");

        ArgumentCaptor<ScrmNotification> captor = ArgumentCaptor.forClass(ScrmNotification.class);
        verify(webSocketHandler, times(1)).sendNotification(captor.capture());
        ScrmNotification n = captor.getValue();
        assertThat(n.getType()).isEqualTo("RISK_SIGNAL");
        assertThat(n.getLevel()).isEqualTo("ERROR");
        assertThat(n.getContent()).isEqualTo("敏感词命中");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) n.getData();
        assertThat(data).containsEntry("ruleId", "R001")
                .containsEntry("personaId", "P001")
                .containsEntry("riskLevel", "HIGH")
                .containsEntry("detail", "敏感词命中");
    }

    @Test
    @DisplayName("notifyRiskSignal: detail 为 null 时正文回退为规则命中描述")
    void notifyRiskSignal_nullDetail() {
        service.notifyRiskSignal("R002", null, "MEDIUM", null);

        ArgumentCaptor<ScrmNotification> captor = ArgumentCaptor.forClass(ScrmNotification.class);
        verify(webSocketHandler, times(1)).sendNotification(captor.capture());
        assertThat(captor.getValue().getLevel()).isEqualTo("WARNING");
        assertThat(captor.getValue().getContent()).contains("R002");
    }

    // ==================== 账号健康通知 ====================

    @Test
    @DisplayName("notifyAccountHealth: FROZEN 映射 ERROR")
    void notifyAccountHealth_frozen() {
        service.notifyAccountHealth(200L, "FROZEN", "账号已被冻结");

        ArgumentCaptor<ScrmNotification> captor = ArgumentCaptor.forClass(ScrmNotification.class);
        verify(webSocketHandler, times(1)).sendNotification(captor.capture());
        ScrmNotification n = captor.getValue();
        assertThat(n.getType()).isEqualTo("ACCOUNT_HEALTH");
        assertThat(n.getLevel()).isEqualTo("ERROR");
        assertThat(n.getContent()).isEqualTo("账号已被冻结");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) n.getData();
        assertThat(data).containsEntry("accountId", 200L)
                .containsEntry("checkResult", "FROZEN");
    }

    @Test
    @DisplayName("notifyAccountHealth: detail 为 null 时正文回退为账号 + 状态")
    void notifyAccountHealth_nullDetail() {
        service.notifyAccountHealth(201L, "OFFLINE", null);

        ArgumentCaptor<ScrmNotification> captor = ArgumentCaptor.forClass(ScrmNotification.class);
        verify(webSocketHandler, times(1)).sendNotification(captor.capture());
        assertThat(captor.getValue().getLevel()).isEqualTo("WARNING");
        assertThat(captor.getValue().getContent()).contains("201").contains("OFFLINE");
    }

    // ==================== 任务执行日志通知 ====================

    @Test
    @DisplayName("notifyCampaignLog: status=FAILED 映射 ERROR")
    void notifyCampaignLog_failed() {
        service.notifyCampaignLog(300L, "执行步骤1", "FAILED");

        ArgumentCaptor<ScrmNotification> captor = ArgumentCaptor.forClass(ScrmNotification.class);
        verify(webSocketHandler, times(1)).sendNotification(captor.capture());
        ScrmNotification n = captor.getValue();
        assertThat(n.getType()).isEqualTo("CAMPAIGN_LOG");
        assertThat(n.getLevel()).isEqualTo("ERROR");
        assertThat(n.getContent()).contains("300").contains("执行步骤1").contains("FAILED");
    }

    @Test
    @DisplayName("notifyCampaignLog: status=SUCCESS 映射 INFO")
    void notifyCampaignLog_success() {
        service.notifyCampaignLog(301L, "完成", "SUCCESS");

        ArgumentCaptor<ScrmNotification> captor = ArgumentCaptor.forClass(ScrmNotification.class);
        verify(webSocketHandler, times(1)).sendNotification(captor.capture());
        assertThat(captor.getValue().getLevel()).isEqualTo("INFO");
    }

    // ==================== 系统通知 ====================

    @Test
    @DisplayName("notifySystem: level 为 null 时默认 INFO")
    void notifySystem_nullLevel() {
        service.notifySystem("系统维护", "将于今晚维护", null);

        ArgumentCaptor<ScrmNotification> captor = ArgumentCaptor.forClass(ScrmNotification.class);
        verify(webSocketHandler, times(1)).sendNotification(captor.capture());
        ScrmNotification n = captor.getValue();
        assertThat(n.getType()).isEqualTo("SYSTEM");
        assertThat(n.getLevel()).isEqualTo("INFO");
        assertThat(n.getTitle()).isEqualTo("系统维护");
        assertThat(n.getContent()).isEqualTo("将于今晚维护");
        assertThat(n.getData()).isNull();
    }

    @Test
    @DisplayName("notifySystem: 显式 level 时按调用方指定")
    void notifySystem_explicitLevel() {
        service.notifySystem("严重告警", "数据库异常", "CRITICAL");

        ArgumentCaptor<ScrmNotification> captor = ArgumentCaptor.forClass(ScrmNotification.class);
        verify(webSocketHandler, times(1)).sendNotification(captor.capture());
        assertThat(captor.getValue().getLevel()).isEqualTo("CRITICAL");
    }

    // ==================== 新消息通知 ====================

    @Test
    @DisplayName("notifyNewMessage: 内容超过 50 字符时截断并加省略号")
    void notifyNewMessage_truncate() {
        String longContent = "一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十END";
        service.notifyNewMessage(400L, "IN", "TEXT", longContent);

        ArgumentCaptor<ScrmNotification> captor = ArgumentCaptor.forClass(ScrmNotification.class);
        verify(webSocketHandler, times(1)).sendNotification(captor.capture());
        ScrmNotification n = captor.getValue();
        assertThat(n.getType()).isEqualTo("NEW_MESSAGE");
        assertThat(n.getLevel()).isEqualTo("INFO");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) n.getData();
        String summary = (String) data.get("content");
        assertThat(summary).hasSize(53).endsWith("...");
    }

    @Test
    @DisplayName("notifyNewMessage: IN 方向正文标识客户消息")
    void notifyNewMessage_inDirection() {
        service.notifyNewMessage(401L, "IN", "TEXT", "你好");

        ArgumentCaptor<ScrmNotification> captor = ArgumentCaptor.forClass(ScrmNotification.class);
        verify(webSocketHandler, times(1)).sendNotification(captor.capture());
        assertThat(captor.getValue().getContent()).contains("客户");
    }

    @Test
    @DisplayName("notifyNewMessage: OUT 方向正文标识发出消息")
    void notifyNewMessage_outDirection() {
        service.notifyNewMessage(402L, "OUT", "IMAGE", null);

        ArgumentCaptor<ScrmNotification> captor = ArgumentCaptor.forClass(ScrmNotification.class);
        verify(webSocketHandler, times(1)).sendNotification(captor.capture());
        ScrmNotification n = captor.getValue();
        assertThat(n.getContent()).contains("发出").contains("IMAGE");
    }

    // ==================== 看板统计更新通知 ====================

    @Test
    @DisplayName("sendDashboardStatUpdate: 使用显式账号 ID 并携带 statType")
    void sendDashboardStatUpdate_success() {
        service.sendDashboardStatUpdate("customers", "detail");

        ArgumentCaptor<ScrmNotification> captor = ArgumentCaptor.forClass(ScrmNotification.class);
        verify(webSocketHandler, times(1)).sendNotification(captor.capture());
        ScrmNotification n = captor.getValue();
        assertThat(n.getType()).isEqualTo("DASHBOARD_STAT_UPDATE");
        assertThat(n.getLevel()).isEqualTo("INFO");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) n.getData();
        assertThat(data).containsEntry("statType", "customers")
                .containsEntry("detail", "detail");
    }

    // ==================== 风险规则告警通知 ====================

    @Test
    @DisplayName("sendRiskAlert: 固定 ERROR 级别并使用显式账号 ID")
    void sendRiskAlert_success() {
        service.sendRiskAlert(200L, "RULE_X", "触发告警");

        ArgumentCaptor<ScrmNotification> captor = ArgumentCaptor.forClass(ScrmNotification.class);
        verify(webSocketHandler, times(1)).sendNotification(captor.capture());
        ScrmNotification n = captor.getValue();
        assertThat(n.getType()).isEqualTo("RISK_ALERT");
        assertThat(n.getLevel()).isEqualTo("ERROR");
        assertThat(n.getContent()).isEqualTo("触发告警");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) n.getData();
        assertThat(data).containsEntry("ruleCode", "RULE_X")
                .containsEntry("accountId", 200L)
                .containsEntry("description", "触发告警");
    }

    @Test
    @DisplayName("sendRiskAlert: description 为 null 时回退为规则触发描述")
    void sendRiskAlert_nullDescription() {
        service.sendRiskAlert(null, "RULE_Y", null);

        ArgumentCaptor<ScrmNotification> captor = ArgumentCaptor.forClass(ScrmNotification.class);
        verify(webSocketHandler, times(1)).sendNotification(captor.capture());
        assertThat(captor.getValue().getContent()).contains("RULE_Y");
    }

    // ==================== 跟进提醒通知 ====================

    @Test
    @DisplayName("sendFollowupReminder: 写入跟进时间与客户名")
    void sendFollowupReminder_success() {
        LocalDateTime followUpAt = LocalDateTime.of(2026, 8, 10, 14, 30);
        service.sendFollowupReminder(500L, "Alice", followUpAt, "重点客户");

        ArgumentCaptor<ScrmNotification> captor = ArgumentCaptor.forClass(ScrmNotification.class);
        verify(webSocketHandler, times(1)).sendNotification(captor.capture());
        ScrmNotification n = captor.getValue();
        assertThat(n.getType()).isEqualTo("FOLLOWUP_REMINDER");
        assertThat(n.getLevel()).isEqualTo("INFO");
        assertThat(n.getContent()).contains("Alice").contains("2026-08-10 14:30");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) n.getData();
        assertThat(data).containsEntry("customerId", 500L)
                .containsEntry("customerName", "Alice")
                .containsEntry("remark", "重点客户");
    }

    @Test
    @DisplayName("sendFollowupReminder: followUpAt 为 null 时显示未知时间")
    void sendFollowupReminder_nullTime() {
        service.sendFollowupReminder(501L, null, null, null);

        ArgumentCaptor<ScrmNotification> captor = ArgumentCaptor.forClass(ScrmNotification.class);
        verify(webSocketHandler, times(1)).sendNotification(captor.capture());
        ScrmNotification n = captor.getValue();
        assertThat(n.getContent()).contains("未知时间").contains("501");
    }

    // ==================== 异步发送容错 ====================

    @Test
    @DisplayName("doSendAsync: WebSocket 抛异常时不向调用方抛出")
    void doSendAsync_swallowException() {
        doThrow(new RuntimeException("ws down")).when(webSocketHandler).sendNotification(any());

        // 不应抛出异常
        service.notifySystem("标题", "内容", "INFO");

        verify(webSocketHandler, times(1)).sendNotification(any());
    }

    // ==================== 私有映射方法测试 (ReflectionTestUtils) ====================

    @Test
    @DisplayName("mapTaskStatusLevel: null / RUNNING / 未知状态映射 INFO")
    void mapTaskStatusLevel_defaultInfo() {
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "mapTaskStatusLevel", (Object) null))
                .isEqualTo("INFO");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "mapTaskStatusLevel", "RUNNING"))
                .isEqualTo("INFO");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "mapTaskStatusLevel", "UNKNOWN"))
                .isEqualTo("INFO");
    }

    @Test
    @DisplayName("mapTaskStatusLevel: SUCCESS → INFO, FAILED → ERROR (大小写不敏感)")
    void mapTaskStatusLevel_caseInsensitive() {
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "mapTaskStatusLevel", "SUCCESS"))
                .isEqualTo("INFO");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "mapTaskStatusLevel", "failed"))
                .isEqualTo("ERROR");
    }

    @Test
    @DisplayName("mapRiskLevel: LOW/MEDIUM/HIGH/CRITICAL 正确映射, 其他默认 WARNING")
    void mapRiskLevel_allLevels() {
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "mapRiskLevel", "LOW"))
                .isEqualTo("INFO");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "mapRiskLevel", "MEDIUM"))
                .isEqualTo("WARNING");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "mapRiskLevel", "HIGH"))
                .isEqualTo("ERROR");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "mapRiskLevel", "CRITICAL"))
                .isEqualTo("CRITICAL");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "mapRiskLevel", "UNKNOWN"))
                .isEqualTo("WARNING");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "mapRiskLevel", (Object) null))
                .isEqualTo("WARNING");
    }

    @Test
    @DisplayName("mapHealthLevel: HEALTHY/OFFLINE/FROZEN 正确映射, 其他默认 WARNING")
    void mapHealthLevel_allResults() {
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "mapHealthLevel", "HEALTHY"))
                .isEqualTo("INFO");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "mapHealthLevel", "OFFLINE"))
                .isEqualTo("WARNING");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "mapHealthLevel", "FROZEN"))
                .isEqualTo("ERROR");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "mapHealthLevel", "UNKNOWN"))
                .isEqualTo("WARNING");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "mapHealthLevel", (Object) null))
                .isEqualTo("WARNING");
    }

    @Test
    @DisplayName("buildTaskStatusContent: detail 为空白时正文不含冒号分隔")
    void buildTaskStatusContent_blankDetail() {
        String result = (String) ReflectionTestUtils.invokeMethod(service, "buildTaskStatusContent",
                10L, "SUCCESS", "   ");
        assertThat(result).contains("10").contains("SUCCESS").doesNotContain(":");
    }
}
