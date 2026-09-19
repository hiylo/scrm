/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsExchangeRecordEntity.java
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * SCRM 积分兑换记录实体。
 * <p>
 * 记录每次积分兑换: 关联兑换商品 {@link #exchangeId}、消耗积分 {@link #pointsCost}、兑换数量
 * {@link #quantity}、兑换码 {@link #exchangeCode} (唯一)。状态 {@link #status}:
 * PENDING 待发货 / SHIPPED 已发货 / COMPLETED 已完成 / CANCELLED 已取消。
 * </p>
 * <p>
 * 实物兑换需填写 {@link #shippingAddress} 收货地址, 发货后填写 {@link #shippingNo} 物流单号。
 * 取消兑换时由 Service 返还积分并记录 {@link #cancelledAt}。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_points_exchange_record", schema = "scrm", indexes = {
        @Index(name = "idx_points_ex_record_exchange", columnList = "exchange_id"),
        @Index(name = "idx_points_ex_record_customer", columnList = "customer_id"),
        @Index(name = "idx_points_ex_record_status", columnList = "status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_points_ex_record_code", columnNames = {"exchange_code"})
})
@Data
public class ScrmPointsExchangeRecordEntity {

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
     * 持久化前回调: 自动填充创建/更新时间、版本号初值与兑换时间
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (version == null) {
            version = 0L;
        }
        if (exchangedAt == null) {
            exchangedAt = now;
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

    /** 关联兑换商品 ID */
    @Column(name = "exchange_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long exchangeId;

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 冗余客户名称 */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 消耗积分 */
    @Column(name = "points_cost", nullable = false)
    private Integer pointsCost;

    /** 兑换数量 (默认 1) */
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** 兑换码 (唯一) */
    @Column(name = "exchange_code", nullable = false, length = 100)
    private String exchangeCode;

    /** 状态: PENDING/SHIPPED/COMPLETED/CANCELLED (默认 PENDING) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 收货地址 (实物兑换, 可空) */
    @Column(name = "shipping_address", length = 500)
    private String shippingAddress;

    /** 物流单号 (可空) */
    @Column(name = "shipping_no", length = 200)
    private String shippingNo;

    /** 兑换时间 */
    @Column(name = "exchanged_at", nullable = false)
    private LocalDateTime exchangedAt;

    /** 完成时间 (可空) */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 取消时间 (可空) */
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /** 备注 (可空) */
    @Column(name = "notes", length = 500)
    private String notes;
}
