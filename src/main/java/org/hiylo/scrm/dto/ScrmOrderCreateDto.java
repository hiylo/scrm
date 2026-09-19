/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOrderCreateDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * SCRM 订单创建请求 DTO。
 * <p>
 * {@code customerId} 指定下单客户, {@code items} 为订单项列表 (至少一项), {@code couponId}
 * 可选关联优惠券, {@code remark} / {@code channel} / 收货信息 / 销售员等可选。
 * 订单编号由服务端自动生成, 金额由订单项汇总计算。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmOrderCreateDto {

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 订单项列表 (至少一项) */
    @NotEmpty(message = "订单项不能为空")
    @Valid
    private List<ScrmOrderItemDto> items;

    /** 优惠券 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long couponId;

    /** 优惠券码 (可空) */
    @Size(max = 100, message = "优惠券码长度不能超过 100")
    private String couponCode;

    /** 订单类型: SALE / REFUND / EXCHANGE / PRE_ORDER (默认 SALE) */
    @Pattern(regexp = "SALE|REFUND|EXCHANGE|PRE_ORDER", message = "订单类型仅支持 SALE/REFUND/EXCHANGE/PRE_ORDER")
    private String orderType;

    /** 下单渠道 (可空) */
    @Size(max = 50, message = "下单渠道长度不能超过 50")
    private String channel;

    /** 销售员 ID (可空) */
    @Size(max = 100, message = "销售员 ID 长度不能超过 100")
    private String salespersonId;

    /** 销售员名称 (可空) */
    @Size(max = 100, message = "销售员名称长度不能超过 100")
    private String salespersonName;

    /** 收货地址 (可空) */
    @Size(max = 500, message = "收货地址长度不能超过 500")
    private String shippingAddress;

    /** 收件人 (可空) */
    @Size(max = 100, message = "收件人长度不能超过 100")
    private String shippingName;

    /** 收件人电话 (可空) */
    @Size(max = 50, message = "收件人电话长度不能超过 50")
    private String shippingPhone;

    /** 运费 (可空, 默认 0) */
    private Double shippingAmount;

    /** 税费 (可空, 默认 0) */
    private Double taxAmount;

    /** 折扣金额 (可空, 默认 0) */
    private Double discountAmount;

    /** 币种 (默认 CNY) */
    @Size(max = 10, message = "币种长度不能超过 10")
    private String currency;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;
}
