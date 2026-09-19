/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBudgetAllocationEntity.java
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 营销预算分配实体。
 * <p>
 * 将预算方案按分配类型 (DEPARTMENT/CHANNEL/CAMPAIGN/TEAM/PRODUCT/REGION) 分配到具体目标,
 * 记录分配金额/已消耗/剩余/消耗率、预警阈值与预警触发标记。状态流转: ACTIVE / PAUSED /
 * EXHAUSTED / CLOSED。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_budget_allocation", schema = "scrm", indexes = {
        @Index(name = "idx_budget_allocation_plan", columnList = "plan_id"),
        @Index(name = "idx_budget_allocation_type", columnList = "allocation_type"),
        @Index(name = "idx_budget_allocation_target", columnList = "target_type"),
        @Index(name = "idx_budget_allocation_status", columnList = "status"),
        @Index(name = "idx_budget_allocation_period", columnList = "period_start,period_end")
})
@Data
public class ScrmBudgetAllocationEntity {

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
        if (allocatedAmount == null) {
            allocatedAmount = 0d;
        }
        if (spentAmount == null) {
            spentAmount = 0d;
        }
        if (remainingAmount == null) {
            remainingAmount = 0d;
        }
        if (spendRate == null) {
            spendRate = 0d;
        }
        if (status == null) {
            status = "ACTIVE";
        }
        if (alertThreshold == null) {
            alertThreshold = 0.8d;
        }
        if (isAlertTriggered == null) {
            isAlertTriggered = false;
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

    /** 预算方案 ID */
    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /** 预算方案名称 (冗余便于展示, 可空) */
    @Column(name = "plan_name", length = 200)
    private String planName;

    /** 分配名称 */
    @Column(name = "allocation_name", nullable = false, length = 200)
    private String allocationName;

    /** 分配类型: DEPARTMENT/CHANNEL/CAMPAIGN/TEAM/PRODUCT/REGION */
    @Column(name = "allocation_type", nullable = false, length = 30)
    private String allocationType;

    /** 目标类型值 (如部门 ID / 渠道名) */
    @Column(name = "target_type", nullable = false, length = 100)
    private String targetType;

    /** 目标名称 (可空) */
    @Column(name = "target_name", length = 200)
    private String targetName;

    /** 分配金额 */
    @Column(name = "allocated_amount", nullable = false)
    private Double allocatedAmount;

    /** 已消耗金额 */
    @Column(name = "spent_amount")
    private Double spentAmount;

    /** 剩余金额 */
    @Column(name = "remaining_amount")
    private Double remainingAmount;

    /** 消耗率 (已消耗/分配金额) */
    @Column(name = "spend_rate")
    private Double spendRate;

    /** 周期开始日期 */
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    /** 周期结束日期 */
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /** 状态: ACTIVE/PAUSED/EXHAUSTED/CLOSED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 预警阈值 (0-1, 默认 0.8) */
    @Column(name = "alert_threshold")
    private Double alertThreshold;

    /** 是否已触发预警 */
    @Column(name = "is_alert_triggered", nullable = false)
    private Boolean isAlertTriggered;

    /** 最近预警时间 (可空) */
    @Column(name = "last_alert_at")
    private LocalDateTime lastAlertAt;

    /** 备注 (可空) */
    @Column(name = "notes", length = 500)
    private String notes;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
