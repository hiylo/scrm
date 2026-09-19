/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBlacklistRuleEntity.java
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
 * SCRM 黑名单风控规则实体。
 * <p>
 * 通过条件字段 / 操作符 / 条件值定义风控判定规则, 在风险评估时由
 * {@code ScrmBlacklistService} 评估, 命中后触发风险事件并执行动作
 * (ALERT / BLOCK / REVIEW / QUARANTINE / AUTO_BLACKLIST / NOTIFY)。
 * {@link #ruleCode} 为唯一编码, 规则按 {@link #priority} 升序评估 (数字越小越优先)。
 * </p>
 * <p>
 * 注: 表名 {@code scrm_blacklist_rule} 与既有会话级 SpEL 风险规则表
 * {@code scrm_risk_rule} 区分, 互不影响。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_blacklist_rule", schema = "scrm", indexes = {
        @Index(name = "idx_blacklist_rule_code", columnList = "rule_code", unique = true),
        @Index(name = "idx_blacklist_rule_type", columnList = "rule_type"),
        @Index(name = "idx_blacklist_rule_category", columnList = "risk_category"),
        @Index(name = "idx_blacklist_rule_severity", columnList = "severity"),
        @Index(name = "idx_blacklist_rule_enabled", columnList = "enabled")
})
@Data
public class ScrmBlacklistRuleEntity {

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

    /** 规则代码 (唯一) */
    @Column(name = "rule_code", nullable = false, length = 50)
    private String ruleCode;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

/** 规则类型: FREQUENCY / AMOUNT / BEHAVIOR / PATTERN / BLACKLIST_MATCH / COMPOSITE / TIME / LOCATION / DEVICE /
         * TRANSACTION */
    @Column(name = "rule_type", nullable = false, length = 30)
    private String ruleType;

    /** 风险类别: FRAUD / ABUSE / SPAM / HARASSMENT / FAKE / VIOLATION / POLICY / SECURITY */
    @Column(name = "risk_category", nullable = false, length = 50)
    private String riskCategory;

    /** 检查字段 */
    @Column(name = "condition_field", nullable = false, length = 100)
    private String conditionField;

    /** 条件操作符: GT / GTE / LT / LTE / EQ / NE / CONTAINS / NOT_CONTAINS / IN / NOT_IN / REGEX / MATCH */
    @Column(name = "condition_operator", nullable = false, length = 20)
    private String conditionOperator;

    /** 条件值 */
    @Column(name = "condition_value", nullable = false, length = 1000)
    private String conditionValue;

    /** 第二条件值 (可空, 用于区间判断) */
    @Column(name = "condition_value2", length = 500)
    private String conditionValue2;

    /** 时间窗口分钟 (可空) */
    @Column(name = "time_window_minutes")
    private Integer timeWindowMinutes;

    /** 阈值次数 (可空) */
    @Column(name = "threshold_count")
    private Integer thresholdCount;

    /** 阈值金额 (可空) */
    @Column(name = "threshold_amount")
    private Double thresholdAmount;

    /** 严重程度: LOW / MEDIUM / HIGH / CRITICAL (默认 MEDIUM) */
    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    /** 执行动作: ALERT / BLOCK / REVIEW / QUARANTINE / AUTO_BLACKLIST / NOTIFY (默认 ALERT) */
    @Column(name = "action", nullable = false, length = 30)
    private String action;

    /** JSON 执行参数 (可空) */
    @Column(name = "action_params", length = 1000)
    private String actionParams;

    /** 适用模块 (可空) */
    @Column(name = "applicable_modules", length = 500)
    private String applicableModules;

    /** 适用场景 (可空) */
    @Column(name = "applicable_scenarios", length = 500)
    private String applicableScenarios;

    /** 匹配后加入名单类型 (可空): BLACKLIST / GRAYLIST / WATCHLIST */
    @Column(name = "target_list_type", length = 20)
    private String targetListType;

    /** 通知渠道 (可空) */
    @Column(name = "notification_channels", length = 500)
    private String notificationChannels;

    /** 通知接收人 (可空) */
    @Column(name = "notification_recipients", length = 500)
    private String notificationRecipients;

    /** 是否启用 (默认 TRUE) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 优先级 (默认 0, 数字越小越优先) */
    @Column(name = "priority")
    private Integer priority;

    /** 触发次数 (默认 0) */
    @Column(name = "trigger_count")
    private Integer triggerCount;

    /** 最近触发时间 (可空) */
    @Column(name = "last_triggered_at")
    private LocalDateTime lastTriggeredAt;

    /** 误报次数 (默认 0) */
    @Column(name = "false_positive_count")
    private Integer falsePositiveCount;

    /** 误报率 (%) (默认 0) */
    @Column(name = "false_positive_rate")
    private Double falsePositiveRate;

    /** 准确率 (%) (默认 0) */
    @Column(name = "accuracy_rate")
    private Double accuracyRate;

    /** 最近评估时间 (可空) */
    @Column(name = "last_evaluated_at")
    private LocalDateTime lastEvaluatedAt;

    /** 评估次数 (默认 0) */
    @Column(name = "evaluation_count")
    private Integer evaluationCount;

    /** 标签 (可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
