/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWebhookConfigEntity.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM Webhook 配置实体。
 * <p>
 * 描述外部系统事件订阅: 配置订阅事件类型、目标 URL、签名密钥与重试策略,
 * 事件发生时自动 POST 通知到目标 URL。状态流转: ACTIVE / INACTIVE / ERROR。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_webhook_config", schema = "scrm", indexes = {
        @Index(name = "idx_webhook_config_status", columnList = "status"),
        @Index(name = "idx_webhook_config_last_trigger", columnList = "last_trigger_at")
})
@Data
public class ScrmWebhookConfigEntity {

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

    /** Webhook 名称 */
    @Column(name = "webhook_name", nullable = false, length = 200)
    private String webhookName;

    /** 接收 URL */
    @Column(name = "target_url", nullable = false, length = 500)
    private String targetUrl;

    /** 签名密钥（可空, 用于 HMAC-SHA256 签名验证） */
    @Column(name = "secret", length = 200)
    private String secret;

    /** 订阅事件类型列表 JSON (如 ["CUSTOMER_CREATED", "MESSAGE_RECEIVED"]) */
    @Column(name = "subscribed_events", columnDefinition = "TEXT", nullable = false)
    private String subscribedEvents;

    /** 事件过滤条件 JSON（可空, 用于细粒度过滤） */
    @Column(name = "event_filter", columnDefinition = "TEXT")
    private String eventFilter;

    /** HTTP 方法, 默认 POST */
    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    /** 自定义 HTTP 头 JSON（可空, 如 {"X-Custom":"value"}） */
    @Column(name = "headers", columnDefinition = "TEXT")
    private String headers;

    /** 请求超时秒数 */
    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;

    /** 最大重试次数 */
    @Column(name = "max_retries")
    private Integer maxRetries;

    /** 重试间隔秒数 */
    @Column(name = "retry_interval_seconds")
    private Integer retryIntervalSeconds;

    /** 状态: ACTIVE / INACTIVE / ERROR */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 最近触发时间（可空） */
    @Column(name = "last_trigger_at")
    private LocalDateTime lastTriggerAt;

    /** 最近一次响应码（可空） */
    @Column(name = "last_status_code")
    private Integer lastStatusCode;

    /** 最近一次错误信息（可空） */
    @Column(name = "last_error", length = 500)
    private String lastError;

    /** 成功推送次数 */
    @Column(name = "success_count")
    private Integer successCount;

    /** 失败推送次数 */
    @Column(name = "fail_count")
    private Integer failCount;

    /** 创建人（可空） */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
