/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBudgetRoiEntity.java
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
 * SCRM 营销预算 ROI 实体。
 * <p>
 * 记录预算方案/分配/活动在指定周期内的 ROI 数据: 总支出、总收入、总利润、收入 ROI (收入/支出)、
 * 利润 ROI (利润/支出)、ROAS (收入/广告支出)、CPA/CPC/CPM/CAC/LTV/LTV-CAC 比率、转化/点击/展示
 * 数量与率、回本月数, breakdown 以 JSON 文本存储渠道/产品拆分。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_budget_roi", schema = "scrm", indexes = {
        @Index(name = "idx_budget_roi_plan", columnList = "plan_id"),
        @Index(name = "idx_budget_roi_allocation", columnList = "allocation_id"),
        @Index(name = "idx_budget_roi_campaign", columnList = "campaign_id"),
        @Index(name = "idx_budget_roi_period", columnList = "period"),
        @Index(name = "idx_budget_roi_calculated", columnList = "calculated_at")
})
@Data
public class ScrmBudgetRoiEntity {

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
        if (totalSpend == null) {
            totalSpend = 0d;
        }
        if (totalRevenue == null) {
            totalRevenue = 0d;
        }
        if (totalProfit == null) {
            totalProfit = 0d;
        }
        if (revenueRoi == null) {
            revenueRoi = 0d;
        }
        if (profitRoi == null) {
            profitRoi = 0d;
        }
        if (roas == null) {
            roas = 0d;
        }
        if (cpa == null) {
            cpa = 0d;
        }
        if (cpc == null) {
            cpc = 0d;
        }
        if (cpm == null) {
            cpm = 0d;
        }
        if (cac == null) {
            cac = 0d;
        }
        if (ltv == null) {
            ltv = 0d;
        }
        if (ltvCacRatio == null) {
            ltvCacRatio = 0d;
        }
        if (conversions == null) {
            conversions = 0;
        }
        if (clicks == null) {
            clicks = 0;
        }
        if (impressions == null) {
            impressions = 0;
        }
        if (conversionRate == null) {
            conversionRate = 0d;
        }
        if (clickRate == null) {
            clickRate = 0d;
        }
        if (calculatedAt == null) {
            calculatedAt = now;
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

    /** 预算方案 ID (可空) */
    @Column(name = "plan_id")
    private Long planId;

    /** 预算分配 ID (可空) */
    @Column(name = "allocation_id")
    private Long allocationId;

    /** 关联营销活动 ID (可空) */
    @Column(name = "campaign_id")
    private Long campaignId;

    /** 关联营销活动名称 (可空) */
    @Column(name = "campaign_name", length = 200)
    private String campaignName;

    /** 周期 (可空, 格式 yyyy-MM) */
    @Column(name = "period", length = 20)
    private String period;

    /** 总支出 */
    @Column(name = "total_spend", nullable = false)
    private Double totalSpend;

    /** 总收入 */
    @Column(name = "total_revenue")
    private Double totalRevenue;

    /** 总利润 */
    @Column(name = "total_profit")
    private Double totalProfit;

    /** 收入 ROI = 收入 / 支出 */
    @Column(name = "revenue_roi")
    private Double revenueRoi;

    /** 利润 ROI = 利润 / 支出 */
    @Column(name = "profit_roi")
    private Double profitRoi;

    /** ROAS = 收入 / 广告支出 */
    @Column(name = "roas")
    private Double roas;

    /** 每行动成本 (CPA) */
    @Column(name = "cpa")
    private Double cpa;

    /** 每点击成本 (CPC) */
    @Column(name = "cpc")
    private Double cpc;

    /** 千次展示成本 (CPM) */
    @Column(name = "cpm")
    private Double cpm;

    /** 获客成本 (CAC) */
    @Column(name = "cac")
    private Double cac;

    /** 客户 LTV */
    @Column(name = "ltv")
    private Double ltv;

    /** LTV / CAC 比率 */
    @Column(name = "ltv_cac_ratio")
    private Double ltvCacRatio;

    /** 转化数 */
    @Column(name = "conversions")
    private Integer conversions;

    /** 点击数 */
    @Column(name = "clicks")
    private Integer clicks;

    /** 展示数 */
    @Column(name = "impressions")
    private Integer impressions;

    /** 转化率 = 转化数 / 点击数 */
    @Column(name = "conversion_rate")
    private Double conversionRate;

    /** 点击率 = 点击数 / 展示数 */
    @Column(name = "click_rate")
    private Double clickRate;

    /** 回本月数 (可空) */
    @Column(name = "payback_period_months")
    private Integer paybackPeriodMonths;

    /** 渠道/产品拆分 JSON (可空): [{channel,spend,revenue,roi}] */
    @Column(name = "breakdown", columnDefinition = "TEXT")
    private String breakdown;

    /** 计算时间 */
    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    /** 备注 (可空) */
    @Column(name = "notes", length = 500)
    private String notes;
}
