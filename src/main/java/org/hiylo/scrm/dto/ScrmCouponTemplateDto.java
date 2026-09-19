/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCouponTemplateDto.java
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
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 优惠券模板 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCouponTemplateDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 200, message = "模板名称长度不能超过 200")
    private String templateName;

    /** 优惠券类型: DISCOUNT / FIXED_AMOUNT / EXCHANGE / GIFT / CASH_VOUCHER */
    @NotBlank(message = "优惠券类型不能为空")
    @Size(max = 20, message = "优惠券类型长度不能超过 20")
    @Pattern(regexp = "DISCOUNT|FIXED_AMOUNT|EXCHANGE|GIFT|CASH_VOUCHER",
            message = "优惠券类型仅支持 DISCOUNT/FIXED_AMOUNT/EXCHANGE/GIFT/CASH_VOUCHER")
    private String couponType;

    /** 面值/折扣率 (DISCOUNT 为折扣率 0~1, 其余为面值金额) */
    @NotNull(message = "面值不能为空")
    @PositiveOrZero(message = "面值不能为负数")
    private Double faceValue;

    /** 使用门槛满 X 元 */
    @PositiveOrZero(message = "使用门槛不能为负数")
    private Double thresholdAmount;

    /** 折扣上限 (仅 DISCOUNT 类型生效, 可空) */
    @PositiveOrZero(message = "折扣上限不能为负数")
    private Double discountLimit;

    /** 有效期类型: FIXED (固定日期) / RELATIVE (领取后 N 天) */
    @NotBlank(message = "有效期类型不能为空")
    @Pattern(regexp = "FIXED|RELATIVE", message = "有效期类型仅支持 FIXED/RELATIVE")
    private String validType;

    /** 固定开始日期 (validType=FIXED 时生效) */
    private LocalDate validStart;

    /** 固定结束日期 (validType=FIXED 时生效) */
    private LocalDate validEnd;

    /** 领取后有效天数 (validType=RELATIVE 时生效) */
    @Positive(message = "有效天数必须为正数")
    private Integer validDays;

    /** 总发行量 */
    @NotNull(message = "总发行量不能为空")
    @Positive(message = "总发行量必须为正数")
    private Integer totalQuantity;

    /** 已发放数量 */
    private Integer issuedQuantity;

    /** 已使用数量 */
    private Integer usedQuantity;

    /** 已领取数量 */
    private Integer claimedQuantity;

    /** 每人限领 */
    @PositiveOrZero(message = "每人限领不能为负数")
    private Integer perUserLimit;

    /** 适用商品 ID 逗号分隔 (可空) */
    @Size(max = 1000, message = "适用商品长度不能超过 1000")
    private String applicableProducts;

    /** 适用场景 (可空) */
    @Size(max = 500, message = "适用场景长度不能超过 500")
    private String applicableScenes;

    /** 使用说明 (可空) */
    @Size(max = 500, message = "使用说明长度不能超过 500")
    private String description;

    /** 使用规则 (可空) */
    @Size(max = 2000, message = "使用规则长度不能超过 2000")
    private String rules;

    /** 状态: ACTIVE / INACTIVE / EXPIRED / SOLD_OUT */
    @Pattern(regexp = "ACTIVE|INACTIVE|EXPIRED|SOLD_OUT",
            message = "状态仅支持 ACTIVE/INACTIVE/EXPIRED/SOLD_OUT")
    private String status;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
