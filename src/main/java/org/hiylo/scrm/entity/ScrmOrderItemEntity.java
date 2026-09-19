/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOrderItemEntity.java
 * Date : 2026/07/29 21:19:51
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
 * SCRM 订单项实体。
 * <p>
 * 承载订单的商品明细行, 关联订单 ID 与商品 ID (可空, 兼容删除商品后的快照), 记录商品编码、
 * 名称、图片、规格、单价、数量、单项折扣、小计、税率与税额。订单项随订单创建时一并写入,
 * 也可在订单确认前动态增删改。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_order_item", schema = "scrm", indexes = {
        @Index(name = "idx_order_item_order", columnList = "order_id"),
        @Index(name = "idx_order_item_product", columnList = "product_id")
})
@Data
public class ScrmOrderItemEntity {

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

    /** 订单 ID */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** 商品 ID (可空, 兼容商品删除后的快照) */
    @Column(name = "product_id")
    private Long productId;

    /** 商品编码 (可空) */
    @Column(name = "product_code", length = 100)
    private String productCode;

    /** 商品名称 */
    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    /** 商品图片 (可空) */
    @Column(name = "product_image", length = 500)
    private String productImage;

    /** 规格 (可空) */
    @Column(name = "spec", length = 500)
    private String spec;

    /** 单价 (默认 0) */
    @Column(name = "unit_price", nullable = false)
    private Double unitPrice;

    /** 数量 (默认 1) */
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** 单项折扣 (默认 0) */
    @Column(name = "discount_amount")
    private Double discountAmount;

    /** 小计 (默认 0) */
    @Column(name = "subtotal", nullable = false)
    private Double subtotal;

    /** 税率 (默认 0) */
    @Column(name = "tax_rate")
    private Double taxRate;

    /** 税额 (默认 0) */
    @Column(name = "tax_amount")
    private Double taxAmount;

    /** 备注 (可空) */
    @Column(name = "remark", length = 500)
    private String remark;
}
