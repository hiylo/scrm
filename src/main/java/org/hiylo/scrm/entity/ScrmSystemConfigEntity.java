/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSystemConfigEntity.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 系统配置实体。
 * <p>
 * 描述一项系统配置的定义与值, 包括键值、分组、类型、校验规则、UI 渲染元数据、
 * 依赖关系、缓存策略与变更统计, 用于系统参数与配置管理。配置键 (configKey)
 * 在同唯一, 配置类型 (configType) 标识值的语义类型, 环境限定 (environment)
 * 控制配置生效范围。
 * </p>
 * <p>
 * 配置类型: STRING / INTEGER / DOUBLE / BOOLEAN / JSON / XML / DATE / TIME /
 * DATETIME / ENUM / PASSWORD / ENCRYPTED / FILE / URL / EMAIL / PHONE / COLOR / RICH_TEXT。
 * 环境限定: ALL / DEV / STAGING / PRODUCTION。
 * UI 组件: INPUT / TEXTAREA / SELECT / MULTI_SELECT / RADIO / CHECKBOX / SWITCH /
 * SLIDER / DATE_PICKER / TIME_PICKER / COLOR_PICKER / FILE_UPLOAD / RICH_EDITOR /
 * CODE_EDITOR / JSON_EDITOR。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_system_config", schema = "scrm", indexes = {
        @Index(name = "idx_system_config_key", columnList = "config_key", unique = true),
        @Index(name = "idx_system_config_group", columnList = "config_group"),
        @Index(name = "idx_system_config_type", columnList = "config_type"),
        @Index(name = "idx_system_config_env", columnList = "environment"),
        @Index(name = "idx_system_config_enabled", columnList = "enabled"),
        @Index(name = "idx_system_config_sensitive", columnList = "is_sensitive"),
        @Index(name = "idx_system_config_overridable", columnList = "is_overridable")
})
@Data
public class ScrmSystemConfigEntity {

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

    /** 配置键 (全局唯一) */
    @Column(name = "config_key", nullable = false, length = 200)
    private String configKey;

    /** 配置值 (可空, TEXT) */
    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    /** 默认值 (可空, TEXT) */
    @Column(name = "default_value", columnDefinition = "TEXT")
    private String defaultValue;

    /** 配置名称 */
    @Column(name = "config_name", nullable = false, length = 200)
    private String configName;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 配置分组 (默认 GENERAL) */
    @Column(name = "config_group", nullable = false, length = 100)
    private String configGroup;

    /*
     * 配置类型:
     * STRING/INTEGER/DOUBLE/BOOLEAN/JSON/XML/DATE/TIME/DATETIME/ENUM/PASSWORD/ENCRYPTED/
     * FILE/URL/EMAIL/PHONE/COLOR/RICH_TEXT
     */
    @Column(name = "config_type", nullable = false, length = 30)
    private String configType;

    /** 数据类型 (可空) */
    @Column(name = "data_type", length = 50)
    private String dataType;

    /** 枚举选项 JSON: [{label,value}] (可空) */
    @Column(name = "enum_options", length = 1000)
    private String enumOptions;

    /** 验证正则 (可空) */
    @Column(name = "validation_regex", length = 500)
    private String validationRegex;

    /** 验证提示 (可空) */
    @Column(name = "validation_message", length = 500)
    private String validationMessage;

    /** 最小值 (可空) */
    @Column(name = "min_value")
    private Double minValue;

    /** 最大值 (可空) */
    @Column(name = "max_value")
    private Double maxValue;

    /** 最大长度 (可空) */
    @Column(name = "max_length")
    private Integer maxLength;

    /** 是否必填 (默认 FALSE) */
    @Column(name = "is_required", nullable = false)
    private Boolean isRequired;

    /** 是否只读 (默认 FALSE) */
    @Column(name = "is_read_only", nullable = false)
    private Boolean isReadOnly;

    /** 是否加密 (默认 FALSE) */
    @Column(name = "is_encrypted", nullable = false)
    private Boolean isEncrypted;

    /** 是否敏感信息 (默认 FALSE) */
    @Column(name = "is_sensitive", nullable = false)
    private Boolean isSensitive;

    /** 是否系统级 (默认 FALSE, 系统级不可删) */
    @Column(name = "is_system", nullable = false)
    private Boolean isSystem;

    /** 是否可见 (默认 TRUE) */
    @Column(name = "is_visible", nullable = false)
    private Boolean isVisible;

    /** 是否可搜索 (默认 FALSE) */
    @Column(name = "is_searchable", nullable = false)
    private Boolean isSearchable;

    /** 显示顺序 (默认 0) */
    @Column(name = "display_order")
    private Integer displayOrder;

    /** 帮助文本 (可空) */
    @Column(name = "help_text", length = 1000)
    private String helpText;

    /** 占位提示 (可空) */
    @Column(name = "placeholder", length = 500)
    private String placeholder;

    /*
     * UI 组件:
     * INPUT/TEXTAREA/SELECT/MULTI_SELECT/RADIO/CHECKBOX/SWITCH/SLIDER/DATE_PICKER/TIME_PICKER/
     * COLOR_PICKER/FILE_UPLOAD/RICH_EDITOR/CODE_EDITOR/JSON_EDITOR
     * (可空)
     */
    @Column(name = "ui_component", length = 50)
    private String uiComponent;

    /** UI 属性 JSON (可空, TEXT) */
    @Column(name = "ui_props", columnDefinition = "TEXT")
    private String uiProps;

    /** 依赖配置键 (可空) */
    @Column(name = "depends_on", length = 200)
    private String dependsOn;

    /** 依赖条件 (可空) */
    @Column(name = "dependency_condition", length = 500)
    private String dependencyCondition;

    /** 适用模块 (可空) */
    @Column(name = "applicable_modules", length = 500)
    private String applicableModules;

    /** 可见角色 (可空) */
    @Column(name = "applicable_roles", length = 500)
    private String applicableRoles;

    /** 环境限定: ALL/DEV/STAGING/PRODUCTION (默认 ALL) */
    @Column(name = "environment", nullable = false, length = 50)
    private String environment;

    /** 账号可覆盖 (默认 TRUE) */
    @Column(name = "is_overridable", nullable = false)
    private Boolean isOverridable;

    /** 可缓存 (默认 TRUE) */
    @Column(name = "is_cachable", nullable = false)
    private Boolean isCachable;

    /** 缓存 TTL 秒 (默认 300) */
    @Column(name = "cache_ttl_seconds")
    private Integer cacheTtlSeconds;

    /** 变更次数 (默认 0) */
    @Column(name = "change_count")
    private Integer changeCount;

    /** 最近变更时间 (可空) */
    @Column(name = "last_changed_at")
    private LocalDateTime lastChangedAt;

    /** 最近变更人 (可空) */
    @Column(name = "last_changed_by", length = 100)
    private String lastChangedBy;

    /** 是否启用 (默认 TRUE) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 标签 (可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
