/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChurnWarningEntity.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM 客户流失预警实体。
 * <p>
 * 由 {@code ScrmChurnWarningService.scanCustomer} 在规则命中后创建, 记录单次客户流失
 * 风险检测结果。{@link #riskScore} (0-100, 越高越危险) 为综合风险分, 由
 * {@link #riskFactors} (JSON 数组: [{factor, value, detail}]) 拆解贡献因子。
 * </p>
 * <p>
 * 状态流转: ACTIVE (待处理) → RESOLVED (已解决) / IGNORED (已忽略) / ESCALATED (已升级)。
 * {@link #actionType} 与命中规则一致, {@link #actionResult} / {@link #actionExecutedAt}
 * 记录动作执行回执。负责人 ({@link #assigneeId}) 可在分配后处理预警。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_churn_warning", schema = "scrm", indexes = {
        @Index(name = "idx_churn_warning_customer", columnList = "customer_id"),
        @Index(name = "idx_churn_warning_rule", columnList = "rule_id"),
        @Index(name = "idx_churn_warning_status", columnList = "status"),
        @Index(name = "idx_churn_warning_risk_level", columnList = "risk_level"),
        @Index(name = "idx_churn_warning_assignee", columnList = "assignee_id"),
        @Index(name = "idx_churn_warning_detected", columnList = "detected_at")
})
@Data
public class ScrmChurnWarningEntity {

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

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (冗余, 便于列表展示) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 触发规则 ID */
    @Column(name = "rule_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ruleId;

    /** 触发规则名称 (冗余) */
    @Column(name = "rule_name", length = 200)
    private String ruleName;

    /** 风险等级: HIGH / MEDIUM / LOW */
    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel;

    /** 风险分 (0-100, 越高越危险) */
    @Column(name = "risk_score", nullable = false)
    private Double riskScore;

    /** 风险因子列表 JSON: [{factor, value, detail}] */
    @Column(name = "risk_factors", nullable = false, columnDefinition = "TEXT")
    private String riskFactors;

    /** 预警状态: ACTIVE / RESOLVED / IGNORED / ESCALATED (默认 ACTIVE) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 预警动作类型 (与规则一致) */
    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    /** 动作执行结果 (可空) */
    @Column(name = "action_result", length = 500)
    private String actionResult;

    /** 动作执行时间 (可空) */
    @Column(name = "action_executed_at")
    private LocalDateTime actionExecutedAt;

    /** 负责人 ID (可空) */
    @Column(name = "assignee_id", length = 100)
    private String assigneeId;

    /** 负责人名称 (可空) */
    @Column(name = "assignee_name", length = 100)
    private String assigneeName;

    /** 处理说明 (可空) */
    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    /** 处理时间 (可空) */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /** 处理人 (可空) */
    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    /** 检测时间 */
    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    /** 最后互动时间 (可空) */
    @Column(name = "last_interaction_at")
    private LocalDateTime lastInteractionAt;
}
