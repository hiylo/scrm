/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmForecastModelDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 销售预测模型 DTO。
 * <p>
 * 对应 {@code ScrmForecastModelEntity} 的业务字段, 创建/更新接口入参与查询返回。
 * parameters 为 JSON 文本, 形如 {alpha, beta, gamma, windowSize, ...}。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmForecastModelDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 模型名称 */
    @NotBlank(message = "模型名称不能为空")
    @Size(max = 200, message = "模型名称长度不能超过 200")
    private String modelName;

    /** 模型编码 (唯一) */
    @NotBlank(message = "模型编码不能为空")
    @Size(max = 50, message = "模型编码长度不能超过 50")
    private String modelCode;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 模型类型: LINEAR_REGRESSION/MOVING_AVERAGE/EXPONENTIAL_SMOOTHING/SEASONAL_ARIMA/
     *  ML_RANDOM_FOREST/ML_GRADIENT_BOOST/ENSEMBLE/CUSTOM */
    @NotBlank(message = "模型类型不能为空")
    @Pattern(regexp = "LINEAR_REGRESSION|MOVING_AVERAGE|EXPONENTIAL_SMOOTHING|SEASONAL_ARIMA"
            + "|ML_RANDOM_FOREST|ML_GRADIENT_BOOST|ENSEMBLE|CUSTOM",
            message = "模型类型仅支持 LINEAR_REGRESSION/MOVING_AVERAGE/EXPONENTIAL_SMOOTHING"
                    + "/SEASONAL_ARIMA/ML_RANDOM_FOREST/ML_GRADIENT_BOOST/ENSEMBLE/CUSTOM")
    private String modelType;

    /** 算法名称 (可空) */
    @Size(max = 100, message = "算法名称长度不能超过 100")
    private String algorithm;

    /** 目标指标: REVENUE/ORDER_COUNT/CUSTOMER_COUNT/DEAL_COUNT/AVG_ORDER_VALUE/CONVERSION_RATE */
    @NotBlank(message = "目标指标不能为空")
    @Pattern(regexp = "REVENUE|ORDER_COUNT|CUSTOMER_COUNT|DEAL_COUNT|AVG_ORDER_VALUE|CONVERSION_RATE",
            message = "目标指标仅支持 REVENUE/ORDER_COUNT/CUSTOMER_COUNT/DEAL_COUNT"
                    + "/AVG_ORDER_VALUE/CONVERSION_RATE")
    private String targetMetric;

    /** 粒度: DAILY/WEEKLY/MONTHLY/QUARTERLY */
    @Pattern(regexp = "DAILY|WEEKLY|MONTHLY|QUARTERLY",
            message = "粒度仅支持 DAILY/WEEKLY/MONTHLY/QUARTERLY")
    private String granularity;

    /** 回看周期数 (默认 12) */
    @Min(value = 1, message = "回看周期数不能小于 1")
    private Integer lookbackPeriods;

    /** 预测周期数 (默认 3) */
    @Min(value = 1, message = "预测周期数不能小于 1")
    private Integer forecastPeriods;

    /** 季节周期 (可空) */
    @Min(value = 1, message = "季节周期不能小于 1")
    private Integer seasonalityPeriod;

    /** JSON 模型参数: {alpha, beta, gamma, windowSize, ...} */
    private String parameters;

    /** 训练数据起始日期 (可空) */
    private LocalDate trainingDataStart;

    /** 训练数据结束日期 (可空) */
    private LocalDate trainingDataEnd;

    /** 训练数据点数 (查询返回) */
    private Integer trainingDataPoints;

    /** 上次训练时间 (查询返回) */
    private LocalDateTime lastTrainedAt;

    /** 上次准确度评分 (查询返回) */
    private Double lastAccuracyScore;

    /** 上次 MAPE (查询返回) */
    private Double lastMape;

    /** 上次 MAE (查询返回) */
    private Double lastMae;

    /** 上次 RMSE (查询返回) */
    private Double lastRmse;

    /** 上次 R2 (查询返回) */
    private Double lastR2;

    /** 交叉验证评分 (查询返回) */
    private Double crossValidationScore;

    /** 是否已训练 (查询返回) */
    private Boolean isTrained;

    /** 是否自动重训练 */
    private Boolean isAutoRetrain;

    /** 重训练频率: DAILY/WEEKLY/MONTHLY/QUARTERLY */
    @Pattern(regexp = "DAILY|WEEKLY|MONTHLY|QUARTERLY",
            message = "重训练频率仅支持 DAILY/WEEKLY/MONTHLY/QUARTERLY")
    private String retrainFrequency;

    /** 适用客群 (逗号分隔, 可空) */
    @Size(max = 500, message = "适用客群长度不能超过 500")
    private String applicableSegments;

    /** 适用产品 (逗号分隔, 可空) */
    @Size(max = 500, message = "适用产品长度不能超过 500")
    private String applicableProducts;

    /** 适用渠道 (逗号分隔, 可空) */
    @Size(max = 500, message = "适用渠道长度不能超过 500")
    private String applicableChannels;

    /** 适用地区 (逗号分隔, 可空) */
    @Size(max = 500, message = "适用地区长度不能超过 500")
    private String applicableRegions;

    /** 是否启用 */
    private Boolean enabled;

    /** 模型版本号 */
    private Integer modelVersion;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String notes;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
