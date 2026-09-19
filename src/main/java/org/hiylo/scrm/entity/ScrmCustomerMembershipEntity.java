/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerMembershipEntity.java
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
 * SCRM 客户会员实体。
 * <p>
 * 承载客户会员档案: 客户与等级关联 (含等级快照名称/序号/编码)、会员卡 (卡号唯一/卡类型)、
 * 会员状态 (ACTIVE/FROZEN/EXPIRED/CANCELLED/PENDING)、加入与等级日期、等级到期日、累计与周期
 * 消费/订单/积分、下一等级进度 (距下一等级消费/下一等级信息/升级进度)、降级风险与积分过期、
 * 权益使用统计、活动/升降级日期、JSON 升降级历史、推荐码与推荐人。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_customer_membership", schema = "scrm", indexes = {
        @Index(name = "idx_customer_membership_customer", columnList = "customer_id"),
        @Index(name = "idx_customer_membership_card", columnList = "member_card_no"),
        @Index(name = "idx_customer_membership_tier", columnList = "tier_id"),
        @Index(name = "idx_customer_membership_status", columnList = "membership_status"),
        @Index(name = "idx_customer_membership_level", columnList = "tier_level"),
        @Index(name = "idx_customer_membership_expiry", columnList = "tier_expiry_date"),
        @Index(name = "idx_customer_membership_created", columnList = "create_time")
})
@Data
public class ScrmCustomerMembershipEntity {

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

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** 客户名称 (可空) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 等级 ID */
    @Column(name = "tier_id", nullable = false)
    private Long tierId;

    /** 等级名称快照 (可空) */
    @Column(name = "tier_name", length = 200)
    private String tierName;

    /** 当前等级序号 */
    @Column(name = "tier_level", nullable = false)
    private Integer tierLevel;

    /** 等级编码快照 (可空) */
    @Column(name = "tier_code", length = 50)
    private String tierCode;

    /** 会员卡号 (唯一) */
    @Column(name = "member_card_no", nullable = false, length = 100, unique = true)
    private String memberCardNo;

    /** 会员卡类型: STANDARD / VIP / BLACK_GOLD / DIAMOND / CUSTOM (默认 STANDARD) */
    @Column(name = "member_card_type", nullable = false, length = 30)
    private String memberCardType;

    /** 会员状态: ACTIVE / FROZEN / EXPIRED / CANCELLED / PENDING (默认 ACTIVE) */
    @Column(name = "membership_status", nullable = false, length = 20)
    private String membershipStatus;

    /** 加入日期 */
    @Column(name = "join_date", nullable = false)
    private LocalDate joinDate;

    /** 当前等级日期 */
    @Column(name = "current_tier_date", nullable = false)
    private LocalDate currentTierDate;

    /** 等级到期日 (可空) */
    @Column(name = "tier_expiry_date")
    private LocalDate tierExpiryDate;

    /** 累计消费 (默认 0) */
    @Column(name = "total_spend")
    private Double totalSpend;

    /** 累计订单 (默认 0) */
    @Column(name = "total_orders")
    private Integer totalOrders;

    /** 累计积分 (默认 0) */
    @Column(name = "total_points")
    private Integer totalPoints;

    /** 可用积分 (默认 0) */
    @Column(name = "available_points")
    private Integer availablePoints;

    /** 周期内消费 (默认 0) */
    @Column(name = "spend_in_period")
    private Double spendInPeriod;

    /** 周期内订单 (默认 0) */
    @Column(name = "orders_in_period")
    private Integer ordersInPeriod;

    /** 周期开始 (可空) */
    @Column(name = "period_start_date")
    private LocalDate periodStartDate;

    /** 周期结束 (可空) */
    @Column(name = "period_end_date")
    private LocalDate periodEndDate;

    /** 距下一等级还需消费 (默认 0) */
    @Column(name = "next_tier_spend_needed")
    private Double nextTierSpendNeeded;

    /** 下一等级 ID (可空) */
    @Column(name = "next_tier_id")
    private Long nextTierId;

    /** 下一等级名称 (可空) */
    @Column(name = "next_tier_name", length = 200)
    private String nextTierName;

    /** 升级进度 0-1 (默认 0) */
    @Column(name = "upgrade_progress")
    private Double upgradeProgress;

    /** 降级风险 (默认 FALSE) */
    @Column(name = "downgrade_risk", nullable = false)
    private Boolean downgradeRisk;

    /** 即将过期积分 (默认 0) */
    @Column(name = "points_to_expire")
    private Integer pointsToExpire;

    /** 积分过期日 (可空) */
    @Column(name = "points_expiry_date")
    private LocalDate pointsExpiryDate;

    /** 权益使用次数 (默认 0) */
    @Column(name = "benefits_used_count")
    private Integer benefitsUsedCount;

    /** 权益节省金额 (默认 0) */
    @Column(name = "benefits_saved_amount")
    private Double benefitsSavedAmount;

    /** 最后活动日 (可空) */
    @Column(name = "last_activity_date")
    private LocalDate lastActivityDate;

    /** 最后升级日 (可空) */
    @Column(name = "last_upgrade_date")
    private LocalDate lastUpgradeDate;

    /** 最后降级日 (可空) */
    @Column(name = "last_downgrade_date")
    private LocalDate lastDowngradeDate;

    /** JSON 升降级历史 (可空): [{date,fromTier,toTier,reason,type}] */
    @Column(name = "upgrade_history", columnDefinition = "TEXT")
    private String upgradeHistory;

    /** 会员推荐码 (可空) */
    @Column(name = "referral_code", length = 100)
    private String referralCode;

    /** 推荐人 (可空) */
    @Column(name = "referred_by", length = 100)
    private String referredBy;

    /** 备注 (可空) */
    @Column(name = "notes", length = 500)
    private String notes;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
