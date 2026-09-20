/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVisitPlanEntity.java
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
 * SCRM 客户回访计划实体。
 * <p>
 * 描述一条定期回访计划: 计划基础信息 ({@link #planName} / {@link #planCode} /
 * {@link #description} / {@link #planType}), 目标设定 ({@link #targetType} /
 * {@link #targetCriteria}), 频率配置 ({@link #visitFrequency} /
 * {@link #frequencyConfig}), 执行方式 ({@link #visitMethod} / {@link #templateId} /
 * {@link #assignedTo} / {@link #teamId}), 时间区间 ({@link #startDate} /
 * {@link #endDate}), 状态流转 ({@link #status} / {@link #priority}), 任务统计
 * ({@link #totalTasks} / {@link #completedTasks} / {@link #pendingTasks} /
 * {@link #overdueTasks} / {@link #completionRate} / {@link #avgSatisfactionScore} /
 * {@link #successRate}), 自动生成 ({@link #autoGenerate} / {@link #lastGeneratedAt} /
 * {@link #nextGenerateAt})。
 * </p>
 * <p>
 * 状态流转: ACTIVE (生效) → PAUSED (暂停) → COMPLETED (已完成) / EXPIRED (已过期)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_visit_plan", schema = "scrm",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_visit_plan_code",
                columnNames = {"plan_code"}),
        indexes = {
                @Index(name = "idx_visit_plan_type", columnList = "plan_type"),
                @Index(name = "idx_visit_plan_status", columnList = "status"),
                @Index(name = "idx_visit_plan_assigned", columnList = "assigned_to"),
                @Index(name = "idx_visit_plan_template", columnList = "template_id")
        })
/**
 * SCRM 客户回访计划实体。
 * <p>定义面向目标客群的批量回访计划: 计划名称与编码、描述、计划类型与目标类型、
 * 目标筛选条件、回访频率与频率配置、回访方式、关联回访模板、负责人与状态、
 * 计划起止时间、预计与已完成任务数、备注。计划编码 (planCode) 唯一。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmVisitPlanEntity {

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

    /** 回访类型: REGULAR/FOLLOW_UP/SATISFACTION/RENEWAL/UPSELL/CROSS_SELL/CARE/COMPLAINT_FOLLOWUP/CUSTOM */
    @Column(name = "plan_type", nullable = false, length = 30)
    private String planType;

    /** 目标类型: CUSTOMER/CUSTOMER_LEVEL/SEGMENT/ALL */
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    /** 目标条件 JSON (可空, {customerLevel,segment,tags,region}) */
    @Column(name = "target_criteria", columnDefinition = "TEXT")
    private String targetCriteria;

    /** 回访频率: ONCE/DAILY/WEEKLY/BIWEEKLY/MONTHLY/QUARTERLY/SEMIANNUALLY/ANNUALLY */
    @Column(name = "visit_frequency", nullable = false, length = 20)
    private String visitFrequency;

    /** 频率配置 JSON (可空, {dayOfWeek,dayOfMonth,time}) */
    @Column(name = "frequency_config", columnDefinition = "TEXT")
    private String frequencyConfig;

    /** 回访方式: PHONE/ON_SITE/VIDEO/WECHAT/EMAIL/SMS/MIXED (默认 PHONE) */
    @Column(name = "visit_method", nullable = false, length = 30)
    private String visitMethod;

    /** 关联回访模板 ID (可空) */
    @Column(name = "template_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

    /** 默认负责人 (可空) */
    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    /** 团队 ID (可空) */
    @Column(name = "team_id", length = 100)
    private String teamId;

    /** 开始日期 */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 结束日期 (可空, 为空表示长期计划) */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** 状态: ACTIVE/PAUSED/COMPLETED/EXPIRED (默认 ACTIVE) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 总任务数 (默认 0) */
    @Column(name = "total_tasks")
    private Integer totalTasks;

    /** 已完成任务数 (默认 0) */
    @Column(name = "completed_tasks")
    private Integer completedTasks;

    /** 待执行任务数 (默认 0) */
    @Column(name = "pending_tasks")
    private Integer pendingTasks;

    /** 逾期任务数 (默认 0) */
    @Column(name = "overdue_tasks")
    private Integer overdueTasks;

    /** 完成率 (默认 0) */
    @Column(name = "completion_rate")
    private Double completionRate;

    /** 平均满意度 (默认 0) */
    @Column(name = "avg_satisfaction_score")
    private Double avgSatisfactionScore;

    /** 成功率 (默认 0) */
    @Column(name = "success_rate")
    private Double successRate;

    /** 优先级 (默认 0, 数字越大越优先) */
    @Column(name = "priority")
    private Integer priority;

    /** 标签 (逗号分隔, 可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 是否自动生成任务 (默认 FALSE) */
    @Column(name = "auto_generate", nullable = false)
    private Boolean autoGenerate;

    /** 上次生成任务时间 (可空) */
    @Column(name = "last_generated_at")
    private LocalDateTime lastGeneratedAt;

    /** 下次生成任务时间 (可空) */
    @Column(name = "next_generate_at")
    private LocalDateTime nextGenerateAt;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
