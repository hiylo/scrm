/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCompetitorProductEntity.java
 * Date : 2026/08/05 08:55:12
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 竞品产品实体。
 * <p>
 * 记录竞品单个产品的价格与定位信息: 当前价 / 原价 / 折扣 / 历史价格区间 / 价格变化统计,
 * 以及与我方产品的对比 (ourProductId / ourPrice / priceComparison / advantageScore)。
 * {@link #priceHistory} (TEXT JSON: [{date, price, change}]) 保留完整价格轨迹,
 * 由 {@code ScrmCompetitorService.updatePrice} 在每次调价时追加。
 * </p>
 * <p>
 * 状态: ACTIVE (在售) / INACTIVE (下架) / DISCONTINUED (停产)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_competitor_product", schema = "scrm", indexes = {
        @Index(name = "idx_competitor_product_competitor", columnList = "competitor_id"),
        @Index(name = "idx_competitor_product_category", columnList = "product_category"),
        @Index(name = "idx_competitor_product_status", columnList = "status"),
        @Index(name = "idx_competitor_product_monitoring", columnList = "monitoring_enabled"),
        @Index(name = "idx_competitor_product_last_monitored", columnList = "last_monitored_at")
})
@Data
public class ScrmCompetitorProductEntity {

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

    /** 竞品 ID */
    @Column(name = "competitor_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long competitorId;

    /** 竞品名称 (冗余, 便于列表展示) */
    @Column(name = "competitor_name", length = 200)
    private String competitorName;

    /** 产品名称 */
    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    /** 产品编码 (可空) */
    @Column(name = "product_code", length = 50)
    private String productCode;

    /** 产品分类 (可空) */
    @Column(name = "product_category", length = 100)
    private String productCategory;

    /** 描述 (可空) */
    @Column(name = "description", length = 1000)
    private String description;

    /** 当前价格 */
    @Column(name = "current_price", nullable = false)
    private Double currentPrice;

    /** 原价 */
    @Column(name = "original_price", nullable = false)
    private Double originalPrice;

    /** 折扣率 */
    @Column(name = "discount_rate", nullable = false)
    private Double discountRate;

    /** 币种 (默认 CNY) */
    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    /** 计价单位 (可空) */
    @Column(name = "price_unit", length = 50)
    private String priceUnit;

    /** 产品链接 (可空) */
    @Column(name = "product_url", length = 500)
    private String productUrl;

    /** 图片 URL (可空) */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** 产品特点 (可空) */
    @Column(name = "features", length = 1000)
    private String features;

    /** 产品规格 (可空) */
    @Column(name = "specifications", length = 1000)
    private String specifications;

    /** 目标客群 (可空) */
    @Column(name = "target_segment", length = 200)
    private String targetSegment;

    /** 产品定位 (可空) */
    @Column(name = "positioning", length = 200)
    private String positioning;

    /** 上市日期 (可空) */
    @Column(name = "launch_date")
    private LocalDate launchDate;

    /** 最近价格变化日期 (可空) */
    @Column(name = "last_price_change_date")
    private LocalDate lastPriceChangeDate;

    /** 最近价格变化幅度 (%) */
    @Column(name = "last_price_change_percent", nullable = false)
    private Double lastPriceChangePercent;

    /** 价格变化次数 */
    @Column(name = "price_change_count", nullable = false)
    private Integer priceChangeCount;

    /** 历史最低价 */
    @Column(name = "lowest_price", nullable = false)
    private Double lowestPrice;

    /** 历史最高价 */
    @Column(name = "highest_price", nullable = false)
    private Double highestPrice;

    /** 平均价 */
    @Column(name = "avg_price", nullable = false)
    private Double avgPrice;

    /** 价格历史 JSON: [{date, price, change}] (可空) */
    @Column(name = "price_history", columnDefinition = "TEXT")
    private String priceHistory;

    /** 对应我方产品 ID (可空) */
    @Column(name = "our_product_id", length = 100)
    private String ourProductId;

    /** 对应我方产品名称 (可空) */
    @Column(name = "our_product_name", length = 200)
    private String ourProductName;

    /** 我方价格 */
    @Column(name = "our_price", nullable = false)
    private Double ourPrice;

    /** 价格对比: HIGHER / LOWER / EQUAL / SIMILAR (可空) */
    @Column(name = "price_comparison", length = 20)
    private String priceComparison;

    /** 优势评分 (-100 到 100, 正数代表我方占优) */
    @Column(name = "advantage_score", nullable = false)
    private Integer advantageScore;

    /** 是否启用监测 (默认 TRUE) */
    @Column(name = "monitoring_enabled", nullable = false)
    private Boolean monitoringEnabled;

    /** 状态: ACTIVE / INACTIVE / DISCONTINUED (默认 ACTIVE) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 最近监测时间 (可空) */
    @Column(name = "last_monitored_at")
    private LocalDateTime lastMonitoredAt;

    /** 备注 (可空) */
    @Column(name = "notes", length = 500)
    private String notes;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
