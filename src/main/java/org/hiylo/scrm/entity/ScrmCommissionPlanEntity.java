/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommissionPlanEntity.java
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
 * SCRM 销售佣金方案实体。
 * <p>
 * 按方案类型 (REVENUE_BASED/PROFIT_BASED/QUOTA_BASED/TIERED/BONUS/COMBO) 与计算基础
 * (ORDER_AMOUNT/ORDER_PROFIT/ORDER_COUNT/REVENUE_TARGET/UNITS_SOLD) 设定佣金规则,
 * 支持起止周期、目标金额、佣金上下限、退款追回天数、发放频率 (周/双周/月/季/按需)、
 * 适用产品与团队、是否默认方案、已发放佣金与销售总额统计。
 * 状态流转: ACTIVE / PAUSED / EXPIRED / DRAFT。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_commission_plan", schema = "scrm", indexes = {
        @Index(name = "idx_commission_plan_code", columnList = "plan_code"),
        @Index(name = "idx_commission_plan_type", columnList = "plan_type"),
        @Index(name = "idx_commission_plan_status", columnList = "status"),
        @Index(name = "idx_commission_plan_period", columnList = "start_date,end_date"),
        @Index(name = "idx_commission_plan_default", columnList = "is_default")
})
@Data
public class ScrmCommissionPlanEntity {

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
            status = "ACTIVE";
        }
        if (targetAmount == null) {
            targetAmount = 0d;
        }
        if (capAmount == null) {
            capAmount = 0d;
        }
        if (minAmount == null) {
            minAmount = 0d;
        }
        if (clawbackDays == null) {
            clawbackDays = 0;
        }
        if (payoutFrequency == null) {
            payoutFrequency = "MONTHLY";
        }
        if (payoutDay == null) {
            payoutDay = 15;
        }
        if (isDefault == null) {
            isDefault = false;
        }
        if (totalCommissionPaid == null) {
            totalCommissionPaid = 0d;
        }
        if (totalSalesAmount == null) {
            totalSalesAmount = 0d;
        }
        if (totalOrders == null) {
            totalOrders = 0;
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

    /** 方案名称 */
    @Column(name = "plan_name", nullable = false, length = 200)
    private String planName;

    /** 方案编码 (唯一) */
    @Column(name = "plan_code", nullable = false, length = 50, unique = true)
    private String planCode;

    /** 方案描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 方案类型: REVENUE_BASED/PROFIT_BASED/QUOTA_BASED/TIERED/BONUS/COMBO */
    @Column(name = "plan_type", nullable = false, length = 30)
    private String planType;

    /** 计算基础: ORDER_AMOUNT/ORDER_PROFIT/ORDER_COUNT/REVENUE_TARGET/UNITS_SOLD */
    @Column(name = "calculation_basis", nullable = false, length = 30)
    private String calculationBasis;

    /** 生效开始日期 */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 生效结束日期 (可空, 表示长期有效) */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** 状态: ACTIVE/PAUSED/EXPIRED/DRAFT */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 目标金额 (用于 QUOTA_BASED 类型) */
    @Column(name = "target_amount")
    private Double targetAmount;

    /** 佣金上限 (0 表示无限) */
    @Column(name = "cap_amount")
    private Double capAmount;

    /** 佣金下限 */
    @Column(name = "min_amount")
    private Double minAmount;

    /** 退款追回天数 */
    @Column(name = "clawback_days")
    private Integer clawbackDays;

    /** 发放频率: WEEKLY/BIWEEKLY/MONTHLY/QUARTERLY/ON_DEMAND */
    @Column(name = "payout_frequency", nullable = false, length = 20)
    private String payoutFrequency;

    /** 发放日 (1-31) */
    @Column(name = "payout_day")
    private Integer payoutDay;

    /** 适用产品 (逗号分隔) */
    @Column(name = "applicable_products", length = 500)
    private String applicableProducts;

    /** 适用团队 (逗号分隔) */
    @Column(name = "applicable_teams", length = 500)
    private String applicableTeams;

    /** 是否为默认方案 */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    /** 已发放佣金总额 */
    @Column(name = "total_commission_paid")
    private Double totalCommissionPaid;

    /** 已计算销售总额 */
    @Column(name = "total_sales_amount")
    private Double totalSalesAmount;

    /** 已计算订单总数 */
    @Column(name = "total_orders")
    private Integer totalOrders;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
