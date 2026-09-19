/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInteractionCalendarEntity.java
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 客户互动日历实体。
 * <p>
 * 描述一个客户互动日历: 基础信息 ({@link #calendarName} / {@link #calendarCode} /
 * {@link #description} / {@link #calendarType}), 所有者与共享 ({@link #ownerId} /
 * {@link #ownerName} / {@link #sharedWith} / {@link #isPublic}), 呈现配置
 * ({@link #color} / {@link #icon}), 工作时间配置 ({@link #workingHoursStart} /
 * {@link #workingHoursEnd} / {@link #workingDays} / {@link #timezone}), 默认设置
 * ({@link #defaultReminderMinutes} / {@link #defaultDurationMinutes}), 统计指标
 * ({@link #planCount} / {@link #completedCount} / {@link #cancelledCount} /
 * {@link #completionRate} / {@link #lastActivityDate}), 启停状态 ({@link #enabled})。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_interaction_calendar", schema = "scrm",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_interaction_calendar_code",
                columnNames = {"calendar_code"}),
        indexes = {
                @Index(name = "idx_interaction_calendar_owner", columnList = "owner_id"),
                @Index(name = "idx_interaction_calendar_type", columnList = "calendar_type"),
                @Index(name = "idx_interaction_calendar_enabled", columnList = "enabled")
        })
/**
 * SCRM 客户互动日历实体。
 * <p>承载互动日程的日历视图配置: 日历名称与编码、描述、日历类型、
 * 归属人 (ownerId/ownerName)、共享范围与是否公开、颜色与图标、
 * 默认视图与时间范围、是否启用与排序、备注。日历编码 (calendarCode) 唯一。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmInteractionCalendarEntity {

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

    /** 日历名称 */
    @Column(name = "calendar_name", nullable = false, length = 200)
    private String calendarName;

    /** 日历编码 (唯一) */
    @Column(name = "calendar_code", nullable = false, length = 50)
    private String calendarCode;

    /** 日历描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 日历类型: PERSONAL/TEAM/DEPARTMENT/COMPANY/CUSTOMER (默认 PERSONAL) */
    @Column(name = "calendar_type", nullable = false, length = 30)
    private String calendarType;

    /** 所有者 ID */
    @Column(name = "owner_id", nullable = false, length = 100)
    private String ownerId;

    /** 所有者名称 (可空) */
    @Column(name = "owner_name", length = 100)
    private String ownerName;

    /** 共享给用户 ID 列表 (逗号分隔, 可空) */
    @Column(name = "shared_with", length = 500)
    private String sharedWith;

    /** 是否公开 (默认 FALSE) */
    @Column(name = "is_public", nullable = false)
    private Boolean isPublic;

    /** 日历颜色 (可空, 用于前端渲染) */
    @Column(name = "color", length = 20)
    private String color;

    /** 图标 (可空) */
    @Column(name = "icon", length = 200)
    private String icon;

    /** 工作时间开始 (HH:mm, 默认 09:00) */
    @Column(name = "working_hours_start", nullable = false, length = 10)
    private String workingHoursStart;

    /** 工作时间结束 (HH:mm, 默认 18:00) */
    @Column(name = "working_hours_end", nullable = false, length = 10)
    private String workingHoursEnd;

    /** 工作日 (逗号分隔, 默认 1,2,3,4,5) */
    @Column(name = "working_days", nullable = false, length = 20)
    private String workingDays;

    /** 时区 (默认 Asia/Shanghai) */
    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone;

    /** 默认提前提醒分钟数 (默认 15) */
    @Column(name = "default_reminder_minutes")
    private Integer defaultReminderMinutes;

    /** 默认时长分钟数 (默认 60) */
    @Column(name = "default_duration_minutes")
    private Integer defaultDurationMinutes;

    /** 计划数 (默认 0) */
    @Column(name = "plan_count")
    private Integer planCount;

    /** 已完成计划数 (默认 0) */
    @Column(name = "completed_count")
    private Integer completedCount;

    /** 已取消计划数 (默认 0) */
    @Column(name = "cancelled_count")
    private Integer cancelledCount;

    /** 完成率 (默认 0) */
    @Column(name = "completion_rate")
    private Double completionRate;

    /** 最近活动日期 (可空) */
    @Column(name = "last_activity_date")
    private LocalDate lastActivityDate;

    /** 是否启用 (默认 TRUE) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
