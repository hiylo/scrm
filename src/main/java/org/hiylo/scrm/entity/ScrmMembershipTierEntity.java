/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMembershipTierEntity.java
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

import java.time.LocalDateTime;

/**
 * SCRM 会员等级实体。
 * <p>
 * 承载会员等级体系配置: 等级名称与编码、等级序号 (1-10)、升级/降级阈值与有效期、升级规则类型
 * (SPEND/POINTS/ORDER_COUNT/MANUAL)、权益摘要与积分倍数/折扣率/免费配送/优先支持等内置权益开关、
 * 各类赠送积分 (注册/每月/年度/升级)、最大兑换比例与免费退货天数、JSON 自定义权益、
 * 适用商品与渠道、可见与启用开关、等级统计 (会员数/总消费/平均消费) 与排序。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_membership_tier", schema = "scrm", indexes = {
        @Index(name = "idx_membership_tier_code", columnList = "tier_code"),
        @Index(name = "idx_membership_tier_level", columnList = "tier_level"),
        @Index(name = "idx_membership_tier_enabled", columnList = "enabled"),
        @Index(name = "idx_membership_tier_sort", columnList = "sort_order"),
        @Index(name = "idx_membership_tier_created", columnList = "create_time")
})
@Data
public class ScrmMembershipTierEntity {

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

    /** 等级名称 */
    @Column(name = "tier_name", nullable = false, length = 200)
    private String tierName;

    /** 等级编码 (唯一) */
    @Column(name = "tier_code", nullable = false, length = 50, unique = true)
    private String tierCode;

    /** 等级序号 (1-10, 数值越大等级越高) */
    @Column(name = "tier_level", nullable = false)
    private Integer tierLevel;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 等级颜色 (可空) */
    @Column(name = "tier_color", length = 20)
    private String tierColor;

    /** 等级图标 (可空) */
    @Column(name = "tier_icon", length = 200)
    private String tierIcon;

    /** 升级阈值 (累计消费/积分, 默认 0) */
    @Column(name = "upgrade_threshold", nullable = false)
    private Double upgradeThreshold;

    /** 降级阈值 (默认 0) */
    @Column(name = "downgrade_threshold")
    private Double downgradeThreshold;

    /** 等级有效期月 (默认 12) */
    @Column(name = "validity_period_months")
    private Integer validityPeriodMonths;

    /** 升级规则类型: SPEND / POINTS / ORDER_COUNT / MANUAL (默认 SPEND) */
    @Column(name = "upgrade_rule_type", nullable = false, length = 30)
    private String upgradeRuleType;

    /** 权益摘要 (可空) */
    @Column(name = "benefits_summary", length = 1000)
    private String benefitsSummary;

    /** 积分倍数 (默认 1.0) */
    @Column(name = "point_multiplier")
    private Double pointMultiplier;

    /** 折扣率 0-1 (默认 1.0) */
    @Column(name = "discount_rate")
    private Double discountRate;

    /** 免费配送 (默认 FALSE) */
    @Column(name = "free_shipping", nullable = false)
    private Boolean freeShipping;

    /** 优先支持 (默认 FALSE) */
    @Column(name = "priority_support", nullable = false)
    private Boolean prioritySupport;

    /** 专属商品 (默认 FALSE) */
    @Column(name = "exclusive_products", nullable = false)
    private Boolean exclusiveProducts;

    /** 生日礼金 (默认 0) */
    @Column(name = "birthday_bonus")
    private Double birthdayBonus;

    /** 注册赠送积分 (默认 0) */
    @Column(name = "signup_bonus_points")
    private Integer signupBonusPoints;

    /** 每月赠送积分 (默认 0) */
    @Column(name = "monthly_bonus_points")
    private Integer monthlyBonusPoints;

    /** 年度赠送积分 (默认 0) */
    @Column(name = "annual_bonus_points")
    private Integer annualBonusPoints;

    /** 升级赠送积分 (默认 0) */
    @Column(name = "tier_up_bonus_points")
    private Integer tierUpBonusPoints;

    /** 最大兑换比例 (默认 0.5) */
    @Column(name = "max_redeem_rate")
    private Double maxRedeemRate;

    /** 免费退货天数 (默认 7) */
    @Column(name = "free_return_days")
    private Integer freeReturnDays;

    /** JSON 自定义权益 (可空): [{benefitName,benefitType,value,description}] */
    @Column(name = "custom_benefits", columnDefinition = "TEXT")
    private String customBenefits;

    /** 适用商品 (可空) */
    @Column(name = "applicable_products", length = 500)
    private String applicableProducts;

    /** 适用渠道 (可空) */
    @Column(name = "applicable_channels", length = 500)
    private String applicableChannels;

    /** 是否启用 (默认 TRUE) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 是否可见 (默认 TRUE) */
    @Column(name = "is_visible", nullable = false)
    private Boolean isVisible;

    /** 该等级会员数 (默认 0) */
    @Column(name = "member_count")
    private Integer memberCount;

    /** 该等级总消费 (默认 0) */
    @Column(name = "total_spend")
    private Double totalSpend;

    /** 平均消费 (默认 0) */
    @Column(name = "avg_spend")
    private Double avgSpend;

    /** 排序序号 (默认 0) */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
