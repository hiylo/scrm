/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationEntity.java
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
 * SCRM 通知记录实体。
 * <p>
 * 记录单条通知的完整生命周期: 来源模板, 渲染后的标题与内容, 接收者 (类型/ID/名称/联系方式),
 * 发送者, 状态 (PENDING/SENDING/SENT/DELIVERED/READ/FAILED/CANCELLED), 优先级, 计划/发送/
 * 送达/已读时间, 错误信息, 重试次数与上限, JSON 附加数据, 关联业务对象。IN_APP 渠道送达即
 * 标记 DELIVERED, 用户阅读后标记 READ。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_notification", schema = "scrm", indexes = {
        @Index(name = "idx_notification_template", columnList = "template_id"),
        @Index(name = "idx_notification_channel", columnList = "channel"),
        @Index(name = "idx_notification_category", columnList = "category"),
        @Index(name = "idx_notification_status", columnList = "status"),
        @Index(name = "idx_notification_recipient", columnList = "recipient_id"),
        @Index(name = "idx_notification_scheduled", columnList = "status,scheduled_at"),
        @Index(name = "idx_notification_sent", columnList = "sent_at")
})
@Data
public class ScrmNotificationEntity {

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

    /** 来源模板 ID (可空, 直接发送时为空) */
    @Column(name = "template_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

    /** 来源模板编码 (可空) */
    @Column(name = "template_code", length = 100)
    private String templateCode;

    /** 通知渠道: IN_APP/EMAIL/SMS/PUSH/WEBHOOK */
    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    /** 分类: SYSTEM/MARKETING/SERVICE/ALERT/REMINDER/VERIFICATION */
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    /** 标题 (渲染后) */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 内容 (渲染后) */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 接收者类型: USER/CUSTOMER/EXTERNAL/ROLE/DEPARTMENT (默认 USER) */
    @Column(name = "recipient_type", nullable = false, length = 20)
    private String recipientType;

    /** 接收者 ID */
    @Column(name = "recipient_id", nullable = false, length = 200)
    private String recipientId;

    /** 接收者名称 (可空) */
    @Column(name = "recipient_name", length = 200)
    private String recipientName;

    /** 接收者联系方式: 手机/邮箱 (可空) */
    @Column(name = "recipient_contact", length = 200)
    private String recipientContact;

    /** 发送者 ID (可空) */
    @Column(name = "sender_id", length = 100)
    private String senderId;

    /** 发送者名称 (可空) */
    @Column(name = "sender_name", length = 100)
    private String senderName;

    /** 状态: PENDING/SENDING/SENT/DELIVERED/READ/FAILED/CANCELLED (默认 PENDING) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 优先级 (默认 0, 数值越大优先级越高) */
    @Column(name = "priority")
    private Integer priority;

    /** 计划发送时间 (可空) */
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    /** 实际发送时间 (可空) */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /** 送达时间 (可空) */
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /** 已读时间 (可空) */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    /** 错误信息 (可空) */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** 重试次数 (默认 0) */
    @Column(name = "retry_count")
    private Integer retryCount;

    /** 最大重试次数 (默认 3) */
    @Column(name = "max_retries")
    private Integer maxRetries;

    /** JSON 附加数据 (可空) */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /** 关联类型 (可空) */
    @Column(name = "related_type", length = 50)
    private String relatedType;

    /** 关联 ID (可空) */
    @Column(name = "related_id", length = 100)
    private String relatedId;
}
