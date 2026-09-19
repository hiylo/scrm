/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVisitTaskEntity.java
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
import java.time.LocalTime;

/**
 * SCRM 客户回访任务实体。
 * <p>
 * 描述一次具体的客户回访任务: 任务标识 ({@link #taskNo} / {@link #planId} /
 * {@link #planName}), 客户信息 ({@link #customerId} / {@link #customerName} /
 * {@link #customerLevel} / {@link #customerPhone}), 计划时间 ({@link #scheduledDate} /
 * {@link #scheduledTime}), 实际执行 ({@link #actualVisitDate} /
 * {@link #actualVisitTime} / {@link #actualDurationMinutes}), 状态流转
 * ({@link #status} / {@link #assignedTo} / {@link #assignedAt} / {@link #startedAt} /
 * {@link #completedAt}), 回访结果 ({@link #visitOutcome} / {@link #satisfactionScore} /
 * {@link #npsScore} / {@link #feedback} / {@link #summary} / {@link #actionItems}),
 * 跟进 ({@link #followUpRequired} / {@link #followUpDate} / {@link #followUpType}),
 * 商机与问题 ({@link #opportunityFound} / {@link #opportunityDescription} /
 * {@link #issueFound} / {@link #issueDescription} / {@link #issueResolved}),
 * 附加信息 ({@link #recordingUrl} / {@link #notes} / {@link #location}),
 * 提醒与改期 ({@link #reminderSent} / {@link #reminderSentAt} /
 * {@link #rescheduleCount} / {@link #originalDate})。
 * </p>
 * <p>
 * 状态流转: PENDING (待执行) → ASSIGNED (已分配) → IN_PROGRESS (进行中) →
 * COMPLETED (已完成) / CANCELLED (已取消) / RESCHEDULED (已改期) /
 * OVERDUE (已逾期) / FAILED (失败)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_visit_task", schema = "scrm",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_visit_task_no",
                columnNames = {"task_no"}),
        indexes = {
                @Index(name = "idx_visit_task_plan", columnList = "plan_id"),
                @Index(name = "idx_visit_task_customer", columnList = "customer_id"),
                @Index(name = "idx_visit_task_status", columnList = "status"),
                @Index(name = "idx_visit_task_assigned", columnList = "assigned_to"),
                @Index(name = "idx_visit_task_date", columnList = "scheduled_date"),
                @Index(name = "idx_visit_task_type", columnList = "visit_type")
        })
/**
 * SCRM 客户回访任务实体。
 * <p>承载回访计划拆分后的单条回访执行任务: 任务编号、所属计划 (planId/planName)、
 * 目标客户 (customerId/customerName/客户等级/电话)、回访类型与回访方式、
 * 计划与完成时间、负责人、状态、回访记录与结果、下一次跟进时间与备注。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmVisitTaskEntity {

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

    /** 任务编号 (唯一, VT+年月日+序号) */
    @Column(name = "task_no", nullable = false, length = 100)
    private String taskNo;

    /** 关联计划 ID (可空, 手动创建的任务无计划) */
    @Column(name = "plan_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long planId;

    /** 关联计划名称 (冗余, 可空) */
    @Column(name = "plan_name", length = 200)
    private String planName;

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 客户等级 (可空) */
    @Column(name = "customer_level", length = 50)
    private String customerLevel;

    /** 客户电话 (可空) */
    @Column(name = "customer_phone", length = 50)
    private String customerPhone;

    /** 回访类型: REGULAR/FOLLOW_UP/SATISFACTION/RENEWAL/UPSELL/CROSS_SELL/CARE/COMPLAINT_FOLLOWUP/CUSTOM */
    @Column(name = "visit_type", nullable = false, length = 30)
    private String visitType;

    /** 回访方式: PHONE/ON_SITE/VIDEO/WECHAT/EMAIL/SMS/MIXED */
    @Column(name = "visit_method", nullable = false, length = 30)
    private String visitMethod;

    /** 计划回访日期 */
    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    /** 计划回访时间 (可空) */
    @Column(name = "scheduled_time")
    private LocalTime scheduledTime;

    /** 实际回访日期 (可空) */
    @Column(name = "actual_visit_date")
    private LocalDate actualVisitDate;

    /** 实际回访时间 (可空) */
    @Column(name = "actual_visit_time")
    private LocalTime actualVisitTime;

    /** 实际回访时长分钟 (可空) */
    @Column(name = "actual_duration_minutes")
    private Integer actualDurationMinutes;

    /** 状态: PENDING/ASSIGNED/IN_PROGRESS/COMPLETED/CANCELLED/RESCHEDULED/OVERDUE/FAILED (默认 PENDING) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 负责人 ID (可空) */
    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    /** 分配时间 (可空) */
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    /** 开始执行时间 (可空) */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** 完成时间 (可空) */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 回访结果: SUCCESS/PARTIAL/NO_ANSWER/REFUSED/RESCHEDULED/FAILED (可空) */
    @Column(name = "visit_outcome", length = 20)
    private String visitOutcome;

    /** 满意度评分 1-5 (可空) */
    @Column(name = "satisfaction_score")
    private Integer satisfactionScore;

    /** NPS 评分 0-10 (可空) */
    @Column(name = "nps_score")
    private Integer npsScore;

    /** 客户反馈 (可空) */
    @Column(name = "feedback", length = 2000)
    private String feedback;

    /** 回访总结 (可空) */
    @Column(name = "summary", length = 1000)
    private String summary;

    /** 后续行动项 (可空) */
    @Column(name = "action_items", length = 1000)
    private String actionItems;

    /** 是否需要跟进 (默认 FALSE) */
    @Column(name = "follow_up_required", nullable = false)
    private Boolean followUpRequired;

    /** 跟进日期 (可空) */
    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    /** 跟进类型 (可空) */
    @Column(name = "follow_up_type", length = 30)
    private String followUpType;

    /** 是否发现商机 (默认 FALSE) */
    @Column(name = "opportunity_found", nullable = false)
    private Boolean opportunityFound;

    /** 商机描述 (可空) */
    @Column(name = "opportunity_description", length = 500)
    private String opportunityDescription;

    /** 是否发现问题 (默认 FALSE) */
    @Column(name = "issue_found", nullable = false)
    private Boolean issueFound;

    /** 问题描述 (可空) */
    @Column(name = "issue_description", length = 500)
    private String issueDescription;

    /** 问题是否已解决 (默认 FALSE) */
    @Column(name = "issue_resolved", nullable = false)
    private Boolean issueResolved;

    /** 录音 URL (可空) */
    @Column(name = "recording_url", length = 500)
    private String recordingUrl;

    /** 备注 (可空) */
    @Column(name = "notes", length = 500)
    private String notes;

    /** 上门地址 (可空) */
    @Column(name = "location", length = 200)
    private String location;

    /** 是否已发送提醒 (默认 FALSE) */
    @Column(name = "reminder_sent", nullable = false)
    private Boolean reminderSent;

    /** 提醒发送时间 (可空) */
    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    /** 改期次数 (默认 0) */
    @Column(name = "reschedule_count")
    private Integer rescheduleCount;

    /** 原始计划日期 (改期后保留, 可空) */
    @Column(name = "original_date")
    private LocalDate originalDate;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
