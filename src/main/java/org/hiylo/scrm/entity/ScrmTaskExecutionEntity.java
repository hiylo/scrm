/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTaskExecutionEntity.java
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
 * SCRM 任务执行记录实体。
 * <p>
 * 记录每次任务调度的执行轨迹, 包括触发类型 (定时/手动/重试/依赖/事件)、执行状态
 * (PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT/CANCELLED/SKIPPED)、起止时间、执行耗时、
 * 执行节点、本次参数与执行结果、错误堆栈、重试信息与执行进度。
 * 由 {@code ScrmTaskSchedulerService.executeTask} 在每次调度时创建并维护状态流转。
 * </p>
 * <p>
 * 支持进度上报 ({@code progress} 0-100 与 {@code progressMessage})、超时检测
 * (超过 scheduledTask.timeoutSeconds 后由 {@code handleTimeout} 标记 TIMEOUT) 与
 * 重试链路追踪 ({@code retryCount} / {@code retryExecutionId} 串联重试执行)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_task_execution", schema = "scrm", indexes = {
        @Index(name = "idx_task_execution_task", columnList = "task_id"),
        @Index(name = "idx_task_execution_status", columnList = "status"),
        @Index(name = "idx_task_execution_trigger", columnList = "trigger_type"),
        @Index(name = "idx_task_execution_no", columnList = "execution_no"),
        @Index(name = "idx_task_execution_started", columnList = "started_at")
})
@Data
public class ScrmTaskExecutionEntity {

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

    /** 任务 ID (引用 scrm_scheduled_task.id) */
    @Column(name = "task_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 任务名称（执行时快照, 可空） */
    @Column(name = "task_name", length = 200)
    private String taskName;

    /** 任务编码（执行时快照, 可空） */
    @Column(name = "task_code", length = 50)
    private String taskCode;

    /** 执行编号（唯一, 用于外部追踪） */
    @Column(name = "execution_no", length = 100, unique = true)
    private String executionNo;

    /** 触发类型: SCHEDULED/MANUAL/RETRY/DEPENDENCY/EVENT（默认 SCHEDULED） */
    @Column(name = "trigger_type", length = 20, nullable = false)
    private String triggerType;

    /** 执行状态: PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT/CANCELLED/SKIPPED（默认 PENDING） */
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    /** 计划执行时间（可空） */
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    /** 实际开始时间（可空） */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** 完成时间（可空） */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 执行时长毫秒（可空） */
    @Column(name = "duration_ms")
    private Integer durationMs;

    /** 执行节点 ID（可空） */
    @Column(name = "worker_id", length = 100)
    private String workerId;

    /** 执行节点名称（可空） */
    @Column(name = "worker_name", length = 100)
    private String workerName;

    /** JSON 本次参数（覆盖任务默认参数, 可空） */
    @Column(name = "parameters", columnDefinition = "TEXT")
    private String parameters;

    /** JSON 执行结果（可空） */
    @Column(name = "result", columnDefinition = "TEXT")
    private String result;

    /** 返回值（可空, 2KB 上限） */
    @Column(name = "return_value", length = 2000)
    private String returnValue;

    /** 执行日志（可空） */
    @Column(name = "output_logs", columnDefinition = "TEXT")
    private String outputLogs;

    /** 错误信息（可空, 2KB 上限） */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /** 错误堆栈（可空） */
    @Column(name = "error_stack", columnDefinition = "TEXT")
    private String errorStack;

    /** 当前重试次数（默认 0） */
    @Column(name = "retry_count")
    private Integer retryCount;

    /** 最大重试次数（默认 3, 快照自任务配置） */
    @Column(name = "max_retries")
    private Integer maxRetries;

    /** 下次重试时间（可空） */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    /** 触发者（可空） */
    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    /** 触发者名称（可空） */
    @Column(name = "triggered_by_name", length = 100)
    private String triggeredByName;

    /** 依赖的执行 ID（可空, triggerType=DEPENDENCY 时关联父执行） */
    @Column(name = "dependency_execution_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dependencyExecutionId;

    /** 是否为重试执行（默认 FALSE） */
    @Column(name = "is_retried", nullable = false)
    private Boolean isRetried;

    /** 重试执行 ID（可空, 指向新创建的重试执行记录） */
    @Column(name = "retry_execution_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long retryExecutionId;

    /** 执行进度 0-100（默认 0） */
    @Column(name = "progress")
    private Integer progress;

    /** 进度消息（可空） */
    @Column(name = "progress_message", length = 500)
    private String progressMessage;

    /** JSON 附加数据（可空） */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}
