/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApiScopeEntity.java
 * Date : 2026/07/29 21:19:51
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
 * SCRM 开放API 权限范围实体。
 * <p>
 * 定义细粒度的访问权限: {@link #scopeName} (如 customer:read) 唯一,
 * {@link #resource} (customer/contact/follow/order) 标识受保护资源,
 * {@link #actions} (read/write/admin) 声明允许的操作集合, {@link #isDefault} 标记是否
 * 在创建应用时默认授予, {@link #enabled} 控制是否可用。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_api_scope", schema = "scrm", indexes = {
        @Index(name = "idx_api_scope_resource", columnList = "resource"),
        @Index(name = "idx_api_scope_enabled", columnList = "enabled"),
        @Index(name = "idx_api_scope_default", columnList = "is_default")
})
@Data
public class ScrmApiScopeEntity {

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

    /** 权限范围名 (唯一, 如 customer:read) */
    @Column(name = "scope_name", nullable = false, length = 100)
    private String scopeName;

    /** 显示名称 */
    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 资源: customer/contact/follow/order 等 */
    @Column(name = "resource", nullable = false, length = 100)
    private String resource;

    /** 允许操作: read/write/admin (逗号分隔) */
    @Column(name = "actions", nullable = false, length = 200)
    private String actions;

    /** 是否默认权限 (默认 FALSE) */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    /** 是否启用 (默认 TRUE) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
