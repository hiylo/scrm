/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChurnRecoveryEntity.java
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
 * SCRM 客户流失挽留记录实体。
 * <p>
 * 记录针对某条流失预警 ({@link #warningId}) / 客户 ({@link #customerId}) 执行的挽留动作,
 * 用于跟踪挽留效果与激活率。{@link #recoveryAction} 标注动作类型 (FOLLOW_UP / MASS_SEND /
 * COUPON / CALL / VISIT / OTHER), {@link #result} 记录动作执行结果 (SUCCESS / FAILED / PENDING)。
 * </p>
 * <p>
 * 客户回应与激活状态由 {@link #customerResponded} / {@link #responseAt} / {@link #reactivated}
 * 跟踪, 由 {@code ScrmChurnWarningService.markReactivated} 标记最终激活结果。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_churn_recovery", schema = "scrm", indexes = {
        @Index(name = "idx_churn_recovery_warning", columnList = "warning_id"),
        @Index(name = "idx_churn_recovery_customer", columnList = "customer_id"),
        @Index(name = "idx_churn_recovery_result", columnList = "result"),
        @Index(name = "idx_churn_recovery_executed", columnList = "action_executed_at")
})
@Data
public class ScrmChurnRecoveryEntity {

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

    /** 关联预警 ID */
    @Column(name = "warning_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long warningId;

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (冗余, 便于列表展示) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 挽留动作: FOLLOW_UP / MASS_SEND / COUPON / CALL / VISIT / OTHER */
    @Column(name = "recovery_action", nullable = false, length = 30)
    private String recoveryAction;

    /** 动作详情 (可空) */
    @Column(name = "action_detail", length = 500)
    private String actionDetail;

    /** 动作执行时间 */
    @Column(name = "action_executed_at", nullable = false)
    private LocalDateTime actionExecutedAt;

    /** 执行人 */
    @Column(name = "executed_by", nullable = false, length = 100)
    private String executedBy;

    /** 动作结果: SUCCESS / FAILED / PENDING */
    @Column(name = "result", nullable = false, length = 20)
    private String result;

    /** 客户是否回应 (默认 false) */
    @Column(name = "customer_responded", nullable = false)
    private Boolean customerResponded;

    /** 客户回应时间 (可空) */
    @Column(name = "response_at")
    private LocalDateTime responseAt;

    /** 是否成功激活 (默认 false) */
    @Column(name = "reactivated", nullable = false)
    private Boolean reactivated;

    /** 备注 (可空) */
    @Column(name = "notes", length = 500)
    private String notes;
}
