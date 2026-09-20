/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationService.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.websocket.ScrmNotification;
import org.hiylo.scrm.websocket.ScrmNotificationWebSocketHandler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SCRM 通知服务
 * <p>
 * 封装各类业务场景的实时通知构建逻辑, 通过 {@link ScrmNotificationWebSocketHandler}
 * 异步推送给前端。所有方法均使用 {@code @Async} 注解, 不阻塞调用方 (主要在
 * {@code ScrmCallbackController} 回调链路中被调用)。
 * </p>
 * <p>
 * 当前账号 ID 在调用线程从请求上下文读取后, 作为参数传入异步方法,
 * 避免 ThreadLocal 在异步线程中丢失。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmNotificationService {

    /** 通知类型: 任务状态变更 */
    private static final String TYPE_TASK_STATUS = "TASK_STATUS";
    /** 通知类型: 风控告警 */
    private static final String TYPE_RISK_SIGNAL = "RISK_SIGNAL";
    /** 通知类型: 账号健康 */
    private static final String TYPE_ACCOUNT_HEALTH = "ACCOUNT_HEALTH";
    /** 通知类型: 任务执行日志 */
    private static final String TYPE_CAMPAIGN_LOG = "CAMPAIGN_LOG";
    /** 通知类型: 系统通知 */
    private static final String TYPE_SYSTEM = "SYSTEM";
    /** 通知类型: 新会话消息 */
    private static final String TYPE_NEW_MESSAGE = "NEW_MESSAGE";
    /** 通知类型: 跟进提醒 */
    private static final String TYPE_FOLLOWUP_REMINDER = "FOLLOWUP_REMINDER";
    /** 通知类型: 看板统计实时更新 (新客户 / 新消息 / 新会话) */
    private static final String TYPE_DASHBOARD_STAT_UPDATE = "DASHBOARD_STAT_UPDATE";
    /** 通知类型: 风险规则触发告警 */
    private static final String TYPE_RISK_ALERT = "RISK_ALERT";

    /** 看板统计类型: 新客户 */
    private static final String STAT_CUSTOMERS = "customers";
    /** 看板统计类型: 新消息 */
    private static final String STAT_MESSAGES = "messages";
    /** 看板统计类型: 新会话 */
    private static final String STAT_CONVERSATIONS = "conversations";

    /** 通知级别: 信息 */
    private static final String LEVEL_INFO = "INFO";
    /** 通知级别: 警告 */
    private static final String LEVEL_WARNING = "WARNING";
    /** 通知级别: 错误 */
    private static final String LEVEL_ERROR = "ERROR";
    /** 通知级别: 严重 */
    private static final String LEVEL_CRITICAL = "CRITICAL";

    /** 任务状态: 成功 */
    private static final String STATUS_SUCCESS = "SUCCESS";
    /** 任务状态: 失败 */
    private static final String STATUS_FAILED = "FAILED";
    /** 任务状态: 运行中 */
    private static final String STATUS_RUNNING = "RUNNING";

    /** 风险等级: 低 */
    private static final String RISK_LOW = "LOW";
    /** 风险等级: 中 */
    private static final String RISK_MEDIUM = "MEDIUM";
    /** 风险等级: 高 */
    private static final String RISK_HIGH = "HIGH";
    /** 风险等级: 严重 */
    private static final String RISK_CRITICAL = "CRITICAL";

    /** 账号健康: 健康 */
    private static final String HEALTH_HEALTHY = "HEALTHY";
    /** 账号健康: 离线 */
    private static final String HEALTH_OFFLINE = "OFFLINE";
    /** 账号健康: 冻结 */
    private static final String HEALTH_FROZEN = "FROZEN";

    /** SCRM 通知 WebSocket 处理器 */
    private final ScrmNotificationWebSocketHandler webSocketHandler;

    /**
     * 任务状态变更通知。
     * <p>
     * 在 {@code ScrmCallbackController#onTaskStatus} 回调处理完成后调用,
     * data 中携带 campaignId / status / detail, 供前端刷新任务列表与详情页。
     * </p>
     *
     * @param campaignId 营销任务 ID
     * @param status     任务状态 (SUCCESS / FAILED / RUNNING)
     * @param detail     通知详情 (可空)
     */
    public void notifyTaskStatus(Long campaignId, String status, String detail) {
        // 在调用线程捕获  避免异步线程 ThreadLocal 丢失
        String level = mapTaskStatusLevel(status);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("campaignId", campaignId);
        data.put("status", status);
        data.put("detail", detail);
        ScrmNotification notification = ScrmNotification.builder()
                .type(TYPE_TASK_STATUS)
                .level(level)
                .title("任务状态更新")
                .content(buildTaskStatusContent(campaignId, status, detail))
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
        doSendAsync(notification);
    }

    /**
     * 风控告警通知。
     * <p>
     * 在 {@code ScrmCallbackController#onRiskSignal} 回调处理完成后调用,
     * level 根据 riskLevel 映射 (LOW→INFO / MEDIUM→WARNING / HIGH→ERROR / CRITICAL→CRITICAL)。
     * </p>
     *
     * @param ruleId     风控规则 ID
     * @param personaId  关联人设 ID (可空)
     * @param riskLevel  风险等级 (LOW / MEDIUM / HIGH / CRITICAL)
     * @param detail     风险详情描述
     */
    public void notifyRiskSignal(String ruleId, String personaId, String riskLevel, String detail) {
        String level = mapRiskLevel(riskLevel);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ruleId", ruleId);
        data.put("personaId", personaId);
        data.put("riskLevel", riskLevel);
        data.put("detail", detail);
        ScrmNotification notification = ScrmNotification.builder()
                .type(TYPE_RISK_SIGNAL)
                .level(level)
                .title("风控告警")
                .content(detail != null ? detail : "风控规则 " + ruleId + " 命中")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
        doSendAsync(notification);
    }

    /**
     * 账号健康通知。
     * <p>
     * level 根据 checkResult 映射 (HEALTHY→INFO / OFFLINE→WARNING / FROZEN→ERROR)。
     * 用于账号掉线 / 冻结等场景实时提醒前端。
     * </p>
     *
     * @param accountId   账号 ID
     * @param checkResult 检查结果 (HEALTHY / OFFLINE / FROZEN)
     * @param detail      通知详情
     */
    public void notifyAccountHealth(Long accountId, String checkResult, String detail) {
        String level = mapHealthLevel(checkResult);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accountId", accountId);
        data.put("checkResult", checkResult);
        data.put("detail", detail);
        ScrmNotification notification = ScrmNotification.builder()
                .type(TYPE_ACCOUNT_HEALTH)
                .level(level)
                .title("账号健康状态")
                .content(detail != null ? detail : "账号 " + accountId + " 状态: " + checkResult)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
        doSendAsync(notification);
    }

    /**
     * 任务执行日志通知。
     * <p>
     * 用于任务执行过程中的步骤日志推送 (如 "开始执行"、"完成第 N 步" 等),
     * level 固定为 INFO, 除非 status 为 FAILED 时为 ERROR。
     * </p>
     *
     * @param campaignId 营销任务 ID
     * @param action     执行动作描述
     * @param status     执行状态 (SUCCESS / FAILED / RUNNING 等)
     */
    public void notifyCampaignLog(Long campaignId, String action, String status) {
        String level = STATUS_FAILED.equalsIgnoreCase(status) ? LEVEL_ERROR : LEVEL_INFO;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("campaignId", campaignId);
        data.put("action", action);
        data.put("status", status);
        ScrmNotification notification = ScrmNotification.builder()
                .type(TYPE_CAMPAIGN_LOG)
                .level(level)
                .title("任务执行日志")
                .content("任务 " + campaignId + " " + action + " (" + status + ")")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
        doSendAsync(notification);
    }

    /**
     * 系统通知。
     * <p>
     * 通用系统消息, level 由调用方指定。
     * </p>
     *
     * @param title   通知标题
     * @param content 通知正文
     * @param level   通知级别 (INFO / WARNING / ERROR / CRITICAL)
     */
    public void notifySystem(String title, String content, String level) {
        ScrmNotification notification = ScrmNotification.builder()
                .type(TYPE_SYSTEM)
                .level(level != null ? level : LEVEL_INFO)
                .title(title)
                .content(content)
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
        doSendAsync(notification);
    }

    /**
     * 新会话消息通知。
     * <p>
     * 前端发送消息或回调收到消息后调用, 推送 NEW_MESSAGE 通知给在线用户,
     * 前端收到后自动刷新会话列表与消息列表。
     * </p>
     *
     * @param conversationId 会话 ID
     * @param direction      消息方向 (IN / OUT)
     * @param messageType    消息类型 (TEXT / IMAGE 等)
     * @param content        消息内容摘要
     */
    public void notifyNewMessage(Long conversationId, String direction, String messageType, String content) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversationId", conversationId);
        data.put("direction", direction);
        data.put("messageType", messageType);
        String summary = content != null && content.length() > 50
                ? content.substring(0, 50) + "..." : content;
        data.put("content", summary);
        ScrmNotification notification = ScrmNotification.builder()
                .type(TYPE_NEW_MESSAGE)
                .level(LEVEL_INFO)
                .title("新消息")
                .content("收到" + ("IN".equals(direction) ? "客户" : "发出")
                        + "消息: " + (summary != null ? summary : messageType))
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
        doSendAsync(notification);
    }

    /**
     * 看板统计实时更新通知。
     * <p>
     * 在新消息保存 / 新客户创建 / 新会话生成等场景调用, 推送 DASHBOARD_STAT_UPDATE 通知给前端,
     * 前端 Dashboard 收到后按 statType (customers / messages / conversations) 增量递增对应统计卡片,
     * 避免整页刷新。账号 ID 由调用方显式传入 (常用于事务提交后回调, 此时 ThreadLocal 可能已清理)。
     * </p>
     *
     *      * @param statType 统计类型 (customers / messages / conversations)
     * @param data     附加数据 (任意可序列化对象, 如 conversationId / customerId 等)
     */
    public void sendDashboardStatUpdate(String statType, Object data) {
        // 构建附加数据体, 始终携带 statType 便于前端按类型分流
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("statType", statType);
        payload.put("detail", data);
        ScrmNotification notification = ScrmNotification.builder()
                .type(TYPE_DASHBOARD_STAT_UPDATE)
                .level(LEVEL_INFO)
                .title("看板数据更新")
                .content("看板统计更新: " + (statType != null ? statType : "unknown"))
                .data(payload)
                .timestamp(LocalDateTime.now())
                .build();
        doSendAsync(notification);
    }

    /**
     * 风险规则触发告警通知。
     * <p>
     * 在风控规则命中时调用, 推送 RISK_ALERT 通知给前端, 前端 Layout 全局监听后弹出红色告警弹窗。
     * level 固定为 ERROR, 突出告警优先级。账号 ID 由调用方显式传入。
     * </p>
     *
     *      * @param accountId   触发风险的账号 ID (可空)
     * @param ruleCode    风险规则代码
     * @param description 风险描述 (前端弹窗正文)
     */
    public void sendRiskAlert(Long accountId, String ruleCode, String description) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ruleCode", ruleCode);
        data.put("accountId", accountId);
        data.put("description", description);
        ScrmNotification notification = ScrmNotification.builder()
                .type(TYPE_RISK_ALERT)
                .level(LEVEL_ERROR)
                .title("风险规则告警")
                .content(description != null ? description : "风险规则 " + ruleCode + " 触发")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
        doSendAsync(notification);
    }

    /**
     * 客户跟进提醒通知。
     * <p>
     * 在跟进任务到期 / 定时提醒触发时调用, 推送 FOLLOWUP_REMINDER 通知给前端,
     * level 为 INFO (蓝色信息样式)。账号 ID 由调用方显式传入, 因为调度器运行在
     * 无请求上下文的线程中, 无法从请求上下文获取。
     * </p>
     *
     *      * @param customerId   客户 ID
     * @param customerName 客户名称 (用于提醒正文展示)
     * @param followUpAt   计划跟进时间 (用于提醒正文与前端跳转展示)
     * @param remark       跟进备注 (可空)
     */
    public void sendFollowupReminder(Long customerId, String customerName,
                                     LocalDateTime followUpAt, String remark) {
        String formattedTime = followUpAt != null
                ? followUpAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                : "未知时间";
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("customerId", customerId);
        data.put("customerName", customerName);
        data.put("followUpAt", followUpAt != null ? followUpAt.toString() : null);
        data.put("remark", remark);
        ScrmNotification notification = ScrmNotification.builder()
                .type(TYPE_FOLLOWUP_REMINDER)
                .level(LEVEL_INFO)
                .title("客户跟进提醒")
                .content("客户 " + (customerName != null ? customerName : customerId)
                        + " 的跟进时间将至: " + formattedTime)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
        doSendAsync(notification);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 异步发送通知, 由 Spring AsyncConfig 中的虚拟线程执行器驱动。
     * <p>捕获所有异常, 仅记录日志, 不向调用方抛出。</p>
     *
     * @param notification 通知对象
     */
    @Async("asyncTaskExecutor")
    public void doSendAsync(ScrmNotification notification) {
        try {
            webSocketHandler.sendNotification(notification);
        } catch (Exception e) {
            log.warn("异步推送通知失败: type={}, title={}, err={}",
                    notification.getType(), notification.getTitle(), e.getMessage(), e);
        }
    }

    /**
     * 任务状态 → 通知级别映射。
     * <ul>
     *   <li>SUCCESS → INFO</li>
     *   <li>FAILED → ERROR</li>
     *   <li>RUNNING / 其他 → INFO</li>
     * </ul>
     *
     * @param status 任务状态
     * @return 通知级别
     */
    private String mapTaskStatusLevel(String status) {
        if (status == null) {
            return LEVEL_INFO;
        }
        switch (status.toUpperCase()) {
            case STATUS_SUCCESS:
                return LEVEL_INFO;
            case STATUS_FAILED:
                return LEVEL_ERROR;
            case STATUS_RUNNING:
            default:
                return LEVEL_INFO;
        }
    }

    /**
     * 风险等级 → 通知级别映射。
     * <ul>
     *   <li>LOW → INFO</li>
     *   <li>MEDIUM → WARNING</li>
     *   <li>HIGH → ERROR</li>
     *   <li>CRITICAL → CRITICAL</li>
     *   <li>其他 → WARNING</li>
     * </ul>
     *
     * @param riskLevel 风险等级
     * @return 通知级别
     */
    private String mapRiskLevel(String riskLevel) {
        if (riskLevel == null) {
            return LEVEL_WARNING;
        }
        switch (riskLevel.toUpperCase()) {
            case RISK_LOW:
                return LEVEL_INFO;
            case RISK_MEDIUM:
                return LEVEL_WARNING;
            case RISK_HIGH:
                return LEVEL_ERROR;
            case RISK_CRITICAL:
                return LEVEL_CRITICAL;
            default:
                return LEVEL_WARNING;
        }
    }

    /**
     * 账号健康检查结果 → 通知级别映射。
     * <ul>
     *   <li>HEALTHY → INFO</li>
     *   <li>OFFLINE → WARNING</li>
     *   <li>FROZEN → ERROR</li>
     *   <li>其他 → WARNING</li>
     * </ul>
     *
     * @param checkResult 检查结果
     * @return 通知级别
     */
    private String mapHealthLevel(String checkResult) {
        if (checkResult == null) {
            return LEVEL_WARNING;
        }
        switch (checkResult.toUpperCase()) {
            case HEALTH_HEALTHY:
                return LEVEL_INFO;
            case HEALTH_OFFLINE:
                return LEVEL_WARNING;
            case HEALTH_FROZEN:
                return LEVEL_ERROR;
            default:
                return LEVEL_WARNING;
        }
    }

    /**
     * 构建任务状态变更通知正文。
     *
     * @param campaignId 营销任务 ID
     * @param status     任务状态
     * @param detail     详情 (可空)
     * @return 通知正文
     */
    private String buildTaskStatusContent(Long campaignId, String status, String detail) {
        StringBuilder sb = new StringBuilder("营销任务");
        sb.append(campaignId).append(" 状态变更为 ").append(status);
        if (detail != null && !detail.isBlank()) {
            sb.append(": ").append(detail);
        }
        return sb.toString();
    }
}
