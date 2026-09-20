/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReferralDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 客户推荐关系 DTO。
 * <p>
 * 用于推荐记录的查询返回与更新。推荐记录通过 {@link ScrmReferralCreateDto} 创建,
 * 创建时由服务端生成推荐码与状态。此 DTO 主要承载查询返回与状态更新场景。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmReferralDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 推荐活动 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long programId;

    /** 推荐码 (唯一) */
    @Size(max = 100, message = "推荐码长度不能超过 100")
    private String referralCode;

    /** 推荐人客户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long referrerCustomerId;

    /** 推荐人名称 (可空) */
    @Size(max = 200, message = "推荐人名称长度不能超过 200")
    private String referrerName;

    /** 被推荐人客户 ID (可空, 注册前未关联) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long refereeCustomerId;

    /** 被推荐人名称 (可空) */
    @Size(max = 200, message = "被推荐人名称长度不能超过 200")
    private String refereeName;

    /** 被推荐人联系方式 (可空) */
    @Size(max = 200, message = "被推荐人联系方式长度不能超过 200")
    private String refereeContact;

    /** 推荐渠道: LINK / QR_CODE / CODE / EMAIL / SMS / WECHAT (可空) */
    @Size(max = 30, message = "推荐渠道长度不能超过 30")
    private String referralChannel;

    /** 推荐链接 (可空) */
    @Size(max = 500, message = "推荐链接长度不能超过 500")
    private String referralLink;

    /** 状态: PENDING / SIGNED_UP / QUALIFIED / REWARDED / EXPIRED / CANCELLED */
    @Size(max = 20, message = "状态长度不能超过 20")
    private String status;

    /** 注册时间 (可空) */
    private LocalDateTime signedUpAt;

    /** 达标时间 (可空) */
    private LocalDateTime qualifiedAt;

    /** 奖励发放时间 (可空) */
    private LocalDateTime rewardedAt;

    /** 推荐人奖励状态: PENDING / ISSUED / REDEEMED / EXPIRED */
    @Size(max = 20, message = "推荐人奖励状态长度不能超过 20")
    private String referrerRewardStatus;

    /** 被推荐人奖励状态: PENDING / ISSUED / REDEEMED / EXPIRED */
    @Size(max = 20, message = "被推荐人奖励状态长度不能超过 20")
    private String refereeRewardStatus;

    /** 推荐人奖励详情 (可空) */
    @Size(max = 500, message = "推荐人奖励详情长度不能超过 500")
    private String referrerRewardDetails;

    /** 被推荐人奖励详情 (可空) */
    @Size(max = 500, message = "被推荐人奖励详情长度不能超过 500")
    private String refereeRewardDetails;

    /** 被推荐人消费金额 */
    private Double purchaseAmount;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String notes;

    /** 过期时间 (可空) */
    private LocalDateTime expiredAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
