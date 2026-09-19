/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTrackingEntity.java
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
 * SCRM 消息跟踪实体。
 * <p>
 * 记录消息发送后的全生命周期跟踪: 发送状态 (PENDING/SENT/DELIVERED/FAILED/CANCELLED)、
 * 送达与阅读状态 (firstReadAt/lastReadAt/readCount/isRead)、撤回管理
 * (isRecalled/recalledAt/recallReason)、转发追踪 (isForwarded/forwardCount/firstForwardedAt)、
 * 回复追踪 (isReplied/repliedAt/replyContent) 与互动评分 (engagementScore)。
 * </p>
 * <p>
 * {@link #messageId} 为业务唯一标识 (VARCHAR 200 NOT_NULL UNIQUE), 由应用层保证全局唯一,
 * 渠道 (channel) 覆盖 WECHAT/WORK_WECHAT/SMS/EMAIL/APP_PUSH/WEB_SOCKET 等多触点。
 * {@link #metadata} (TEXT JSON) 承载附加数据 (转发链/回复历史/扩展字段)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_message_tracking", schema = "scrm", indexes = {
        @Index(name = "idx_msg_tracking_message_id", columnList = "message_id"),
        @Index(name = "idx_msg_tracking_batch", columnList = "batch_id"),
        @Index(name = "idx_msg_tracking_sender", columnList = "sender_id"),
        @Index(name = "idx_msg_tracking_recipient", columnList = "recipient_id"),
        @Index(name = "idx_msg_tracking_channel", columnList = "channel"),
        @Index(name = "idx_msg_tracking_send_status", columnList = "send_status"),
        @Index(name = "idx_msg_tracking_is_read", columnList = "is_read"),
        @Index(name = "idx_msg_tracking_is_recalled", columnList = "is_recalled"),
        @Index(name = "idx_msg_tracking_sent_at", columnList = "sent_at")
})
@Data
public class ScrmMessageTrackingEntity {

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
        if (recipientType == null) {
            recipientType = "CUSTOMER";
        }
        if (contentType == null) {
            contentType = "TEXT";
        }
        if (sendStatus == null) {
            sendStatus = "PENDING";
        }
        if (readCount == null) {
            readCount = 0;
        }
        if (isRead == null) {
            isRead = false;
        }
        if (isRecalled == null) {
            isRecalled = false;
        }
        if (forwardCount == null) {
            forwardCount = 0;
        }
        if (isForwarded == null) {
            isForwarded = false;
        }
        if (isReplied == null) {
            isReplied = false;
        }
        if (engagementScore == null) {
            engagementScore = 0.0;
        }
        if (readDurationSeconds == null) {
            readDurationSeconds = 0;
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

    /** 消息 ID (业务唯一, 由应用层保证全局唯一) */
    @Column(name = "message_id", nullable = false, length = 200, unique = true)
    private String messageId;

    /** 批次 ID (可空, 群发/批量发送时关联批次) */
    @Column(name = "batch_id")
    private Long batchId;

    /** 发送者 ID */
    @Column(name = "sender_id", nullable = false, length = 100)
    private String senderId;

    /** 发送者名称 (可空) */
    @Column(name = "sender_name", length = 100)
    private String senderName;

    /** 接收者 ID / 客户 ID */
    @Column(name = "recipient_id", nullable = false, length = 200)
    private String recipientId;

    /** 接收者名称 (可空) */
    @Column(name = "recipient_name", length = 200)
    private String recipientName;

    /** 接收者类型: CUSTOMER/GROUP/EXTERNAL (默认 CUSTOMER) */
    @Column(name = "recipient_type", nullable = false, length = 20)
    private String recipientType;

    /** 渠道: WECHAT/WORK_WECHAT/SMS/EMAIL/APP_PUSH/WEB_SOCKET */
    @Column(name = "channel", nullable = false, length = 30)
    private String channel;

    /** 消息内容摘要 (可空) */
    @Column(name = "message_content", length = 2000)
    private String messageContent;

    /** 内容类型: TEXT/IMAGE/VIDEO/FILE/LINK/CARD/HTML (默认 TEXT) */
    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    /** 发送状态: PENDING/SENT/DELIVERED/FAILED/CANCELLED (默认 PENDING) */
    @Column(name = "send_status", nullable = false, length = 20)
    private String sendStatus;

    /** 发送时间 (可空) */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /** 送达时间 (可空) */
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /** 首次阅读时间 (可空) */
    @Column(name = "first_read_at")
    private LocalDateTime firstReadAt;

    /** 最后阅读时间 (可空) */
    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    /** 阅读次数 (默认 0) */
    @Column(name = "read_count")
    private Integer readCount;

    /** 是否已读 (默认 false) */
    @Column(name = "is_read", nullable = false)
    private Boolean isRead;

    /** 是否已撤回 (默认 false) */
    @Column(name = "is_recalled", nullable = false)
    private Boolean isRecalled;

    /** 撤回时间 (可空) */
    @Column(name = "recalled_at")
    private LocalDateTime recalledAt;

    /** 撤回原因 (可空) */
    @Column(name = "recall_reason", length = 500)
    private String recallReason;

    /** 是否被转发 (默认 false) */
    @Column(name = "is_forwarded", nullable = false)
    private Boolean isForwarded;

    /** 转发次数 (默认 0) */
    @Column(name = "forward_count")
    private Integer forwardCount;

    /** 首次转发时间 (可空) */
    @Column(name = "first_forwarded_at")
    private LocalDateTime firstForwardedAt;

    /** 是否已回复 (默认 false) */
    @Column(name = "is_replied", nullable = false)
    private Boolean isReplied;

    /** 回复时间 (可空) */
    @Column(name = "replied_at")
    private LocalDateTime repliedAt;

    /** 回复摘要 (可空) */
    @Column(name = "reply_content", length = 500)
    private String replyContent;

    /** 互动评分 (默认 0) */
    @Column(name = "engagement_score")
    private Double engagementScore;

    /** 客户端 IP (可空) */
    @Column(name = "client_ip", length = 100)
    private String clientIp;

    /** 设备类型 (可空) */
    @Column(name = "device_type", length = 30)
    private String deviceType;

    /** 阅读时长秒 (默认 0) */
    @Column(name = "read_duration_seconds")
    private Integer readDurationSeconds;

    /** JSON 附加数据 (可空): {forwardChain, replyHistory, ...} */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /** 错误信息 (可空) */
    @Column(name = "error_message", length = 500)
    private String errorMessage;
}
