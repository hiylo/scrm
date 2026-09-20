/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAbTestEntity.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM A/B 测试实体。
 * <p>
 * 记录营销活动 A/B 测试的全流程数据: 测试类型 (SUBJECT/CONTENT/CTA/DESIGN/TIMING/SEGMENT/
 * CHANNEL/OFFER/LANDING_PAGE/FULL)、主要指标、变体 JSON (含各变体指标)、流量分配、目标/最小/当前
 * 样本量、获胜变体与置信度、显著性水平 α、统计方法、p 值、置信区间、效应量、检验效能、结论与建议、
 * 持续天数、停止规则、自动停止、分析结果 JSON。状态流转: DRAFT / RUNNING / PAUSED / COMPLETED /
 * CANCELLED。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_ab_test", schema = "scrm", indexes = {
        @Index(name = "idx_ab_test_code", columnList = "test_code"),
        @Index(name = "idx_ab_test_campaign", columnList = "campaign_id"),
        @Index(name = "idx_ab_test_type", columnList = "test_type"),
        @Index(name = "idx_ab_test_status", columnList = "status"),
        @Index(name = "idx_ab_test_metric", columnList = "metric"),
        @Index(name = "idx_ab_test_analyzed", columnList = "last_analyzed_at")
})
@Data
public class ScrmAbTestEntity {

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
        if (variantCount == null) {
            variantCount = 2;
        }
        if (trafficSplit == null) {
            trafficSplit = "50,50";
        }
        if (targetSampleSize == null) {
            targetSampleSize = 0;
        }
        if (minSampleSize == null) {
            minSampleSize = 0;
        }
        if (currentSampleSize == null) {
            currentSampleSize = 0;
        }
        if (winnerConfidence == null) {
            winnerConfidence = 0d;
        }
        if (winnerImprovement == null) {
            winnerImprovement = 0d;
        }
        if (isSignificant == null) {
            isSignificant = false;
        }
        if (significanceLevel == null) {
            significanceLevel = 0.05d;
        }
        if (statisticalMethod == null) {
            statisticalMethod = "CHI_SQUARE";
        }
        if (pValue == null) {
            pValue = 1.0d;
        }
        if (effectSize == null) {
            effectSize = 0d;
        }
        if (power == null) {
            power = 0d;
        }
        if (durationDays == null) {
            durationDays = 0;
        }
        if (autoStop == null) {
            autoStop = false;
        }
        if (lastAnalyzedAt == null) {
            lastAnalyzedAt = now;
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

    /** 测试名称 */
    @Column(name = "test_name", nullable = false, length = 200)
    private String testName;

    /** 测试编码 (唯一) */
    @Column(name = "test_code", nullable = false, length = 50, unique = true)
    private String testCode;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 关联营销活动 ID (可空) */
    @Column(name = "campaign_id")
    private Long campaignId;

    /** 关联营销活动名称 (可空) */
    @Column(name = "campaign_name", length = 200)
    private String campaignName;

    /** 测试类型: SUBJECT/CONTENT/CTA/DESIGN/TIMING/SEGMENT/CHANNEL/OFFER/LANDING_PAGE/FULL */
    @Column(name = "test_type", nullable = false, length = 30)
    private String testType;

    /** 假设 (可空) */
    @Column(name = "hypothesis", length = 1000)
    private String hypothesis;

    /** 主要指标: OPEN_RATE/CLICK_RATE/CONVERSION_RATE/REVENUE/ENGAGEMENT/BOUNCE_RATE/UNSUBSCRIBE_RATE/CTR/CVR/CPS */
    @Column(name = "metric", nullable = false, length = 50)
    private String metric;

    /** 次要指标 (可空) */
    @Column(name = "secondary_metrics", length = 500)
    private String secondaryMetrics;

    /** JSON 变体: [{name,description,audienceSize,metrics:{}}] */
    @Column(name = "variants", nullable = false, columnDefinition = "TEXT")
    private String variants;

    /** 变体数 (默认 2) */
    @Column(name = "variant_count", nullable = false)
    private Integer variantCount;

    /** 流量分配 (逗号分隔, 默认 50,50) */
    @Column(name = "traffic_split", nullable = false, length = 200)
    private String trafficSplit;

    /** 目标样本量 */
    @Column(name = "target_sample_size")
    private Integer targetSampleSize;

    /** 最小样本量 */
    @Column(name = "min_sample_size")
    private Integer minSampleSize;

    /** 当前样本量 */
    @Column(name = "current_sample_size")
    private Integer currentSampleSize;

    /** 测试开始日期 */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 测试结束日期 (可空) */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** 状态: DRAFT/RUNNING/PAUSED/COMPLETED/CANCELLED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 获胜变体 (可空) */
    @Column(name = "winning_variant", length = 100)
    private String winningVariant;

    /** 获胜置信度% */
    @Column(name = "winner_confidence")
    private Double winnerConfidence;

    /** 提升幅度% */
    @Column(name = "winner_improvement")
    private Double winnerImprovement;

    /** 是否显著 */
    @Column(name = "is_significant", nullable = false)
    private Boolean isSignificant;

    /** 显著性水平 α (默认 0.05) */
    @Column(name = "significance_level")
    private Double significanceLevel;

    /** 统计方法: CHI_SQUARE/T_TEST/Z_TEST/MANN_WHITNEY/BAYESIAN */
    @Column(name = "statistical_method", nullable = false, length = 50)
    private String statisticalMethod;

    /** p 值 (默认 1.0) */
    @Column(name = "p_value")
    private Double pValue;

    /** 置信区间 (可空) */
    @Column(name = "confidence_interval", length = 200)
    private String confidenceInterval;

    /** 效应量 */
    @Column(name = "effect_size")
    private Double effectSize;

    /** 检验效能 */
    @Column(name = "power")
    private Double power;

    /** 结论 (可空) */
    @Column(name = "conclusion", length = 2000)
    private String conclusion;

    /** 建议 (可空) */
    @Column(name = "recommendation", length = 2000)
    private String recommendation;

    /** 持续天数 */
    @Column(name = "duration_days")
    private Integer durationDays;

    /** 停止规则 (可空) */
    @Column(name = "stopping_rule", length = 200)
    private String stoppingRule;

    /** 自动停止 */
    @Column(name = "auto_stop", nullable = false)
    private Boolean autoStop;

    /** 停止时间 (可空) */
    @Column(name = "stopped_at")
    private LocalDate stoppedAt;

    /** 停止原因 (可空) */
    @Column(name = "stopped_reason", length = 500)
    private String stoppedReason;

    /** JSON 分析结果 (可空) */
    @Column(name = "analysis_result", columnDefinition = "TEXT")
    private String analysisResult;

    /** 最近分析时间 (可空) */
    @Column(name = "last_analyzed_at")
    private LocalDateTime lastAnalyzedAt;

    /** 标签 (可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
