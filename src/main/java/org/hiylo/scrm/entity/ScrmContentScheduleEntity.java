/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContentScheduleEntity.java
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

import java.time.LocalDateTime;

/**
 * SCRM 内容排期实体。
 * <p>
 * 描述内容在指定时间点向多渠道分发的排期任务。{@link #channels} 以逗号分隔存储多渠道,
 * {@link #repeatType} 支持周期性重复 (NONE/DAILY/WEEKLY/MONTHLY), {@link #repeatConfig}
 * 承载 JSON 重复配置 (如每周一的 09:00)。调度器扫描到期排期并触发
 * {@code publishToChannels} 模拟发布。
 * </p>
 * <p>
 * 状态流转: PENDING (待执行) → EXECUTING (执行中) → COMPLETED (已完成) /
 * FAILED (执行失败) / CANCELLED (已取消)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_content_schedule", schema = "scrm", indexes = {
        @Index(name = "idx_content_schedule_content", columnList = "content_id"),
        @Index(name = "idx_content_schedule_status", columnList = "status"),
        @Index(name = "idx_content_schedule_scheduled", columnList = "scheduled_at")
})
@Data
public class ScrmContentScheduleEntity {

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

    /** 内容 ID */
    @Column(name = "content_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contentId;

    /** 排期名称 */
    @Column(name = "schedule_name", nullable = false, length = 200)
    private String scheduleName;

    /** 渠道 (逗号分隔) */
    @Column(name = "channels", nullable = false, length = 500)
    private String channels;

    /** 计划发布时间 */
    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    /** 时区 (默认 Asia/Shanghai) */
    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone;

    /** 重复类型: NONE / DAILY / WEEKLY / MONTHLY (默认 NONE) */
    @Column(name = "repeat_type", nullable = false, length = 20)
    private String repeatType;

    /** 重复配置 JSON (可空) */
    @Column(name = "repeat_config", columnDefinition = "TEXT")
    private String repeatConfig;

    /** 状态: PENDING / EXECUTING / COMPLETED / FAILED / CANCELLED (默认 PENDING) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 执行时间 (可空) */
    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    /** 错误信息 (可空) */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
