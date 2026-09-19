/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQualityInspectionRuleDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 质检规则 DTO。
 * <p>
 * 对应 {@code ScrmQualityInspectionRuleEntity} 的业务字段, 不含公共字段。
 * 创建/更新接口入参, 校验注解保证必填字段与长度约束。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmQualityInspectionRuleDto {

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 200, message = "规则名称长度不能超过 200")
    private String ruleName;

    /** 质检类别: SCRIPT_COMPLIANCE/SERVICE_ATTITUDE/SENSITIVE_WORD/RESPONSE_TIME/PROFESSIONALISM/COMPLIANCE */
    @NotBlank(message = "质检类别不能为空")
    @Size(max = 30, message = "质检类别长度不能超过 30")
    @Pattern(regexp = "SCRIPT_COMPLIANCE|SERVICE_ATTITUDE|SENSITIVE_WORD|RESPONSE_TIME|PROFESSIONALISM|COMPLIANCE",
            message = "质检类别仅支持 SCRIPT_COMPLIANCE/SERVICE_ATTITUDE/SENSITIVE_WORD/RESPONSE_TIME/PROFESSIONALISM/COMPLIANCE")
    private String category;

    /** 规则描述（可空） */
    @Size(max = 500, message = "规则描述长度不能超过 500")
    private String description;

    /** 规则类型: KEYWORD_MATCH/REGEX/DURATION/RESPONSE_TIME/AI_EVALUATE */
    @NotBlank(message = "规则类型不能为空")
    @Size(max = 20, message = "规则类型长度不能超过 20")
    @Pattern(regexp = "KEYWORD_MATCH|REGEX|DURATION|RESPONSE_TIME|AI_EVALUATE",
            message = "规则类型仅支持 KEYWORD_MATCH/REGEX/DURATION/RESPONSE_TIME/AI_EVALUATE")
    private String ruleType;

    /** 规则配置 JSON: {keywords:[], regex:"", maxResponseSeconds:300, promptTemplate:"", scoreWeight:1.0} */
    @NotBlank(message = "规则配置不能为空")
    private String ruleConfig;

    /** 通过条件: GTE:80/LTE:30/CONTAINS/NOT_CONTAINS（创建时可选, 默认 GTE:80） */
    @Size(max = 20, message = "通过条件长度不能超过 20")
    private String passCondition;

    /** 评分权重（默认 1.0） */
    @Positive(message = "评分权重必须大于 0")
    private Double scoreWeight;

    /** 是否启用（创建时可选, 默认 true） */
    private Boolean enabled;

    /** 创建人（可空） */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;
}
