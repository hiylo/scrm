/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmEngagementRuleDto.java
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
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 互动评分规则 DTO。
 * <p>
 * 对应 {@code ScrmEngagementRuleEntity} 的业务字段, 创建/更新接口入参。channel 为空表示
 * 任意渠道, decayType 为 LINEAR (线性衰减) / EXPONENTIAL (指数衰减) / STEP (阶梯衰减) /
 * NONE (不衰减)。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmEngagementRuleDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 200, message = "规则名称长度不能超过 200")
    private String ruleName;

    /** 行为类型: PAGE_VIEW/MESSAGE_SEND/MESSAGE_REPLY/CALL/EMAIL_OPEN/EMAIL_CLICK/LINK_CLICK/FORM_SUBMIT/PURCHASE/SHARE/FAVORITE/COMMENT/LOGIN/SEARCH/DOWNLOAD/APPOINTMENT */
    @NotBlank(message = "行为类型不能为空")
    @Size(max = 50, message = "行为类型长度不能超过 50")
    private String behaviorType;

    /** 发生渠道: WECHAT/WEB/APP/EMAIL/PHONE/STORE/OTHER (可空表示任意渠道) */
    @Size(max = 30, message = "渠道长度不能超过 30")
    private String channel;

    /** 单次得分 (正数) */
    @NotNull(message = "得分不能为空")
    @Positive(message = "得分必须为正数")
    private Integer points;

    /** 每日上限 (0 表示不限) */
    private Integer dailyLimit;

    /** 每周上限 (0 表示不限) */
    private Integer weeklyLimit;

    /** 每月上限 (0 表示不限) */
    private Integer monthlyLimit;

    /** 衰减天数 (默认 30) */
    private Integer decayDays;

    /** 衰减类型: LINEAR/EXPONENTIAL/STEP/NONE (默认 LINEAR) */
    @Pattern(regexp = "LINEAR|EXPONENTIAL|STEP|NONE", message = "衰减类型仅支持 LINEAR/EXPONENTIAL/STEP/NONE")
    private String decayType;

    /** 权重 (默认 1.0) */
    private Double weight;

    /** 描述 */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 是否启用 (创建时可选, 默认 true) */
    private Boolean enabled;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 匹配次数 (查询返回) */
    private Integer matchCount;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
