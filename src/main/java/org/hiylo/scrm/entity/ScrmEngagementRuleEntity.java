/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmEngagementRuleEntity.java
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
 * SCRM 互动评分规则实体。
 * <p>
 * 定义客户互动行为的评分规则: 行为类型 {@link #behaviorType} (PAGE_VIEW/MESSAGE_SEND/
 * MESSAGE_REPLY/CALL/EMAIL_OPEN/EMAIL_CLICK/LINK_CLICK/FORM_SUBMIT/PURCHASE/SHARE/
 * FAVORITE/COMMENT/LOGIN/SEARCH/DOWNLOAD/APPOINTMENT), 发生渠道 {@link #channel}
 * (WECHAT/WEB/APP/EMAIL/PHONE/STORE/OTHER, 为空表示任意渠道)。
 * </p>
 * <p>
 * 单次得分 {@link #points} 经 {@link #weight} 权重放大后计入评分; {@link #dailyLimit} /
 * {@link #weeklyLimit} / {@link #monthlyLimit} 限制该规则在指定周期内可累计的得分上限
 * (0 表示不限)。{@link #decayDays} 与 {@link #decayType} (LINEAR 线性 / EXPONENTIAL 指数 /
 * STEP 阶梯 / NONE 不衰减) 控制得分随时间衰减, 用于计算 currentScore。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_engagement_rule", schema = "scrm", indexes = {
        @Index(name = "idx_engagement_rule_behavior", columnList = "behavior_type"),
        @Index(name = "idx_engagement_rule_channel", columnList = "channel"),
        @Index(name = "idx_engagement_rule_enabled", columnList = "enabled")
})
@Data
public class ScrmEngagementRuleEntity {

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

    /** 规则名称 */
    @Column(name = "rule_name", nullable = false, length = 200)
    private String ruleName;

    /** 行为类型: PAGE_VIEW/MESSAGE_SEND/MESSAGE_REPLY/CALL/EMAIL_OPEN/EMAIL_CLICK/LINK_CLICK/FORM_SUBMIT/PURCHASE/SHARE/FAVORITE/COMMENT/LOGIN/SEARCH/DOWNLOAD/APPOINTMENT */
    @Column(name = "behavior_type", nullable = false, length = 50)
    private String behaviorType;

    /** 发生渠道: WECHAT/WEB/APP/EMAIL/PHONE/STORE/OTHER (可空表示任意渠道) */
    @Column(name = "channel", length = 30)
    private String channel;

    /** 单次得分 (默认 1) */
    @Column(name = "points", nullable = false)
    private Integer points;

    /** 每日上限 (0 表示不限) */
    @Column(name = "daily_limit")
    private Integer dailyLimit;

    /** 每周上限 (0 表示不限) */
    @Column(name = "weekly_limit")
    private Integer weeklyLimit;

    /** 每月上限 (0 表示不限) */
    @Column(name = "monthly_limit")
    private Integer monthlyLimit;

    /** 衰减天数 (默认 30) */
    @Column(name = "decay_days")
    private Integer decayDays;

    /** 衰减类型: LINEAR/EXPONENTIAL/STEP/NONE (默认 LINEAR) */
    @Column(name = "decay_type", nullable = false, length = 20)
    private String decayType;

    /** 权重 (默认 1.0, 与 points 相乘得到实际得分) */
    @Column(name = "weight")
    private Double weight;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 是否启用 (默认 true) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 匹配次数 (命中累计) */
    @Column(name = "match_count")
    private Integer matchCount;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
