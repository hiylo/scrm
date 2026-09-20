/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAssetCategoryEntity.java
 * Date : 2026/07/27 02:41:22
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
 * SCRM 营销素材分类实体。
 * <p>
 * 支持多级父子分类组织营销素材: {@code categoryCode} 在唯一, {@code parentId} 为空表示
 * 顶级分类, {@code categoryPath} 冗余存储分类路径便于检索, {@code assetCount} /
 * {@code totalSizeBytes} 冗余存储分类下素材数与总大小, {@code enabled} 控制分类是否可见,
 * {@code visibleToRoles} 限定可见角色 (逗号分隔)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_asset_category", schema = "scrm",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_asset_category_code",
                columnNames = {"category_code"}),
        indexes = {
                @Index(name = "idx_asset_category_parent", columnList = "parent_id"),
                @Index(name = "idx_asset_category_code", columnList = "category_code"),
                @Index(name = "idx_asset_category_enabled", columnList = "enabled")
        })
/**
 * SCRM 营销素材分类实体。
 * <p>维护营销素材的多级分类目录: 分类名称与编码、描述、父子层级
 * (parentId/categoryLevel/categoryPath)、排序、图标与颜色、素材计数、
 * 是否启用与是否系统内置、备注。分类编码 (categoryCode) 唯一。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmAssetCategoryEntity {

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

    /** 分类名称 */
    @Column(name = "category_name", nullable = false, length = 200)
    private String categoryName;

    /** 分类编码 (唯一) */
    @Column(name = "category_code", nullable = false, length = 50)
    private String categoryCode;

    /** 分类描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 父分类 ID (可空, 空表示顶级分类) */
    @Column(name = "parent_id")
    private Long parentId;

    /** 分类层级 (默认 1, 顶级为 1) */
    @Column(name = "category_level")
    private Integer categoryLevel;

    /** 分类路径 (可空, 如 1/5/12/) */
    @Column(name = "category_path", length = 1000)
    private String categoryPath;

    /** 排序值 (默认 0, 数字越小越靠前) */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /** 图标 (可空) */
    @Column(name = "icon", length = 100)
    private String icon;

    /** 颜色 (可空) */
    @Column(name = "color", length = 20)
    private String color;

    /** 素材数 (冗余, 便于检索) */
    @Column(name = "asset_count")
    private Integer assetCount;

    /** 总大小字节 (冗余, 便于统计) */
    @Column(name = "total_size_bytes")
    private Long totalSizeBytes;

    /** 是否启用 (默认 TRUE) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 可见角色 (逗号分隔, 可空, 空表示全部可见) */
    @Column(name = "visible_to_roles", length = 500)
    private String visibleToRoles;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
