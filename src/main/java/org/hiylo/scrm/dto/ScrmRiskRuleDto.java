/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRiskRuleDto.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 风险规则 DTO。
 * <p>
 * 对应 {@code ScrmRiskRuleEntity} 的业务字段, 不含公共字段 (id/createTime/updateTime/version)。
 * 创建/更新接口入参, 校验注解保证必填字段与长度约束。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmRiskRuleDto {

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 200, message = "规则名称长度不能超过 200")
    private String ruleName;

    /** 规则代码（业务唯一） */
    @NotBlank(message = "规则代码不能为空")
    @Size(max = 100, message = "规则代码长度不能超过 100")
    private String ruleCode;

    /** SpEL 条件表达式 */
    @NotBlank(message = "SpEL 条件表达式不能为空")
    private String conditionExpression;

    /** 风险等级: LOW / MEDIUM / HIGH / CRITICAL */
    @NotBlank(message = "风险等级不能为空")
    @Size(max = 20, message = "风险等级长度不能超过 20")
    @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL", message = "风险等级仅支持 LOW/MEDIUM/HIGH/CRITICAL")
    private String riskLevel;

    /** 信号类型（如 frequency_overflow / keyword_match / time_anomaly） */
    @NotBlank(message = "信号类型不能为空")
    @Size(max = 50, message = "信号类型长度不能超过 50")
    private String signalType;

    /** 规则描述 */
    private String description;

    /** 是否启用（创建时可选, 默认 true） */
    private Boolean enabled;

    /** 优先级（数字越小越优先, 默认 100） */
    private Integer priority;

    /** 触发后动作: ALERT / PAUSE_ACCOUNT / STOP_CAMPAIGN（默认 ALERT） */
    @Size(max = 50, message = "动作长度不能超过 50")
    @Pattern(regexp = "ALERT|PAUSE_ACCOUNT|STOP_CAMPAIGN|",
            message = "动作仅支持 ALERT/PAUSE_ACCOUNT/STOP_CAMPAIGN")
    private String action;
}
