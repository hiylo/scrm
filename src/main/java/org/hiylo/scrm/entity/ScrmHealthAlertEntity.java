/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmHealthAlertEntity.java
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
 * SCRM 客户健康度告警实体。
 * <p>
 * 记录由健康度评分触发的告警: {@link #alertType} (SCORE_DROP / LOW_SCORE / INACTIVITY /
 * PAYMENT_ISSUE / CHURN_RISK / SUPPORT_OVERLOAD / RISK_FACTOR / THRESHOLD_BREACH) 告警类型,
 * {@link #severity} (INFO / WARNING / URGENT / CRITICAL) 严重程度,
 * {@link #triggerValue} / {@link #thresholdValue} 触发值与阈值,
 * {@link #status} (ACTIVE / ACKNOWLEDGED / RESOLVED / DISMISSED) 状态。
 * </p>
 * <p>
 * 告警生命周期: ACTIVE (触发) → ACKNOWLEDGED (确认) → RESOLVED (解决) / DISMISSED (忽略)。
 * 通过 {@link #assignedTo} / {@link #acknowledgedBy} / {@link #resolvedBy} 跟踪处理人,
 * {@link #metadata} 携带 JSON 附加数据 (如客户当时评分快照)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_health_alert", schema = "scrm", indexes = {
        @Index(name = "idx_health_alert_customer", columnList = "customer_id"),
        @Index(name = "idx_health_alert_score", columnList = "health_score_id"),
        @Index(name = "idx_health_alert_type", columnList = "alert_type"),
        @Index(name = "idx_health_alert_severity", columnList = "severity"),
        @Index(name = "idx_health_alert_status", columnList = "status"),
        @Index(name = "idx_health_alert_triggered", columnList = "triggered_at"),
        @Index(name = "idx_health_alert_assigned", columnList = "assigned_to")
})
@Data
public class ScrmHealthAlertEntity {

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
        if (triggeredAt == null) {
            triggeredAt = now;
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

    /** 告警名称 */
    @Column(name = "alert_name", nullable = false, length = 200)
    private String alertName;

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (冗余, 便于列表展示) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 关联健康度评分 ID (可空) */
    @Column(name = "health_score_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long healthScoreId;

/** 告警类型: SCORE_DROP / LOW_SCORE / INACTIVITY / PAYMENT_ISSUE / CHURN_RISK / SUPPORT_OVERLOAD / RISK_FACTOR /
         * THRESHOLD_BREACH */
    @Column(name = "alert_type", nullable = false, length = 30)
    private String alertType;

    /** 严重程度: INFO / WARNING / URGENT / CRITICAL (默认 WARNING) */
    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    /** 触发值 */
    @Column(name = "trigger_value")
    private Double triggerValue;

    /** 阈值值 */
    @Column(name = "threshold_value")
    private Double thresholdValue;

    /** 触发条件 (可空) */
    @Column(name = "condition", length = 100)
    private String condition;

    /** 告警描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 风险因素 (逗号分隔, 可空) */
    @Column(name = "risk_factors", length = 1000)
    private String riskFactors;

    /** 建议动作 (逗号分隔, 可空) */
    @Column(name = "recommended_actions", length = 1000)
    private String recommendedActions;

    /** 状态: ACTIVE / ACKNOWLEDGED / RESOLVED / DISMISSED (默认 ACTIVE) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 分配给 (可空) */
    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    /** 分配时间 (可空) */
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    /** 确认人 (可空) */
    @Column(name = "acknowledged_by", length = 100)
    private String acknowledgedBy;

    /** 确认时间 (可空) */
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    /** 解决人 (可空) */
    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    /** 解决时间 (可空) */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /** 解决备注 (可空) */
    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    /** 触发时间 */
    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;

    /** 附加数据 JSON (可空) */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
