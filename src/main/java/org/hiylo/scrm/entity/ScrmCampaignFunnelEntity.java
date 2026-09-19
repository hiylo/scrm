/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignFunnelEntity.java
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
 * SCRM 营销活动转化漏斗实体。
 * <p>
 * 描述单次活动按漏斗类型 (AWARENESS/REGISTRATION/PURCHASE/ENGAGEMENT/RETENTION/CUSTOM)
 * 拆分的各阶段 (AWARENESS/INTEREST/CONSIDERATION/INTENT/PURCHASE/RETENTION/ADVOCACY)
 * 进入/退出/转化/流失/耗时指标, 并标记瓶颈阶段。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_campaign_funnel", schema = "scrm", indexes = {
        @Index(name = "idx_campaign_funnel_analysis", columnList = "analysis_id"),
        @Index(name = "idx_campaign_funnel_campaign", columnList = "campaign_id"),
        @Index(name = "idx_campaign_funnel_type", columnList = "funnel_type"),
        @Index(name = "idx_campaign_funnel_bottleneck", columnList = "is_bottleneck")
})
@Data
public class ScrmCampaignFunnelEntity {

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
        if (funnelType == null) {
            funnelType = "PURCHASE";
        }
        if (entryCount == null) {
            entryCount = 0;
        }
        if (exitCount == null) {
            exitCount = 0;
        }
        if (conversionCount == null) {
            conversionCount = 0;
        }
        if (dropoffCount == null) {
            dropoffCount = 0;
        }
        if (conversionRate == null) {
            conversionRate = 0d;
        }
        if (dropoffRate == null) {
            dropoffRate = 0d;
        }
        if (avgTimeSpent == null) {
            avgTimeSpent = 0d;
        }
        if (revenue == null) {
            revenue = 0d;
        }
        if (cost == null) {
            cost = 0d;
        }
        if (isBottleneck == null) {
            isBottleneck = false;
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

    /** 漏斗名称 */
    @Column(name = "funnel_name", nullable = false, length = 200)
    private String funnelName;

    /** 漏斗类型: AWARENESS/REGISTRATION/PURCHASE/ENGAGEMENT/RETENTION/CUSTOM (默认 PURCHASE) */
    @Column(name = "funnel_type", nullable = false, length = 50)
    private String funnelType;

    /** 阶段名称 */
    @Column(name = "stage_name", nullable = false, length = 200)
    private String stageName;

    /** 阶段顺序 */
    @Column(name = "stage_order", nullable = false)
    private Integer stageOrder;

    /** 阶段类型: AWARENESS/INTEREST/CONSIDERATION/INTENT/PURCHASE/RETENTION/ADVOCACY */
    @Column(name = "stage_type", nullable = false, length = 50)
    private String stageType;

    /** 进入数 (默认 0) */
    @Column(name = "entry_count")
    private Integer entryCount;

    /** 退出数 (默认 0) */
    @Column(name = "exit_count")
    private Integer exitCount;

    /** 转化数 (默认 0) */
    @Column(name = "conversion_count")
    private Integer conversionCount;

    /** 流失数 (默认 0) */
    @Column(name = "dropoff_count")
    private Integer dropoffCount;

    /** 转化率 (默认 0) */
    @Column(name = "conversion_rate")
    private Double conversionRate;

    /** 流失率 (默认 0) */
    @Column(name = "dropoff_rate")
    private Double dropoffRate;

    /** 平均耗时 (默认 0) */
    @Column(name = "avg_time_spent")
    private Double avgTimeSpent;

    /** 收入 (默认 0) */
    @Column(name = "revenue")
    private Double revenue;

    /** 成本 (默认 0) */
    @Column(name = "cost")
    private Double cost;

    /** 是否瓶颈阶段 (默认 FALSE) */
    @Column(name = "is_bottleneck", nullable = false)
    private Boolean isBottleneck;

    /** 优化建议 (可空) */
    @Column(name = "optimization_notes", length = 1000)
    private String optimizationNotes;

    /** 元数据 JSON (可空) */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
