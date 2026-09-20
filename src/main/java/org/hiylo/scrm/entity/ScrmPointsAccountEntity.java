/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsAccountEntity.java
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * SCRM 积分账户实体。
 * <p>
 * 每个客户在每个账号下拥有唯一积分账户 (customer_id 唯一)。账户记录当前可用积分
 * {@link #currentPoints}、冻结积分 {@link #frozenPoints}、累计获取 {@link #totalEarned}、
 * 累计消耗 {@link #totalRedeemed}、累计过期 {@link #totalExpired} 及积分等级 {@link #level}。
 * </p>
 * <p>
 * {@link #updatedAt} 记录最近一次积分变动时间, 由 Service 层在加减积分时维护。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_points_account", schema = "scrm", indexes = {
        @Index(name = "idx_points_account_customer", columnList = "customer_id"),
        @Index(name = "idx_points_account_level", columnList = "level")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_points_account_customer", columnNames = {"customer_id"})
})
@Data
public class ScrmPointsAccountEntity {

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
        if (updatedAt == null) {
            updatedAt = now;
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
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 冗余客户名称 */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 当前可用积分 */
    @Column(name = "current_points", nullable = false)
    private Integer currentPoints;

    /** 冻结积分 */
    @Column(name = "frozen_points")
    private Integer frozenPoints;

    /** 累计获取 */
    @Column(name = "total_earned")
    private Integer totalEarned;

    /** 累计消耗 */
    @Column(name = "total_redeemed")
    private Integer totalRedeemed;

    /** 累计过期 */
    @Column(name = "total_expired")
    private Integer totalExpired;

    /** 积分等级 (可空) */
    @Column(name = "level", length = 50)
    private String level;

    /** 最近获取时间 */
    @Column(name = "last_earn_at")
    private LocalDateTime lastEarnAt;

    /** 最近消耗时间 */
    @Column(name = "last_redeem_at")
    private LocalDateTime lastRedeemAt;

    /** 积分变动时间 (业务字段, 由 Service 维护) */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
