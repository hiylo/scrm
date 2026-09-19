/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAbTestDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM A/B 测试 DTO。
 * <p>
 * 对应 {@code ScrmAbTestEntity} 的业务字段, 创建/更新接口入参。
 * variants 为 JSON 文本: {@code [{name,description,audienceSize,metrics:{}}]}。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmAbTestDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 测试名称 */
    @NotBlank(message = "测试名称不能为空")
    @Size(max = 200, message = "测试名称长度不能超过 200")
    private String testName;

    /** 测试编码 (唯一) */
    @NotBlank(message = "测试编码不能为空")
    @Size(max = 50, message = "测试编码长度不能超过 50")
    private String testCode;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 关联营销活动 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long campaignId;

    /** 关联营销活动名称 (可空) */
    @Size(max = 200, message = "营销活动名称长度不能超过 200")
    private String campaignName;

    /** 测试类型 */
    @NotBlank(message = "测试类型不能为空")
    @Pattern(regexp = "SUBJECT|CONTENT|CTA|DESIGN|TIMING|SEGMENT|CHANNEL|OFFER|LANDING_PAGE|FULL",
            message = "测试类型仅支持 SUBJECT/CONTENT/CTA/DESIGN/TIMING/SEGMENT/CHANNEL/OFFER/LANDING_PAGE/FULL")
    private String testType;

    /** 假设 (可空) */
    @Size(max = 1000, message = "假设长度不能超过 1000")
    private String hypothesis;

    /** 主要指标 */
    @NotBlank(message = "主要指标不能为空")
    @Pattern(
            regexp = "OPEN_RATE|CLICK_RATE|CONVERSION_RATE|REVENUE|ENGAGEMENT|BOUNCE_RATE|UNSUBSCRIBE_RATE|CTR|CVR|CPS",
            message = "主要指标仅支持 OPEN_RATE/CLICK_RATE/CONVERSION_RATE/REVENUE/ENGAGEMENT/BOUNCE_RATE/UNSUBSCRIBE_RATE/CTR/CVR/CPS")
    private String metric;

    /** 次要指标 (可空) */
    @Size(max = 500, message = "次要指标长度不能超过 500")
    private String secondaryMetrics;

    /** JSON 变体: [{name,description,audienceSize,metrics:{}}] */
    @NotBlank(message = "变体数据不能为空")
    private String variants;

    /** 变体数 (默认 2) */
    private Integer variantCount;

    /** 流量分配 (逗号分隔, 默认 50,50) */
    @Size(max = 200, message = "流量分配长度不能超过 200")
    private String trafficSplit;

    /** 目标样本量 */
    private Integer targetSampleSize;

    /** 最小样本量 */
    private Integer minSampleSize;

    /** 当前样本量 */
    private Integer currentSampleSize;

    /** 测试开始日期 */
    @NotNull(message = "测试开始日期不能为空")
    private LocalDate startDate;

    /** 测试结束日期 (可空) */
    private LocalDate endDate;

    /** 状态: DRAFT/RUNNING/PAUSED/COMPLETED/CANCELLED */
    @Pattern(regexp = "DRAFT|RUNNING|PAUSED|COMPLETED|CANCELLED",
            message = "状态仅支持 DRAFT/RUNNING/PAUSED/COMPLETED/CANCELLED")
    private String status;

    /** 获胜变体 (查询返回) */
    private String winningVariant;

    /** 获胜置信度% (查询返回) */
    private Double winnerConfidence;

    /** 提升幅度% (查询返回) */
    private Double winnerImprovement;

    /** 是否显著 (查询返回) */
    private Boolean isSignificant;

    /** 显著性水平 α (默认 0.05) */
    private Double significanceLevel;

    /** 统计方法 */
    @Pattern(regexp = "CHI_SQUARE|T_TEST|Z_TEST|MANN_WHITNEY|BAYESIAN",
            message = "统计方法仅支持 CHI_SQUARE/T_TEST/Z_TEST/MANN_WHITNEY/BAYESIAN")
    private String statisticalMethod;

    /** p 值 (查询返回) */
    private Double pValue;

    /** 置信区间 (查询返回) */
    private String confidenceInterval;

    /** 效应量 (查询返回) */
    private Double effectSize;

    /** 检验效能 (查询返回) */
    private Double power;

    /** 结论 (可空) */
    @Size(max = 2000, message = "结论长度不能超过 2000")
    private String conclusion;

    /** 建议 (可空) */
    @Size(max = 2000, message = "建议长度不能超过 2000")
    private String recommendation;

    /** 持续天数 */
    private Integer durationDays;

    /** 停止规则 (可空) */
    @Size(max = 200, message = "停止规则长度不能超过 200")
    private String stoppingRule;

    /** 自动停止 */
    private Boolean autoStop;

    /** 停止时间 (查询返回) */
    private LocalDate stoppedAt;

    /** 停止原因 (查询返回) */
    private String stoppedReason;

    /** JSON 分析结果 (查询返回) */
    private String analysisResult;

    /** 最近分析时间 (查询返回) */
    private LocalDateTime lastAnalyzedAt;

    /** 标签 (可空) */
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
