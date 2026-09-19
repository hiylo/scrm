/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReferralEntity.java
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

import java.time.LocalDateTime;

/**
 * SCRM 客户推荐关系实体。
 * <p>
 * 承载一次推荐的全生命周期: 推荐活动、推荐码、推荐人与被推荐人信息、推荐渠道与链接、
 * 状态流转 (PENDING → SIGNED_UP → QUALIFIED → REWARDED, 可 EXPIRED / CANCELLED)、
 * 注册/达标/奖励时间戳、推荐人与被推荐人奖励状态与详情、被推荐人消费金额、过期与备注。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_referral", schema = "scrm", indexes = {
        @Index(name = "idx_referral_program", columnList = "program_id"),
        @Index(name = "idx_referral_referrer", columnList = "referrer_customer_id"),
        @Index(name = "idx_referral_referee", columnList = "referee_customer_id"),
        @Index(name = "idx_referral_status", columnList = "status"),
        @Index(name = "idx_referral_channel", columnList = "referral_channel"),
        @Index(name = "idx_referral_created", columnList = "create_time")
})
@Data
public class ScrmReferralEntity {

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

    /** 推荐活动 ID */
    @Column(name = "program_id", nullable = false)
    private Long programId;

    /** 推荐码 (唯一) */
    @Column(name = "referral_code", nullable = false, length = 100, unique = true)
    private String referralCode;

    /** 推荐人客户 ID */
    @Column(name = "referrer_customer_id", nullable = false)
    private Long referrerCustomerId;

    /** 推荐人名称 (可空) */
    @Column(name = "referrer_name", length = 200)
    private String referrerName;

    /** 被推荐人客户 ID (可空, 注册前未关联) */
    @Column(name = "referee_customer_id")
    private Long refereeCustomerId;

    /** 被推荐人名称 (可空) */
    @Column(name = "referee_name", length = 200)
    private String refereeName;

    /** 被推荐人联系方式 (可空) */
    @Column(name = "referee_contact", length = 200)
    private String refereeContact;

    /** 推荐渠道: LINK / QR_CODE / CODE / EMAIL / SMS / WECHAT (可空) */
    @Column(name = "referral_channel", length = 30)
    private String referralChannel;

    /** 推荐链接 (可空) */
    @Column(name = "referral_link", length = 500)
    private String referralLink;

    /** 状态: PENDING / SIGNED_UP / QUALIFIED / REWARDED / EXPIRED / CANCELLED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 注册时间 (可空) */
    @Column(name = "signed_up_at")
    private LocalDateTime signedUpAt;

    /** 达标时间 (可空) */
    @Column(name = "qualified_at")
    private LocalDateTime qualifiedAt;

    /** 奖励发放时间 (可空) */
    @Column(name = "rewarded_at")
    private LocalDateTime rewardedAt;

    /** 推荐人奖励状态: PENDING / ISSUED / REDEEMED / EXPIRED */
    @Column(name = "referrer_reward_status", nullable = false, length = 20)
    private String referrerRewardStatus;

    /** 被推荐人奖励状态: PENDING / ISSUED / REDEEMED / EXPIRED */
    @Column(name = "referee_reward_status", nullable = false, length = 20)
    private String refereeRewardStatus;

    /** 推荐人奖励详情 (可空) */
    @Column(name = "referrer_reward_details", length = 500)
    private String referrerRewardDetails;

    /** 被推荐人奖励详情 (可空) */
    @Column(name = "referee_reward_details", length = 500)
    private String refereeRewardDetails;

    /** 被推荐人消费金额 (默认 0) */
    @Column(name = "purchase_amount")
    private Double purchaseAmount;

    /** 备注 (可空) */
    @Column(name = "notes", length = 500)
    private String notes;

    /** 过期时间 (可空) */
    @Column(name = "expired_at")
    private LocalDateTime expiredAt;
}
