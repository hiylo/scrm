/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTagRuleDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 客户标签自动规则 DTO。
 * <p>
 * 对应 {@code ScrmTagRuleEntity} 的业务字段, 不含公共字段与执行统计字段 (matchedCount /
 * lastExecutedAt)。创建/更新接口入参, conditions 为 JSON 数组字符串,
 * 由 Service 层用 ObjectMapper 解析为 {@code List<Map<String, Object>>}。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmTagRuleDto {

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 200, message = "规则名称长度不能超过 200")
    private String ruleName;

    /** 关联标签 ID */
    @NotNull(message = "标签 ID 不能为空")
    private Long tagId;

    /** 规则描述 */
    @Size(max = 500, message = "规则描述长度不能超过 500")
    private String description;

    /** 条件组合类型: ALL 全部满足 / ANY 任一满足 / NONE 全部不满足 */
    @NotBlank(message = "条件组合类型不能为空")
    @Size(max = 20, message = "条件组合类型长度不能超过 20")
    @Pattern(regexp = "ALL|ANY|NONE", message = "条件组合类型仅支持 ALL/ANY/NONE")
    private String conditionType;

    /** 条件 JSON 数组: [{field, operator, value}] */
    @NotBlank(message = "条件 JSON 不能为空")
    private String conditions;

    /** 可用字段逗号分隔 (如 customer_name,order_count,last_interaction) */
    @Size(max = 500, message = "可用字段长度不能超过 500")
    private String targetFields;

    /** 执行频率: REALTIME / HOURLY / DAILY / WEEKLY / MANUAL */
    @NotBlank(message = "执行频率不能为空")
    @Size(max = 20, message = "执行频率长度不能超过 20")
    @Pattern(regexp = "REALTIME|HOURLY|DAILY|WEEKLY|MANUAL",
            message = "执行频率仅支持 REALTIME/HOURLY/DAILY/WEEKLY/MANUAL")
    private String executionFrequency;

    /** 状态: ACTIVE 活跃 / INACTIVE 停用 / DRAFT 草稿 (创建时可选, 默认 ACTIVE) */
    @Size(max = 20, message = "状态长度不能超过 20")
    @Pattern(regexp = "ACTIVE|INACTIVE|DRAFT|", message = "状态仅支持 ACTIVE/INACTIVE/DRAFT")
    private String status;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;
}
