/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmProductDto.java
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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 商品 DTO。
 * <p>
 * 用于商品创建、更新与查询返回。创建时 {@code productCode} / {@code productName} / {@code price}
 * 必填, 其余字段可选; 更新时字段非空才覆盖。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmProductDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 商品编码 (唯一) */
    @NotBlank(message = "商品编码不能为空")
    @Size(max = 100, message = "商品编码长度不能超过 100")
    private String productCode;

    /** 商品名称 */
    @NotBlank(message = "商品名称不能为空")
    @Size(max = 200, message = "商品名称长度不能超过 200")
    private String productName;

    /** 分类 (可空) */
    @Size(max = 100, message = "分类长度不能超过 100")
    private String category;

    /** 品牌 (可空) */
    @Size(max = 100, message = "品牌长度不能超过 100")
    private String brand;

    /** 规格 (可空) */
    @Size(max = 500, message = "规格长度不能超过 500")
    private String spec;

    /** 商品描述 (可空) */
    private String description;

    /** 售价 (默认 0) */
    @NotNull(message = "售价不能为空")
    @PositiveOrZero(message = "售价不能为负数")
    private Double price;

    /** 原价 (可空) */
    @PositiveOrZero(message = "原价不能为负数")
    private Double originalPrice;

    /** 成本 (可空) */
    @PositiveOrZero(message = "成本不能为负数")
    private Double cost;

    /** 币种 (默认 CNY) */
    @Size(max = 10, message = "币种长度不能超过 10")
    private String currency;

    /** 库存 (默认 0) */
    @PositiveOrZero(message = "库存不能为负数")
    private Integer stock;

    /** 单位 (可空) */
    @Size(max = 50, message = "单位长度不能超过 50")
    private String unit;

    /** 主图 URL (可空) */
    @Size(max = 500, message = "主图 URL 长度不能超过 500")
    private String imageUrl;

    /** 图片列表 JSON (可空) */
    @Size(max = 1000, message = "图片列表长度不能超过 1000")
    private String images;

    /** 标签 (可空) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 状态: ACTIVE / INACTIVE / DISCONTINUED (默认 ACTIVE) */
    @Pattern(regexp = "ACTIVE|INACTIVE|DISCONTINUED", message = "状态仅支持 ACTIVE/INACTIVE/DISCONTINUED")
    private String status;

    /** SKU (可空) */
    @Size(max = 100, message = "SKU 长度不能超过 100")
    private String sku;

    /** 条码 (可空) */
    @Size(max = 100, message = "条码长度不能超过 100")
    private String barcode;

    /** 重量 kg (可空) */
    @PositiveOrZero(message = "重量不能为负数")
    private Double weight;

    /** 销量 (默认 0) */
    @PositiveOrZero(message = "销量不能为负数")
    private Integer salesCount;

    /** 浏览量 (默认 0) */
    @PositiveOrZero(message = "浏览量不能为负数")
    private Integer viewCount;

    /** 评分 (默认 0) */
    @Min(value = 0, message = "评分不能小于 0")
    private Double ratingScore;

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
