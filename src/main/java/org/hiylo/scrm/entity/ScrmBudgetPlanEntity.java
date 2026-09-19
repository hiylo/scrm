/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBudgetPlanEntity.java
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
 * SCRM 营销预算方案实体。
 * <p>
 * 按财年与财年周期 (ANNUAL/SEMIANNUAL/QUARTERLY/MONTHLY) 设定营销预算, 支持预算类型
 * (MARKETING/ADVERTISING/PROMOTION/CONTENT/EVENT/CHANNEL/TEAM/PROJECT)、总预算/已分配/已消耗/剩余
 * 预算统计、分配率与消耗率、预警阈值与预警触发标记、审批信息。状态流转: DRAFT / ACTIVE /
 * PAUSED / EXPIRED / CLOSED。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_budget_plan", schema = "scrm", indexes = {
        @Index(name = "idx_budget_plan_code", columnList = "plan_code"),
        @Index(name = "idx_budget_plan_type", columnList = "budget_type"),
        @Index(name = "idx_budget_plan_status", columnList = "status"),
        @Index(name = "idx_budget_plan_period", columnList = "period_start,period_end"),
        @Index(name = "idx_budget_plan_fiscal", columnList = "fiscal_year,fiscal_period")
})
@Data
public class ScrmBudgetPlanEntity {

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
        if (status == null) {
            status = "DRAFT";
        }
        if (totalBudget == null) {
            totalBudget = 0d;
        }
        if (allocatedBudget == null) {
            allocatedBudget = 0d;
        }
        if (spentBudget == null) {
            spentBudget = 0d;
        }
        if (remainingBudget == null) {
            remainingBudget = 0d;
        }
        if (allocationRate == null) {
            allocationRate = 0d;
        }
        if (spendRate == null) {
            spendRate = 0d;
        }
        if (currency == null) {
            currency = "CNY";
        }
        if (approvedAmount == null) {
            approvedAmount = 0d;
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

    /** 预算方案名称 */
    @Column(name = "plan_name", nullable = false, length = 200)
    private String planName;

    /** 预算方案编码 (唯一) */
    @Column(name = "plan_code", nullable = false, length = 50, unique = true)
    private String planCode;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 财年 */
    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    /** 财年周期: ANNUAL/SEMIANNUAL/QUARTERLY/MONTHLY */
    @Column(name = "fiscal_period", nullable = false, length = 20)
    private String fiscalPeriod;

    /** 周期开始日期 */
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    /** 周期结束日期 */
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /** 总预算 */
    @Column(name = "total_budget", nullable = false)
    private Double totalBudget;

    /** 已分配预算 */
    @Column(name = "allocated_budget")
    private Double allocatedBudget;

    /** 已消耗预算 */
    @Column(name = "spent_budget")
    private Double spentBudget;

    /** 剩余预算 */
    @Column(name = "remaining_budget")
    private Double remainingBudget;

    /** 分配率 (已分配/总预算) */
    @Column(name = "allocation_rate")
    private Double allocationRate;

    /** 消耗率 (已消耗/总预算) */
    @Column(name = "spend_rate")
    private Double spendRate;

    /** 币种 (默认 CNY) */
    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    /** 预算类型: MARKETING/ADVERTISING/PROMOTION/CONTENT/EVENT/CHANNEL/TEAM/PROJECT */
    @Column(name = "budget_type", nullable = false, length = 30)
    private String budgetType;

    /** 部门 (逗号分隔, 可空) */
    @Column(name = "departments", length = 500)
    private String departments;

    /** 状态: DRAFT/ACTIVE/PAUSED/EXPIRED/CLOSED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 审批人 (可空) */
    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    /** 审批时间 (可空) */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** 审批金额 */
    @Column(name = "approved_amount")
    private Double approvedAmount;

    /** 预警阈值 (0-1, 默认 0.8) */
    @Column(name = "alert_threshold")
    private Double alertThreshold;

    /** 是否已触发预警 */
    @Column(name = "is_alert_triggered", nullable = false)
    private Boolean isAlertTriggered;

    /** 最近预警时间 (可空) */
    @Column(name = "last_alert_at")
    private LocalDateTime lastAlertAt;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
