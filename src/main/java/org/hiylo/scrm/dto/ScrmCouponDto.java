/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCouponDto.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM 优惠券实例 DTO。
 * <p>
 * 仅用于查询返回, 优惠券实例由发券接口与领取接口创建, 不接收前端直接入参。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmCouponDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 关联模板 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

    /** 优惠券码 (唯一) */
    private String couponCode;

    /** 持有客户 ID (可空, 未领取时为 null) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 持有客户名称 (可空) */
    private String customerName;

    /** 领取来源: MANUAL / MASS_SEND / CHANNEL_CODE / AUTO / ACTIVITY */
    private String claimSource;

    /** 领取时间 (可空) */
    private LocalDateTime claimedAt;

    /** 过期时间 */
    private LocalDateTime expiresAt;

    /** 状态: UNUSED / USED / EXPIRED / RETURNED */
    private String status;

    /** 使用时间 (可空) */
    private LocalDateTime usedAt;

    /** 使用订单号 (可空) */
    @Size(max = 100)
    private String usedOrder;

    /** 实际抵扣金额 (可空) */
    private Double usedAmount;

    /** 发放人 (可空) */
    private String issuedBy;

    /** 备注 (可空) */
    private String notes;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
