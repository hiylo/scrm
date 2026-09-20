/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFunnelAnalysisEntity.java
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
 * SCRM 转化漏斗分析实体。
 * <p>
 * 记录营销/销售/注册/激活等转化漏斗的阶段定义、进入/完成统计、整体转化率与流失率、
 * 阶段指标 JSON (含各阶段转化率/流失率/平均时间)、瓶颈分析 JSON、最佳/最差/最大流失阶段、
 * 分群/设备/渠道维度分析 JSON、时间范围与计算状态。漏斗类型: MARKETING/SALES/ONBOARDING/
 * PURCHASE/REGISTRATION/ACTIVATION/RETENTION/CUSTOM。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_funnel_analysis", schema = "scrm", indexes = {
        @Index(name = "idx_funnel_code", columnList = "funnel_code"),
        @Index(name = "idx_funnel_campaign", columnList = "campaign_id"),
        @Index(name = "idx_funnel_type", columnList = "funnel_type"),
        @Index(name = "idx_funnel_status", columnList = "calculation_status"),
        @Index(name = "idx_funnel_period", columnList = "start_date,end_date")
})
@Data
public class ScrmFunnelAnalysisEntity {

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
        if (stageCount == null) {
            stageCount = 0;
        }
        if (totalEntrants == null) {
            totalEntrants = 0;
        }
        if (totalCompleted == null) {
            totalCompleted = 0;
        }
        if (overallConversionRate == null) {
            overallConversionRate = 0d;
        }
        if (overallDropOffRate == null) {
            overallDropOffRate = 0d;
        }
        if (avgTimeToComplete == null) {
            avgTimeToComplete = 0d;
        }
        if (timeRange == null) {
            timeRange = "30D";
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

    /** 漏斗名称 */
    @Column(name = "funnel_name", nullable = false, length = 200)
    private String funnelName;

    /** 漏斗编码 (唯一) */
    @Column(name = "funnel_code", nullable = false, length = 50, unique = true)
    private String funnelCode;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 关联营销活动 ID (可空) */
    @Column(name = "campaign_id")
    private Long campaignId;

    /** 关联营销活动名称 (可空) */
    @Column(name = "campaign_name", length = 200)
    private String campaignName;

    /** 漏斗类型: MARKETING/SALES/ONBOARDING/PURCHASE/REGISTRATION/ACTIVATION/RETENTION/CUSTOM */
    @Column(name = "funnel_type", nullable = false, length = 30)
    private String funnelType;

    /** JSON 阶段: [{name,order,event,description}] */
    @Column(name = "stages", nullable = false, columnDefinition = "TEXT")
    private String stages;

    /** 阶段数 */
    @Column(name = "stage_count", nullable = false)
    private Integer stageCount;

    /** 总进入数 */
    @Column(name = "total_entrants")
    private Integer totalEntrants;

    /** 总完成数 */
    @Column(name = "total_completed")
    private Integer totalCompleted;

    /** 整体转化率% */
    @Column(name = "overall_conversion_rate")
    private Double overallConversionRate;

    /** 整体流失率% */
    @Column(name = "overall_drop_off_rate")
    private Double overallDropOffRate;

    /** 平均完成时间 (小时) */
    @Column(name = "avg_time_to_complete")
    private Double avgTimeToComplete;

    /** JSON 阶段指标 (可空): [{stage,entrants,completed,conversionRate,dropOffRate,avgTime,avgTimeFromStart}] */
    @Column(name = "stage_metrics", columnDefinition = "TEXT")
    private String stageMetrics;

    /** JSON 瓶颈分析 (可空): [{stage,issue,impact,recommendation}] */
    @Column(name = "bottlenecks", columnDefinition = "TEXT")
    private String bottlenecks;

    /** 最佳表现阶段 (可空) */
    @Column(name = "best_performing_stage", length = 200)
    private String bestPerformingStage;

    /** 最差表现阶段 (可空) */
    @Column(name = "worst_performing_stage", length = 200)
    private String worstPerformingStage;

    /** 最大流失阶段 (可空) */
    @Column(name = "max_drop_off_stage", length = 200)
    private String maxDropOffStage;

    /** JSON 分群分析 (可空): [{segment,stages:{}}] */
    @Column(name = "segment_analysis", columnDefinition = "TEXT")
    private String segmentAnalysis;

    /** JSON 设备分析 (可空) */
    @Column(name = "device_analysis", columnDefinition = "TEXT")
    private String deviceAnalysis;

    /** JSON 渠道分析 (可空) */
    @Column(name = "channel_analysis", columnDefinition = "TEXT")
    private String channelAnalysis;

    /** 分析时间范围: 7D/30D/90D/180D/365D/ALL (默认 30D) */
    @Column(name = "time_range", nullable = false, length = 50)
    private String timeRange;

    /** 分析开始日期 */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 分析结束日期 */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** 最近计算时间 (可空) */
    @Column(name = "last_calculated_at")
    private LocalDateTime lastCalculatedAt;

    /** 计算状态: PENDING/CALCULATING/COMPLETED/FAILED */
    @Column(name = "calculation_status", nullable = false, length = 20)
    private String calculationStatus;

    /** 洞察 (可空) */
    @Column(name = "insights", length = 2000)
    private String insights;

    /** 建议 (可空) */
    @Column(name = "recommendations", length = 2000)
    private String recommendations;

    /** 标签 (可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
