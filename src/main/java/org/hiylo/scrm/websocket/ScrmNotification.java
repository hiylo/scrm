/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotification.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * SCRM 实时通知 DTO, 通过 WebSocket 推送给前端。
 * <p>
 * 统一承载任务状态变更 / 风控告警 / 账号健康 / 任务执行日志 / 系统通知等场景的消息体,
 * 由 {@link ScrmNotificationWebSocketHandler#sendNotification} 序列化为 JSON 后推送。
 * </p>
 * <p>
 * 支持的通知类型:
 * <ul>
 *   <li>TASK_STATUS - 任务状态变更</li>
 *   <li>RISK_SIGNAL - 风控信号</li>
 *   <li>ACCOUNT_HEALTH - 账号健康</li>
 *   <li>CAMPAIGN_LOG - 任务执行日志</li>
 *   <li>SYSTEM - 系统通知</li>
 *   <li>NEW_MESSAGE - 新会话消息</li>
 *   <li>DASHBOARD_STAT_UPDATE - 看板统计实时更新 (新客户 / 新消息 / 新会话)</li>
 *   <li>RISK_ALERT - 风险规则触发告警</li>
 *   <li>FOLLOWUP_REMINDER - 客户跟进提醒</li>
 * </ul>
 * </p>
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrmNotification {

    /** 通知类型: TASK_STATUS / RISK_SIGNAL / ACCOUNT_HEALTH / CAMPAIGN_LOG / SYSTEM /
     *  NEW_MESSAGE / DASHBOARD_STAT_UPDATE / RISK_ALERT / FOLLOWUP_REMINDER */
    private String type;

    /** 通知级别: INFO / WARNING / ERROR / CRITICAL */
    private String level;

    /** 通知标题 (前端弹窗 / 列表显示) */
    private String title;

    /** 通知正文 (前端弹窗 / 详情显示) */
    private String content;

    /** 附加数据 (任意 JSON 可序列化对象, 如 campaignId / status 等) */
    private Object data;

    /** 通知生成时间 */
    private LocalDateTime timestamp;

}
