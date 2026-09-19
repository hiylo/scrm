/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRiskSignalEntity.java
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
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.hibernate.annotations.GenericGenerator;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 风控信号实体。
 * <p>
 * scrm-server 命中风控规则后通过回调写入 SCRM，用于账号风控态势感知与看板聚合。
 * 每条记录对应一次风险触发事件，关联人设 / 账号便于追溯。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_risk_signal", schema = "scrm", indexes = {
        @Index(name = "idx_risk_signal_account", columnList = "account_id"),
        @Index(name = "idx_risk_signal_persona", columnList = "persona_id"),
        @Index(name = "idx_risk_signal_triggered_at", columnList = "triggered_at")
})
@Data
public class ScrmRiskSignalEntity {

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

    /** 乐观锁版本号（并发更新保护，后写入者触发 OptimisticLockException） */
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

    /** 触发的风险规则 ID */
    @Column(name = "rule_id", length = 100)
    private String ruleId;

    /** 关联的人设 ID */
    @Column(name = "persona_id", length = 100)
    private String personaId;

    /** 关联的账号 ID（引用 scrm_account.id，可空） */
    @Column(name = "account_id")
    private Long accountId;

    /** 信号类型（如 login_anomaly / frequency_overflow） */
    @Column(name = "signal_type", length = 50)
    private String signalType;

    /** 风险等级：LOW / MEDIUM / HIGH / CRITICAL */
    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    /** 风险详情描述 */
    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    /** 风险触发时间 */
    @Column(name = "triggered_at")
    private LocalDateTime triggeredAt;

    /** 处理状态: PENDING(待处理) / RESOLVED(已处理) / IGNORED(已忽略) */
    @Column(name = "status", length = 20)
    private String status = "PENDING";

    /** 创建时间 (内存字段: 本表无 created_at 列, 触发时间由 triggered_at 承担) */
    @Transient
    private LocalDateTime createdAt;

    /** 更新时间 (内存字段: 本表无 updated_at 列) */
    @Transient
    private LocalDateTime updatedAt;

    /** 处理时间 (标记为已处理/已忽略时记录) */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /** 处理人用户名 */
    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    /** 处理备注 (处理时填写的原因/措施说明) */
    @Column(name = "resolve_remark", columnDefinition = "TEXT")
    private String resolveRemark;
}
