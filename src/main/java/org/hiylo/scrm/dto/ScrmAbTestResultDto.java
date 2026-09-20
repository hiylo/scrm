/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAbTestResultDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * SCRM A/B 测试结果 DTO。
 * <p>
 * 封装测试的完整结果: 各变体指标 ({@link #variants}), 统计显著性检验结果
 * ({@link #statisticalSignificance}) 与胜出者信息 ({@link #winner})。
 * {@link #isSignificant} 标记结果是否达到显著性阈值, {@link #conclusion} 为实验结论。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmAbTestResultDto {

    /** 测试 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long testId;

    /** 测试名称 */
    private String testName;

    /** 测试类型 */
    private String testType;

    /** 测试目标 */
    private String testObjective;

    /** 状态 */
    private String status;

    /** 置信水平 */
    private Double confidenceLevel;

    /** 显著性阈值 */
    private Double significanceThreshold;

    /** 结果是否显著 */
    private Boolean isSignificant;

    /** 实验结论 */
    private String conclusion;

    /** 总参与人数 */
    private Integer totalParticipants;

    /** 总转化数 */
    private Integer totalConversions;

    /** 各变体指标列表 [{variantId, variantName, isControl, participants, conversions, conversionRate, revenue, ...}] */
    private List<Map<String, Object>> variants;

    /** 统计显著性检验结果 {method, chiSquare, zScore, pValue, isSignificant, confidenceInterval, ...} */
    private Map<String, Object> statisticalSignificance;

    /** 胜出者信息 {winnerVariantId, winnerVariantName, lift, improvement, reason} */
    private Map<String, Object> winner;
}
