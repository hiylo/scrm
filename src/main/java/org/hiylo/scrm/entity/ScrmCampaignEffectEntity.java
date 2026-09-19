/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignEffectEntity.java
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
 * SCRM 营销活动效果分析实体。
 * <p>
 * 记录营销活动的效果指标、ROI、A/B 测试对照组与转化漏斗:
 * 触达/互动/响应/点击/打开/转化/退回/退订/投诉/分享/转发/下载/注册/购买等计数指标,
 * 收入/成本/利润/ROI/ROAS/CPA/CPC/CPM/CPL/CPS/CVR/CTR 等财务与比率指标,
 * segments/channels/funnel/attributions/benchmarks 等 JSON 文本拆分,
 * 对照组 (controlGroupSize/controlConversionCount/controlRevenue/uplift) 与统计显著性 (p-value)。
 * 状态流转: PLANNED / LAUNCHED / PAUSED / COMPLETED / CANCELLED。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_campaign_effect", schema = "scrm", indexes = {
        @Index(name = "idx_campaign_effect_campaign", columnList = "campaign_id"),
        @Index(name = "idx_campaign_effect_type", columnList = "campaign_type"),
        @Index(name = "idx_campaign_effect_objective", columnList = "objective"),
        @Index(name = "idx_campaign_effect_status", columnList = "status"),
        @Index(name = "idx_campaign_effect_period", columnList = "start_date,end_date"),
        @Index(name = "idx_campaign_effect_calc", columnList = "calculation_status")
})
@Data
public class ScrmCampaignEffectEntity {

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
        if (targetAudienceCount == null) {
            targetAudienceCount = 0;
        }
        if (reachedCount == null) {
            reachedCount = 0;
        }
        if (engagedCount == null) {
            engagedCount = 0;
        }
        if (respondedCount == null) {
            respondedCount = 0;
        }
        if (clickedCount == null) {
            clickedCount = 0;
        }
        if (openedCount == null) {
            openedCount = 0;
        }
        if (convertedCount == null) {
            convertedCount = 0;
        }
        if (bouncedCount == null) {
            bouncedCount = 0;
        }
        if (unsubscribedCount == null) {
            unsubscribedCount = 0;
        }
        if (complainedCount == null) {
            complainedCount = 0;
        }
        if (sharedCount == null) {
            sharedCount = 0;
        }
        if (forwardedCount == null) {
            forwardedCount = 0;
        }
        if (downloadedCount == null) {
            downloadedCount = 0;
        }
        if (registeredCount == null) {
            registeredCount = 0;
        }
        if (purchasedCount == null) {
            purchasedCount = 0;
        }
        if (revenue == null) {
            revenue = 0d;
        }
        if (cost == null) {
            cost = 0d;
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
        if (cpa == null) {
            cpa = 0d;
        }
        if (cpc == null) {
            cpc = 0d;
        }
        if (cpm == null) {
            cpm = 0d;
        }
        if (cpl == null) {
            cpl = 0d;
        }
        if (cps == null) {
            cps = 0d;
        }
        if (cvr == null) {
            cvr = 0d;
        }
        if (ctr == null) {
            ctr = 0d;
        }
        if (openRate == null) {
            openRate = 0d;
        }
        if (bounceRate == null) {
            bounceRate = 0d;
        }
        if (unsubscribeRate == null) {
            unsubscribeRate = 0d;
        }
        if (engagementRate == null) {
            engagementRate = 0d;
        }
        if (reachRate == null) {
            reachRate = 0d;
        }
        if (responseRate == null) {
            responseRate = 0d;
        }
        if (conversionValue == null) {
            conversionValue = 0d;
        }
        if (avgOrderValue == null) {
            avgOrderValue = 0d;
        }
        if (avgConversionTime == null) {
            avgConversionTime = 0d;
        }
        if (customerAcquisitionCost == null) {
            customerAcquisitionCost = 0d;
        }
        if (ltvAcquired == null) {
            ltvAcquired = 0d;
        }
        if (paybackPeriod == null) {
            paybackPeriod = 0d;
        }
        if (attributionModel == null) {
            attributionModel = "LAST_TOUCH";
        }
        if (controlGroupSize == null) {
            controlGroupSize = 0;
        }
        if (controlConversionCount == null) {
            controlConversionCount = 0;
        }
        if (controlRevenue == null) {
            controlRevenue = 0d;
        }
        if (uplift == null) {
            uplift = 0d;
        }
        if (incrementalRevenue == null) {
            incrementalRevenue = 0d;
        }
        if (statisticalSignificance == null) {
            statisticalSignificance = 0d;
        }
        if (confidenceLevel == null) {
            confidenceLevel = 0.95d;
        }
        if (isSignificant == null) {
            isSignificant = false;
        }
        if (calculationStatus == null) {
            calculationStatus = "PENDING";
        }
        if (lastCalculatedAt == null) {
            lastCalculatedAt = now;
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

    /** 关联营销活动名称 (可空) */
    @Column(name = "campaign_name", length = 200)
    private String campaignName;

    /** 关联营销活动编码 (可空) */
    @Column(name = "campaign_code", length = 100)
    private String campaignCode;

    /** 活动类型: EMAIL/SMS/PUSH/WECHAT/WEBINAR/CONTENT/COUPON/DISCOUNT/REFERRAL/AD/SEO/SEM/SOCIAL/OFFLINE/COMPOSITE */
    @Column(name = "campaign_type", nullable = false, length = 50)
    private String campaignType;

    /** 活动目标: AWARENESS/ENGAGEMENT/CONVERSION/RETENTION/REACTIVATION/CROSS_SELL/UP_SELL/BRAND/LEAD_GEN */
    @Column(name = "objective", nullable = false, length = 100)
    private String objective;

    /** 活动开始日期 */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 活动结束日期 */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** 状态: PLANNED/LAUNCHED/PAUSED/COMPLETED/CANCELLED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 目标受众数 */
    @Column(name = "target_audience_count")
    private Integer targetAudienceCount;

    /** 触达数 */
    @Column(name = "reached_count")
    private Integer reachedCount;

    /** 互动数 */
    @Column(name = "engaged_count")
    private Integer engagedCount;

    /** 响应数 */
    @Column(name = "responded_count")
    private Integer respondedCount;

    /** 点击数 */
    @Column(name = "clicked_count")
    private Integer clickedCount;

    /** 打开数 */
    @Column(name = "opened_count")
    private Integer openedCount;

    /** 转化数 */
    @Column(name = "converted_count")
    private Integer convertedCount;

    /** 退回数 */
    @Column(name = "bounced_count")
    private Integer bouncedCount;

    /** 退订数 */
    @Column(name = "unsubscribed_count")
    private Integer unsubscribedCount;

    /** 投诉数 */
    @Column(name = "complained_count")
    private Integer complainedCount;

    /** 分享数 */
    @Column(name = "shared_count")
    private Integer sharedCount;

    /** 转发数 */
    @Column(name = "forwarded_count")
    private Integer forwardedCount;

    /** 下载数 */
    @Column(name = "downloaded_count")
    private Integer downloadedCount;

    /** 注册数 */
    @Column(name = "registered_count")
    private Integer registeredCount;

    /** 购买数 */
    @Column(name = "purchased_count")
    private Integer purchasedCount;

    /** 收入 */
    @Column(name = "revenue")
    private Double revenue;

    /** 成本 */
    @Column(name = "cost")
    private Double cost;

    /** 利润 */
    @Column(name = "profit")
    private Double profit;

    /** ROI% (收入-成本)/成本*100 */
    @Column(name = "roi")
    private Double roi;

    /** ROAS = 收入/成本 */
    @Column(name = "roas")
    private Double roas;

    /** CPA = 成本/转化 */
    @Column(name = "cpa")
    private Double cpa;

    /** CPC = 成本/点击 */
    @Column(name = "cpc")
    private Double cpc;

    /** CPM = 成本/触达*1000 */
    @Column(name = "cpm")
    private Double cpm;

    /** CPL = 成本/注册 */
    @Column(name = "cpl")
    private Double cpl;

    /** CPS = 成本/购买 */
    @Column(name = "cps")
    private Double cps;

    /** 转化率% = 转化/触达*100 */
    @Column(name = "cvr")
    private Double cvr;

    /** 点击率% = 点击/触达*100 */
    @Column(name = "ctr")
    private Double ctr;

    /** 打开率% = 打开/触达*100 */
    @Column(name = "open_rate")
    private Double openRate;

    /** 退回率% = 退回/触达*100 */
    @Column(name = "bounce_rate")
    private Double bounceRate;

    /** 退订率% = 退订/触达*100 */
    @Column(name = "unsubscribe_rate")
    private Double unsubscribeRate;

    /** 互动率% = 互动/触达*100 */
    @Column(name = "engagement_rate")
    private Double engagementRate;

    /** 触达率% = 触达/受众*100 */
    @Column(name = "reach_rate")
    private Double reachRate;

    /** 响应率% = 响应/触达*100 */
    @Column(name = "response_rate")
    private Double responseRate;

    /** 转化价值 */
    @Column(name = "conversion_value")
    private Double conversionValue;

    /** 平均订单价值 */
    @Column(name = "avg_order_value")
    private Double avgOrderValue;

    /** 平均转化时间 (小时) */
    @Column(name = "avg_conversion_time")
    private Double avgConversionTime;

    /** 客户获取成本 (CAC) */
    @Column(name = "customer_acquisition_cost")
    private Double customerAcquisitionCost;

    /** 新客户 LTV */
    @Column(name = "ltv_acquired")
    private Double ltvAcquired;

    /** 回收期 (月) */
    @Column(name = "payback_period")
    private Double paybackPeriod;

    /** JSON 分群效果 (可空): [{segment,audience,reached,converted,revenue}] */
    @Column(name = "segments", columnDefinition = "TEXT")
    private String segments;

    /** JSON 渠道效果 (可空): [{channel,reached,engaged,converted,cost,revenue}] */
    @Column(name = "channels", columnDefinition = "TEXT")
    private String channels;

    /** JSON 转化漏斗 (可空): [{stage,count,rate,dropOff}] */
    @Column(name = "funnel", columnDefinition = "TEXT")
    private String funnel;

    /** 归因模型: FIRST_TOUCH/LAST_TOUCH/LINEAR/TIME_DECAY/POSITION_BASED/DATA_DRIVEN */
    @Column(name = "attribution_model", nullable = false, length = 50)
    private String attributionModel;

    /** JSON 归因数据 (可空) */
    @Column(name = "attributions", columnDefinition = "TEXT")
    private String attributions;

    /** 对照组大小 */
    @Column(name = "control_group_size")
    private Integer controlGroupSize;

    /** 对照组转化数 */
    @Column(name = "control_conversion_count")
    private Integer controlConversionCount;

    /** 对照组收入 */
    @Column(name = "control_revenue")
    private Double controlRevenue;

    /** 提升率% */
    @Column(name = "uplift")
    private Double uplift;

    /** 增量收入 */
    @Column(name = "incremental_revenue")
    private Double incrementalRevenue;

    /** 统计显著性 p-value */
    @Column(name = "statistical_significance")
    private Double statisticalSignificance;

    /** 置信水平 (默认 0.95) */
    @Column(name = "confidence_level")
    private Double confidenceLevel;

    /** 是否显著 */
    @Column(name = "is_significant", nullable = false)
    private Boolean isSignificant;

    /** JSON 基准对比 (可空): [{metric,value,benchmark,variance}] */
    @Column(name = "benchmarks", columnDefinition = "TEXT")
    private String benchmarks;

    /** 最近计算时间 (可空) */
    @Column(name = "last_calculated_at")
    private LocalDateTime lastCalculatedAt;

    /** 计算状态: PENDING/CALCULATING/COMPLETED/FAILED */
    @Column(name = "calculation_status", nullable = false, length = 20)
    private String calculationStatus;

    /** 备注 (可空) */
    @Column(name = "notes", length = 2000)
    private String notes;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
