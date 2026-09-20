/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInteractionPlanEntity.java
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 客户互动计划实体。
 * <p>
 * 描述一条客户互动计划: 基础信息 ({@link #planName} / {@link #planCode} /
 * {@link #description} / {@link #title} / {@link #content}), 客户与联系人
 * ({@link #customerId} / {@link #customerName} / {@link #customerContactId} /
 * {@link #customerContactName}), 互动配置 ({@link #interactionType} /
 * {@link #interactionMethod} / {@link #objectives} / {@link #prepareMaterials}),
 * 时间安排 ({@link #scheduledStart} / {@link #scheduledEnd} / {@link #actualStart} /
 * {@link #actualEnd} / {@link #timezone} / {@link #isAllDay}), 地点
 * ({@link #location} / {@link #locationType}), 负责与参与 ({@link #ownerId} /
 * {@link #ownerName} / {@link #participantIds}), 提醒 ({@link #reminderType} /
 * {@link #reminderMinutesBefore} / {@link #isReminderSent} / {@link #reminderSentAt}),
 * 重复规则 ({@link #repeatType} / {@link #repeatInterval} / {@link #repeatEndDate} /
 * {@link #repeatCount} / {@link #maxRepeatCount} / {@link #weekDays} / {@link #monthDay}),
 * 状态流转 ({@link #status} / {@link #priority} / {@link #completionNotes} /
 * {@link #outcome} / {@link #followUpAction} / {@link #followUpDate}), 日历呈现
 * ({@link #color} / {@link #isPinned} / {@link #tags} / {@link #attachments} /
 * {@link #relatedPlanId})。
 * </p>
 * <p>
 * 状态流转: PLANNED (已计划) → CONFIRMED (已确认) → IN_PROGRESS (进行中) →
 * COMPLETED (已完成) / CANCELLED (已取消) / RESCHEDULED (已改期) / NO_SHOW (未到)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_interaction_plan", schema = "scrm",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_interaction_plan_code",
                columnNames = {"plan_code"}),
        indexes = {
                @Index(name = "idx_interaction_plan_customer", columnList = "customer_id"),
                @Index(name = "idx_interaction_plan_owner", columnList = "owner_id"),
                @Index(name = "idx_interaction_plan_status", columnList = "status"),
                @Index(name = "idx_interaction_plan_type", columnList = "interaction_type"),
                @Index(name = "idx_interaction_plan_date_range",
                        columnList = "scheduled_start,scheduled_end"),
                @Index(name = "idx_interaction_plan_related", columnList = "related_plan_id")
        })
/**
 * SCRM 客户互动计划实体。
 * <p>承载针对单个客户的互动触达计划: 计划名称与编码、描述、目标客户
 * (customerId/customerName)、互动类型与互动方式、标题与内容与目标、
 * 负责人与状态、计划起止时间、关联计划、执行结果与备注。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmInteractionPlanEntity {

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

    /** 计划名称 */
    @Column(name = "plan_name", nullable = false, length = 200)
    private String planName;

    /** 计划编码 (唯一) */
    @Column(name = "plan_code", nullable = false, length = 50)
    private String planCode;

    /** 计划描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 互动类型: CALL/EMAIL/WECHAT/MEETING/VISIT/FOLLOW_UP/REVIEW/GREETING/GIFT/OTHER */
    @Column(name = "interaction_type", nullable = false, length = 30)
    private String interactionType;

    /** 互动方式: PHONE/VIDEO/ONSITE/ONLINE/MESSAGE/MAIL */
    @Column(name = "interaction_method", nullable = false, length = 30)
    private String interactionMethod;

    /** 互动主题 */
    @Column(name = "title", nullable = false, length = 500)
    private String title;

    /** 互动内容 (可空) */
    @Column(name = "content", length = 2000)
    private String content;

    /** 互动目标 (可空) */
    @Column(name = "objectives", length = 1000)
    private String objectives;

    /** 准备材料 (可空) */
    @Column(name = "prepare_materials", length = 1000)
    private String prepareMaterials;

    /** 计划开始时间 */
    @Column(name = "scheduled_start", nullable = false)
    private LocalDateTime scheduledStart;

    /** 计划结束时间 (可空) */
    @Column(name = "scheduled_end")
    private LocalDateTime scheduledEnd;

    /** 实际开始时间 (可空) */
    @Column(name = "actual_start")
    private LocalDateTime actualStart;

    /** 实际结束时间 (可空) */
    @Column(name = "actual_end")
    private LocalDateTime actualEnd;

    /** 时区 (默认 Asia/Shanghai) */
    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone;

    /** 互动地点 (可空) */
    @Column(name = "location", length = 500)
    private String location;

    /** 地点类型: OFFICE/CUSTOMER_SITE/ONLINE/PHONE/OTHER (可空) */
    @Column(name = "location_type", length = 30)
    private String locationType;

    /** 负责人 ID */
    @Column(name = "owner_id", nullable = false, length = 100)
    private String ownerId;

    /** 负责人名称 (可空) */
    @Column(name = "owner_name", length = 100)
    private String ownerName;

    /** 参与人 ID 列表 (逗号分隔, 可空) */
    @Column(name = "participant_ids", length = 500)
    private String participantIds;

    /** 客户联系人 ID (可空) */
    @Column(name = "customer_contact_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerContactId;

    /** 客户联系人名称 (可空) */
    @Column(name = "customer_contact_name", length = 200)
    private String customerContactName;

    /** 提醒类型: NONE/NOTIFICATION/EMAIL/SMS/ALL (默认 NOTIFICATION) */
    @Column(name = "reminder_type", nullable = false, length = 30)
    private String reminderType;

    /** 提前提醒分钟数 (默认 15) */
    @Column(name = "reminder_minutes_before")
    private Integer reminderMinutesBefore;

    /** 是否已发送提醒 (默认 FALSE) */
    @Column(name = "is_reminder_sent", nullable = false)
    private Boolean isReminderSent;

    /** 提醒发送时间 (可空) */
    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    /** 重复类型: NONE/DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY/CUSTOM (默认 NONE) */
    @Column(name = "repeat_type", nullable = false, length = 30)
    private String repeatType;

    /** 重复间隔 (默认 1) */
    @Column(name = "repeat_interval")
    private Integer repeatInterval;

    /** 重复结束日期 (可空) */
    @Column(name = "repeat_end_date")
    private LocalDate repeatEndDate;

    /** 已重复次数 (默认 0) */
    @Column(name = "repeat_count")
    private Integer repeatCount;

    /** 最大重复次数 (默认 0, 0 表示无限) */
    @Column(name = "max_repeat_count")
    private Integer maxRepeatCount;

    /** 周几重复 (如 1,3,5, 可空) */
    @Column(name = "week_days", length = 20)
    private String weekDays;

    /** 每月几号重复 (可空) */
    @Column(name = "month_day")
    private Integer monthDay;

    /** 优先级: LOW/MEDIUM/HIGH/URGENT (默认 MEDIUM) */
    @Column(name = "priority", nullable = false, length = 20)
    private String priority;

    /** 状态: PLANNED/CONFIRMED/IN_PROGRESS/COMPLETED/CANCELLED/RESCHEDULED/NO_SHOW (默认 PLANNED) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 完成备注 (可空) */
    @Column(name = "completion_notes", length = 2000)
    private String completionNotes;

    /** 互动结果: POSITIVE/NEUTRAL/NEGATIVE/FOLLOW_UP_NEEDED (可空) */
    @Column(name = "outcome", length = 30)
    private String outcome;

    /** 后续行动 (可空) */
    @Column(name = "follow_up_action", length = 500)
    private String followUpAction;

    /** 后续日期 (可空) */
    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    /** 标签 (逗号分隔, 可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 日历颜色 (可空, 用于前端渲染) */
    @Column(name = "color", length = 20)
    private String color;

    /** 是否全天事件 (默认 FALSE) */
    @Column(name = "is_all_day", nullable = false)
    private Boolean isAllDay;

    /** 是否置顶 (默认 FALSE) */
    @Column(name = "is_pinned", nullable = false)
    private Boolean isPinned;

    /** 附件 JSON (可空) */
    @Column(name = "attachments", length = 1000)
    private String attachments;

    /** 关联计划 ID (可空, 用于重复计划关联) */
    @Column(name = "related_plan_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long relatedPlanId;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
