/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCalendarEventEntity.java
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
import java.time.LocalTime;

/**
 * SCRM 营销日历事件实体。
 * <p>
 * 描述一条营销日历上的事件: 事件基础信息 ({@link #eventTitle} / {@link #eventType} /
 * {@link #description}), 时间安排 ({@link #startDate} / {@link #endDate} /
 * {@link #startTime} / {@link #endTime} / {@link #isAllDay}), 重复规则
 * ({@link #isRecurring} / {@link #recurringType} / {@link #recurringConfig}),
 * 关联资源 ({@link #campaignId} / {@link #contentId} / {@link #channels} /
 * {@link #targetSegment}), 状态流转 ({@link #status} / {@link #priority}),
 * 负责与执行 ({@link #ownerId} / {@link #teamId} / {@link #budget} /
 * {@link #estimatedReach} / {@link #actualReach})。
 * </p>
 * <p>
 * 状态流转: PLANNED (已计划) → CONFIRMED (已确认) → IN_PROGRESS (进行中) →
 * COMPLETED (已完成) / CANCELLED (已取消) / POSTPONED (已延期)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_calendar_event", schema = "scrm", indexes = {
        @Index(name = "idx_calendar_event_type", columnList = "event_type"),
        @Index(name = "idx_calendar_event_status", columnList = "status"),
        @Index(name = "idx_calendar_event_owner", columnList = "owner_id"),
        @Index(name = "idx_calendar_event_date_range", columnList = "start_date,end_date")
})
@Data
public class ScrmCalendarEventEntity {

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

    /** 事件标题 */
    @Column(name = "event_title", nullable = false, length = 200)
    private String eventTitle;

/** 事件类型: CAMPAIGN / PROMOTION / HOLIDAY / FESTIVAL / ANNIVERSARY / CONTENT_PUBLISH / LIVE_STREAMING /
         * PRODUCT_LAUNCH / SALES_TARGET / MEETING / REMINDER / CUSTOM */
    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    /** 事件描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 开始日期 */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 结束日期 */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** 开始时间 (可空, 非全天事件时使用) */
    @Column(name = "start_time")
    private LocalTime startTime;

    /** 结束时间 (可空, 非全天事件时使用) */
    @Column(name = "end_time")
    private LocalTime endTime;

    /** 是否全天事件 (默认 TRUE) */
    @Column(name = "is_all_day", nullable = false)
    private Boolean isAllDay;

    /** 是否重复事件 (默认 FALSE) */
    @Column(name = "is_recurring", nullable = false)
    private Boolean isRecurring;

    /** 重复类型: DAILY / WEEKLY / MONTHLY / YEARLY (可空) */
    @Column(name = "recurring_type", length = 20)
    private String recurringType;

    /** 重复配置 JSON (可空, 描述重复规则的细节) */
    @Column(name = "recurring_config", columnDefinition = "TEXT")
    private String recurringConfig;

    /** 渠道 (逗号分隔, 可空) */
    @Column(name = "channels", length = 500)
    private String channels;

    /** 关联营销活动 ID (可空) */
    @Column(name = "campaign_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long campaignId;

    /** 关联内容 ID (可空) */
    @Column(name = "content_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contentId;

    /** 目标客群 (可空) */
    @Column(name = "target_segment", length = 500)
    private String targetSegment;

    /** 状态: PLANNED / CONFIRMED / IN_PROGRESS / COMPLETED / CANCELLED / POSTPONED (默认 PLANNED) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 优先级 (默认 0, 数字越大越优先) */
    @Column(name = "priority")
    private Integer priority;

    /** 日历颜色 (可空, 用于前端渲染) */
    @Column(name = "color", length = 20)
    private String color;

    /** 标签 (逗号分隔, 可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 地点 (可空) */
    @Column(name = "location", length = 200)
    private String location;

    /** 负责人 ID (可空) */
    @Column(name = "owner_id", length = 100)
    private String ownerId;

    /** 负责人名称 (可空) */
    @Column(name = "owner_name", length = 100)
    private String ownerName;

    /** 团队 ID (可空) */
    @Column(name = "team_id", length = 100)
    private String teamId;

    /** 预算 (默认 0) */
    @Column(name = "budget")
    private Double budget;

    /** 预计触达 (默认 0) */
    @Column(name = "estimated_reach")
    private Integer estimatedReach;

    /** 实际触达 (默认 0) */
    @Column(name = "actual_reach")
    private Integer actualReach;

    /** 备注 (可空) */
    @Column(name = "notes", length = 500)
    private String notes;

    /** 提前提醒分钟数 (默认 0) */
    @Column(name = "reminder_minutes")
    private Integer reminderMinutes;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
