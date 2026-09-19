/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignChannelEntity.java
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

import java.time.LocalDateTime;

/**
 * SCRM 营销活动渠道效果实体。
 * <p>
 * 描述单次活动在单一渠道 (WECHAT/EMAIL/SMS/PUSH/WEB/APP/DOUYIN/WEIBO/XIAOHONGSHU/OFFLINE/REFERRAL/OTHER)
 * 下的触达/点击/转化/ROI/效率评分等指标, 并标记最优/最差渠道。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_campaign_channel", schema = "scrm", indexes = {
        @Index(name = "idx_campaign_channel_analysis", columnList = "analysis_id"),
        @Index(name = "idx_campaign_channel_campaign", columnList = "campaign_id"),
        @Index(name = "idx_campaign_channel_type", columnList = "channel_type"),
        @Index(name = "idx_campaign_channel_best", columnList = "is_best_performer,is_underperforming")
})
@Data
public class ScrmCampaignChannelEntity {

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
        if (channelCost == null) {
            channelCost = 0d;
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
        if (cpc == null) {
            cpc = 0d;
        }
        if (cpa == null) {
            cpa = 0d;
        }
        if (cpm == null) {
            cpm = 0d;
        }
        if (newCustomerCount == null) {
            newCustomerCount = 0;
        }
        if (repeatCustomerCount == null) {
            repeatCustomerCount = 0;
        }
        if (averageOrderValue == null) {
            averageOrderValue = 0d;
        }
        if (engagementRate == null) {
            engagementRate = 0d;
        }
        if (bounceRate == null) {
            bounceRate = 0d;
        }
        if (shareRate == null) {
            shareRate = 0d;
        }
        if (costWeight == null) {
            costWeight = 0d;
        }
        if (revenueWeight == null) {
            revenueWeight = 0d;
        }
        if (efficiencyScore == null) {
            efficiencyScore = 0d;
        }
        if (isBestPerformer == null) {
            isBestPerformer = false;
        }
        if (isUnderperforming == null) {
            isUnderperforming = false;
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

    /** 分析 ID */
    @Column(name = "analysis_id", nullable = false)
    private Long analysisId;

    /** 营销活动 ID (可空) */
    @Column(name = "campaign_id")
    private Long campaignId;

    /** 渠道名称 */
    @Column(name = "channel_name", nullable = false, length = 100)
    private String channelName;

    /** 渠道类型: WECHAT/EMAIL/SMS/PUSH/WEB/APP/DOUYIN/WEIBO/XIAOHONGSHU/OFFLINE/REFERRAL/OTHER */
    @Column(name = "channel_type", nullable = false, length = 50)
    private String channelType;

    /** 渠道花费 (默认 0) */
    @Column(name = "channel_cost")
    private Double channelCost;

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

    /** CPC (默认 0) */
    @Column(name = "cpc")
    private Double cpc;

    /** CPA (默认 0) */
    @Column(name = "cpa")
    private Double cpa;

    /** CPM (默认 0) */
    @Column(name = "cpm")
    private Double cpm;

    /** 新客户数 (默认 0) */
    @Column(name = "new_customer_count")
    private Integer newCustomerCount;

    /** 老客户数 (默认 0) */
    @Column(name = "repeat_customer_count")
    private Integer repeatCustomerCount;

    /** 平均订单价值 (默认 0) */
    @Column(name = "average_order_value")
    private Double averageOrderValue;

    /** 互动率 (默认 0) */
    @Column(name = "engagement_rate")
    private Double engagementRate;

    /** 跳出率 (默认 0) */
    @Column(name = "bounce_rate")
    private Double bounceRate;

    /** 分享率 (默认 0) */
    @Column(name = "share_rate")
    private Double shareRate;

    /** 成本占比 (默认 0) */
    @Column(name = "cost_weight")
    private Double costWeight;

    /** 收入占比 (默认 0) */
    @Column(name = "revenue_weight")
    private Double revenueWeight;

    /** 效率评分 (默认 0) */
    @Column(name = "efficiency_score")
    private Double efficiencyScore;

    /** 是否最优渠道 (默认 FALSE) */
    @Column(name = "is_best_performer", nullable = false)
    private Boolean isBestPerformer;

    /** 是否最差渠道 (默认 FALSE) */
    @Column(name = "is_underperforming", nullable = false)
    private Boolean isUnderperforming;

    /** 备注 (可空) */
    @Column(name = "notes", length = 1000)
    private String notes;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
