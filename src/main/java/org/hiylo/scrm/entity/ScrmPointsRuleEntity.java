/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsRuleEntity.java
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

import java.time.LocalDateTime;

/**
 * SCRM 积分规则实体。
 * <p>
 * 定义积分获取 (EARN) / 消耗 (REDEEM) 规则, 由 {@code ScrmPointsService.earnPoints} 在触发事件命中时
 * 执行。{@link #pointsType} 分为 FIXED (固定积分) / PERCENTAGE (按基准字段百分比计算, 如订单金额)。
 * 触发事件 {@link #triggerEvent}: PURCHASE / SIGN_IN / SHARE / REVIEW / INVITE / BIRTHDAY /
 * PROFILE_COMPLETE / NEW_CUSTOMER / CONSUMPTION。
 * </p>
 * <p>
 * dailyLimit / monthlyLimit 用于限制单客户每日/每月通过该规则获取的积分上限 (可空表示不限)。
 * minPoints / maxPoints 对单次计算结果做上下界裁剪。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_points_rule", schema = "scrm", indexes = {
        @Index(name = "idx_points_rule_type", columnList = "rule_type"),
        @Index(name = "idx_points_rule_event", columnList = "trigger_event"),
        @Index(name = "idx_points_rule_enabled", columnList = "enabled")
})
@Data
public class ScrmPointsRuleEntity {

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

    /** 规则类型: EARN 获取 / REDEEM 消耗 */
    @Column(name = "rule_type", nullable = false, length = 20)
    private String ruleType;

    /** 触发事件: PURCHASE/SIGN_IN/SHARE/REVIEW/INVITE/BIRTHDAY/PROFILE_COMPLETE/NEW_CUSTOMER/CONSUMPTION */
    @Column(name = "trigger_event", nullable = false, length = 50)
    private String triggerEvent;

    /** 积分值 (正数获取, 负数消耗) */
    @Column(name = "points_value", nullable = false)
    private Integer pointsValue;

    /** 积分计算类型: FIXED 固定 / PERCENTAGE 百分比 (默认 FIXED) */
    @Column(name = "points_type", nullable = false, length = 20)
    private String pointsType;

    /** 百分比基准字段 (如 orderAmount, pointsType=PERCENTAGE 时使用) */
    @Column(name = "basis_field", length = 50)
    private String basisField;

    /** 每日上限 (可空表示不限) */
    @Column(name = "daily_limit")
    private Integer dailyLimit;

    /** 每月上限 (可空表示不限) */
    @Column(name = "monthly_limit")
    private Integer monthlyLimit;

    /** 最少积分 (单次计算下界, 默认 0) */
    @Column(name = "min_points")
    private Integer minPoints;

    /** 最多积分 (单次计算上界, 可空表示不限) */
    @Column(name = "max_points")
    private Integer maxPoints;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 是否启用 (默认 true) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 触发次数 (命中累计) */
    @Column(name = "trigger_count")
    private Integer triggerCount;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
