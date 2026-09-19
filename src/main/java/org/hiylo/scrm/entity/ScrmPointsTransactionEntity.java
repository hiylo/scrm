/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsTransactionEntity.java
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
 * SCRM 积分流水实体。
 * <p>
 * 记录每一次积分变动: 交易类型 {@link #transactionType} (EARN 获取 / REDEEM 消耗 / FREEZE 冻结 /
 * UNFREEZE 解冻 / EXPIRE 过期 / ADJUST 手动调整), 变动值 {@link #points} (正/负), 变动后余额
 * {@link #balanceAfter}, 关联规则 {@link #ruleId}, 触发事件 {@link #triggerEvent}, 来源
 * {@link #sourceType} (SYSTEM 系统 / MANUAL 手动 / EXCHANGE 兑换 / ORDER 订单)。
 * </p>
 * <p>
 * {@link #expiresAt} 标记该笔获取积分的过期时间, {@link #expired} 标记是否已过期, 由
 * {@code ScrmPointsService.processExpiredPoints} 定时清理。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_points_transaction", schema = "scrm", indexes = {
        @Index(name = "idx_points_txn_account", columnList = "account_id"),
        @Index(name = "idx_points_txn_customer", columnList = "customer_id"),
        @Index(name = "idx_points_txn_type", columnList = "transaction_type"),
        @Index(name = "idx_points_txn_created", columnList = "created_at"),
        @Index(name = "idx_points_txn_expires", columnList = "expires_at,expired")
})
@Data
public class ScrmPointsTransactionEntity {

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
     * 持久化前回调: 自动填充创建/更新时间、版本号初值与业务创建时间
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (version == null) {
            version = 0L;
        }
        if (createdAt == null) {
            createdAt = now;
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

    /** 关联积分账户 ID */
    @Column(name = "account_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 冗余客户名称 */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 交易类型: EARN/REDEEM/FREEZE/UNFREEZE/EXPIRE/ADJUST */
    @Column(name = "transaction_type", nullable = false, length = 20)
    private String transactionType;

    /** 积分变动值 (正/负) */
    @Column(name = "points", nullable = false)
    private Integer points;

    /** 变动后余额 */
    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    /** 关联规则 ID (可空) */
    @Column(name = "rule_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ruleId;

    /** 触发事件 (可空) */
    @Column(name = "trigger_event", length = 50)
    private String triggerEvent;

    /** 来源: SYSTEM/MANUAL/EXCHANGE/ORDER */
    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    /** 来源 ID (如订单号 / 兑换记录 ID, 可空) */
    @Column(name = "source_id", length = 100)
    private String sourceId;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 积分过期时间 (可空, 仅 EARN 类流水设置) */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** 是否已过期 */
    @Column(name = "expired", nullable = false)
    private Boolean expired;

    /** 流水发生时间 (业务字段) */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
