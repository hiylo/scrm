/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsRuleDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
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
 * SCRM 积分规则 DTO。
 * <p>
 * 对应 {@code ScrmPointsRuleEntity} 的业务字段, 创建/更新接口入参。ruleType 为 EARN (获取) /
 * REDEEM (消耗), pointsType 为 FIXED (固定) / PERCENTAGE (百分比, 按 basisField 基准字段计算)。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmPointsRuleDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 200, message = "规则名称长度不能超过 200")
    private String ruleName;

    /** 规则类型: EARN 获取 / REDEEM 消耗 */
    @NotBlank(message = "规则类型不能为空")
    @Pattern(regexp = "EARN|REDEEM", message = "规则类型仅支持 EARN/REDEEM")
    private String ruleType;

    /** 触发事件: PURCHASE/SIGN_IN/SHARE/REVIEW/INVITE/BIRTHDAY/PROFILE_COMPLETE/NEW_CUSTOMER/CONSUMPTION */
    @NotBlank(message = "触发事件不能为空")
    @Size(max = 50, message = "触发事件长度不能超过 50")
    private String triggerEvent;

    /** 积分值 (正数获取, 负数消耗) */
    @NotNull(message = "积分值不能为空")
    private Integer pointsValue;

    /** 积分计算类型: FIXED 固定 / PERCENTAGE 百分比 (默认 FIXED) */
    @Pattern(regexp = "FIXED|PERCENTAGE", message = "积分计算类型仅支持 FIXED/PERCENTAGE")
    private String pointsType;

    /** 百分比基准字段 (如 orderAmount) */
    @Size(max = 50, message = "基准字段长度不能超过 50")
    private String basisField;

    /** 每日上限 (可空表示不限) */
    private Integer dailyLimit;

    /** 每月上限 (可空表示不限) */
    private Integer monthlyLimit;

    /** 最少积分 (默认 0) */
    private Integer minPoints;

    /** 最多积分 (可空表示不限) */
    private Integer maxPoints;

    /** 描述 */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 是否启用 (创建时可选, 默认 true) */
    private Boolean enabled;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 触发次数 (查询返回) */
    private Integer triggerCount;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
