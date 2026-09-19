/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAlertRuleEntity.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
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
 * SCRM 告警规则实体。
 * <p>
 * 描述一项告警规则: 关联监控指标 (metricId)、条件操作符 (condition) 与阈值
 * (thresholdValue / thresholdValue2)、严重程度 (severity)、持续触发与冷却控制
 * (durationSeconds / evaluationPeriods / cooldownMinutes)、通知渠道与接收人
 * (notificationChannels / recipients / escalationRecipients)、升级与自动恢复策略
 * (escalationAfterMinutes / autoResolve)。
 * </p>
 * <p>
 * 条件操作符: GT / GTE / LT / LTE / EQ / NE / CONTAINS / NOT_CONTAINS。
 * 严重程度: INFO / WARNING / CRITICAL / FATAL。
 * 通知渠道: EMAIL / SMS / WECHAT / WEBHOOK / APP_PUSH / PHONE。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_alert_rule", schema = "scrm", indexes = {
        @Index(name = "idx_alert_rule_code", columnList = "rule_code", unique = true),
        @Index(name = "idx_alert_rule_metric", columnList = "metric_id"),
        @Index(name = "idx_alert_rule_severity", columnList = "severity"),
        @Index(name = "idx_alert_rule_enabled", columnList = "enabled")
})
@Data
public class ScrmAlertRuleEntity {

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

    /** 规则名称 */
    @Column(name = "rule_name", nullable = false, length = 200)
    private String ruleName;

    /** 规则编码 (全局唯一) */
    @Column(name = "rule_code", nullable = false, length = 50)
    private String ruleCode;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 关联指标 ID */
    @Column(name = "metric_id", nullable = false)
    private Long metricId;

    /** 指标名称 (可空, 快照) */
    @Column(name = "metric_name", length = 200)
    private String metricName;

    /** 指标编码 (可空, 快照) */
    @Column(name = "metric_code", length = 50)
    private String metricCode;

    /** 条件操作符: GT/GTE/LT/LTE/EQ/NE/CONTAINS/NOT_CONTAINS */
    @Column(name = "condition", nullable = false, length = 20)
    private String condition;

    /** 阈值 */
    @Column(name = "threshold_value", nullable = false)
    private Double thresholdValue;

    /** 第二阈值 (范围用, 默认 0) */
    @Column(name = "threshold_value2")
    private Double thresholdValue2;

    /** 严重程度: INFO/WARNING/CRITICAL/FATAL (默认 WARNING) */
    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    /** 持续时间秒 (0=立即, 默认 0) */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /** 连续触发次数 (默认 1) */
    @Column(name = "evaluation_periods")
    private Integer evaluationPeriods;

    /** 冷却分钟 (默认 30) */
    @Column(name = "cooldown_minutes")
    private Integer cooldownMinutes;

    /** 通知渠道 (逗号分隔): EMAIL/SMS/WECHAT/WEBHOOK/APP_PUSH/PHONE */
    @Column(name = "notification_channels", nullable = false, length = 500)
    private String notificationChannels;

    /** 通知模板 ID (可空) */
    @Column(name = "notification_template_id")
    private Long notificationTemplateId;

    /** 接收人 (可空, 逗号分隔) */
    @Column(name = "recipients", length = 1000)
    private String recipients;

    /** 升级接收人 (可空) */
    @Column(name = "escalation_recipients", length = 1000)
    private String escalationRecipients;

    /** 升级时间分钟 (默认 60) */
    @Column(name = "escalation_after_minutes")
    private Integer escalationAfterMinutes;

    /** 自动恢复 (默认 TRUE) */
    @Column(name = "auto_resolve", nullable = false)
    private Boolean autoResolve;

    /** 自动恢复消息 (可空) */
    @Column(name = "auto_resolve_message", length = 500)
    private String autoResolveMessage;

    /** 是否启用 (默认 TRUE) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 触发次数 (默认 0) */
    @Column(name = "trigger_count")
    private Integer triggerCount;

    /** 最近触发时间 (可空) */
    @Column(name = "last_triggered_at")
    private LocalDateTime lastTriggeredAt;

    /** 最近恢复时间 (可空) */
    @Column(name = "last_resolved_at")
    private LocalDateTime lastResolvedAt;

    /** 标签 (可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
