/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFollowUpTaskEntity.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.hibernate.annotations.GenericGenerator;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 跟进任务实体。
 * <p>
 * 描述销售跟进任务, 关联客户与负责人, 含计划时间/优先级/提醒分钟数, 支持状态流转
 * (PENDING/IN_PROGRESS/COMPLETED/CANCELLED/OVERDUE)。taskType 决定跟进方式:
 * CALL/MESSAGE/VISIT/EMAIL/MEETING/OTHER。任务完成后记录 followUpResult 与 nextFollowUpAt。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_follow_up_task", schema = "scrm", indexes = {
        @Index(name = "idx_follow_up_task_customer", columnList = "customer_id"),
        @Index(name = "idx_follow_up_task_assignee", columnList = "assignee_id"),
        @Index(name = "idx_follow_up_task_status", columnList = "status"),
        @Index(name = "idx_follow_up_task_planned_at", columnList = "planned_at"),
        @Index(name = "idx_follow_up_task_reminder", columnList = "reminded,planned_at")
})
@Data
public class ScrmFollowUpTaskEntity {

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

    /** 关联客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 冗余客户昵称 */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 关联账号 ID（可空） */
    @Column(name = "account_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 负责人 userId */
    @Column(name = "assignee_id", nullable = false, length = 100)
    private String assigneeId;

    /** 负责人名称（冗余） */
    @Column(name = "assignee_name", length = 100)
    private String assigneeName;

    /** 跟进类型: CALL/MESSAGE/VISIT/EMAIL/MEETING/OTHER */
    @Column(name = "task_type", nullable = false, length = 30)
    private String taskType;

    /** 任务标题 */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 跟进内容/备注 */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** 计划跟进时间 */
    @Column(name = "planned_at", nullable = false)
    private LocalDateTime plannedAt;

    /** 完成时间 */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 状态: PENDING/IN_PROGRESS/COMPLETED/CANCELLED/OVERDUE */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 优先级: HIGH/MEDIUM/LOW */
    @Column(name = "priority", nullable = false, length = 10)
    private String priority;

    /** 提前提醒分钟数 */
    @Column(name = "reminder_minutes")
    private Integer reminderMinutes;

    /** 是否已提醒 */
    @Column(name = "reminded", nullable = false)
    private Boolean reminded;

    /** 跟进结果 */
    @Column(name = "follow_up_result", length = 500)
    private String followUpResult;

    /** 下次跟进时间 */
    @Column(name = "next_follow_up_at")
    private LocalDateTime nextFollowUpAt;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
