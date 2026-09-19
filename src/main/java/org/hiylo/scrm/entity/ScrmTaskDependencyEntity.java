/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTaskDependencyEntity.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
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
 * SCRM 任务依赖关系实体。
 * <p>
 * 定义任务之间的有向依赖关系, 由 {@code ScrmTaskSchedulerService.checkDependencies}
 * 在调度前校验依赖是否满足, 由 {@code executeDependentTasks} 在父执行完成后按
 * dependencyType (ON_SUCCESS/ON_COMPLETION/ON_FAILURE) 触发后续任务。
 * </p>
 * <p>
 * conditionExpression 支持基于父执行结果的条件表达式 (例如 {@code result.status=='SUCCESS'
 * &amp;&amp; result.durationMs&lt;5000}), 由 Service 层在依赖判定时评估。maxWaitMinutes
 * 定义依赖等待上限, 超时后若 isRequired=TRUE 则取消主任务, 否则跳过依赖。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_task_dependency", schema = "scrm", indexes = {
        @Index(name = "idx_task_dependency_task", columnList = "task_id"),
        @Index(name = "idx_task_dependency_depends_on", columnList = "depends_on_task_id")
})
@Data
public class ScrmTaskDependencyEntity {

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
     * 持久化前回调: 自动填充创建/更新时间、业务字段 createdAt 与版本号初值
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (createdAt == null) {
            createdAt = now;
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

    /** 主任务 ID (引用 scrm_scheduled_task.id) */
    @Column(name = "task_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 主任务编码（快照, 可空） */
    @Column(name = "task_code", length = 50)
    private String taskCode;

    /** 依赖任务 ID (引用 scrm_scheduled_task.id) */
    @Column(name = "depends_on_task_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dependsOnTaskId;

    /** 依赖任务编码（快照, 可空） */
    @Column(name = "depends_on_task_code", length = 50)
    private String dependsOnTaskCode;

    /** 依赖类型: ON_SUCCESS/ON_COMPLETION/ON_FAILURE（默认 ON_SUCCESS） */
    @Column(name = "dependency_type", length = 20, nullable = false)
    private String dependencyType;

    /** 条件表达式（基于父执行结果评估, 可空） */
    @Column(name = "condition_expression", length = 500)
    private String conditionExpression;

    /** 延迟执行秒（默认 0） */
    @Column(name = "delay_seconds")
    private Integer delaySeconds;

    /** 是否必须（默认 TRUE, TRUE 时依赖不满足则取消主任务） */
    @Column(name = "is_required", nullable = false)
    private Boolean isRequired;

    /** 最长等待分钟（默认 60, 超时后按 isRequired 决定取消或跳过） */
    @Column(name = "max_wait_minutes")
    private Integer maxWaitMinutes;

    /** 创建时间（业务字段, 与 createTime 同义, 由 PrePersist 写入） */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
