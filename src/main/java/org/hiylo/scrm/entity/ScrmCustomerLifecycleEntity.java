/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLifecycleEntity.java
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
 * SCRM 客户当前生命周期阶段实体。
 * <p>
 * 记录单个客户当前所处的生命周期阶段: {@link #currentStageId} / {@link #currentStageCode}
 * / {@link #currentStageName} 标识当前阶段, {@link #enteredCurrentStageAt} /
 * {@link #durationInStageDays} 记录进入时间与停留天数, {@link #previousStageId} /
 * {@link #previousStageCode} 保留上一阶段信息, {@link #stageHistoryCount} 累计变更次数。
 * {@link #isOverdue} / {@link #overdueDays} 基于阶段目标停留天数判定超期,
 * {@link #nextStageId} / {@link #expectedTransitionAt} 预期下一阶段与转换时间。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_customer_lifecycle", schema = "scrm", indexes = {
        @Index(name = "idx_customer_lifecycle_customer", columnList = "customer_id", unique = true),
        @Index(name = "idx_customer_lifecycle_stage", columnList = "current_stage_id"),
        @Index(name = "idx_customer_lifecycle_overdue", columnList = "is_overdue")
})
@Data
public class ScrmCustomerLifecycleEntity {

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

    /** 客户名称 (可空) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 当前阶段 ID */
    @Column(name = "current_stage_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentStageId;

    /** 当前阶段编码 */
    @Column(name = "current_stage_code", nullable = false, length = 50)
    private String currentStageCode;

    /** 当前阶段名称 (可空) */
    @Column(name = "current_stage_name", length = 100)
    private String currentStageName;

    /** 进入当前阶段时间 */
    @Column(name = "entered_current_stage_at", nullable = false)
    private LocalDateTime enteredCurrentStageAt;

    /** 在当前阶段天数 (默认 0) */
    @Column(name = "duration_in_stage_days")
    private Integer durationInStageDays;

    /** 上一阶段 ID (可空) */
    @Column(name = "previous_stage_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long previousStageId;

    /** 上一阶段编码 (可空) */
    @Column(name = "previous_stage_code", length = 50)
    private String previousStageCode;

    /** 阶段变更次数 (默认 0) */
    @Column(name = "stage_history_count")
    private Integer stageHistoryCount;

    /** 是否超期 (默认 FALSE) */
    @Column(name = "is_overdue", nullable = false)
    private Boolean isOverdue;

    /** 超期天数 (默认 0) */
    @Column(name = "overdue_days")
    private Integer overdueDays;

    /** 预期下一阶段 ID (可空) */
    @Column(name = "next_stage_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long nextStageId;

    /** 预期转换时间 (可空) */
    @Column(name = "expected_transition_at")
    private LocalDateTime expectedTransitionAt;

    /** 负责人 (可空) */
    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    /** 备注 (可空) */
    @Column(name = "notes", length = 500)
    private String notes;

    /** 最近更新时间 (业务字段, 区别于 updateTime) */
    @Column(name = "last_updated_at", nullable = false)
    private LocalDateTime lastUpdatedAt;
}
