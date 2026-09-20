/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommissionRuleDto.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 销售佣金规则 DTO。
 * <p>
 * 对应 {@code ScrmCommissionRuleEntity} 的业务字段, 创建/更新接口入参。
 * conditions 为 JSON 数组字符串: {@code [{field,operator,value}]}, 字段如
 * product_category / order_amount / team / customer_type; tierConfig 为 JSON 数组字符串:
 * {@code [{minValue,maxValue,rate,bonusAmount}]}。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCommissionRuleDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 所属方案 ID */
    @NotNull(message = "方案 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long planId;

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 200, message = "规则名称长度不能超过 200")
    private String ruleName;

    /** 规则类型: FLAT_RATE/TIERED_RATE/BONUS/MULTIPLIER/DEDUCTION */
    @NotBlank(message = "规则类型不能为空")
    @Pattern(regexp = "FLAT_RATE|TIERED_RATE|BONUS|MULTIPLIER|DEDUCTION",
            message = "规则类型仅支持 FLAT_RATE/TIERED_RATE/BONUS/MULTIPLIER/DEDUCTION")
    private String ruleType;

    /** 匹配条件 JSON (可空): [{field,operator,value}] */
    private String conditions;

    /** 佣金比例 (0-1, FLAT_RATE/TIERED_RATE 使用) */
    private Double commissionRate;

    /** 固定佣金金额 */
    private Double commissionAmount;

    /** 阶梯配置 JSON (可空): [{minValue,maxValue,rate,bonusAmount}] */
    private String tierConfig;

    /** 奖金金额 */
    private Double bonusAmount;

    /** 倍数 (默认 1.0) */
    private Double multiplier;

    /** 扣减金额 */
    private Double deductionAmount;

    /** 最低订单金额 (订单金额低于此值不计算佣金) */
    private Double minOrderAmount;

    /** 每单最大佣金 (0 表示无限) */
    private Double maxCommissionPerOrder;

    /** 优先级 (数值越大优先级越高) */
    private Integer priority;

    /** 是否启用 */
    private Boolean enabled;

    /** 匹配次数 (查询返回) */
    private Integer matchCount;

    /** 累计计算佣金 (查询返回) */
    private Double totalCommissionCalculated;

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
