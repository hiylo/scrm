/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRiskAssessmentDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * SCRM 风险评估 DTO。
 * <p>
 * 用于对指定目标进行综合风险评估: 检查名单命中 → 评估风控规则 → 加权计算风险分 → 返回建议。
 * targetType 与 targetValue 必填, context 携带规则评估所需的上下文字段, amount 为交易金额
 * (用于金额类规则评估)。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmRiskAssessmentDto {

    /** 目标类型: CUSTOMER / PHONE / EMAIL / IP / DEVICE / ID_CARD / BANK_CARD / ADDRESS / WECHAT_ID / COMPANY */
    @NotBlank(message = "目标类型不能为空")
    @Size(max = 30, message = "目标类型长度不能超过 30")
    private String targetType;

    /** 目标值 */
    @NotBlank(message = "目标值不能为空")
    @Size(max = 500, message = "目标值长度不能超过 500")
    private String targetValue;

    /** 客户 ID (可空, 便于关联客户风险评分) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 评估上下文 (可空), 如 {frequency: 10, region: 'CN', device: 'ios'} */
    private Map<String, Object> context;

    /** 交易金额 (可空, 用于金额类规则评估) */
    private Double amount;
}
