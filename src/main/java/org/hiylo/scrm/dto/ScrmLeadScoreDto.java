/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLeadScoreDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 销售线索评分 DTO。
 * <p>
 * 对应 {@code ScrmLeadScoreEntity} 的业务字段, 创建/更新接口入参与查询返回。
 * dimensionScores 为 JSON 数组字符串: {@code [{dimension, score, maxScore, details}]}。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmLeadScoreDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    private String customerName;

    /** 评分模型 ID */
    @NotNull(message = "模型 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    /** 总分 */
    private Double totalScore;

    /** 满分 */
    private Double maxScore;

    /** 得分百分比 */
    private Double scorePercent;

    /** 等级: A_PLUS / SUPER_HOT / HOT / WARM / COLD / DEAD / A / B / C / D / E */
    private String grade;

    /** 等级标签 */
    private String gradeLabel;

    /** 各维度得分 JSON */
    private String dimensionScores;

    /** 转化概率 (0-1) */
    private Double conversionProbability;

    /** 预测价值 */
    private Double predictedValue;

    /** 是否热线索 */
    private Boolean isHotLead;

    /** 是否合格线索 */
    private Boolean isQualified;

    /** 最近计算时间 */
    private LocalDateTime lastCalculatedAt;

    /** 评分趋势: UP / STABLE / DOWN */
    private String scoreTrend;

    /** 趋势变化 */
    private Double trendChange;

    /** 上次得分 */
    private Double previousScore;

    /** 分配给 */
    private String assignedTo;

    /** 分配时间 */
    private LocalDateTime assignedAt;

    /** 最近联系时间 */
    private LocalDateTime contactedAt;

    /** 转化时间 */
    private LocalDateTime convertedAt;

    /** 是否已转化 */
    private Boolean isConverted;

    /** 转化价值 */
    private Double conversionValue;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
