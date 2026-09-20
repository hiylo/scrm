/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDataTransferLogEntity.java
 * Date : 2026/07/27 02:41:22
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
 * SCRM 数据导入导出日志实体。
 * <p>
 * 记录单条记录在导入/导出过程中的处理轨迹, 包括行号、记录键、操作类型
 * (CREATE/UPDATE/SKIP/FAIL)、字段级错误 (fieldErrors JSON) 与处理状态
 * (SUCCESS/WARNING/ERROR), 用于数据校验失败的精确定位与错误回溯。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_data_transfer_log", schema = "scrm", indexes = {
        @Index(name = "idx_dt_log_task", columnList = "task_id"),
        @Index(name = "idx_dt_log_status", columnList = "status")
})
@Data
public class ScrmDataTransferLogEntity {

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

    /** 乐观锁版本号（并发更新保护，后写入者触发 OptimisticLockException） */
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
        if (processedAt == null) {
            processedAt = now;
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

    /** 关联任务 ID */
    @Column(name = "task_id", nullable = false)
    private Long taskId;

    /** 任务类型: IMPORT/EXPORT */
    @Column(name = "task_type", nullable = false, length = 10)
    private String taskType;

    /** 行号（可空） */
    @Column(name = "row_index")
    private Integer rowIndex;

    /** 记录键（可空, 去重键或主业务键） */
    @Column(name = "record_key", length = 200)
    private String recordKey;

    /** 操作: CREATE/UPDATE/SKIP/FAIL */
    @Column(name = "operation", nullable = false, length = 20)
    private String operation;

    /** 字段错误 JSON（可空） */
    @Column(name = "field_errors", columnDefinition = "TEXT")
    private String fieldErrors;

    /** 消息（可空） */
    @Column(name = "message", length = 500)
    private String message;

    /** 状态: SUCCESS/WARNING/ERROR */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 处理时间 */
    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
