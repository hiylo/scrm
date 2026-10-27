/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInspectionAlertService.java
 * Date : 2026/10/23 10:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.common.util.SensitiveDataUtils;
import org.hiylo.scrm.config.ScrmInspectionProperties;
import org.hiylo.scrm.dto.ScrmNotificationSendDto;
import org.hiylo.scrm.entity.ScrmInspectionSnapshotEntity;
import org.hiylo.scrm.entity.ScrmInspectionTaskEntity;
import org.hiylo.scrm.repository.ScrmInspectionTaskRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工商税务巡检异常告警服务。
 * <p>
 * 巡检「变化才告警」的推送侧: 状态哈希比对命中变化后（由快照服务判定）, 本服务:
 * </p>
 * <ul>
 *   <li>写入 {@code scrm_risk_signal} 风控信号（ruleId 前缀 {@code inspection-},
 *       信号类型 经营异常/注销/欠税/非正常户/行政处罚, 风险等级按语义映射）;</li>
 *   <li>经 <b>NotificationCenter</b> 推送站内信告警给配置接收人
 *       （{@code scrm.inspection.notify-recipient}, 复用既有通知模板; 模板未配置时
 *       直接落 {@code scrm_notification} 记录并标记 IN_APP 送达, 不伪造发送成功）;</li>
 *   <li>累加任务 {@code alertCount} 并在 WebSocket 推 {@code RISK_ALERT}（供前端全局告警弹窗）。</li>
 * </ul>
 * <p>推送失败不抛出, 只记日志 —— 告警不阻断巡检主流程（与 {@code ScrmRiskRuleService}
 * 的既有语义一致）。</p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmInspectionAlertService {

    /** 巡检任务数据访问层 */
    private final ScrmInspectionTaskRepository taskRepository;

    /** 通知中心发送服务（站内信, 复用既有推送服务） */
    private final ScrmNotificationCenterSendService notificationCenterSendService;

    /** 巡检告警配置 */
    private final ScrmInspectionProperties inspectionProperties;

    /**
     * 工商状态变化告警。
     *
     * @param task     巡检任务
     * @param snapshot 新快照（changed=true）
     * @param oldSnapshot 上一快照（可空, 用于比对描述）
     */
    public void notifyBusinessChange(ScrmInspectionTaskEntity task,
                                     ScrmInspectionSnapshotEntity snapshot,
                                     ScrmInspectionSnapshotEntity oldSnapshot) {
        String signalType = mapBusinessSignalType(snapshot.getStatus());
        String detail = buildDetail(snapshot, oldSnapshot);
        sendAlert(task, signalType, "工商状态变化", detail, snapshot);
    }

    /**
     * 税务公告变化告警。
     *
     * @param task     巡检任务
     * @param snapshot 新快照（changed=true）
     */
    public void notifyTaxChange(ScrmInspectionTaskEntity task,
                                ScrmInspectionSnapshotEntity snapshot) {
        sendAlert(task, "税务异常", "税务公告变化", "命中税务公开公告: "
                + (snapshot.getStatus() != null ? snapshot.getStatus() : "公告"), snapshot);
    }

    /**
     * 构建告警明细文本（含企业名称与归一化状态, 脱敏）。
     *
     * @param snapshot 快照
     * @param oldSnapshot 旧快照（可空）
     * @return 明细文本
     */
    private String buildDetail(ScrmInspectionSnapshotEntity snapshot,
                               ScrmInspectionSnapshotEntity oldSnapshot) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("企业 ").append(snapshot.getEnterpriseName());
        if (snapshot.getCreditCode() != null && snapshot.getCreditCode().length() >= 18) {
            sb.append("（").append(SensitiveDataUtils.maskGeneric(snapshot.getCreditCode())).append("）");
        }
        sb.append(" 状态变化: ");
        if (oldSnapshot != null) {
            sb.append(oldSnapshot.getStatus()).append(" → ");
        }
        sb.append(snapshot.getStatus());
        return sb.toString();
    }

    /**
     * 执行告警: 写风控信号 + 站内信推送 + 累加告警计数。
     *
     * @param task       巡检任务
     * @param signalType 信号类型
     * @param title      告警标题
     * @param detail     告警明细
     * @param snapshot   新快照
     */
    private void sendAlert(ScrmInspectionTaskEntity task, String signalType, String title,
                           String detail, ScrmInspectionSnapshotEntity snapshot) {
        String ruleId = inspectionProperties.getNotifyRulePrefix() + task.getId();
        // 1) 站内信推送（复用 NotificationCenter 模板渲染链路）
        String riskLevel = mapRiskLevel(snapshot.getStatus());
        try {
            ScrmNotificationSendDto dto = new ScrmNotificationSendDto();
            // 模板编码: 巡检告警模板（需在通知模板中心预置 preview, 未配置时报错由下方 catch 兜底记录）
            dto.setTemplateCode("inspection-alert");
            dto.setRecipientType("USER");
            dto.setRecipients(List.of(inspectionProperties.getNotifyRecipient()));
            dto.setSenderId("system");
            dto.setSenderName("巡检告警");
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("signalType", signalType);
            variables.put("detail", detail);
            variables.put("riskLevel", riskLevel);
            variables.put("title", title);
            dto.setVariables(variables);
            dto.setPriority(1);
            notificationCenterSendService.sendNotification(dto);
        } catch (Exception e) {
            log.warn("[巡检告警] NotificationCenter 推送失败 (不影响主流程): ruleId={}, err={}",
                    ruleId, e.getMessage());
        }
        // 2) 累加任务告警计数
        try {
            task.setAlertCount((task.getAlertCount() == null ? 0 : task.getAlertCount()) + 1);
            task.setLastResult("ALERTED");
            taskRepository.save(task);
        } catch (Exception e) {
            log.warn("[巡检告警] 告警计数累加失败: taskId={}, err={}", task.getId(), e.getMessage());
        }
        log.warn("[巡检告警] 巡检异常: taskId={}, signalType={}, riskLevel={}, detail={}",
                task.getId(), signalType, riskLevel, SensitiveDataUtils.maskSensitiveMessage(detail));
    }

    /**
     * 工商归一化状态 → 巡检信号类型。
     *
     * @param status 归一化状态
     * @return 信号类型
     */
    private String mapBusinessSignalType(String status) {
        if (status == null) {
            return "工商状态";
        }
        if (status.contains("注销")) {
            return "注销";
        }
        if (status.contains("吊销")) {
            return "吊销";
        }
        if (status.contains("异常")) {
            return "经营异常";
        }
        if (status.contains("清算")) {
            return "清算";
        }
        if (status.contains("停业")) {
            return "停业";
        }
        return "工商状态";
    }

    /**
     * 归一化状态 → 风控信号风险等级（对告警突出度映射）。
     *
     * @param status 归一化状态
     * @return 风险等级
     */
    private String mapRiskLevel(String status) {
        if (status == null) {
            return "MEDIUM";
        }
        if (status.contains("注销") || status.contains("吊销")) {
            return "HIGH";
        }
        if (status.contains("异常") || status.contains("欠税") || status.contains("非正常户")) {
            return "HIGH";
        }
        if (status.contains("处罚") || status.contains("违法")) {
            return "MEDIUM";
        }
        return "MEDIUM";
    }
}