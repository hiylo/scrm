/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApiKeyEntity.java
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
 * SCRM 开放API 密钥实体。
 * <p>
 * 挂载在 API 应用下的访问密钥: {@link #apiKey} 唯一且创建时自动生成, {@link #keyType}
 * (PERMANENT/TEMPORARY) 区分长效与临时密钥, {@link #allowedIps} 限制来源 IP,
 * {@link #rateLimitPerMinute} 控制调用频率。{@link #lastUsedAt} / {@link #lastUsedIp} /
 * {@link #usageCount} 记录使用情况, {@link #expiresAt} 控制有效期。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_api_key", schema = "scrm", indexes = {
        @Index(name = "idx_api_key_app", columnList = "app_id"),
        @Index(name = "idx_api_key_status", columnList = "status"),
        @Index(name = "idx_api_key_key", columnList = "api_key")
})
@Data
public class ScrmApiKeyEntity {

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

    /** 所属应用 ID */
    @Column(name = "app_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long appId;

    /** 密钥名称 */
    @Column(name = "key_name", nullable = false, length = 200)
    private String keyName;

    /** API 密钥 (唯一, 创建时自动生成) */
    @Column(name = "api_key", nullable = false, length = 500)
    private String apiKey;

    /** 密钥密文 (可空, 用于签名校验) */
    @Column(name = "key_secret", length = 500)
    private String keySecret;

    /** 密钥类型: PERMANENT/TEMPORARY (默认 PERMANENT) */
    @Column(name = "key_type", nullable = false, length = 20)
    private String keyType;

    /** 权限范围 (逗号分隔, 可空) */
    @Column(name = "scopes", length = 500)
    private String scopes;

    /** 允许 IP (逗号分隔, 可空) */
    @Column(name = "allowed_ips", length = 500)
    private String allowedIps;

    /** 每分钟速率限制 (默认 60) */
    @Column(name = "rate_limit_per_minute")
    private Integer rateLimitPerMinute;

    /** 状态: ACTIVE/EXPIRED/REVOKED (默认 ACTIVE) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 过期时间 (可空) */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** 最后使用时间 (可空) */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /** 最后使用 IP (可空) */
    @Column(name = "last_used_ip", length = 100)
    private String lastUsedIp;

    /** 使用次数 (默认 0) */
    @Column(name = "usage_count")
    private Integer usageCount;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
