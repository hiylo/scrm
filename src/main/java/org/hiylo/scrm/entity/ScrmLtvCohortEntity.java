/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLtvCohortEntity.java
 * Date : 2026/08/05 08:55:12
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM LTV 分组分析实体。
 * <p>
 * 按获客分组类型 (获客月份/获客渠道/客户层级/地域) 对客户进行同期群分组,
 * 统计各分组的客户数、平均/中位 LTV、总收入、留存率、活跃与流失客户数等,
 * 用于横向对比不同分组客户的生命周期价值与留存表现。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_ltv_cohort", schema = "scrm", indexes = {
        @Index(name = "idx_ltv_cohort_type", columnList = "cohort_type"),
        @Index(name = "idx_ltv_cohort_key", columnList = "cohort_type,cohort_key")
})
@Data
public class ScrmLtvCohortEntity {

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

    /** 分组名称 */
    @Column(name = "cohort_name", nullable = false, length = 200)
    private String cohortName;

    /** 获客分组类型: ACQUISITION_MONTH / ACQUISITION_CHANNEL / CUSTOMER_TIER / GEOGRAPHY */
    @Column(name = "cohort_type", nullable = false, length = 30)
    private String cohortType;

    /** 分组键值 (如 2026-01 / WECHAT / VIP / 华东) */
    @Column(name = "cohort_key", nullable = false, length = 200)
    private String cohortKey;

    /** 分组开始日期 */
    @Column(name = "cohort_start_date", nullable = false)
    private LocalDate cohortStartDate;

    /** 分组客户数 */
    @Column(name = "cohort_size", nullable = false)
    private Integer cohortSize;

    /** 周期月数 (该分组分析覆盖的月数) */
    @Column(name = "period_months", nullable = false)
    private Integer periodMonths;

    /** 平均 LTV */
    @Column(name = "avg_ltv")
    private Double avgLtv;

    /** 中位 LTV */
    @Column(name = "median_ltv")
    private Double medianLtv;

    /** 总收入 */
    @Column(name = "total_revenue")
    private Double totalRevenue;

    /** 平均收入 */
    @Column(name = "avg_revenue")
    private Double avgRevenue;

    /** 平均订单数 */
    @Column(name = "avg_orders")
    private Integer avgOrders;

    /** 留存率 (0-1) */
    @Column(name = "retention_rate")
    private Double retentionRate;

    /** 活跃客户数 */
    @Column(name = "active_customers")
    private Integer activeCustomers;

    /** 流失客户数 */
    @Column(name = "churned_customers")
    private Integer churnedCustomers;

    /** 平均客户年龄天数 */
    @Column(name = "avg_customer_age_days")
    private Integer avgCustomerAgeDays;

    /** 高价值客户数 (VIP/HIGH 层级) */
    @Column(name = "top_tier_customers")
    private Integer topTierCustomers;

    /** 计算时间 */
    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;
}
