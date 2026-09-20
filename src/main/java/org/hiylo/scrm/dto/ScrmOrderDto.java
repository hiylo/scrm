/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOrderDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 订单 DTO。
 * <p>
 * 用于订单更新与查询返回。订单创建请使用 {@link ScrmOrderCreateDto}, 状态流转请使用
 * {@link ScrmOrderStatusDto}。{@code items} 仅查询返回时填充, 不接收前端入参。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmOrderDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 订单编号 (唯一) */
    @NotBlank(message = "订单编号不能为空")
    @Size(max = 50, message = "订单编号长度不能超过 50")
    private String orderNo;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 订单类型: SALE / REFUND / EXCHANGE / PRE_ORDER (默认 SALE) */
    @Pattern(regexp = "SALE|REFUND|EXCHANGE|PRE_ORDER", message = "订单类型仅支持 SALE/REFUND/EXCHANGE/PRE_ORDER")
    private String orderType;

    /** 订单状态: PENDING / CONFIRMED / PAID / SHIPPED / DELIVERED / COMPLETED / CANCELLED / REFUNDED */
    @Pattern(regexp = "PENDING|CONFIRMED|PAID|SHIPPED|DELIVERED|COMPLETED|CANCELLED|REFUNDED",
            message = "订单状态非法")
    private String orderStatus;

    /** 支付状态: UNPAID / PARTIAL / PAID / REFUNDED */
    @Pattern(regexp = "UNPAID|PARTIAL|PAID|REFUNDED", message = "支付状态非法")
    private String paymentStatus;

    /** 支付方式: WECHAT / ALIPAY / BANK / CARD / COD / OTHER (可空) */
    @Pattern(regexp = "WECHAT|ALIPAY|BANK|CARD|COD|OTHER",
            message = "支付方式仅支持 WECHAT/ALIPAY/BANK/CARD/COD/OTHER")
    private String paymentMethod;

    /** 总金额 (默认 0) */
    @PositiveOrZero(message = "总金额不能为负数")
    private Double totalAmount;

    /** 折扣金额 (默认 0) */
    @PositiveOrZero(message = "折扣金额不能为负数")
    private Double discountAmount;

    /** 运费 (默认 0) */
    @PositiveOrZero(message = "运费不能为负数")
    private Double shippingAmount;

    /** 税费 (默认 0) */
    @PositiveOrZero(message = "税费不能为负数")
    private Double taxAmount;

    /** 已付金额 (默认 0) */
    @PositiveOrZero(message = "已付金额不能为负数")
    private Double paidAmount;

    /** 币种 (默认 CNY) */
    @Size(max = 10, message = "币种长度不能超过 10")
    private String currency;

    /** 优惠券 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long couponId;

    /** 优惠券码 (可空) */
    @Size(max = 100, message = "优惠券码长度不能超过 100")
    private String couponCode;

    /** 销售员 ID (可空) */
    @Size(max = 100, message = "销售员 ID 长度不能超过 100")
    private String salespersonId;

    /** 销售员名称 (可空) */
    @Size(max = 100, message = "销售员名称长度不能超过 100")
    private String salespersonName;

    /** 下单渠道 (可空) */
    @Size(max = 50, message = "下单渠道长度不能超过 50")
    private String channel;

    /** 收货地址 (可空) */
    @Size(max = 500, message = "收货地址长度不能超过 500")
    private String shippingAddress;

    /** 收件人 (可空) */
    @Size(max = 100, message = "收件人长度不能超过 100")
    private String shippingName;

    /** 收件人电话 (可空) */
    @Size(max = 50, message = "收件人电话长度不能超过 50")
    private String shippingPhone;

    /** 物流单号 (可空) */
    @Size(max = 200, message = "物流单号长度不能超过 200")
    private String trackingNo;

    /** 物流公司 (可空) */
    @Size(max = 100, message = "物流公司长度不能超过 100")
    private String trackingCompany;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

    /** 支付时间 (可空) */
    private LocalDateTime paidAt;

    /** 发货时间 (可空) */
    private LocalDateTime shippedAt;

    /** 送达时间 (可空) */
    private LocalDateTime deliveredAt;

    /** 完成时间 (可空) */
    private LocalDateTime completedAt;

    /** 取消时间 (可空) */
    private LocalDateTime cancelledAt;

    /** 订单项快照 JSON (可空) */
    private String itemsJson;

    /** 订单项列表 (仅查询返回时填充) */
    private List<ScrmOrderItemDto> items;

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
