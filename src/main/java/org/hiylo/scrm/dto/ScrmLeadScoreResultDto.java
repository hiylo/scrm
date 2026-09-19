/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLeadScoreResultDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * SCRM 销售线索评分计算结果 DTO。
 * <p>
 * 由 {@code ScrmLeadScoringService.calculateScore} 在评分计算后返回, 仅携带核心结果字段,
 * 供调用方快速获取评分摘要 (总分 / 等级 / 各维度得分 / 转化概率)。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmLeadScoreResultDto {

    /** 客户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 总分 */
    private Double totalScore;

    /** 等级 */
    private String grade;

    /** 各维度得分 JSON */
    private String dimensionScores;

    /** 转化概率 (0-1) */
    private Double conversionProbability;
}
