/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmForecastResultEntity.java
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
 * SCRM 销售预测结果实体。
 * <p>
 * 记录某个预测场景在每个预测周期 (forecastDate / periodLabel) 上的预测值、实际值与偏差、
 * 置信区间上下界与区间宽度、是否实际值标记, 以及按客群/产品/渠道/地区维度的拆分信息。
 * breakdown / contributingFactors / metadata 字段以 JSON 文本存储结构化细分数据。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_forecast_result", schema = "scrm", indexes = {
        @Index(name = "idx_forecast_result_scenario", columnList = "scenario_id"),
        @Index(name = "idx_forecast_result_model", columnList = "model_id"),
        @Index(name = "idx_forecast_result_date", columnList = "forecast_date"),
        @Index(name = "idx_forecast_result_period", columnList = "period_label"),
        @Index(name = "idx_forecast_result_actual", columnList = "is_actual")
})
@Data
public class ScrmForecastResultEntity {

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
        if (periodIndex == null) {
            periodIndex = 0;
        }
        if (forecastValue == null) {
            forecastValue = 0d;
        }
        if (variance == null) {
            variance = 0d;
        }
        if (variancePercent == null) {
            variancePercent = 0d;
        }
        if (confidenceLower == null) {
            confidenceLower = 0d;
        }
        if (confidenceUpper == null) {
            confidenceUpper = 0d;
        }
        if (confidenceRange == null) {
            confidenceRange = 0d;
        }
        if (isActual == null) {
            isActual = false;
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

    /** 场景 ID */
    @Column(name = "scenario_id", nullable = false)
    private Long scenarioId;

    /** 场景名称 (冗余, 便于查询展示, 可空) */
    @Column(name = "scenario_name", length = 200)
    private String scenarioName;

    /** 模型 ID (可空) */
    @Column(name = "model_id")
    private Long modelId;

    /** 模型名称 (可空) */
    @Column(name = "model_name", length = 200)
    private String modelName;

    /** 预测日期 */
    @Column(name = "forecast_date", nullable = false)
    private LocalDate forecastDate;

    /** 周期标签 (如 2026-08, 可空) */
    @Column(name = "period_label", length = 50)
    private String periodLabel;

    /** 周期序号 (默认 0) */
    @Column(name = "period_index")
    private Integer periodIndex;

    /** 预测值 (默认 0) */
    @Column(name = "forecast_value", nullable = false)
    private Double forecastValue;

    /** 实际值 (可空, 实际数据回填) */
    @Column(name = "actual_value")
    private Double actualValue;

    /** 偏差 (默认 0, 实际-预测) */
    @Column(name = "variance")
    private Double variance;

    /** 偏差百分比 (默认 0) */
    @Column(name = "variance_percent")
    private Double variancePercent;

    /** 置信下界 (默认 0) */
    @Column(name = "confidence_lower")
    private Double confidenceLower;

    /** 置信上界 (默认 0) */
    @Column(name = "confidence_upper")
    private Double confidenceUpper;

    /** 置信区间宽度 (默认 0) */
    @Column(name = "confidence_range")
    private Double confidenceRange;

    /** 是否实际值 (默认 FALSE, 训练数据回填时为 TRUE) */
    @Column(name = "is_actual", nullable = false)
    private Boolean isActual;

    /** 客群 (可空) */
    @Column(name = "segment", length = 200)
    private String segment;

    /** 产品 (可空) */
    @Column(name = "product", length = 200)
    private String product;

    /** 渠道 (可空) */
    @Column(name = "channel", length = 200)
    private String channel;

    /** 地区 (可空) */
    @Column(name = "region", length = 200)
    private String region;

    /** JSON 细分拆分: [{dimension, value, forecastValue, actualValue}, ...] (可空) */
    @Column(name = "breakdown", columnDefinition = "TEXT")
    private String breakdown;

    /** JSON 贡献因子 (可空) */
    @Column(name = "contributing_factors", columnDefinition = "TEXT")
    private String contributingFactors;

    /** JSON 附加数据 (可空) */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
