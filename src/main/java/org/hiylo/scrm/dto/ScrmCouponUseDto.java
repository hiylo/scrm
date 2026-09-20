/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCouponUseDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 优惠券核销请求 DTO。
 * <p>
 * 通过 {@code couponCode} 定位优惠券, {@code orderId} 关联业务订单, {@code orderAmount} 为订单金额
 * (用于校验使用门槛与计算实际抵扣金额)。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmCouponUseDto {

    /** 优惠券码 */
    @NotBlank(message = "优惠券码不能为空")
    @Size(max = 100, message = "优惠券码长度不能超过 100")
    private String couponCode;

    /** 业务订单号 */
    @NotBlank(message = "订单号不能为空")
    @Size(max = 100, message = "订单号长度不能超过 100")
    private String orderId;

    /** 订单金额 */
    @NotNull(message = "订单金额不能为空")
    @PositiveOrZero(message = "订单金额不能为负数")
    private Double orderAmount;

    /** 操作人 ID (可空) */
    @Size(max = 100, message = "操作人 ID 长度不能超过 100")
    private String operatorId;

    /** 操作人名称 (可空) */
    @Size(max = 100, message = "操作人名称长度不能超过 100")
    private String operatorName;
}
