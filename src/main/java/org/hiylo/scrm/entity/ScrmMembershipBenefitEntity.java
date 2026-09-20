/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMembershipBenefitEntity.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 会员权益实体。
 * <p>
 * 承载会员权益定义: 权益名称与编码、权益类型 (DISCOUNT/FREE_SHIPPING/POINTS_MULTIPLIER 等 12 种)、
 * 适用等级 (null=所有等级)、权益值与值类型、适用商品/类目/渠道、多维使用限制 (每人/每日/每月/总量)、
 * 使用统计 (当前/会员使用次数)、生效周期、状态 (ACTIVE/INACTIVE/EXPIRED)、兑换说明与使用条款、
 * 图标与展示顺序、可见开关、累计兑换价值与节省金额。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_membership_benefit", schema = "scrm", indexes = {
        @Index(name = "idx_membership_benefit_code", columnList = "benefit_code"),
        @Index(name = "idx_membership_benefit_type", columnList = "benefit_type"),
        @Index(name = "idx_membership_benefit_tier", columnList = "tier_id"),
        @Index(name = "idx_membership_benefit_status", columnList = "status"),
        @Index(name = "idx_membership_benefit_display", columnList = "display_order"),
        @Index(name = "idx_membership_benefit_created", columnList = "create_time")
})
@Data
public class ScrmMembershipBenefitEntity {

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

    /** 权益名称 */
    @Column(name = "benefit_name", nullable = false, length = 200)
    private String benefitName;

    /** 权益编码 (唯一) */
    @Column(name = "benefit_code", nullable = false, length = 50, unique = true)
    private String benefitCode;

/** 权益类型: DISCOUNT / FREE_SHIPPING / POINTS_MULTIPLIER / EXCLUSIVE_PRODUCT / PRIORITY_SUPPORT / BIRTHDAY_BONUS /
         * FREE_RETURN / COUPON / GIFT / EXPERIENCE / SERVICE / CUSTOM */
    @Column(name = "benefit_type", nullable = false, length = 30)
    private String benefitType;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 适用等级 ID (可空, null=所有等级) */
    @Column(name = "tier_id")
    private Long tierId;

    /** 适用等级名称 (可空) */
    @Column(name = "tier_name", length = 200)
    private String tierName;

    /** 权益值 (如折扣率, 默认 0) */
    @Column(name = "value")
    private Double value;

    /** 值类型 (可空): PERCENTAGE / AMOUNT / COUNT / DAYS */
    @Column(name = "value_type", length = 20)
    private String valueType;

    /** 适用商品 (可空) */
    @Column(name = "applicable_products", length = 500)
    private String applicableProducts;

    /** 适用类目 (可空) */
    @Column(name = "applicable_categories", length = 500)
    private String applicableCategories;

    /** 适用渠道 (可空) */
    @Column(name = "applicable_channels", length = 500)
    private String applicableChannels;

    /** 每人限制 (0=无限, 默认 0) */
    @Column(name = "usage_limit_per_member")
    private Integer usageLimitPerMember;

    /** 每日限制 (默认 0) */
    @Column(name = "usage_limit_per_day")
    private Integer usageLimitPerDay;

    /** 每月限制 (默认 0) */
    @Column(name = "usage_limit_per_month")
    private Integer usageLimitPerMonth;

    /** 总限制 (0=无限, 默认 0) */
    @Column(name = "usage_limit_total")
    private Integer usageLimitTotal;

    /** 当前使用次数 (默认 0) */
    @Column(name = "current_usage_count")
    private Integer currentUsageCount;

    /** 会员使用次数 (默认 0) */
    @Column(name = "member_usage_count")
    private Integer memberUsageCount;

    /** 生效开始日期 (可空) */
    @Column(name = "start_date")
    private LocalDate startDate;

    /** 生效结束日期 (可空) */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** 状态: ACTIVE / INACTIVE / EXPIRED (默认 ACTIVE) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 兑换说明 (可空) */
    @Column(name = "redemption_instructions", length = 1000)
    private String redemptionInstructions;

    /** 使用条款 (可空) */
    @Column(name = "terms", length = 1000)
    private String terms;

    /** 图标 (可空) */
    @Column(name = "icon", length = 200)
    private String icon;

    /** 展示顺序 (默认 0) */
    @Column(name = "display_order")
    private Integer displayOrder;

    /** 是否可见 (默认 TRUE) */
    @Column(name = "is_visible", nullable = false)
    private Boolean isVisible;

    /** 总兑换价值 (默认 0) */
    @Column(name = "total_redeemed_value")
    private Double totalRedeemedValue;

    /** 总节省金额 (默认 0) */
    @Column(name = "total_saved_amount")
    private Double totalSavedAmount;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
