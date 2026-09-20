/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignAnalysisEntity.java
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 营销活动效果分析实体。
 * <p>
 * 记录单次营销活动的效果指标与财务指标汇总:
 * 触达/展示/点击/注册/参与/转化等计数指标,
 * ROI/ROAS/CPC/CPA/CPM/CAC/LTV/LTV-CAC/回收期/客单价/复购率等财务与比率指标,
 * 亮点/问题/建议等分析结论, 以及审批状态与标签。
 * 状态流转: PLANNED / RUNNING / COMPLETED / CANCELLED / PAUSED。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_campaign_analysis", schema = "scrm", indexes = {
        @Index(name = "idx_campaign_analysis_campaign", columnList = "campaign_id"),
        @Index(name = "idx_campaign_analysis_type", columnList = "campaign_type"),
        @Index(name = "idx_campaign_analysis_status", columnList = "status"),
        @Index(name = "idx_campaign_analysis_period", columnList = "start_date,end_date"),
        @Index(name = "idx_campaign_analysis_approved", columnList = "is_approved")
})
@Data
public class ScrmCampaignAnalysisEntity {

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
            status = "PLANNED";
        }
        if (durationDays == null) {
            durationDays = 0;
        }
        if (budget == null) {
            budget = 0d;
        }
        if (actualCost == null) {
            actualCost = 0d;
        }
        if (reachCount == null) {
            reachCount = 0;
        }
        if (impressionCount == null) {
            impressionCount = 0;
        }
        if (clickCount == null) {
            clickCount = 0;
        }
        if (clickThroughRate == null) {
            clickThroughRate = 0d;
        }
        if (registrationCount == null) {
            registrationCount = 0;
        }
        if (participationCount == null) {
            participationCount = 0;
        }
        if (conversionCount == null) {
            conversionCount = 0;
        }
        if (conversionRate == null) {
            conversionRate = 0d;
        }
        if (revenue == null) {
            revenue = 0d;
        }
        if (profit == null) {
            profit = 0d;
        }
        if (roi == null) {
            roi = 0d;
        }
        if (roas == null) {
            roas = 0d;
        }
        if (cpc == null) {
            cpc = 0d;
        }
        if (cpa == null) {
            cpa = 0d;
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
        if (paybackPeriod == null) {
            paybackPeriod = 0d;
        }
        if (averageOrderValue == null) {
            averageOrderValue = 0d;
        }
        if (ordersPerCustomer == null) {
            ordersPerCustomer = 0d;
        }
        if (newCustomerCount == null) {
            newCustomerCount = 0;
        }
        if (repeatCustomerCount == null) {
            repeatCustomerCount = 0;
        }
        if (newCustomerRate == null) {
            newCustomerRate = 0d;
        }
        if (retentionRate == null) {
            retentionRate = 0d;
        }
        if (npsScore == null) {
            npsScore = 0;
        }
        if (csatScore == null) {
            csatScore = 0d;
        }
        if (isApproved == null) {
            isApproved = false;
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

    /** 营销活动 ID */
    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    /** 活动名称 */
    @Column(name = "campaign_name", nullable = false, length = 200)
    private String campaignName;

    /** 活动编码 (可空) */
    @Column(name = "campaign_code", length = 100)
    private String campaignCode;

    /** 活动类型: PROMOTION/DISCOUNT/COUPON/POINTS/GIVEAWAY/CONTENT/REFERRAL/LOYALTY/FLASH_SALE/GROUP_BUY/OTHER */
    @Column(name = "campaign_type", nullable = false, length = 50)
    private String campaignType;

    /** 活动目标 (可空) */
    @Column(name = "objective", length = 200)
    private String objective;

    /** 活动开始日期 */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 活动结束日期 */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** 活动持续天数 (默认 0) */
    @Column(name = "duration_days")
    private Integer durationDays;

    /** 状态: PLANNED/RUNNING/COMPLETED/CANCELLED/PAUSED (默认 PLANNED) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 预算 (默认 0) */
    @Column(name = "budget")
    private Double budget;

    /** 实际成本 (默认 0) */
    @Column(name = "actual_cost")
    private Double actualCost;

    /** 渠道 (逗号分隔, 可空) */
    @Column(name = "channels", length = 500)
    private String channels;

    /** 分群 (逗号分隔, 可空) */
    @Column(name = "segments", length = 500)
    private String segments;

    /** 商品 (逗号分隔, 可空) */
    @Column(name = "products", length = 500)
    private String products;

    /** 触达数 (默认 0) */
    @Column(name = "reach_count")
    private Integer reachCount;

    /** 展示数 (默认 0) */
    @Column(name = "impression_count")
    private Integer impressionCount;

    /** 点击数 (默认 0) */
    @Column(name = "click_count")
    private Integer clickCount;

    /** 点击率 (默认 0) */
    @Column(name = "click_through_rate")
    private Double clickThroughRate;

    /** 注册数 (默认 0) */
    @Column(name = "registration_count")
    private Integer registrationCount;

    /** 参与数 (默认 0) */
    @Column(name = "participation_count")
    private Integer participationCount;

    /** 转化数 (默认 0) */
    @Column(name = "conversion_count")
    private Integer conversionCount;

    /** 转化率 (默认 0) */
    @Column(name = "conversion_rate")
    private Double conversionRate;

    /** 收入 (默认 0) */
    @Column(name = "revenue")
    private Double revenue;

    /** 利润 (默认 0) */
    @Column(name = "profit")
    private Double profit;

    /** ROI (默认 0) */
    @Column(name = "roi")
    private Double roi;

    /** ROAS (默认 0) */
    @Column(name = "roas")
    private Double roas;

    /** CPC (默认 0) */
    @Column(name = "cpc")
    private Double cpc;

    /** CPA (默认 0) */
    @Column(name = "cpa")
    private Double cpa;

    /** CPM (默认 0) */
    @Column(name = "cpm")
    private Double cpm;

    /** CAC 客户获取成本 (默认 0) */
    @Column(name = "cac")
    private Double cac;

    /** LTV 客户终身价值 (默认 0) */
    @Column(name = "ltv")
    private Double ltv;

    /** LTV/CAC 比率 (默认 0) */
    @Column(name = "ltv_cac_ratio")
    private Double ltvCacRatio;

    /** 回收期 (默认 0) */
    @Column(name = "payback_period")
    private Double paybackPeriod;

    /** 平均订单价值 (默认 0) */
    @Column(name = "average_order_value")
    private Double averageOrderValue;

    /** 人均订单数 (默认 0) */
    @Column(name = "orders_per_customer")
    private Double ordersPerCustomer;

    /** 新客户数 (默认 0) */
    @Column(name = "new_customer_count")
    private Integer newCustomerCount;

    /** 老客户数 (默认 0) */
    @Column(name = "repeat_customer_count")
    private Integer repeatCustomerCount;

    /** 新客户占比 (默认 0) */
    @Column(name = "new_customer_rate")
    private Double newCustomerRate;

    /** 留存率 (默认 0) */
    @Column(name = "retention_rate")
    private Double retentionRate;

    /** NPS 净推荐值 (默认 0) */
    @Column(name = "nps_score")
    private Integer npsScore;

    /** CSAT 客户满意度 (默认 0) */
    @Column(name = "csat_score")
    private Double csatScore;

    /** 亮点 (可空) */
    @Column(name = "highlights", length = 2000)
    private String highlights;

    /** 问题 (可空) */
    @Column(name = "issues", length = 2000)
    private String issues;

    /** 建议 (可空) */
    @Column(name = "recommendations", length = 2000)
    private String recommendations;

    /** 分析人 (可空) */
    @Column(name = "analyzed_by", length = 100)
    private String analyzedBy;

    /** 分析时间 (可空) */
    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    /** 审批人 (可空) */
    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    /** 审批时间 (可空) */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** 是否已审批 (默认 FALSE) */
    @Column(name = "is_approved", nullable = false)
    private Boolean isApproved;

    /** 标签 (逗号分隔, 可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
