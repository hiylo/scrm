/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDataDictionaryEntity.java
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * SCRM 数据字典实体。
 * <p>
 * 维护系统数据字典的元信息: 字典编码 / 类型 (LIST/TREE/CASCADE/MULTI_LEVEL) /
 * 分类 (SYSTEM/BUSINESS/CUSTOM/INDUSTRY/REGION) / 所属模块 / 适用场景 / 缓存策略 /
 * 排序 / 启用状态 / 引用次数等。{@link #dictCode} 在唯一, {@link #parentId}
 * 支持级联字典, {@link #itemCount} 冗余存储字典项数量供快速检索。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_data_dictionary", schema = "scrm",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_data_dictionary_code",
                columnNames = {"dict_code"}),
        indexes = {
                @Index(name = "idx_data_dictionary_code", columnList = "dict_code"),
                @Index(name = "idx_data_dictionary_type", columnList = "dict_type"),
                @Index(name = "idx_data_dictionary_category", columnList = "category"),
                @Index(name = "idx_data_dictionary_module", columnList = "module"),
                @Index(name = "idx_data_dictionary_parent", columnList = "parent_id"),
                @Index(name = "idx_data_dictionary_enabled", columnList = "enabled")
        })
/**
 * SCRM 数据字典实体。
 * <p>定义可复用的字典分类: 字典名称与编码、描述、字典类型与业务分类、
 * 所属模块与适用场景、父级字典 (parentId)、字典项数量、是否系统内置、
 * 是否启用与排序、备注。字典编码 (dictCode) 唯一。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmDataDictionaryEntity {

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

    /** 字典名称 */
    @Column(name = "dict_name", nullable = false, length = 200)
    private String dictName;

    /** 字典编码 (唯一) */
    @Column(name = "dict_code", nullable = false, length = 50)
    private String dictCode;

    /** 字典描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 字典类型: LIST / TREE / CASCADE / MULTI_LEVEL (默认 LIST) */
    @Column(name = "dict_type", nullable = false, length = 30)
    private String dictType;

    /** 字典分类: SYSTEM / BUSINESS / CUSTOM / INDUSTRY / REGION (可空) */
    @Column(name = "category", length = 100)
    private String category;

    /** 父字典 ID (用于级联字典, 可空) */
    @Column(name = "parent_id")
    private Long parentId;

    /** 所属模块 (可空) */
    @Column(name = "module", length = 100)
    private String module;

    /** 适用场景 (可空) */
    @Column(name = "applicable_scenarios", length = 500)
    private String applicableScenarios;

    /** 字典项数量 (冗余, 便于检索) */
    @Column(name = "item_count")
    private Integer itemCount;

    /** 是否系统内置 (默认 false) */
    @Column(name = "is_system", nullable = false)
    private Boolean isSystem;

    /** 是否可缓存 (默认 true) */
    @Column(name = "is_cacheable", nullable = false)
    private Boolean isCacheable;

    /** 缓存 TTL 秒数 (默认 3600) */
    @Column(name = "cache_ttl_seconds")
    private Integer cacheTtlSeconds;

    /** 排序值 (默认 0, 数字越小越靠前) */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /** 是否启用 (默认 true) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 引用次数 (冗余, 用于热度统计) */
    @Column(name = "usage_count")
    private Integer usageCount;

    /** 最近使用时间 (可空) */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
