/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmExternalContactSyncTaskEntity.java
 * Date : 2026/08/04 08:40:58
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
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * SCRM 外部联系人同步任务实体。
 * <p>
 * 描述一次外部联系人同步任务的执行记录, 关联 {@link ScrmExternalContactSyncConfigEntity},
 * 记录任务状态、起止时间、耗时、记录数与新增/更新/失败/跳过等统计。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_external_contact_sync_task", schema = "scrm", indexes = {
        @Index(name = "idx_ecs_task_config", columnList = "config_id"),
        @Index(name = "idx_ecs_task_status", columnList = "status"),
        @Index(name = "idx_ecs_task_start", columnList = "start_time"),
        @Index(name = "idx_ecs_task_triggered_by", columnList = "triggered_by")
})
@Data
public class ScrmExternalContactSyncTaskEntity {

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

    /** 同步配置 ID */
    @Column(name = "config_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long configId;

    /** 任务名称 */
    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    /** 同步模式: INCREMENTAL / FULL */
    @Column(name = "sync_mode", nullable = false, length = 20)
    private String syncMode;

    /** 任务状态: PENDING / RUNNING / SUCCESS / FAILED / CANCELLED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 任务开始时间 (可空) */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /** 任务结束时间 (可空) */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 任务耗时毫秒 (可空) */
    @Column(name = "duration_ms")
    private Integer durationMs;

    /** 总记录数 */
    @Column(name = "total_records")
    private Integer totalRecords;

    /** 成功数 */
    @Column(name = "success_count")
    private Integer successCount;

    /** 失败数 */
    @Column(name = "failed_count")
    private Integer failedCount;

    /** 新增数 */
    @Column(name = "new_count")
    private Integer newCount;

    /** 更新数 */
    @Column(name = "update_count")
    private Integer updateCount;

    /** 跳过数 */
    @Column(name = "skip_count")
    private Integer skipCount;

    /** 错误信息 (可空) */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /** 触发者 */
    @Column(name = "triggered_by", nullable = false, length = 100)
    private String triggeredBy;

    /** 触发者类型: MANUAL / SCHEDULED / SYSTEM */
    @Column(name = "triggered_by_type", nullable = false, length = 20)
    private String triggeredByType;
}
