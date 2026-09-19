/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsExchangeDto.java
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

import java.time.LocalDateTime;

/**
 * SCRM 积分兑换商品 DTO。
 * <p>
 * 对应 {@code ScrmPointsExchangeEntity} 的业务字段, 创建/更新接口入参。exchangeType 为
 * PHYSICAL 实物 / VIRTUAL 虚拟 / COUPON 优惠券 / CASH 现金。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmPointsExchangeDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 兑换商品名 */
    @NotBlank(message = "兑换商品名不能为空")
    @Size(max = 200, message = "兑换商品名长度不能超过 200")
    private String itemName;

    /** 商品图片 URL */
    @Size(max = 500, message = "商品图片 URL 长度不能超过 500")
    private String itemImage;

    /** 商品描述 */
    @Size(max = 500, message = "商品描述长度不能超过 500")
    private String itemDescription;

    /** 商品分类 */
    @Size(max = 50, message = "商品分类长度不能超过 50")
    private String itemCategory;

    /** 所需积分 */
    @NotNull(message = "所需积分不能为空")
    @Positive(message = "所需积分必须为正数")
    private Integer pointsRequired;

    /** 库存 */
    @NotNull(message = "库存不能为空")
    @PositiveOrZero(message = "库存不能为负数")
    private Integer stockQuantity;

    /** 已兑换数量 (查询返回) */
    private Integer exchangedQuantity;

    /** 每人限兑 (默认 1) */
    @PositiveOrZero(message = "每人限兑不能为负数")
    private Integer perUserLimit;

    /** 兑换类型: PHYSICAL/VIRTUAL/COUPON/CASH */
    @NotBlank(message = "兑换类型不能为空")
    @Pattern(regexp = "PHYSICAL|VIRTUAL|COUPON|CASH", message = "兑换类型仅支持 PHYSICAL/VIRTUAL/COUPON/CASH")
    private String exchangeType;

    /** 兑换价值 (如优惠券面额) */
    private Double exchangeValue;

    /** 兑换后有效天数 */
    private Integer validityDays;

    /** 状态: ACTIVE/INACTIVE/SOLD_OUT (创建时可选, 默认 ACTIVE) */
    @Pattern(regexp = "ACTIVE|INACTIVE|SOLD_OUT", message = "状态仅支持 ACTIVE/INACTIVE/SOLD_OUT")
    private String status;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
