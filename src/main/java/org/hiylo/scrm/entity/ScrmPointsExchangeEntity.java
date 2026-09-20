/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsExchangeEntity.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
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
 * SCRM 积分兑换商品实体。
 * <p>
 * 定义可兑换商品: 所需积分 {@link #pointsRequired}、库存 {@link #stockQuantity}、已兑换
 * {@link #exchangedQuantity}、每人限兑 {@link #perUserLimit}。兑换类型 {@link #exchangeType}:
 * PHYSICAL 实物 / VIRTUAL 虚拟 / COUPON 优惠券 / CASH 现金。{@link #exchangeValue} 记录兑换价值
 * (如优惠券面额), {@link #validityDays} 记录兑换后有效天数。
 * </p>
 * <p>
 * 状态 {@link #status}: ACTIVE 可兑换 / INACTIVE 已下架 / SOLD_OUT 已售罄。库存为 0 时由
 * Service 自动置为 SOLD_OUT。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_points_exchange", schema = "scrm", indexes = {
        @Index(name = "idx_points_exchange_category", columnList = "item_category"),
        @Index(name = "idx_points_exchange_status", columnList = "status"),
        @Index(name = "idx_points_exchange_type", columnList = "exchange_type")
})
@Data
public class ScrmPointsExchangeEntity {

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

    /** 兑换商品名 */
    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    /** 商品图片 URL (可空) */
    @Column(name = "item_image", length = 500)
    private String itemImage;

    /** 商品描述 (可空) */
    @Column(name = "item_description", length = 500)
    private String itemDescription;

    /** 商品分类 (可空) */
    @Column(name = "item_category", length = 50)
    private String itemCategory;

    /** 所需积分 */
    @Column(name = "points_required", nullable = false)
    private Integer pointsRequired;

    /** 库存 */
    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    /** 已兑换数量 */
    @Column(name = "exchanged_quantity")
    private Integer exchangedQuantity;

    /** 每人限兑 (默认 1) */
    @Column(name = "per_user_limit")
    private Integer perUserLimit;

    /** 兑换类型: PHYSICAL/VIRTUAL/COUPON/CASH */
    @Column(name = "exchange_type", nullable = false, length = 20)
    private String exchangeType;

    /** 兑换价值 (可空, 如优惠券面额) */
    @Column(name = "exchange_value")
    private Double exchangeValue;

    /** 兑换后有效天数 (可空) */
    @Column(name = "validity_days")
    private Integer validityDays;

    /** 状态: ACTIVE/INACTIVE/SOLD_OUT (默认 ACTIVE) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
