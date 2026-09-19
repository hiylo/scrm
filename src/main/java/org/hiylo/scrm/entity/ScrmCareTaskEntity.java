/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCareTaskEntity.java
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 客户关怀任务实体。
 * <p>
 * 记录针对客户 ({@link #customerId}) 的关怀任务, 可由关怀规则 ({@link #ruleId}) 自动生成
 * 或手动创建 (ruleId 为空)。{@link #careDate} 为关怀日期 (生日/节日/纪念日),
 * {@link #scheduledAt} 为计划执行时间, {@link #status} 标注任务状态
 * (PENDING/EXECUTING/SUCCESS/FAILED/CANCELLED)。
 * </p>
 * <p>
 * 执行后由 {@link #executedAt} / {@link #actionResult} / {@link #errorMessage} 记录执行回执;
 * 客户回应由 {@link #customerResponse} / {@link #responseAt} 跟踪, 用于效果分析。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_care_task", schema = "scrm", indexes = {
        @Index(name = "idx_care_task_rule", columnList = "rule_id"),
        @Index(name = "idx_care_task_customer", columnList = "customer_id"),
        @Index(name = "idx_care_task_care_type", columnList = "care_type"),
        @Index(name = "idx_care_task_status", columnList = "status"),
        @Index(name = "idx_care_task_assignee", columnList = "assignee_id"),
        @Index(name = "idx_care_task_scheduled", columnList = "scheduled_at"),
        @Index(name = "idx_care_task_care_date", columnList = "care_date")
})
@Data
public class ScrmCareTaskEntity {

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

    /** 关联规则 ID (可空, 为空表示手动创建) */
    @Column(name = "rule_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ruleId;

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (冗余, 便于列表展示) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 关怀类型: BIRTHDAY / FESTIVAL / ANNIVERSARY / MEMBERSHIP_EXPIRY / INACTIVITY_REMINDER / CUSTOM */
    @Column(name = "care_type", nullable = false, length = 30)
    private String careType;

    /** 关怀日期 (生日/节日/纪念日等) */
    @Column(name = "care_date", nullable = false)
    private LocalDate careDate;

    /** 计划执行时间 */
    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    /** 关怀动作: SEND_MESSAGE / SEND_COUPON / SEND_GIFT / CALL / CREATE_TASK / NOTIFY_ASSIGNEE */
    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    /** 动作内容 JSON (可空) */
    @Column(name = "action_content", columnDefinition = "TEXT")
    private String actionContent;

    /** 任务状态: PENDING / EXECUTING / SUCCESS / FAILED / CANCELLED (默认 PENDING) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 实际执行时间 (可空) */
    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    /** 动作执行结果 (可空) */
    @Column(name = "action_result", length = 500)
    private String actionResult;

    /** 错误信息 (执行失败时记录, 可空) */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** 负责人 ID (可空) */
    @Column(name = "assignee_id", length = 100)
    private String assigneeId;

    /** 负责人名称 (可空) */
    @Column(name = "assignee_name", length = 100)
    private String assigneeName;

    /** 客户回应 (可空) */
    @Column(name = "customer_response", length = 500)
    private String customerResponse;

    /** 客户回应时间 (可空) */
    @Column(name = "response_at")
    private LocalDateTime responseAt;

    /** 备注 (可空) */
    @Column(name = "notes", length = 500)
    private String notes;
}
