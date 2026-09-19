/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReferralRewardDto.java
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

import java.time.LocalDateTime;

/**
 * SCRM 客户推荐奖励 DTO。
 * <p>
 * 用于推荐奖励的查询返回与状态更新。奖励记录由服务端在发放奖励时创建,
 * 此 DTO 承载查询返回场景, 状态字段用于兑换/过期等状态流转。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmReferralRewardDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 推荐记录 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long referralId;

    /** 推荐活动 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long programId;

    /** 接收者类型: REFERRER / REFEREE */
    @Size(max = 20, message = "接收者类型长度不能超过 20")
    private String recipientType;

    /** 接收者客户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recipientCustomerId;

    /** 接收者名称 (可空) */
    @Size(max = 200, message = "接收者名称长度不能超过 200")
    private String recipientName;

    /** 奖励类型: POINTS / COUPON / CASH / DISCOUNT / GIFT / MEMBERSHIP */
    @Size(max = 30, message = "奖励类型长度不能超过 30")
    private String rewardType;

    /** 奖励值 (面值/积分数/折扣率) */
    private Double rewardValue;

    /** 奖励配置 (可空, JSON 奖励配置) */
    private String rewardConfig;

    /** 状态: PENDING / ISSUED / REDEEMED / EXPIRED / CANCELLED */
    @Size(max = 20, message = "状态长度不能超过 20")
    private String status;

    /** 发放时间 (可空) */
    private LocalDateTime issuedAt;

    /** 兑换时间 (可空) */
    private LocalDateTime redeemedAt;

    /** 过期时间 (可空) */
    private LocalDateTime expiredAt;

    /** 优惠券码 (可空) */
    @Size(max = 100, message = "优惠券码长度不能超过 100")
    private String couponCode;

    /** 积分账户 (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long pointsAccount;

    /** 交易 ID (可空) */
    @Size(max = 100, message = "交易 ID 长度不能超过 100")
    private String transactionId;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String notes;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
