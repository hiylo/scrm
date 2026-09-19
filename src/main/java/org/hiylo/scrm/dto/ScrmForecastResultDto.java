/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmForecastResultDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 销售预测结果 DTO。
 * <p>
 * 对应 {@code ScrmForecastResultEntity} 的业务字段, 主要用于查询返回。
 * breakdown / contributingFactors / metadata 为 JSON 文本。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmForecastResultDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 场景 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long scenarioId;

    /** 场景名称 (查询返回) */
    private String scenarioName;

    /** 模型 ID (查询返回) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    /** 模型名称 (查询返回) */
    private String modelName;

    /** 预测日期 */
    private LocalDate forecastDate;

    /** 周期标签 (如 2026-08) */
    private String periodLabel;

    /** 周期序号 */
    private Integer periodIndex;

    /** 预测值 */
    private Double forecastValue;

    /** 实际值 (可空) */
    private Double actualValue;

    /** 偏差 (实际-预测) */
    private Double variance;

    /** 偏差百分比 */
    private Double variancePercent;

    /** 置信下界 */
    private Double confidenceLower;

    /** 置信上界 */
    private Double confidenceUpper;

    /** 置信区间宽度 */
    private Double confidenceRange;

    /** 是否实际值 (训练数据回填时为 TRUE) */
    private Boolean isActual;

    /** 客群 */
    private String segment;

    /** 产品 */
    private String product;

    /** 渠道 */
    private String channel;

    /** 地区 */
    private String region;

    /** JSON 细分拆分: [{dimension, value, forecastValue, actualValue}, ...] */
    private String breakdown;

    /** JSON 贡献因子 */
    private String contributingFactors;

    /** JSON 附加数据 */
    private String metadata;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
