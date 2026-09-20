/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationBatchEntity.java
 * Date : 2026/07/27 02:41:22
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
 * SCRM 通知批次实体。
 * <p>
 * 描述一次批量发送任务: 批次名称, 来源模板编码, 渠道与分类, 总数与成功/失败/已读计数,
 * 状态 (PENDING/SENDING/COMPLETED/FAILED/CANCELLED), 起止时间, 触发人。
 * {@code ScrmNotificationCenterService.processBatch} 推进批次进度并刷新统计计数。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_notification_batch", schema = "scrm", indexes = {
        @Index(name = "idx_notification_batch_status", columnList = "status"),
        @Index(name = "idx_notification_batch_triggered", columnList = "triggered_by")
})
@Data
public class ScrmNotificationBatchEntity {

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

    /** 批次名称 */
    @Column(name = "batch_name", nullable = false, length = 200)
    private String batchName;

    /** 来源模板编码 (可空) */
    @Column(name = "template_code", length = 100)
    private String templateCode;

    /** 通知渠道: IN_APP/EMAIL/SMS/PUSH/WEBHOOK */
    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    /** 分类: SYSTEM/MARKETING/SERVICE/ALERT/REMINDER/VERIFICATION */
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    /** 总数 (默认 0) */
    @Column(name = "total_count")
    private Integer totalCount;

    /** 已发送数 (默认 0) */
    @Column(name = "sent_count")
    private Integer sentCount;

    /** 成功数 (默认 0) */
    @Column(name = "success_count")
    private Integer successCount;

    /** 失败数 (默认 0) */
    @Column(name = "failed_count")
    private Integer failedCount;

    /** 已读数 (默认 0) */
    @Column(name = "read_count")
    private Integer readCount;

    /** 状态: PENDING/SENDING/COMPLETED/FAILED/CANCELLED (默认 PENDING) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 开始时间 (可空) */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /** 结束时间 (可空) */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 触发人 */
    @Column(name = "triggered_by", nullable = false, length = 100)
    private String triggeredBy;
}
