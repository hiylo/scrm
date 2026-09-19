/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRiskRuleEntity.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.hibernate.annotations.GenericGenerator;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 风险规则实体。
 * <p>
 * 通过 SpEL 表达式定义会话消息维度的风险判定条件, 在会话事件回调时由
 * {@code RiskRuleEvaluator} 评估, 命中后写入 {@link ScrmRiskSignalEntity} 并推送告警。
 * 规则按 priority 升序评估 (数字越小越优先), enabled=false 的规则跳过。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_risk_rule", schema = "scrm", indexes = {
        @Index(name = "idx_risk_rule_code", columnList = "rule_code", unique = true),
        @Index(name = "idx_risk_rule_enabled", columnList = "enabled"),
        @Index(name = "idx_risk_rule_priority", columnList = "priority")
})
@Data
public class ScrmRiskRuleEntity {

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
    @Column(name = "rule_name", length = 200, nullable = false)
    private String ruleName;

    /** 规则代码（业务唯一, 便于引用与去重） */
    @Column(name = "rule_code", length = 100, nullable = false, unique = true)
    private String ruleCode;

    /** SpEL 条件表达式（引用 RiskRuleContext 中的变量, 如 #message.contains('微信')） */
    @Column(name = "condition_expression", columnDefinition = "TEXT", nullable = false)
    private String conditionExpression;

    /** 风险等级: LOW / MEDIUM / HIGH / CRITICAL */
    @Column(name = "risk_level", length = 20, nullable = false)
    private String riskLevel;

    /** 信号类型（如 frequency_overflow / keyword_match / time_anomaly） */
    @Column(name = "signal_type", length = 50, nullable = false)
    private String signalType;

    /** 规则描述 */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** 是否启用 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 优先级（数字越小越优先, 默认 100） */
    @Column(name = "priority")
    private Integer priority;

    /** 触发后动作: ALERT / PAUSE_ACCOUNT / STOP_CAMPAIGN（默认 ALERT） */
    @Column(name = "action", length = 50)
    private String action;
}
