/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMembershipBenefitDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 会员权益 DTO。
 * <p>
 * 用于会员权益的创建、更新与查询返回。创建时必填权益名称、编码与权益类型;
 * 状态缺省 ACTIVE, 可见缺省 TRUE, 各类使用限制与统计字段缺省 0。
 * 更新时字段非空才覆盖。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmMembershipBenefitDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 权益名称 */
    @NotBlank(message = "权益名称不能为空")
    @Size(max = 200, message = "权益名称长度不能超过 200")
    private String benefitName;

    /** 权益编码 (唯一) */
    @NotBlank(message = "权益编码不能为空")
    @Size(max = 50, message = "权益编码长度不能超过 50")
    private String benefitCode;

/** 权益类型: DISCOUNT / FREE_SHIPPING / POINTS_MULTIPLIER / EXCLUSIVE_PRODUCT / PRIORITY_SUPPORT / BIRTHDAY_BONUS /
         * FREE_RETURN / COUPON / GIFT / EXPERIENCE / SERVICE / CUSTOM */
    @NotBlank(message = "权益类型不能为空")
    @Size(max = 30, message = "权益类型长度不能超过 30")
    private String benefitType;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 适用等级 ID (可空, null=所有等级) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tierId;

    /** 适用等级名称 (可空) */
    @Size(max = 200, message = "适用等级名称长度不能超过 200")
    private String tierName;

    /** 权益值 (如折扣率, 默认 0) */
    private Double value;

    /** 值类型 (可空): PERCENTAGE / AMOUNT / COUNT / DAYS */
    @Size(max = 20, message = "值类型长度不能超过 20")
    private String valueType;

    /** 适用商品 (可空) */
    @Size(max = 500, message = "适用商品长度不能超过 500")
    private String applicableProducts;

    /** 适用类目 (可空) */
    @Size(max = 500, message = "适用类目长度不能超过 500")
    private String applicableCategories;

    /** 适用渠道 (可空) */
    @Size(max = 500, message = "适用渠道长度不能超过 500")
    private String applicableChannels;

    /** 每人限制 (0=无限, 默认 0) */
    private Integer usageLimitPerMember;

    /** 每日限制 (默认 0) */
    private Integer usageLimitPerDay;

    /** 每月限制 (默认 0) */
    private Integer usageLimitPerMonth;

    /** 总限制 (0=无限, 默认 0) */
    private Integer usageLimitTotal;

    /** 当前使用次数 (默认 0) */
    private Integer currentUsageCount;

    /** 会员使用次数 (默认 0) */
    private Integer memberUsageCount;

    /** 生效开始日期 (可空) */
    private LocalDate startDate;

    /** 生效结束日期 (可空) */
    private LocalDate endDate;

    /** 状态: ACTIVE / INACTIVE / EXPIRED (默认 ACTIVE) */
    @Size(max = 20, message = "状态长度不能超过 20")
    private String status;

    /** 兑换说明 (可空) */
    @Size(max = 1000, message = "兑换说明长度不能超过 1000")
    private String redemptionInstructions;

    /** 使用条款 (可空) */
    @Size(max = 1000, message = "使用条款长度不能超过 1000")
    private String terms;

    /** 图标 (可空) */
    @Size(max = 200, message = "图标长度不能超过 200")
    private String icon;

    /** 展示顺序 (默认 0) */
    private Integer displayOrder;

    /** 是否可见 (默认 TRUE) */
    private Boolean isVisible;

    /** 总兑换价值 (默认 0) */
    private Double totalRedeemedValue;

    /** 总节省金额 (默认 0) */
    private Double totalSavedAmount;

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
