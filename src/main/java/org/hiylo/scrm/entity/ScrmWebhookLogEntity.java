/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWebhookLogEntity.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM Webhook 推送日志实体。
 * <p>
 * 记录每次事件推送到外部系统的执行明细: 关联的 Webhook 配置、事件类型、事件负载、
 * 实际发送的请求体、HTTP 响应、状态与重试信息。
 * 状态流转: PENDING → SENDING → SUCCESS / FAILED / RETRY / EXPIRED。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_webhook_log", schema = "scrm", indexes = {
        @Index(name = "idx_webhook_log_webhook_id", columnList = "webhook_id"),
        @Index(name = "idx_webhook_log_event_type", columnList = "event_type"),
        @Index(name = "idx_webhook_log_status", columnList = "status"),
        @Index(name = "idx_webhook_log_next_retry", columnList = "next_retry_at")
})
@Data
public class ScrmWebhookLogEntity {

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

    /** Webhook 配置 ID (引用 scrm_webhook_config.id) */
    @Column(name = "webhook_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long webhookId;

    /** 事件类型 */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /** 事件唯一 ID */
    @Column(name = "event_id", nullable = false, length = 200)
    private String eventId;

    /** 事件负载 JSON */
    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    /** 实际发送的请求体（可空, 发送后填充） */
    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    /** HTTP 响应码（可空） */
    @Column(name = "response_status")
    private Integer responseStatus;

    /** 响应体摘要（可空, 截取前 2000 字符） */
    @Column(name = "response_body", length = 2000)
    private String responseBody;

    /** 状态: PENDING / SENDING / SUCCESS / FAILED / RETRY / EXPIRED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 已尝试次数 */
    @Column(name = "attempt_count")
    private Integer attemptCount;

    /** 最大尝试次数 */
    @Column(name = "max_attempts")
    private Integer maxAttempts;

    /** 下次重试时间（可空, RETRY 状态下到期由 processRetries 捞取） */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    /** 实际发送时间（可空） */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /** 完成时间（可空, 成功或最终失败时填充） */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 错误信息（可空） */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** 耗时毫秒（可空） */
    @Column(name = "duration_ms")
    private Integer durationMs;
}
