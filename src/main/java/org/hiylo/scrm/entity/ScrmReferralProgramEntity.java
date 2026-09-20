/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReferralProgramEntity.java
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
 * SCRM 客户推荐活动实体。
 * <p>
 * 承载老带新/推荐奖励活动配置: 活动名称与编码、活动类型、推荐人与被推荐人奖励 (类型/面值/JSON 配置)、
 * 奖励触发条件 (注册/首单/消费金额/留存天数)、推荐上限 (单人/总量)、双向奖励开关、活动周期、
 * 活动条款以及活动统计 (总推荐数/成功推荐数/累计奖励价值)。
 * 活动状态流转 ACTIVE → PAUSED / EXPIRED / COMPLETED。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_referral_program", schema = "scrm", indexes = {
        @Index(name = "idx_referral_program_type", columnList = "program_type"),
        @Index(name = "idx_referral_program_status", columnList = "status"),
        @Index(name = "idx_referral_program_dates", columnList = "start_date,end_date"),
        @Index(name = "idx_referral_program_created", columnList = "create_time")
})
@Data
public class ScrmReferralProgramEntity {

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

    /** 活动名称 */
    @Column(name = "program_name", nullable = false, length = 200)
    private String programName;

    /** 活动编码 (唯一) */
    @Column(name = "program_code", nullable = false, length = 50, unique = true)
    private String programCode;

    /** 活动描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 活动类型: REFERRAL / INVITE / SHARE / AMBASSADOR / AFFILIATE */
    @Column(name = "program_type", nullable = false, length = 30)
    private String programType;

    /** 推荐人奖励类型: POINTS / COUPON / CASH / DISCOUNT / GIFT / MEMBERSHIP */
    @Column(name = "referrer_reward_type", nullable = false, length = 30)
    private String referrerRewardType;

    /** 推荐人奖励值 (面值/积分数/折扣率) */
    @Column(name = "referrer_reward_value", nullable = false)
    private Double referrerRewardValue;

    /** 推荐人奖励配置 (可空, JSON 奖励配置) */
    @Column(name = "referrer_reward_config", columnDefinition = "TEXT")
    private String referrerRewardConfig;

    /** 被推荐人奖励类型: POINTS / COUPON / CASH / DISCOUNT / GIFT / MEMBERSHIP */
    @Column(name = "referee_reward_type", nullable = false, length = 30)
    private String refereeRewardType;

    /** 被推荐人奖励值 (面值/积分数/折扣率) */
    @Column(name = "referee_reward_value", nullable = false)
    private Double refereeRewardValue;

    /** 被推荐人奖励配置 (可空, JSON 奖励配置) */
    @Column(name = "referee_reward_config", columnDefinition = "TEXT")
    private String refereeRewardConfig;

    /** 奖励触发条件: SIGNUP / FIRST_PURCHASE / PURCHASE_AMOUNT / RETENTION_DAYS */
    @Column(name = "reward_trigger", nullable = false, length = 30)
    private String rewardTrigger;

    /** 触发条件值 (如消费金额阈值/留存天数, 可空) */
    @Column(name = "reward_trigger_value")
    private Double rewardTriggerValue;

    /** 每人最大推荐数 (0=无限) */
    @Column(name = "max_referrals_per_referrer")
    private Integer maxReferralsPerReferrer;

    /** 总推荐上限 (0=无限) */
    @Column(name = "max_referrals_total")
    private Integer maxReferralsTotal;

    /** 双向奖励 (默认 TRUE) */
    @Column(name = "double_sided_reward", nullable = false)
    private Boolean doubleSidedReward;

    /** 活动开始日期 */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 活动结束日期 (可空, 无截止) */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** 状态: ACTIVE / PAUSED / EXPIRED / COMPLETED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 活动条款 (可空) */
    @Column(name = "terms_conditions", columnDefinition = "TEXT")
    private String termsConditions;

    /** 总推荐数 */
    @Column(name = "total_referrals")
    private Integer totalReferrals;

    /** 成功推荐数 */
    @Column(name = "successful_referrals")
    private Integer successfulReferrals;

    /** 累计奖励价值 */
    @Column(name = "total_reward_value")
    private Double totalRewardValue;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
