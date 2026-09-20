/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLevelHistoryEntity.java
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
 * SCRM 客户等级变更历史实体。
 * <p>
 * 每次客户等级变更 (首次入等级 INITIAL / 手动分配 MANUAL / 自动升降级 AUTO) 时写入一条记录,
 * 记录变更前后的等级、变更类型、变更原因与操作人、变更时间。
 * </p>
 * <p>
 * 客户当前等级查询: 取 {@code scrm_customer_level_history} 中该 {@code customer_id} 的最新一条
 * (按 changed_at DESC) 记录的 {@link #toLevelId}。无历史记录的客户视为未分级, 由默认等级兜底。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_customer_level_history", schema = "scrm", indexes = {
        @Index(name = "idx_customer_level_hist_customer", columnList = "customer_id,changed_at"),
        @Index(name = "idx_customer_level_hist_changed", columnList = "changed_at"),
        @Index(name = "idx_customer_level_hist_to_level", columnList = "to_level_id")
})
@Data
public class ScrmCustomerLevelHistoryEntity {

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
     * 持久化前回调: 自动填充创建/更新时间、版本号初值与变更时间
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (changedAt == null) {
            changedAt = now;
        }
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
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (变更时快照, 便于审计追溯, 可空) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 原等级 ID (首次入等级时为 null) */
    @Column(name = "from_level_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromLevelId;

    /** 原等级名称 (变更时快照) */
    @Column(name = "from_level_name", length = 100)
    private String fromLevelName;

    /** 新等级 ID */
    @Column(name = "to_level_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long toLevelId;

    /** 新等级名称 (变更时快照) */
    @Column(name = "to_level_name", nullable = false, length = 100)
    private String toLevelName;

    /** 变更类型: UPGRADE 升级 / DOWNGRADE 降级 / INITIAL 首次 / MANUAL 手动 / AUTO 自动 */
    @Column(name = "change_type", nullable = false, length = 20)
    private String changeType;

    /** 变更原因 (可空) */
    @Column(name = "change_reason", length = 500)
    private String changeReason;

    /** 操作人 (MANUAL 时从请求头透传, AUTO 时为 scrm-system) */
    @Column(name = "changed_by", length = 100)
    private String changedBy;

    /** 变更时间 */
    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
}
