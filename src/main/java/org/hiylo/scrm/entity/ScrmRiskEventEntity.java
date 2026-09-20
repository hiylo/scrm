/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRiskEventEntity.java
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
 * SCRM 风险事件实体。
 * <p>
 * 记录风控规则命中后产生的风险事件, 涵盖触发原因 / 风险等级 / 风险评分 /
 * 检测方式 / 执行动作 / 调查处理 / 升级等全生命周期信息。{@link #eventNo} 为业务唯一编号,
 * 由 {@code ScrmBlacklistService.generateEventNo()} 生成 (RISK + 年月日 + 序号)。
 * 状态流转: OPEN → INVESTIGATING → CONFIRMED / FALSE_POSITIVE → RESOLVED / ESCALATED。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_risk_event", schema = "scrm", indexes = {
        @Index(name = "idx_risk_event_no", columnList = "event_no", unique = true),
        @Index(name = "idx_risk_event_rule", columnList = "rule_id"),
        @Index(name = "idx_risk_event_customer", columnList = "customer_id"),
        @Index(name = "idx_risk_event_category", columnList = "risk_category"),
        @Index(name = "idx_risk_event_level", columnList = "risk_level"),
        @Index(name = "idx_risk_event_status", columnList = "status"),
        @Index(name = "idx_risk_event_trigger_time", columnList = "trigger_time")
})
@Data
public class ScrmRiskEventEntity {

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

    /** 事件编号 (业务唯一) */
    @Column(name = "event_no", nullable = false, length = 100)
    private String eventNo;

    /** 触发规则 ID (可空) */
    @Column(name = "rule_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ruleId;

    /** 触发规则名称 (可空, 冗余便于展示) */
    @Column(name = "rule_name", length = 200)
    private String ruleName;

    /** 触发规则代码 (可空) */
    @Column(name = "rule_code", length = 50)
    private String ruleCode;

    /** 客户 ID (可空) */
    @Column(name = "customer_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 目标类型: CUSTOMER / PHONE / EMAIL / IP / DEVICE / ID_CARD / BANK_CARD / ADDRESS / WECHAT_ID / COMPANY */
    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    /** 目标值 */
    @Column(name = "target_value", nullable = false, length = 500)
    private String targetValue;

    /** 风险类别: FRAUD / ABUSE / SPAM / HARASSMENT / FAKE / VIOLATION / POLICY / SECURITY */
    @Column(name = "risk_category", nullable = false, length = 50)
    private String riskCategory;

    /** 风险等级: LOW / MEDIUM / HIGH / CRITICAL */
    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel;

    /** 风险评分 0-100 (默认 0) */
    @Column(name = "risk_score", nullable = false)
    private Double riskScore;

    /** 触发原因 */
    @Column(name = "trigger_reason", nullable = false, length = 1000)
    private String triggerReason;

    /** JSON 触发数据 (可空): {field, value, condition, expected} */
    @Column(name = "trigger_data", columnDefinition = "TEXT")
    private String triggerData;

    /** 触发时间 */
    @Column(name = "trigger_time", nullable = false)
    private LocalDateTime triggerTime;

    /** 检测者 (可空) */
    @Column(name = "detected_by", length = 100)
    private String detectedBy;

    /** 检测方式 (可空): RULE / AUTO / MANUAL / EXTERNAL */
    @Column(name = "detection_method", length = 50)
    private String detectionMethod;

    /** 执行动作: ALERT / BLOCK / REVIEW / QUARANTINE / AUTO_BLACKLIST / NOTIFY (默认 ALERT) */
    @Column(name = "action", nullable = false, length = 30)
    private String action;

    /** 动作状态: PENDING / EXECUTED / FAILED / OVERRIDDEN (默认 PENDING) */
    @Column(name = "action_status", nullable = false, length = 20)
    private String actionStatus;

    /** 动作执行时间 (可空) */
    @Column(name = "action_executed_at")
    private LocalDateTime actionExecutedAt;

    /** 执行结果 (可空) */
    @Column(name = "action_result", length = 500)
    private String actionResult;

    /** 被阻止的行为 (可空) */
    @Column(name = "blocked_action", length = 500)
    private String blockedAction;

    /** 状态: OPEN / INVESTIGATING / CONFIRMED / FALSE_POSITIVE / RESOLVED / ESCALATED (默认 OPEN) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 处理人 (可空) */
    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    /** 分配时间 (可空) */
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    /** 调查人 (可空) */
    @Column(name = "investigated_by", length = 100)
    private String investigatedBy;

    /** 调查时间 (可空) */
    @Column(name = "investigated_at")
    private LocalDateTime investigatedAt;

    /** 调查备注 (可空) */
    @Column(name = "investigation_notes", length = 2000)
    private String investigationNotes;

    /** 确认风险 (可空) */
    @Column(name = "confirmed_risk")
    private Boolean confirmedRisk;

    /** 处理结果 (可空) */
    @Column(name = "resolution", length = 1000)
    private String resolution;

    /** 解决人 (可空) */
    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    /** 解决时间 (可空) */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /** 处理时长小时 (默认 0) */
    @Column(name = "resolution_time_hours")
    private Integer resolutionTimeHours;

    /** 是否误报 (默认 FALSE) */
    @Column(name = "is_false_positive", nullable = false)
    private Boolean isFalsePositive;

    /** 升级给 (可空) */
    @Column(name = "escalated_to", length = 100)
    private String escalatedTo;

    /** 升级时间 (可空) */
    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    /** 相关联事件 (可空, 逗号分隔 ID) */
    @Column(name = "related_event_ids", length = 500)
    private String relatedEventIds;

    /** 受影响实体 (可空) */
    @Column(name = "affected_entities", length = 500)
    private String affectedEntities;

    /** 影响评估 (可空) */
    @Column(name = "impact_assessment", length = 1000)
    private String impactAssessment;

    /** 已发送通知数 (默认 0) */
    @Column(name = "notifications_sent")
    private Integer notificationsSent;

    /** JSON 附加数据 (可空) */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /** 标签 (可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
