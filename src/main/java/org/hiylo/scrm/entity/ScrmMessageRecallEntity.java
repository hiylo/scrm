/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageRecallEntity.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 消息撤回实体。
 * <p>
 * 记录消息撤回操作的执行轨迹: 撤回类型 (MANUAL/AUTO/SYSTEM)、撤回原因、撤回状态
 * (SUCCESS/PARTIAL/FAILED/PENDING)、接收者总数与成功/失败撤回数、撤回时间与完成时间、
 * 撤回窗口 (recallWindowMinutes) 与是否在窗口内 (isWithinWindow)。
 * </p>
 * <p>
 * {@link #affectedReaders} (VARCHAR 1000) 以 JSON 数组形式记录已阅读者列表, 用于撤回时
 * 通知已阅读者。撤回执行为模拟实现, 仅更新状态与计数, 不对接实际消息平台。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_message_recall", schema = "scrm", indexes = {
        @Index(name = "idx_msg_recall_tracking", columnList = "message_tracking_id"),
        @Index(name = "idx_msg_recall_message_id", columnList = "message_id"),
        @Index(name = "idx_msg_recall_sender", columnList = "sender_id"),
        @Index(name = "idx_msg_recall_status", columnList = "recall_status"),
        @Index(name = "idx_msg_recall_recalled_at", columnList = "recalled_at")
})
@Data
public class ScrmMessageRecallEntity {

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
     * 持久化前回调: 自动填充创建/更新时间与版本号初值, 并补齐可空字段的默认值
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (version == null) {
            version = 0L;
        }
        if (recallType == null) {
            recallType = "MANUAL";
        }
        if (recallStatus == null) {
            recallStatus = "SUCCESS";
        }
        if (totalRecipients == null) {
            totalRecipients = 0;
        }
        if (successfulRecalls == null) {
            successfulRecalls = 0;
        }
        if (failedRecalls == null) {
            failedRecalls = 0;
        }
        if (recallWindowMinutes == null) {
            recallWindowMinutes = 2;
        }
        if (isWithinWindow == null) {
            isWithinWindow = true;
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

    /** 关联消息跟踪 ID (引用 scrm_message_tracking.id) */
    @Column(name = "message_tracking_id", nullable = false)
    private Long messageTrackingId;

    /** 消息 ID (冗余, 便于直接按 messageId 查询) */
    @Column(name = "message_id", nullable = false, length = 200)
    private String messageId;

    /** 撤回发起者 ID */
    @Column(name = "sender_id", nullable = false, length = 100)
    private String senderId;

    /** 撤回发起者名称 (可空) */
    @Column(name = "sender_name", length = 100)
    private String senderName;

    /** 撤回类型: MANUAL/AUTO/SYSTEM (默认 MANUAL) */
    @Column(name = "recall_type", nullable = false, length = 20)
    private String recallType;

    /** 撤回原因 (可空) */
    @Column(name = "recall_reason", length = 500)
    private String recallReason;

    /** 撤回状态: SUCCESS/PARTIAL/FAILED/PENDING (默认 SUCCESS) */
    @Column(name = "recall_status", nullable = false, length = 20)
    private String recallStatus;

    /** 总接收者数 (默认 0) */
    @Column(name = "total_recipients")
    private Integer totalRecipients;

    /** 成功撤回数 (默认 0) */
    @Column(name = "successful_recalls")
    private Integer successfulRecalls;

    /** 失败撤回数 (默认 0) */
    @Column(name = "failed_recalls")
    private Integer failedRecalls;

    /** 撤回时间 */
    @Column(name = "recalled_at", nullable = false)
    private LocalDateTime recalledAt;

    /** 撤回完成时间 (可空) */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 撤回窗口分钟 (默认 2) */
    @Column(name = "recall_window_minutes")
    private Integer recallWindowMinutes;

    /** 是否在撤回窗口内 (默认 true) */
    @Column(name = "is_within_window", nullable = false)
    private Boolean isWithinWindow;

    /** 失败原因 (可空) */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /** 已阅读者列表 JSON (可空, [{readerId, readerName, readAt}]) */
    @Column(name = "affected_readers", length = 1000)
    private String affectedReaders;

    /** 备注 (可空) */
    @Column(name = "notes", length = 500)
    private String notes;
}
