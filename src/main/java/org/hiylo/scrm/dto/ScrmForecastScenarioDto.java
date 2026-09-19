/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmForecastScenarioDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 销售预测场景 DTO。
 * <p>
 * 对应 {@code ScrmForecastScenarioEntity} 的业务字段, 创建/更新接口入参与查询返回。
 * inputParameters / adjustmentFactors 为 JSON 文本。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmForecastScenarioDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 场景名称 */
    @NotBlank(message = "场景名称不能为空")
    @Size(max = 200, message = "场景名称长度不能超过 200")
    private String scenarioName;

    /** 场景编码 (唯一) */
    @NotBlank(message = "场景编码不能为空")
    @Size(max = 50, message = "场景编码长度不能超过 50")
    private String scenarioCode;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 关联模型 ID */
    @NotNull(message = "模型 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    /** 关联模型名称 (查询返回) */
    private String modelName;

    /** 场景类型: BASELINE/OPTIMISTIC/PESSIMISTIC/WHAT_IF/TARGET/STRESS_TEST */
    @Pattern(regexp = "BASELINE|OPTIMISTIC|PESSIMISTIC|WHAT_IF|TARGET|STRESS_TEST",
            message = "场景类型仅支持 BASELINE/OPTIMISTIC/PESSIMISTIC/WHAT_IF/TARGET/STRESS_TEST")
    private String scenarioType;

    /** 预测周期 (如 2026-Q3) */
    @NotBlank(message = "预测周期不能为空")
    @Size(max = 20, message = "预测周期长度不能超过 20")
    private String targetPeriod;

    /** 目标开始日期 */
    @NotNull(message = "目标开始日期不能为空")
    private LocalDate targetStartDate;

    /** 目标结束日期 */
    @NotNull(message = "目标结束日期不能为空")
    private LocalDate targetEndDate;

    /** 粒度: DAILY/WEEKLY/MONTHLY/QUARTERLY */
    @Pattern(regexp = "DAILY|WEEKLY|MONTHLY|QUARTERLY",
            message = "粒度仅支持 DAILY/WEEKLY/MONTHLY/QUARTERLY")
    private String granularity;

    /** 假设条件 (可空) */
    @Size(max = 2000, message = "假设条件长度不能超过 2000")
    private String assumptions;

    /** JSON 输入参数: {growthRate, seasonalityFactor, marketCondition, ...} */
    private String inputParameters;

    /** JSON 调整因子: [{factorName, value, description}, ...] */
    private String adjustmentFactors;

    /** 适用客群 (逗号分隔, 可空) */
    @Size(max = 500, message = "适用客群长度不能超过 500")
    private String segments;

    /** 适用产品 (逗号分隔, 可空) */
    @Size(max = 500, message = "适用产品长度不能超过 500")
    private String products;

    /** 适用渠道 (逗号分隔, 可空) */
    @Size(max = 500, message = "适用渠道长度不能超过 500")
    private String channels;

    /** 适用地区 (逗号分隔, 可空) */
    @Size(max = 500, message = "适用地区长度不能超过 500")
    private String regions;

    /** 状态: DRAFT/RUNNING/COMPLETED/FAILED/ARCHIVED (查询返回) */
    private String status;

    /** 运行开始时间 (查询返回) */
    private LocalDateTime runStartedAt;

    /** 运行完成时间 (查询返回) */
    private LocalDateTime runCompletedAt;

    /** 运行耗时 (毫秒, 查询返回) */
    private Integer runDurationMs;

    /** 总预测值 (查询返回) */
    private Double totalForecastValue;

    /** 置信水平 */
    @Min(value = 0, message = "置信水平不能小于 0")
    private Double confidenceLevel;

    /** 置信下界 (查询返回) */
    private Double confidenceLowerBound;

    /** 置信上界 (查询返回) */
    private Double confidenceUpperBound;

    /** 预估准确度 (查询返回) */
    private Double accuracyEstimate;

    /** 风险因素 (可空) */
    @Size(max = 1000, message = "风险因素长度不能超过 1000")
    private String riskFactors;

    /** 机会因素 (可空) */
    @Size(max = 1000, message = "机会因素长度不能超过 1000")
    private String opportunities;

    /** 建议 (可空) */
    @Size(max = 2000, message = "建议长度不能超过 2000")
    private String recommendations;

    /** 审批人 (查询返回) */
    private String approvedBy;

    /** 审批时间 (查询返回) */
    private LocalDateTime approvedAt;

    /** 是否已审批 (查询返回) */
    private Boolean isApproved;

    /** 分享给的用户 ID 列表 (逗号分隔, 可空) */
    @Size(max = 500, message = "分享用户长度不能超过 500")
    private String sharedWith;

    /** 标签 (逗号分隔, 可空) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

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
