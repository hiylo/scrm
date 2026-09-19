/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOrderItemDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 订单项 DTO。
 * <p>
 * 用于订单项创建、更新与查询返回。创建时 {@code productName} / {@code unitPrice} / {@code quantity}
 * 必填, {@code subtotal} 缺省时按 {@code unitPrice × quantity - discountAmount} 计算。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmOrderItemDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 订单 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /** 商品 ID (可空, 兼容商品删除后的快照) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long productId;

    /** 商品编码 (可空) */
    @Size(max = 100, message = "商品编码长度不能超过 100")
    private String productCode;

    /** 商品名称 */
    @NotBlank(message = "商品名称不能为空")
    @Size(max = 200, message = "商品名称长度不能超过 200")
    private String productName;

    /** 商品图片 (可空) */
    @Size(max = 500, message = "商品图片长度不能超过 500")
    private String productImage;

    /** 规格 (可空) */
    @Size(max = 500, message = "规格长度不能超过 500")
    private String spec;

    /** 单价 (默认 0) */
    @NotNull(message = "单价不能为空")
    @PositiveOrZero(message = "单价不能为负数")
    private Double unitPrice;

    /** 数量 (默认 1) */
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于 0")
    private Integer quantity;

    /** 单项折扣 (默认 0) */
    @PositiveOrZero(message = "单项折扣不能为负数")
    private Double discountAmount;

    /** 小计 (默认 0) */
    @PositiveOrZero(message = "小计不能为负数")
    private Double subtotal;

    /** 税率 (默认 0) */
    @PositiveOrZero(message = "税率不能为负数")
    private Double taxRate;

    /** 税额 (默认 0) */
    @PositiveOrZero(message = "税额不能为负数")
    private Double taxAmount;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
