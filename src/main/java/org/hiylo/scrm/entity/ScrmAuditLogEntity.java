/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAuditLogEntity.java
 * Date : 2026/07/27 02:41:22
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
 * SCRM 操作审计日志实体。
 * <p>
 * 由 {@code AuditLogAspect} 切面在 Controller 写操作 (POST/PUT/DELETE) 完成后异步写入,
 * 用于记录操作人、操作资源、动作、请求参数、响应结果、执行耗时与异常信息,
 * 支撑安全审计、操作回溯与失败排查。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_audit_log", schema = "scrm", indexes = {
        @Index(name = "idx_audit_user", columnList = "user_id"),
        @Index(name = "idx_audit_resource", columnList = "resource"),
        @Index(name = "idx_audit_operated_at", columnList = "operated_at"),
        @Index(name = "idx_audit_result", columnList = "result")
})
@Data
public class ScrmAuditLogEntity {

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

    /** 乐观锁版本号（并发更新保护，后写入者触发 OptimisticLockException） */
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

    /** 操作人用户 ID (从 X-User-Id 请求头获取, 可空) */
    @Column(name = "user_id", length = 100)
    private String userId;

    /** 操作人用户名 (可空, 后续可从 SecurityContext 或网关注入) */
    @Column(name = "username", length = 100)
    private String username;

    /** 操作的资源标识 (如 scrm_account / scrm_campaign, 取自 @RequirePermission.resource) */
    @Column(name = "resource", nullable = false, length = 50)
    private String resource;

    /** 操作动作 (如 create / update / delete, 取自 @RequirePermission.action) */
    @Column(name = "action", nullable = false, length = 20)
    private String action;

    /** HTTP 方法: POST / PUT / DELETE */
    @Column(name = "method", nullable = false, length = 10)
    private String method;

    /** 请求路径 (如 /scrm/accounts/123) */
    @Column(name = "request_uri", nullable = false, length = 500)
    private String requestUri;

    /** 请求参数 (JSON 格式, 切面层已截断到 2000 字符防止过大) */
    @Column(name = "request_params", columnDefinition = "TEXT")
    private String requestParams;

    /** 响应体 (JSON 格式, 切面层已截断到 2000 字符, 可空) */
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    /** 操作结果: SUCCESS / FAILED */
    @Column(name = "result", nullable = false, length = 20)
    private String result;

    /** 失败时的异常信息 (切面层已截断到 2000 字符, 可空) */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** 执行耗时 (毫秒) */
    @Column(name = "execution_time")
    private Long executionTime;

    /** 客户端 IP (从 X-Forwarded-For 或 RemoteAddr 解析) */
    @Column(name = "client_ip", length = 50)
    private String clientIp;

    /** 操作发生时间 (Controller 方法执行前后取值) */
    @Column(name = "operated_at", nullable = false)
    private LocalDateTime operatedAt;
}
