/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmScheduledTaskEntity.java
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
 * SCRM 任务调度实体。
 * <p>
 * 统一任务调度中心的核心配置实体, 定义定时任务 / 固定频率任务 / 固定延迟任务 /
 * 一次性任务 / 事件触发任务的调度规则、处理器路由、超时与重试策略、依赖关系与执行统计。
 * 由 {@code ScrmTaskSchedulerService} 负责调度执行并维护统计字段。
 * </p>
 * <p>
 * taskType 决定调度方式: CRON (cronExpression 解析下次执行时间)、FIXED_RATE (fixedRateMs
 * 固定频率)、FIXED_DELAY (fixedDelayMs 上次执行完成后延迟)、ONE_TIME (executeAt 一次性)、
 * EVENT_TRIGGERED (事件驱动)。失败重试采用指数退避策略 (retryDelaySeconds *
 * retryBackoffMultiplier^retryCount, 上限 1 小时)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_scheduled_task", schema = "scrm", indexes = {
        @Index(name = "idx_scheduled_task_status", columnList = "status"),
        @Index(name = "idx_scheduled_task_category", columnList = "task_category"),
        @Index(name = "idx_scheduled_task_type", columnList = "task_type"),
        @Index(name = "idx_scheduled_task_code", columnList = "task_code"),
        @Index(name = "idx_scheduled_task_next_scheduled", columnList = "next_scheduled_at"),
        @Index(name = "idx_scheduled_task_enabled", columnList = "is_enabled")
})
@Data
public class ScrmScheduledTaskEntity {

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

    /** 任务名称 */
    @Column(name = "task_name", length = 200, nullable = false)
    private String taskName;

    /** 任务编码（唯一标识, 用于依赖引用） */
    @Column(name = "task_code", length = 50, nullable = false, unique = true)
    private String taskCode;

    /** 任务描述（可空） */
    @Column(name = "description", length = 500)
    private String description;

    /** 任务类别: DATA_SYNC/CLEANUP/NOTIFICATION/REPORT/MAINTENANCE/CUSTOM/INTEGRATION/ANALYTICS */
    @Column(name = "task_category", length = 50, nullable = false)
    private String taskCategory;

    /** 任务类型: CRON/FIXED_RATE/FIXED_DELAY/ONE_TIME/EVENT_TRIGGERED */
    @Column(name = "task_type", length = 30, nullable = false)
    private String taskType;

    /** Cron 表达式（taskType=CRON 时必填, 可空） */
    @Column(name = "cron_expression", length = 100)
    private String cronExpression;

    /** 固定频率毫秒（taskType=FIXED_RATE 时必填, 可空） */
    @Column(name = "fixed_rate_ms")
    private Integer fixedRateMs;

    /** 固定延迟毫秒（taskType=FIXED_DELAY 时必填, 可空） */
    @Column(name = "fixed_delay_ms")
    private Integer fixedDelayMs;

    /** 一次性任务执行时间（taskType=ONE_TIME 时必填, 可空） */
    @Column(name = "execute_at")
    private LocalDateTime executeAt;

    /** 处理器类全名 */
    @Column(name = "handler_class", length = 500, nullable = false)
    private String handlerClass;

    /** 处理器方法（默认 execute） */
    @Column(name = "handler_method", length = 100, nullable = false)
    private String handlerMethod;

    /** JSON 任务参数（可空） */
    @Column(name = "parameters", columnDefinition = "TEXT")
    private String parameters;

    /** 超时秒数（默认 300） */
    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;

    /** 最大重试次数（默认 3） */
    @Column(name = "max_retries")
    private Integer maxRetries;

    /** 重试延迟秒（默认 60） */
    @Column(name = "retry_delay_seconds")
    private Integer retryDelaySeconds;

    /** 重试退避倍数（默认 2.0, 指数退避 baseDelay * multiplier^retryCount） */
    @Column(name = "retry_backoff_multiplier")
    private Double retryBackoffMultiplier;

    /** 优先级（默认 0, 数字越大越优先） */
    @Column(name = "priority")
    private Integer priority;

    /** 依赖任务编码（逗号分隔, 可空） */
    @Column(name = "dependencies", length = 500)
    private String dependencies;

    /** 状态: ACTIVE/PAUSED/ERROR/DISABLED（默认 ACTIVE） */
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    /** 最近执行时间（可空） */
    @Column(name = "last_executed_at")
    private LocalDateTime lastExecutedAt;

    /** 上次执行状态: SUCCESS/FAILED/TIMEOUT/RUNNING（可空） */
    @Column(name = "last_execution_status", length = 20)
    private String lastExecutionStatus;

    /** 上次执行耗时（毫秒, 可空） */
    @Column(name = "last_execution_duration_ms")
    private Integer lastExecutionDurationMs;

    /** 最近错误信息（可空） */
    @Column(name = "last_error_message", length = 1000)
    private String lastErrorMessage;

    /** 下次计划执行时间（可空） */
    @Column(name = "next_scheduled_at")
    private LocalDateTime nextScheduledAt;

    /** 累计执行次数（默认 0） */
    @Column(name = "total_executions")
    private Integer totalExecutions;

    /** 累计成功次数（默认 0） */
    @Column(name = "success_count")
    private Integer successCount;

    /** 累计失败次数（默认 0） */
    @Column(name = "failure_count")
    private Integer failureCount;

    /** 累计超时次数（默认 0） */
    @Column(name = "timeout_count")
    private Integer timeoutCount;

    /** 平均执行耗时（毫秒, 默认 0） */
    @Column(name = "avg_execution_ms")
    private Integer avgExecutionMs;

    /** 最近成功时间（可空） */
    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    /** 最近失败时间（可空） */
    @Column(name = "last_failure_at")
    private LocalDateTime lastFailureAt;

    /** 连续失败次数（默认 0, 成功一次重置） */
    @Column(name = "consecutive_failures")
    private Integer consecutiveFailures;

    /** 是否启用（默认 TRUE） */
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled;

    /** 标签（逗号分隔, 可空） */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
