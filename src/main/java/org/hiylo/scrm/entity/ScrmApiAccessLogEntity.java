/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApiAccessLogEntity.java
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
 * SCRM 开放API 调用日志实体。
 * <p>
 * 记录每次开放API 调用的明细: 请求路径 {@link #endpoint} / 方法 {@link #method} /
 * 来源 IP {@link #requestIp} / 请求参数 {@link #requestParams} (TEXT) / 请求体 {@link #requestBody}
 * (TEXT), 以及响应状态 {@link #responseStatus} / 耗时 {@link #responseTimeMs} / 错误信息。
 * {@link #requestId} 用于全链路追踪, {@link #accessedAt} 记录访问时间。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_api_access_log", schema = "scrm", indexes = {
        @Index(name = "idx_api_access_log_app", columnList = "app_id"),
        @Index(name = "idx_api_access_log_api_key", columnList = "api_key_id"),
        @Index(name = "idx_api_access_log_client", columnList = "client_id"),
        @Index(name = "idx_api_access_log_endpoint", columnList = "endpoint"),
        @Index(name = "idx_api_access_log_method", columnList = "method"),
        @Index(name = "idx_api_access_log_status", columnList = "response_status"),
        @Index(name = "idx_api_access_log_at", columnList = "accessed_at")
})
@Data
public class ScrmApiAccessLogEntity {

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

    /** 所属应用 ID (可空, 公开接口未携带应用上下文时为空) */
    @Column(name = "app_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long appId;

    /** 调用所用密钥 ID (可空) */
    @Column(name = "api_key_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long apiKeyId;

    /** 客户端 ID (可空) */
    @Column(name = "client_id", length = 200)
    private String clientId;

    /** 请求路径 */
    @Column(name = "endpoint", nullable = false, length = 500)
    private String endpoint;

    /** HTTP 方法: GET/POST/PUT/DELETE */
    @Column(name = "method", nullable = false, length = 10)
    private String method;

    /** 请求 IP */
    @Column(name = "request_ip", nullable = false, length = 100)
    private String requestIp;

    /** User-Agent (可空) */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** JSON 请求参数 (可空) */
    @Column(name = "request_params", columnDefinition = "TEXT")
    private String requestParams;

    /** 请求体 (可空) */
    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    /** HTTP 状态码 */
    @Column(name = "response_status", nullable = false)
    private Integer responseStatus;

    /** 响应时间 (毫秒, 可空) */
    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    /** 错误码 (可空) */
    @Column(name = "error_code", length = 50)
    private String errorCode;

    /** 错误信息 (可空) */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** 请求 ID (链路追踪, 可空) */
    @Column(name = "request_id", length = 100)
    private String requestId;

    /** 访问时间 */
    @Column(name = "accessed_at", nullable = false)
    private LocalDateTime accessedAt;
}
