/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLevelRuleDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 客户等级升降级规则 DTO。
 * <p>
 * 对应 {@code ScrmCustomerLevelRuleEntity} 的业务字段, 创建/更新接口入参。
 * conditions 为 JSON 数组字符串: {@code [{field,operator,value}]}, field 如 totalSpent /
 * orderCount / registrationDays / lastInteractionDays / lifecycle, operator 如 eq/ne/gt/lt/between。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmCustomerLevelRuleDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 200, message = "规则名称长度不能超过 200")
    private String ruleName;

    /** 目标等级 ID (规则命中后客户将设为该等级) */
    @NotNull(message = "目标等级 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetLevelId;

    /** 规则类型: UPGRADE 升级 / DOWNGRADE 降级 */
    @NotBlank(message = "规则类型不能为空")
    @Pattern(regexp = "UPGRADE|DOWNGRADE", message = "规则类型仅支持 UPGRADE/DOWNGRADE")
    private String ruleType;

    /** 条件类型: ALL 所有条件满足 / ANY 任一满足 (默认 ALL) */
    @Pattern(regexp = "ALL|ANY", message = "条件类型仅支持 ALL/ANY")
    private String conditionType;

    /** 条件 JSON 数组: [{field, operator, value}] */
    @NotBlank(message = "条件 JSON 不能为空")
    private String conditions;

    /** 动作类型: SET_LEVEL 设置等级 (默认 SET_LEVEL) */
    @Pattern(regexp = "SET_LEVEL", message = "动作类型仅支持 SET_LEVEL")
    private String actionType;

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
