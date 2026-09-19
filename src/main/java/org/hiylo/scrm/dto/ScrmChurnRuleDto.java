/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChurnRuleDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 客户流失预警规则 DTO。
 * <p>
 * 对应 {@code ScrmChurnRuleEntity} 的业务字段, 创建/更新接口入参。
 * conditions 为 JSON 数组字符串: {@code [{field, operator, value}]}, field 如
 * lastInteractionDays / noInteractionDays / totalInteractions / lifecycle /
 * customerDays / orderFrequency, operator 如 gt/lt/eq/between。
 * actionParams 为 JSON 字符串, 描述动作参数 (如通知模板 ID / 跟进任务标题 / 标签 ID 等)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmChurnRuleDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 200, message = "规则名称长度不能超过 200")
    private String ruleName;

    /** 规则描述 (可空) */
    @Size(max = 500, message = "规则描述长度不能超过 500")
    private String description;

    /** 风险等级: HIGH / MEDIUM / LOW */
    @NotBlank(message = "风险等级不能为空")
    @Pattern(regexp = "HIGH|MEDIUM|LOW", message = "风险等级仅支持 HIGH/MEDIUM/LOW")
    private String riskLevel;

    /** 条件类型: ALL 所有条件满足 / ANY 任一满足 (默认 ALL) */
    @Pattern(regexp = "ALL|ANY", message = "条件类型仅支持 ALL/ANY")
    private String conditionType;

    /** 条件 JSON 数组: [{field, operator, value}] */
    @NotBlank(message = "条件 JSON 不能为空")
    private String conditions;

    /** 预警动作: NOTIFY_ASSIGNEE / CREATE_FOLLOW_UP / TRIGGER_MASS_SEND / ADD_TAG / CHANGE_LIFECYCLE / WEBHOOK */
    @NotBlank(message = "预警动作不能为空")
    @Pattern(regexp = "NOTIFY_ASSIGNEE|CREATE_FOLLOW_UP|TRIGGER_MASS_SEND|ADD_TAG|CHANGE_LIFECYCLE|WEBHOOK",
            message = "预警动作仅支持 NOTIFY_ASSIGNEE/CREATE_FOLLOW_UP/TRIGGER_MASS_SEND/ADD_TAG/CHANGE_LIFECYCLE/WEBHOOK")
    private String actionType;

    /** 动作参数 (JSON 字符串) */
    @NotBlank(message = "动作参数不能为空")
    private String actionParams;

    /** 同一客户冷却天数 (默认 7) */
    private Integer cooldownDays;

    /** 优先级 (数字越小越优先, 默认 0) */
    private Integer priority;

    /** 是否启用 (创建时可选, 默认 true) */
    private Boolean enabled;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 匹配次数 (查询返回) */
    private Integer matchCount;

    /** 最近匹配时间 (查询返回) */
    private LocalDateTime lastMatchAt;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
