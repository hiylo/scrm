/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReferralProgramDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 客户推荐活动 DTO。
 * <p>
 * 用于推荐活动的创建、更新、查询返回。创建时必填活动名称、编码、活动类型、奖励类型与触发条件;
 * 状态缺省由服务端补全为 ACTIVE。更新时字段非空才覆盖。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmReferralProgramDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 活动名称 */
    @NotBlank(message = "活动名称不能为空")
    @Size(max = 200, message = "活动名称长度不能超过 200")
    private String programName;

    /** 活动编码 (唯一) */
    @NotBlank(message = "活动编码不能为空")
    @Size(max = 50, message = "活动编码长度不能超过 50")
    private String programCode;

    /** 活动描述 (可空) */
    @Size(max = 500, message = "活动描述长度不能超过 500")
    private String description;

    /** 活动类型: REFERRAL / INVITE / SHARE / AMBASSADOR / AFFILIATE */
    @NotBlank(message = "活动类型不能为空")
    @Size(max = 30, message = "活动类型长度不能超过 30")
    private String programType;

    /** 推荐人奖励类型: POINTS / COUPON / CASH / DISCOUNT / GIFT / MEMBERSHIP */
    @NotBlank(message = "推荐人奖励类型不能为空")
    @Size(max = 30, message = "推荐人奖励类型长度不能超过 30")
    private String referrerRewardType;

    /** 推荐人奖励值 (面值/积分数/折扣率) */
    @NotNull(message = "推荐人奖励值不能为空")
    private Double referrerRewardValue;

    /** 推荐人奖励配置 (可空, JSON 奖励配置) */
    private String referrerRewardConfig;

    /** 被推荐人奖励类型: POINTS / COUPON / CASH / DISCOUNT / GIFT / MEMBERSHIP */
    @NotBlank(message = "被推荐人奖励类型不能为空")
    @Size(max = 30, message = "被推荐人奖励类型长度不能超过 30")
    private String refereeRewardType;

    /** 被推荐人奖励值 (面值/积分数/折扣率) */
    @NotNull(message = "被推荐人奖励值不能为空")
    private Double refereeRewardValue;

    /** 被推荐人奖励配置 (可空, JSON 奖励配置) */
    private String refereeRewardConfig;

    /** 奖励触发条件: SIGNUP / FIRST_PURCHASE / PURCHASE_AMOUNT / RETENTION_DAYS */
    @Size(max = 30, message = "奖励触发条件长度不能超过 30")
    private String rewardTrigger;

    /** 触发条件值 (如消费金额阈值/留存天数, 可空) */
    private Double rewardTriggerValue;

    /** 每人最大推荐数 (0=无限) */
    private Integer maxReferralsPerReferrer;

    /** 总推荐上限 (0=无限) */
    private Integer maxReferralsTotal;

    /** 双向奖励 */
    private Boolean doubleSidedReward;

    /** 活动开始日期 */
    @NotNull(message = "活动开始日期不能为空")
    private LocalDate startDate;

    /** 活动结束日期 (可空, 无截止) */
    private LocalDate endDate;

    /** 状态: ACTIVE / PAUSED / EXPIRED / COMPLETED */
    @Size(max = 20, message = "状态长度不能超过 20")
    private String status;

    /** 活动条款 (可空) */
    private String termsConditions;

    /** 总推荐数 */
    private Integer totalReferrals;

    /** 成功推荐数 */
    private Integer successfulReferrals;

    /** 累计奖励价值 */
    private Double totalRewardValue;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
