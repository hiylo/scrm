/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApiAppEntity.java
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
 * SCRM 开放API 应用实体。
 * <p>
 * 描述一个接入开放平台的第三方应用: {@link #appType} (INTERNAL/THIRD_PARTY/PARTNER/WEBHOOK)
 * 标识应用类型, {@link #clientId} / {@link #clientSecret} 用于 OAuth2 客户端凭证,
 * {@link #clientSecret} 加密存储。{@link #rateLimitPerMinute} / {@link #rateLimitPerDay}
 * 控制调用频率, {@link #ipWhitelist} 限制来源 IP, {@link #scopes} 声明权限范围。
 * {@link #totalRequestCount} / {@link #todayRequestCount} 记录调用统计。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_api_app", schema = "scrm", indexes = {
        @Index(name = "idx_api_app_type", columnList = "app_type"),
        @Index(name = "idx_api_app_status", columnList = "status"),
        @Index(name = "idx_api_app_client", columnList = "client_id")
})
@Data
public class ScrmApiAppEntity {

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

    /** 应用名称 */
    @Column(name = "app_name", nullable = false, length = 200)
    private String appName;

    /** 应用编码 (唯一) */
    @Column(name = "app_code", nullable = false, length = 100)
    private String appCode;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 应用类型: INTERNAL/THIRD_PARTY/PARTNER/WEBHOOK (默认 THIRD_PARTY) */
    @Column(name = "app_type", nullable = false, length = 20)
    private String appType;

    /** 客户端 ID (唯一, 创建时自动生成) */
    @Column(name = "client_id", nullable = false, length = 200)
    private String clientId;

    /** 客户端密钥 (加密存储, 创建时自动生成) */
    @Column(name = "client_secret", nullable = false, length = 500)
    private String clientSecret;

    /** 回调URL (逗号分隔, 可空) */
    @Column(name = "redirect_uris", length = 1000)
    private String redirectUris;

    /** 权限范围 (逗号分隔, 可空) */
    @Column(name = "scopes", length = 500)
    private String scopes;

    /** 每分钟速率限制 (默认 60) */
    @Column(name = "rate_limit_per_minute")
    private Integer rateLimitPerMinute;

    /** 每日速率限制 (默认 10000) */
    @Column(name = "rate_limit_per_day")
    private Integer rateLimitPerDay;

    /** IP 白名单 (逗号分隔, 可空) */
    @Column(name = "ip_whitelist", length = 500)
    private String ipWhitelist;

    /** 状态: ACTIVE/SUSPENDED/REVOKED (默认 ACTIVE) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 过期时间 (可空) */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** 最后访问时间 (可空) */
    @Column(name = "last_access_at")
    private LocalDateTime lastAccessAt;

    /** 累计请求总数 (默认 0) */
    @Column(name = "total_request_count")
    private Integer totalRequestCount;

    /** 今日请求总数 (默认 0) */
    @Column(name = "today_request_count")
    private Integer todayRequestCount;

    /** 负责人 (可空) */
    @Column(name = "owner_name", length = 100)
    private String ownerName;

    /** 联系邮箱 (可空) */
    @Column(name = "contact_email", length = 200)
    private String contactEmail;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
