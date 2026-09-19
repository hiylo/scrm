/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCouponEntity.java
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
 * SCRM 优惠券实例实体。
 * <p>
 * 优惠券实例由模板批量发行, 持有唯一券码与归属客户。券实例记录领取来源、领取/过期时间、
 * 状态流转 (UNUSED / USED / EXPIRED / RETURNED) 以及核销时的订单号与实际抵扣金额。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_coupon", schema = "scrm", indexes = {
        @Index(name = "idx_coupon_template_ref", columnList = "template_id"),
        @Index(name = "idx_coupon_customer", columnList = "customer_id"),
        @Index(name = "idx_coupon_status", columnList = "status"),
        @Index(name = "idx_coupon_expires", columnList = "status,expires_at")
})
@Data
public class ScrmCouponEntity {

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

    /** 关联模板 ID */
    @Column(name = "template_id", nullable = false)
    private Long templateId;

    /** 优惠券码 (唯一) */
    @Column(name = "coupon_code", nullable = false, length = 100)
    private String couponCode;

    /** 持有客户 ID (可空, 未领取时为 null) */
    @Column(name = "customer_id")
    private Long customerId;

    /** 持有客户名称 (可空) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 领取来源: MANUAL / MASS_SEND / CHANNEL_CODE / AUTO / ACTIVITY */
    @Column(name = "claim_source", nullable = false, length = 30)
    private String claimSource;

    /** 领取时间 (可空) */
    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    /** 过期时间 */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 状态: UNUSED / USED / EXPIRED / RETURNED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 使用时间 (可空) */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /** 使用订单号 (可空) */
    @Column(name = "used_order", length = 100)
    private String usedOrder;

    /** 实际抵扣金额 (可空) */
    @Column(name = "used_amount")
    private Double usedAmount;

    /** 发放人 (可空) */
    @Column(name = "issued_by", length = 100)
    private String issuedBy;

    /** 备注 (可空) */
    @Column(name = "notes", length = 500)
    private String notes;
}
