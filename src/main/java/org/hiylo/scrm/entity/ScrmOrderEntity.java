/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOrderEntity.java
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
 * SCRM 订单实体。
 * <p>
 * 承载 SCRM 内嵌轻量级订单管理的订单主信息, 包含订单编号 (唯一)、客户信息、订单类型
 * (SALE/REFUND/EXCHANGE/PRE_ORDER)、订单状态机 (PENDING/CONFIRMED/PAID/SHIPPED/DELIVERED/
 * COMPLETED/CANCELLED/REFUNDED)、支付状态与方式、金额明细 (总/折扣/运费/税/已付)、优惠券关联、
 * 销售员与渠道、收货信息、物流信息、各状态时间戳, 以及订单项快照 (itemsJson)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_order", schema = "scrm", indexes = {
        @Index(name = "idx_order_no", columnList = "order_no", unique = true),
        @Index(name = "idx_order_customer", columnList = "customer_id"),
        @Index(name = "idx_order_status", columnList = "order_status"),
        @Index(name = "idx_order_payment_status", columnList = "payment_status"),
        @Index(name = "idx_order_type", columnList = "order_type"),
        @Index(name = "idx_order_create_time", columnList = "create_time")
})
@Data
public class ScrmOrderEntity {

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

    /** 订单编号 (唯一) */
    @Column(name = "order_no", nullable = false, length = 50, unique = true)
    private String orderNo;

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** 客户名称 (可空) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 订单类型: SALE / REFUND / EXCHANGE / PRE_ORDER (默认 SALE) */
    @Column(name = "order_type", nullable = false, length = 20)
    private String orderType;

    /** 订单状态: PENDING / CONFIRMED / PAID / SHIPPED / DELIVERED / COMPLETED / CANCELLED / REFUNDED (默认 PENDING) */
    @Column(name = "order_status", nullable = false, length = 20)
    private String orderStatus;

    /** 支付状态: UNPAID / PARTIAL / PAID / REFUNDED (默认 UNPAID) */
    @Column(name = "payment_status", nullable = false, length = 20)
    private String paymentStatus;

    /** 支付方式: WECHAT / ALIPAY / BANK / CARD / COD / OTHER (可空) */
    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    /** 总金额 (默认 0) */
    @Column(name = "total_amount", nullable = false)
    private Double totalAmount;

    /** 折扣金额 (默认 0) */
    @Column(name = "discount_amount")
    private Double discountAmount;

    /** 运费 (默认 0) */
    @Column(name = "shipping_amount")
    private Double shippingAmount;

    /** 税费 (默认 0) */
    @Column(name = "tax_amount")
    private Double taxAmount;

    /** 已付金额 (默认 0) */
    @Column(name = "paid_amount")
    private Double paidAmount;

    /** 币种 (默认 CNY) */
    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    /** 优惠券 ID (可空) */
    @Column(name = "coupon_id")
    private Long couponId;

    /** 优惠券码 (可空) */
    @Column(name = "coupon_code", length = 100)
    private String couponCode;

    /** 销售员 ID (可空) */
    @Column(name = "salesperson_id", length = 100)
    private String salespersonId;

    /** 销售员名称 (可空) */
    @Column(name = "salesperson_name", length = 100)
    private String salespersonName;

    /** 下单渠道 (可空) */
    @Column(name = "channel", length = 50)
    private String channel;

    /** 收货地址 (可空) */
    @Column(name = "shipping_address", length = 500)
    private String shippingAddress;

    /** 收件人 (可空) */
    @Column(name = "shipping_name", length = 100)
    private String shippingName;

    /** 收件人电话 (可空) */
    @Column(name = "shipping_phone", length = 50)
    private String shippingPhone;

    /** 物流单号 (可空) */
    @Column(name = "tracking_no", length = 200)
    private String trackingNo;

    /** 物流公司 (可空) */
    @Column(name = "tracking_company", length = 100)
    private String trackingCompany;

    /** 备注 (可空) */
    @Column(name = "remark", length = 500)
    private String remark;

    /** 支付时间 (可空) */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** 发货时间 (可空) */
    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    /** 送达时间 (可空) */
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /** 完成时间 (可空) */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 取消时间 (可空) */
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /** 订单项快照 JSON (可空) */
    @Column(name = "items_json", columnDefinition = "TEXT")
    private String itemsJson;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
