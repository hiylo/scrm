/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReferralRewardEntity.java
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
 * SCRM 客户推荐奖励实体。
 * <p>
 * 承载一次推荐奖励的发放记录: 关联推荐与活动、接收者类型 (推荐人/被推荐人) 与客户信息、
 * 奖励类型/面值/JSON 配置、状态流转 (PENDING → ISSUED → REDEEMED, 可 EXPIRED / CANCELLED)、
 * 发放/兑换/过期时间戳、优惠券码、积分账户、交易 ID 与备注。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_referral_reward", schema = "scrm", indexes = {
        @Index(name = "idx_referral_reward_referral", columnList = "referral_id"),
        @Index(name = "idx_referral_reward_program", columnList = "program_id"),
        @Index(name = "idx_referral_reward_recipient", columnList = "recipient_customer_id"),
        @Index(name = "idx_referral_reward_type", columnList = "reward_type"),
        @Index(name = "idx_referral_reward_status", columnList = "status"),
        @Index(name = "idx_referral_reward_created", columnList = "create_time")
})
@Data
public class ScrmReferralRewardEntity {

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

    /** 推荐记录 ID */
    @Column(name = "referral_id", nullable = false)
    private Long referralId;

    /** 推荐活动 ID */
    @Column(name = "program_id", nullable = false)
    private Long programId;

    /** 接收者类型: REFERRER / REFEREE */
    @Column(name = "recipient_type", nullable = false, length = 20)
    private String recipientType;

    /** 接收者客户 ID */
    @Column(name = "recipient_customer_id", nullable = false)
    private Long recipientCustomerId;

    /** 接收者名称 (可空) */
    @Column(name = "recipient_name", length = 200)
    private String recipientName;

    /** 奖励类型: POINTS / COUPON / CASH / DISCOUNT / GIFT / MEMBERSHIP */
    @Column(name = "reward_type", nullable = false, length = 30)
    private String rewardType;

    /** 奖励值 (面值/积分数/折扣率) */
    @Column(name = "reward_value", nullable = false)
    private Double rewardValue;

    /** 奖励配置 (可空, JSON 奖励配置) */
    @Column(name = "reward_config", columnDefinition = "TEXT")
    private String rewardConfig;

    /** 状态: PENDING / ISSUED / REDEEMED / EXPIRED / CANCELLED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 发放时间 (可空) */
    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    /** 兑换时间 (可空) */
    @Column(name = "redeemed_at")
    private LocalDateTime redeemedAt;

    /** 过期时间 (可空) */
    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    /** 优惠券码 (可空) */
    @Column(name = "coupon_code", length = 100)
    private String couponCode;

    /** 积分账户 (可空) */
    @Column(name = "points_account")
    private Long pointsAccount;

    /** 交易 ID (可空) */
    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    /** 备注 (可空) */
    @Column(name = "notes", length = 500)
    private String notes;
}
