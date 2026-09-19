/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDataDictionaryItemEntity.java
 * Date : 2026/08/05 08:55:12
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * SCRM 数据字典项实体。
 * <p>
 * 维护字典下的具体可选项。{@link #dictId} / {@link #dictCode} 关联所属字典,
 * {@link #parentId} 与 {@link #itemPath} (形如 {@code 1/5/12/}) 支持树形结构,
 * {@link #itemLevel} 标记层级深度。{@link #extraData} 为 TEXT 类型, 承载
 * JSON 扩展数据 (如 abbreviation / isocode / flag 等)。{@link #itemStyle}
 * 限定前端展示样式 (DEFAULT/PRIMARY/SUCCESS/WARNING/DANGER/INFO)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_data_dictionary_item", schema = "scrm",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_data_dictionary_item_code",
                columnNames = {"dict_code", "item_code"}),
        indexes = {
                @Index(name = "idx_data_dictionary_item_dict", columnList = "dict_id"),
                @Index(name = "idx_data_dictionary_item_code", columnList = "dict_code"),
                @Index(name = "idx_data_dictionary_item_parent", columnList = "parent_id"),
                @Index(name = "idx_data_dictionary_item_path", columnList = "dict_code,item_path"),
                @Index(name = "idx_data_dictionary_item_value", columnList = "dict_code,item_value"),
                @Index(name = "idx_data_dictionary_item_enabled", columnList = "enabled")
        })
/**
 * SCRM 数据字典项实体。
 * <p>承载数据字典的下级可选值: 所属字典 (dictId/dictCode)、项标签与值与编码、
 * 多级层级结构 (parentId/itemLevel/itemPath)、排序、展示样式 (itemStyle/color)、
 * 是否启用与是否系统内置、默认值标记、备注。字典编码与项编码组合在唯一。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmDataDictionaryItemEntity {

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

    /** 字典 ID */
    @Column(name = "dict_id", nullable = false)
    private Long dictId;

    /** 字典编码 (关联字典) */
    @Column(name = "dict_code", nullable = false, length = 50)
    private String dictCode;

    /** 显示标签 */
    @Column(name = "item_label", nullable = false, length = 200)
    private String itemLabel;

    /** 字典值 */
    @Column(name = "item_value", nullable = false, length = 500)
    private String itemValue;

    /** 字典项编码 (字典下编码全局唯一, 可空) */
    @Column(name = "item_code", length = 100)
    private String itemCode;

    /** 父项 ID (用于树形, 可空) */
    @Column(name = "parent_id")
    private Long parentId;

    /** 层级 (默认 1, 根项为 1) */
    @Column(name = "item_level")
    private Integer itemLevel;

    /** 路径 (形如 1/5/12/, 可空) */
    @Column(name = "item_path", length = 1000)
    private String itemPath;

    /** 排序值 (默认 0) */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /** 样式: DEFAULT / PRIMARY / SUCCESS / WARNING / DANGER / INFO (可空) */
    @Column(name = "item_style", length = 50)
    private String itemStyle;

    /** 颜色 (可空) */
    @Column(name = "color", length = 20)
    private String color;

    /** 图标 (可空, emoji 或图标类名) */
    @Column(name = "icon", length = 100)
    private String icon;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** JSON 扩展数据 (可空, 如 {abbreviation, isocode, flag, ...}) */
    @Column(name = "extra_data", columnDefinition = "TEXT")
    private String extraData;

    /** 标签 (可空, 逗号分隔) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 是否默认 (默认 false) */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    /** 是否禁用 (默认 false) */
    @Column(name = "is_disabled", nullable = false)
    private Boolean isDisabled;

    /** 是否可见 (默认 true) */
    @Column(name = "is_visible", nullable = false)
    private Boolean isVisible;

    /** 使用次数 (冗余, 用于热度统计) */
    @Column(name = "usage_count")
    private Integer usageCount;

    /** 是否启用 (默认 true) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
