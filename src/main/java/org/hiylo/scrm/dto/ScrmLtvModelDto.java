/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLtvModelDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM LTV 模型配置 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmLtvModelDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 模型名称 */
    @NotBlank(message = "模型名称不能为空")
    @Size(max = 200, message = "模型名称长度不能超过 200")
    private String modelName;

    /** 模型编码 (全局唯一) */
    @NotBlank(message = "模型编码不能为空")
    @Size(max = 50, message = "模型编码长度不能超过 50")
    private String modelCode;

    /** 模型描述 */
    @Size(max = 500, message = "模型描述长度不能超过 500")
    private String description;

    /** 模型类型: HISTORICAL / PROBABILISTIC / PREDICTIVE / COHORT / HEURISTIC */
    @NotBlank(message = "模型类型不能为空")
    @Pattern(regexp = "HISTORICAL|PROBABILISTIC|PREDICTIVE|COHORT|HEURISTIC",
            message = "模型类型仅支持 HISTORICAL/PROBABILISTIC/PREDICTIVE/COHORT/HEURISTIC")
    private String modelType;

    /** 计算方法: SIMPLE_AVG / WEIGHTED_AVG / DISCOUNTED_CASH_FLOW / PARETO_NBD / BUY_TILL_YOU_DIE */
    @NotBlank(message = "计算方法不能为空")
    @Pattern(regexp = "SIMPLE_AVG|WEIGHTED_AVG|DISCOUNTED_CASH_FLOW|PARETO_NBD|BUY_TILL_YOU_DIE",
            message = "计算方法仅支持 SIMPLE_AVG/WEIGHTED_AVG/DISCOUNTED_CASH_FLOW/PARETO_NBD/BUY_TILL_YOU_DIE")
    private String calculationMethod;

    /** 回溯天数 (默认 365) */
    @Min(value = 1, message = "回溯天数不能小于 1")
    private Integer lookbackDays;

    /** 预测天数 (默认 365) */
    @Min(value = 1, message = "预测天数不能小于 1")
    private Integer forecastDays;

    /** 折现率 (0-1) */
    @Min(value = 0, message = "折现率不能小于 0")
    @Max(value = 1, message = "折现率不能大于 1")
    private Double discountRate;

    /** 流失率 (0-1) */
    @Min(value = 0, message = "流失率不能小于 0")
    @Max(value = 1, message = "流失率不能大于 1")
    private Double churnRate;

    /** 平均利润率 (0-1) */
    @Min(value = 0, message = "平均利润率不能小于 0")
    @Max(value = 1, message = "平均利润率不能大于 1")
    private Double avgProfitMargin;

    /** 购买频次阈值 */
    @Min(value = 0, message = "购买频次阈值不能小于 0")
    private Integer purchaseFrequencyThreshold;

    /** 价值分层阈值 (JSON 数组) */
    private String tierThresholds;

    /** 是否为默认模型 */
    private Boolean isDefault;

    /** 是否已发布 */
    private Boolean isPublished;

    /** 模型版本号 */
    private Integer modelVersion;

    /** 应用次数 */
    private Integer appliedCount;

    /** 最近一次应用时间 */
    private LocalDateTime lastAppliedAt;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
