/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmProductEntity.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * SCRM 商品实体。
 * <p>
 * 承载 SCRM 内嵌轻量级商品管理的商品元数据, 包含商品编码 (唯一)、名称、分类、品牌、规格、
 * 售价/原价/成本、库存、单位、主图与图片列表 (JSON)、标签、状态 (ACTIVE/INACTIVE/DISCONTINUED)、
 * SKU 与条码、重量, 以及销量/浏览量/评分等运营统计字段。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_product", schema = "scrm", indexes = {
        @Index(name = "idx_product_code", columnList = "product_code", unique = true),
        @Index(name = "idx_product_category", columnList = "category"),
        @Index(name = "idx_product_brand", columnList = "brand"),
        @Index(name = "idx_product_status", columnList = "status"),
        @Index(name = "idx_product_sku", columnList = "sku")
})
@Data
public class ScrmProductEntity {

    // ==================== 公共字段 ====================

    /** 主键 ID（Snowflake 雪花算法生成） */
    @Id
    @GeneratedValue(generator = "snowflake")
    @GenericGenerator(name = "snowflake", strategy = "org.hiylo.scrm.config.SnowflakeIdGenerator")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 创建时间 */
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    /** 更新时间 */
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    /** 乐观锁版本号（并发更新保护, 后写入者触发 OptimisticLockException） */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * 持久化前回调: 自动填充创建/更新时间与版本号初值
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (version == null) {
            version = 0L;
        }
    }

    /**
     * 更新前回调: 自动刷新更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    // ==================== 业务字段 ====================

    /** 商品编码 (唯一) */
    @Column(name = "product_code", nullable = false, length = 100, unique = true)
    private String productCode;

    /** 商品名称 */
    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    /** 分类 (可空) */
    @Column(name = "category", length = 100)
    private String category;

    /** 品牌 (可空) */
    @Column(name = "brand", length = 100)
    private String brand;

    /** 规格 (可空) */
    @Column(name = "spec", length = 500)
    private String spec;

    /** 商品描述 (可空) */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** 售价 (默认 0) */
    @Column(name = "price", nullable = false)
    private Double price;

    /** 原价 (可空) */
    @Column(name = "original_price")
    private Double originalPrice;

    /** 成本 (可空) */
    @Column(name = "cost")
    private Double cost;

    /** 币种 (默认 CNY) */
    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    /** 库存 (默认 0) */
    @Column(name = "stock")
    private Integer stock;

    /** 单位 (可空) */
    @Column(name = "unit", length = 50)
    private String unit;

    /** 主图 URL (可空) */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** 图片列表 JSON (可空) */
    @Column(name = "images", length = 1000)
    private String images;

    /** 标签 (可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 状态: ACTIVE / INACTIVE / DISCONTINUED (默认 ACTIVE) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** SKU (可空) */
    @Column(name = "sku", length = 100)
    private String sku;

    /** 条码 (可空) */
    @Column(name = "barcode", length = 100)
    private String barcode;

    /** 重量 kg (可空) */
    @Column(name = "weight")
    private Double weight;

    /** 销量 (默认 0) */
    @Column(name = "sales_count")
    private Integer salesCount;

    /** 浏览量 (默认 0) */
    @Column(name = "view_count")
    private Integer viewCount;

    /** 评分 (默认 0) */
    @Column(name = "rating_score")
    private Double ratingScore;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
