/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmExternalContactSyncLogEntity.java
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
 * SCRM 外部联系人同步日志实体。
 * <p>
 * 记录单个外部联系人在某次同步任务中的处理明细, 包括操作类型 (CREATE/UPDATE/DELETE/SKIP/MERGE)、
 * 关联客户、字段变更详情 (JSON) 与处理状态。供同步任务执行结果审计与字段变更追溯使用。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_external_contact_sync_log", schema = "scrm", indexes = {
        @Index(name = "idx_ecs_log_task", columnList = "task_id"),
        @Index(name = "idx_ecs_log_op_type", columnList = "operation_type"),
        @Index(name = "idx_ecs_log_status", columnList = "status"),
        @Index(name = "idx_ecs_log_customer", columnList = "customer_id")
})
@Data
public class ScrmExternalContactSyncLogEntity {

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

    /** 同步任务 ID */
    @Column(name = "task_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 外部联系人 ID */
    @Column(name = "external_contact_id", nullable = false, length = 200)
    private String externalContactId;

    /** 外部联系人名称 (可空) */
    @Column(name = "external_name", length = 200)
    private String externalName;

    /** 外部联系人头像 URL (可空) */
    @Column(name = "external_avatar", length = 500)
    private String externalAvatar;

    /** 操作类型: CREATE / UPDATE / DELETE / SKIP / MERGE */
    @Column(name = "operation_type", nullable = false, length = 20)
    private String operationType;

    /** 关联客户 ID (可空) */
    @Column(name = "customer_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** JSON 字段变更详情 (可空) */
    @Column(name = "field_changes", columnDefinition = "TEXT")
    private String fieldChanges;

    /** 处理状态: SUCCESS / FAILED / SKIPPED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 错误信息 (可空) */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** 处理时间 */
    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
