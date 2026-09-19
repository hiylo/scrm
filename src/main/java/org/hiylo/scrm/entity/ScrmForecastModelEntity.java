/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmForecastModelEntity.java
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
 * SCRM 销售预测模型实体。
 * <p>
 * 定义基于历史数据的销售预测模型: 模型类型 (线性回归/移动平均/指数平滑/季节ARIMA/
 * 机器学习/集成/自定义)、目标指标 (收入/订单数/客户数/成交数/客单价/转化率)、
 * 粒度 (日/周/月/季)、回看与预测周期数、训练数据范围与训练数据点数、上次训练的准确度
 * 评分 (MAPE/MAE/RMSE/R2)、交叉验证评分、训练与自动重训练开关、重训练频率、适用客群/
 * 产品/渠道/地区, 以及模型版本号。状态字段 enabled 控制模型是否可用。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_forecast_model", schema = "scrm", indexes = {
        @Index(name = "idx_forecast_model_code", columnList = "model_code"),
        @Index(name = "idx_forecast_model_type", columnList = "model_type"),
        @Index(name = "idx_forecast_model_metric", columnList = "target_metric"),
        @Index(name = "idx_forecast_model_enabled", columnList = "enabled"),
        @Index(name = "idx_forecast_model_trained", columnList = "is_trained")
})
@Data
public class ScrmForecastModelEntity {

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
        if (granularity == null) {
            granularity = "MONTHLY";
        }
        if (lookbackPeriods == null) {
            lookbackPeriods = 12;
        }
        if (forecastPeriods == null) {
            forecastPeriods = 3;
        }
        if (trainingDataPoints == null) {
            trainingDataPoints = 0;
        }
        if (lastAccuracyScore == null) {
            lastAccuracyScore = 0d;
        }
        if (lastMape == null) {
            lastMape = 0d;
        }
        if (lastMae == null) {
            lastMae = 0d;
        }
        if (lastRmse == null) {
            lastRmse = 0d;
        }
        if (lastR2 == null) {
            lastR2 = 0d;
        }
        if (crossValidationScore == null) {
            crossValidationScore = 0d;
        }
        if (isTrained == null) {
            isTrained = false;
        }
        if (isAutoRetrain == null) {
            isAutoRetrain = false;
        }
        if (retrainFrequency == null) {
            retrainFrequency = "MONTHLY";
        }
        if (enabled == null) {
            enabled = true;
        }
        if (modelVersion == null) {
            modelVersion = 1;
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

    /** 模型编码 (唯一) */
    @Column(name = "model_code", nullable = false, length = 50, unique = true)
    private String modelCode;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 模型类型: LINEAR_REGRESSION / MOVING_AVERAGE / EXPONENTIAL_SMOOTHING / SEASONAL_ARIMA /
     *  ML_RANDOM_FOREST / ML_GRADIENT_BOOST / ENSEMBLE / CUSTOM */
    @Column(name = "model_type", nullable = false, length = 30)
    private String modelType;

    /** 算法名称 (可空) */
    @Column(name = "algorithm", length = 100)
    private String algorithm;

    /** 目标指标: REVENUE / ORDER_COUNT / CUSTOMER_COUNT / DEAL_COUNT / AVG_ORDER_VALUE / CONVERSION_RATE */
    @Column(name = "target_metric", nullable = false, length = 30)
    private String targetMetric;

    /** 粒度: DAILY / WEEKLY / MONTHLY / QUARTERLY (默认 MONTHLY) */
    @Column(name = "granularity", nullable = false, length = 20)
    private String granularity;

    /** 回看周期数 (默认 12) */
    @Column(name = "lookback_periods", nullable = false)
    private Integer lookbackPeriods;

    /** 预测周期数 (默认 3) */
    @Column(name = "forecast_periods", nullable = false)
    private Integer forecastPeriods;

    /** 季节周期 (可空) */
    @Column(name = "seasonality_period")
    private Integer seasonalityPeriod;

    /** JSON 模型参数: {alpha, beta, gamma, windowSize, ...} (可空) */
    @Column(name = "parameters", columnDefinition = "TEXT")
    private String parameters;

    /** 训练数据起始日期 (可空) */
    @Column(name = "training_data_start")
    private LocalDate trainingDataStart;

    /** 训练数据结束日期 (可空) */
    @Column(name = "training_data_end")
    private LocalDate trainingDataEnd;

    /** 训练数据点数 (默认 0) */
    @Column(name = "training_data_points")
    private Integer trainingDataPoints;

    /** 上次训练时间 (可空) */
    @Column(name = "last_trained_at")
    private LocalDateTime lastTrainedAt;

    /** 上次准确度评分 0-1 (默认 0) */
    @Column(name = "last_accuracy_score")
    private Double lastAccuracyScore;

    /** 上次平均绝对百分比误差 % (默认 0) */
    @Column(name = "last_mape")
    private Double lastMape;

    /** 上次平均绝对误差 (默认 0) */
    @Column(name = "last_mae")
    private Double lastMae;

    /** 上次均方根误差 (默认 0) */
    @Column(name = "last_rmse")
    private Double lastRmse;

    /** 上次 R 平方 (默认 0) */
    @Column(name = "last_r2")
    private Double lastR2;

    /** 交叉验证评分 (默认 0) */
    @Column(name = "cross_validation_score")
    private Double crossValidationScore;

    /** 是否已训练 (默认 FALSE) */
    @Column(name = "is_trained", nullable = false)
    private Boolean isTrained;

    /** 是否自动重训练 (默认 FALSE) */
    @Column(name = "is_auto_retrain", nullable = false)
    private Boolean isAutoRetrain;

    /** 重训练频率: DAILY / WEEKLY / MONTHLY / QUARTERLY (默认 MONTHLY) */
    @Column(name = "retrain_frequency", nullable = false, length = 20)
    private String retrainFrequency;

    /** 适用客群 (逗号分隔, 可空) */
    @Column(name = "applicable_segments", length = 500)
    private String applicableSegments;

    /** 适用产品 (逗号分隔, 可空) */
    @Column(name = "applicable_products", length = 500)
    private String applicableProducts;

    /** 适用渠道 (逗号分隔, 可空) */
    @Column(name = "applicable_channels", length = 500)
    private String applicableChannels;

    /** 适用地区 (逗号分隔, 可空) */
    @Column(name = "applicable_regions", length = 500)
    private String applicableRegions;

    /** 是否启用 (默认 TRUE) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 模型版本号 (业务版本, 默认 1) */
    @Column(name = "model_version")
    private Integer modelVersion;

    /** 备注 (可空) */
    @Column(name = "notes", length = 500)
    private String notes;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
