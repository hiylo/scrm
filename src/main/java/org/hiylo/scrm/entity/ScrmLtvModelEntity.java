/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLtvModelEntity.java
 * Date : 2026/08/04 08:40:58
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

import java.time.LocalDateTime;

/**
 * SCRM LTV 模型配置实体。
 * <p>
 * 定义客户终身价值预测模型参数: 模型类型 (历史/概率/预测/同期群/启发式)、
 * 计算方法 (简单平均/加权平均/折现现金流/Pareto-NBD/BG/NBD)、回溯与预测天数、
 * 折现率、流失率、平均利润率与价值分层阈值。一个账号下可存在多个模型,
 * 其中 isDefault=true 的模型为批量计算时的默认模型, isPublished=true 表示模型已发布可用。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_ltv_model", schema = "scrm", indexes = {
        @Index(name = "idx_ltv_model_default", columnList = "is_default"),
        @Index(name = "idx_ltv_model_published", columnList = "is_published")
})
@Data
public class ScrmLtvModelEntity {

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

    /** 模型名称 */
    @Column(name = "model_name", nullable = false, length = 200)
    private String modelName;

    /** 模型编码 (全局唯一) */
    @Column(name = "model_code", nullable = false, length = 50, unique = true)
    private String modelCode;

    /** 模型描述 */
    @Column(name = "description", length = 500)
    private String description;

    /** 模型类型: HISTORICAL / PROBABILISTIC / PREDICTIVE / COHORT / HEURISTIC */
    @Column(name = "model_type", nullable = false, length = 30)
    private String modelType;

    /** 计算方法: SIMPLE_AVG / WEIGHTED_AVG / DISCOUNTED_CASH_FLOW / PARETO_NBD / BUY_TILL_YOU_DIE */
    @Column(name = "calculation_method", nullable = false, length = 30)
    private String calculationMethod;

    /** 回溯天数 (历史数据采集窗口, 默认 365) */
    @Column(name = "lookback_days", nullable = false)
    private Integer lookbackDays;

    /** 预测天数 (未来 LTV 预测窗口, 默认 365) */
    @Column(name = "forecast_days", nullable = false)
    private Integer forecastDays;

    /** 折现率 (DCF 计算用, 默认 0.1) */
    @Column(name = "discount_rate")
    private Double discountRate;

    /** 流失率 (流失概率计算用, 默认 0.05) */
    @Column(name = "churn_rate")
    private Double churnRate;

    /** 平均利润率 (收入转利润用, 默认 0.3) */
    @Column(name = "avg_profit_margin")
    private Double avgProfitMargin;

    /** 购买频次阈值 (判定活跃客户用, 默认 2) */
    @Column(name = "purchase_frequency_threshold")
    private Integer purchaseFrequencyThreshold;

    /** 价值分层阈值 (JSON 数组: [{tier,minValue,maxValue,color}]) */
    @Column(name = "tier_thresholds", columnDefinition = "TEXT")
    private String tierThresholds;

    /** 是否为默认模型（账号下仅一个默认模型） */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    /** 是否已发布 */
    @Column(name = "is_published", nullable = false)
    private Boolean isPublished;

    /** 模型版本号 (业务版本, 每次复制/更新递增) */
    @Column(name = "model_version")
    private Integer modelVersion;

    /** 应用次数 (该模型被用于计算的次数) */
    @Column(name = "applied_count")
    private Integer appliedCount;

    /** 最近一次应用时间 */
    @Column(name = "last_applied_at")
    private LocalDateTime lastAppliedAt;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
