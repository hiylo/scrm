/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmConfigGroupEntity.java
 * Date : 2026/07/27 02:41:22
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
 * SCRM 配置分组实体。
 * <p>
 * 描述配置分组的层级结构、可见性、显示控制与配置数统计, 用于组织系统配置的分组管理。
 * 分组编码 (groupCode) 在同唯一, 通过 parentGroupCode 构建分组树。
 * </p>
 * <p>
 * 环境限定: ALL / DEV / STAGING / PRODUCTION。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_config_group", schema = "scrm", indexes = {
        @Index(name = "idx_config_group_code", columnList = "group_code", unique = true),
        @Index(name = "idx_config_group_parent", columnList = "parent_group_code"),
        @Index(name = "idx_config_group_enabled", columnList = "enabled")
})
@Data
public class ScrmConfigGroupEntity {

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

    /** 分组名称 */
    @Column(name = "group_name", nullable = false, length = 200)
    private String groupName;

    /** 分组编码 (全局唯一) */
    @Column(name = "group_code", nullable = false, length = 100)
    private String groupCode;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 父分组编码 (可空) */
    @Column(name = "parent_group_code", length = 100)
    private String parentGroupCode;

    /** 分组层级 (默认 1) */
    @Column(name = "group_level")
    private Integer groupLevel;

    /** 分组图标 (可空) */
    @Column(name = "group_icon", length = 200)
    private String groupIcon;

    /** 显示顺序 (默认 0) */
    @Column(name = "display_order")
    private Integer displayOrder;

    /** 配置数 (默认 0) */
    @Column(name = "config_count")
    private Integer configCount;

    /** 最近修改时间 (可空) */
    @Column(name = "last_modified_at")
    private LocalDateTime lastModifiedAt;

    /** 是否可见 (默认 TRUE) */
    @Column(name = "is_visible", nullable = false)
    private Boolean isVisible;

    /** 默认展开 (默认 TRUE) */
    @Column(name = "is_expanded", nullable = false)
    private Boolean isExpanded;

    /** 可见角色 (可空) */
    @Column(name = "applicable_roles", length = 500)
    private String applicableRoles;

    /** 适用模块 (可空) */
    @Column(name = "applicable_modules", length = 500)
    private String applicableModules;

    /** 环境限定: ALL/DEV/STAGING/PRODUCTION (默认 ALL) */
    @Column(name = "environment", nullable = false, length = 50)
    private String environment;

    /** 是否启用 (默认 TRUE) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
