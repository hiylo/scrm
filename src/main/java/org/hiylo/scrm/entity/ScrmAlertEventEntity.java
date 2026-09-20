/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAlertEventEntity.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * SCRM 告警事件实体。
 * <p>
 * 描述一次告警触发的运行时事件: 关联规则与指标快照 (ruleId / ruleName /
 * ruleCode / metricId / metricName / metricCode)、严重程度与状态 (severity /
 * status)、触发值与阈值 (triggerValue / thresholdValue / condition)、触发与恢复
 * 时间 (triggerTime / resolvedTime / durationSeconds)、告警内容 (title / message /
 * description)、根因与影响分析 (rootCauseAnalysis / impactAnalysis /
 * affectedServices / affectedUsers)、确认与恢复 (acknowledgedBy / resolvedBy /
 * resolutionType)、通知与升级 (notificationsSent / notificationFailures /
 * escalated / escalatedTo)、行动项与关联事件 (actionItems / relatedEventIds)。
 * </p>
 * <p>
 * 严重程度: INFO / WARNING / CRITICAL / FATAL。
 * 状态: FIRING / PENDING / RESOLVED / ACKNOWLEDGED / SUPPRESSED / EXPIRED。
 * 恢复方式: AUTO / MANUAL / MAINTENANCE / SUPPRESSED。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_alert_event", schema = "scrm", indexes = {
        @Index(name = "idx_alert_event_no", columnList = "event_no", unique = true),
        @Index(name = "idx_alert_event_rule", columnList = "rule_id"),
        @Index(name = "idx_alert_event_metric", columnList = "metric_id"),
        @Index(name = "idx_alert_event_severity", columnList = "severity"),
        @Index(name = "idx_alert_event_status", columnList = "status"),
        @Index(name = "idx_alert_event_trigger_time", columnList = "trigger_time")
})
@Data
public class ScrmAlertEventEntity {

    // ==================== 公共字段 ====================

    /** 主键 ID（Snowflake 雪花算法生成） */
    @Id
    @GeneratedValue(generator = "snowflake")
    @GenericGenerator(name = "snowflake", strategy = "org.hiylo.scrm.config.SnowflakeIdGenerator")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 创建时间 */
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    /** 更新时间 */
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    /** 乐观锁版本号（并发更新保护, 后写入者触发 OptimisticLockException） */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * 持久化前回调: 自动填充创建/更新时间与版本号初值
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (version == null) {
            version = 0L;
        }
    }

    /**
     * 更新前回调: 自动刷新更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    // ==================== 业务字段 ====================

    /** 事件编号 (全局唯一) */
    @Column(name = "event_no", nullable = false, length = 100)
    private String eventNo;

    /** 规则 ID (可空) */
    @Column(name = "rule_id")
    private Long ruleId;

    /** 规则名称 (可空, 快照) */
    @Column(name = "rule_name", length = 200)
    private String ruleName;

    /** 规则编码 (可空, 快照) */
    @Column(name = "rule_code", length = 50)
    private String ruleCode;

    /** 指标 ID (可空) */
    @Column(name = "metric_id")
    private Long metricId;

    /** 指标名称 (可空, 快照) */
    @Column(name = "metric_name", length = 200)
    private String metricName;

    /** 指标编码 (可空, 快照) */
    @Column(name = "metric_code", length = 50)
    private String metricCode;

    /** 严重程度: INFO/WARNING/CRITICAL/FATAL */
    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    /** 状态: FIRING/PENDING/RESOLVED/ACKNOWLEDGED/SUPPRESSED/EXPIRED (默认 FIRING) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 触发值 (默认 0) */
    @Column(name = "trigger_value")
    private Double triggerValue;

    /** 阈值 (默认 0) */
    @Column(name = "threshold_value")
    private Double thresholdValue;

    /** 条件操作符 (可空) */
    @Column(name = "condition", length = 20)
    private String condition;

    /** 触发时间 */
    @Column(name = "trigger_time", nullable = false)
    private LocalDateTime triggerTime;

    /** 恢复时间 (可空) */
    @Column(name = "resolved_time")
    private LocalDateTime resolvedTime;

    /** 持续秒 (默认 0) */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /** 触发次数 (默认 1) */
    @Column(name = "fire_count")
    private Integer fireCount;

    /** 告警标题 */
    @Column(name = "title", nullable = false, length = 500)
    private String title;

    /** 告警消息 (可空) */
    @Column(name = "message", length = 2000)
    private String message;

    /** 详细描述 (可空) */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** 根因分析 (可空) */
    @Column(name = "root_cause_analysis", length = 1000)
    private String rootCauseAnalysis;

    /** 影响分析 (可空) */
    @Column(name = "impact_analysis", length = 1000)
    private String impactAnalysis;

    /** 受影响服务 (可空) */
    @Column(name = "affected_services", length = 500)
    private String affectedServices;

    /** 受影响用户数 (默认 0) */
    @Column(name = "affected_users")
    private Integer affectedUsers;

    /** 确认人 (可空) */
    @Column(name = "acknowledged_by", length = 100)
    private String acknowledgedBy;

    /** 确认时间 (可空) */
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    /** 确认备注 (可空) */
    @Column(name = "acknowledge_note", length = 500)
    private String acknowledgeNote;

    /** 恢复人 (可空) */
    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    /** 恢复备注 (可空) */
    @Column(name = "resolved_note", length = 500)
    private String resolvedNote;

    /** 恢复方式: AUTO/MANUAL/MAINTENANCE/SUPPRESSED (可空) */
    @Column(name = "resolution_type", length = 20)
    private String resolutionType;

    /** 通知发送数 (默认 0) */
    @Column(name = "notifications_sent")
    private Integer notificationsSent;

    /** 通知失败数 (默认 0) */
    @Column(name = "notification_failures")
    private Integer notificationFailures;

    /** 最近通知时间 (可空) */
    @Column(name = "last_notification_at")
    private LocalDateTime lastNotificationAt;

    /** 是否已升级 (默认 FALSE) */
    @Column(name = "escalated", nullable = false)
    private Boolean escalated;

    /** 升级时间 (可空) */
    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    /** 升级接收人 (可空) */
    @Column(name = "escalated_to", length = 500)
    private String escalatedTo;

    /** 行动项 (可空) */
    @Column(name = "action_items", length = 1000)
    private String actionItems;

    /** 附加数据 JSON (可空) */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /** 相关联事件 (可空) */
    @Column(name = "related_event_ids", length = 500)
    private String relatedEventIds;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
