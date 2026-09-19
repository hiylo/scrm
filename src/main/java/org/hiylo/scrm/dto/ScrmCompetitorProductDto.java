/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCompetitorProductDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 竞品产品 DTO。
 * <p>
 * 用于竞品产品增删改查接口入参与返回。价格统计字段 (lowestPrice / highestPrice / avgPrice /
 * priceChangeCount 等) 由 {@code updatePrice} 自动维护, 创建时缺省取当前价。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCompetitorProductDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 竞品 ID */
    @NotNull(message = "竞品 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long competitorId;

    /** 竞品名称 (可空, 缺省时由 Service 从竞品实体填充) */
    @Size(max = 200, message = "竞品名称长度不能超过 200")
    private String competitorName;

    /** 产品名称 */
    @NotBlank(message = "产品名称不能为空")
    @Size(max = 200, message = "产品名称长度不能超过 200")
    private String productName;

    /** 产品编码 (可空) */
    @Size(max = 50, message = "产品编码长度不能超过 50")
    private String productCode;

    /** 产品分类 (可空) */
    @Size(max = 100, message = "产品分类长度不能超过 100")
    private String productCategory;

    /** 描述 (可空) */
    @Size(max = 1000, message = "描述长度不能超过 1000")
    private String description;

    /** 当前价格 */
    private Double currentPrice;

    /** 原价 */
    private Double originalPrice;

    /** 折扣率 */
    private Double discountRate;

    /** 币种 (默认 CNY) */
    @Size(max = 10, message = "币种长度不能超过 10")
    private String currency;

    /** 计价单位 (可空) */
    @Size(max = 50, message = "计价单位长度不能超过 50")
    private String priceUnit;

    /** 产品链接 (可空) */
    @Size(max = 500, message = "产品链接长度不能超过 500")
    private String productUrl;

    /** 图片 URL (可空) */
    @Size(max = 500, message = "图片 URL 长度不能超过 500")
    private String imageUrl;

    /** 产品特点 (可空) */
    @Size(max = 1000, message = "产品特点长度不能超过 1000")
    private String features;

    /** 产品规格 (可空) */
    @Size(max = 1000, message = "产品规格长度不能超过 1000")
    private String specifications;

    /** 目标客群 (可空) */
    @Size(max = 200, message = "目标客群长度不能超过 200")
    private String targetSegment;

    /** 产品定位 (可空) */
    @Size(max = 200, message = "产品定位长度不能超过 200")
    private String positioning;

    /** 上市日期 (可空) */
    private LocalDate launchDate;

    /** 对应我方产品 ID (可空) */
    @Size(max = 100, message = "我方产品 ID 长度不能超过 100")
    private String ourProductId;

    /** 对应我方产品名称 (可空) */
    @Size(max = 200, message = "我方产品名称长度不能超过 200")
    private String ourProductName;

    /** 我方价格 */
    private Double ourPrice;

    /** 价格对比: HIGHER / LOWER / EQUAL / SIMILAR (可空) */
    @Pattern(regexp = "HIGHER|LOWER|EQUAL|SIMILAR",
            message = "价格对比仅支持 HIGHER/LOWER/EQUAL/SIMILAR")
    private String priceComparison;

    /** 优势评分 (-100 到 100) */
    private Integer advantageScore;

    /** 是否启用监测 (默认 TRUE) */
    private Boolean monitoringEnabled;

    /** 状态: ACTIVE / INACTIVE / DISCONTINUED */
    @Pattern(regexp = "ACTIVE|INACTIVE|DISCONTINUED",
            message = "状态仅支持 ACTIVE/INACTIVE/DISCONTINUED")
    private String status;

    /** 最近监测时间 (可空) */
    private LocalDateTime lastMonitoredAt;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String notes;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 最近价格变化日期 (查询返回) */
    private LocalDate lastPriceChangeDate;

    /** 最近价格变化幅度 (%) (查询返回) */
    private Double lastPriceChangePercent;

    /** 价格变化次数 (查询返回) */
    private Integer priceChangeCount;

    /** 历史最低价 (查询返回) */
    private Double lowestPrice;

    /** 历史最高价 (查询返回) */
    private Double highestPrice;

    /** 平均价 (查询返回) */
    private Double avgPrice;

    /** 价格历史 JSON (查询返回) */
    private String priceHistory;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
