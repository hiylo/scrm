/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCouponTemplateEntity.java
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 优惠券模板实体。
 * <p>
 * 优惠券模板封装一类营销优惠券的发行规则, 包含优惠券类型 (折扣/满减/兑换/赠品/代金券)、
 * 面值/折扣率、使用门槛、有效期类型 (固定日期/领取后 N 天)、发行量与限领规则。
 * 基于模板可批量发券并统计发放/领取/使用/过期数据。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_coupon_template", schema = "scrm", indexes = {
        @Index(name = "idx_coupon_template_type", columnList = "coupon_type"),
        @Index(name = "idx_coupon_template_status", columnList = "status")
})
@Data
public class ScrmCouponTemplateEntity {

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

    /** 模板名称 */
    @Column(name = "template_name", nullable = false, length = 200)
    private String templateName;

    /** 优惠券类型: DISCOUNT / FIXED_AMOUNT / EXCHANGE / GIFT / CASH_VOUCHER (折扣/满减/兑换/赠品/代金券) */
    @Column(name = "coupon_type", nullable = false, length = 20)
    private String couponType;

    /** 面值/折扣率 (DISCOUNT 为折扣率 0~1, 其余为面值金额) */
    @Column(name = "face_value", nullable = false)
    private Double faceValue;

    /** 使用门槛满 X 元 (订单金额需达到此值才可使用, 0 表示无门槛) */
    @Column(name = "threshold_amount", nullable = false)
    private Double thresholdAmount;

    /** 折扣上限 (仅 DISCOUNT 类型生效, 可空) */
    @Column(name = "discount_limit")
    private Double discountLimit;

    /** 有效期类型: FIXED (固定日期) / RELATIVE (领取后 N 天) */
    @Column(name = "valid_type", nullable = false, length = 20)
    private String validType;

    /** 固定开始日期 (validType=FIXED 时生效) */
    @Column(name = "valid_start")
    private LocalDate validStart;

    /** 固定结束日期 (validType=FIXED 时生效) */
    @Column(name = "valid_end")
    private LocalDate validEnd;

    /** 领取后有效天数 (validType=RELATIVE 时生效) */
    @Column(name = "valid_days")
    private Integer validDays;

    /** 总发行量 */
    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    /** 已发放数量 */
    @Column(name = "issued_quantity", nullable = false)
    private Integer issuedQuantity;

    /** 已使用数量 */
    @Column(name = "used_quantity", nullable = false)
    private Integer usedQuantity;

    /** 已领取数量 */
    @Column(name = "claimed_quantity", nullable = false)
    private Integer claimedQuantity;

    /** 每人限领 */
    @Column(name = "per_user_limit", nullable = false)
    private Integer perUserLimit;

    /** 适用商品 ID 逗号分隔 (可空) */
    @Column(name = "applicable_products", length = 1000)
    private String applicableProducts;

    /** 适用场景 (可空) */
    @Column(name = "applicable_scenes", length = 500)
    private String applicableScenes;

    /** 使用说明 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 使用规则 (可空) */
    @Column(name = "rules", length = 2000)
    private String rules;

    /** 状态: ACTIVE / INACTIVE / EXPIRED / SOLD_OUT */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
