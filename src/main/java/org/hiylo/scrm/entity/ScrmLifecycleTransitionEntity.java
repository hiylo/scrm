/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLifecycleTransitionEntity.java
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
 * SCRM 客户生命周期阶段流转规则实体。
 * <p>
 * 描述从 {@link #fromStageId} (源阶段, null 表示新客户) 到 {@link #toStageId} (目标阶段)
 * 的流转规则。{@link #transitionType} 区分自动 / 手动 / 系统触发,
 * {@link #triggerCondition} (JSON) 与 {@link #triggerEvents} (逗号分隔事件列表)
 * 定义触发条件, {@link #priority} 控制多规则命中时的优先级,
 * {@link #cooldownDays} 防止短期内重复转换, {@link #triggerCount} / {@link #lastTriggeredAt}
 * 记录触发统计。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_lifecycle_transition", schema = "scrm", indexes = {
        @Index(name = "idx_lifecycle_transition_from", columnList = "from_stage_id"),
        @Index(name = "idx_lifecycle_transition_to", columnList = "to_stage_id"),
        @Index(name = "idx_lifecycle_transition_type", columnList = "transition_type"),
        @Index(name = "idx_lifecycle_transition_enabled", columnList = "is_enabled")
})
@Data
public class ScrmLifecycleTransitionEntity {

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

    /** 源阶段 ID (可空, null 表示新客户进入) */
    @Column(name = "from_stage_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromStageId;

    /** 源阶段编码 (可空) */
    @Column(name = "from_stage_code", length = 50)
    private String fromStageCode;

    /** 目标阶段 ID */
    @Column(name = "to_stage_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long toStageId;

    /** 目标阶段编码 */
    @Column(name = "to_stage_code", nullable = false, length = 50)
    private String toStageCode;

    /** 转换名称 */
    @Column(name = "transition_name", nullable = false, length = 200)
    private String transitionName;

    /** 转换类型: AUTO/MANUAL/SYSTEM (默认 AUTO) */
    @Column(name = "transition_type", nullable = false, length = 20)
    private String transitionType;

    /** 触发条件 JSON (可空) */
    @Column(name = "trigger_condition", columnDefinition = "TEXT")
    private String triggerCondition;

    /** 触发事件逗号分隔: PURCHASE/LOGIN/INACTIVE_DAYS/FIRST_CONTACT/REFUND/CUSTOM (可空) */
    @Column(name = "trigger_events", length = 500)
    private String triggerEvents;

    /** 优先级 (值越大越优先, 默认 0) */
    @Column(name = "priority")
    private Integer priority;

    /** 冷却天数 (默认 0, 防止短期重复转换) */
    @Column(name = "cooldown_days")
    private Integer cooldownDays;

    /** 是否启用 (默认 TRUE) */
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled;

    /** 触发次数 (默认 0) */
    @Column(name = "trigger_count")
    private Integer triggerCount;

    /** 最近触发时间 (可空) */
    @Column(name = "last_triggered_at")
    private LocalDateTime lastTriggeredAt;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
