/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmKnowledgeCategoryEntity.java
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
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * SCRM 知识分类实体。
 * <p>
 * 知识库分类树节点, {@link #parentId} / {@link #categoryLevel} / {@link #categoryPath}
 * 共同描述层级关系 (路径如 "1/5/12/")。{@link #articleCount} / {@link #totalViews} /
 * {@link #totalLikes} 由 Service 在文章变更时同步更新。{@link #visibleToRoles} 为逗号分隔
 * 的角色编码, 控制分类可见性。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_knowledge_category", schema = "scrm", indexes = {
        @Index(name = "idx_knowledge_category_code", columnList = "category_code", unique = true),
        @Index(name = "idx_knowledge_category_parent", columnList = "parent_id"),
        @Index(name = "idx_knowledge_category_level", columnList = "category_level")
})
@Data
public class ScrmKnowledgeCategoryEntity {

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

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 父分类 ID (可空, 顶层分类为 null) */
    @Column(name = "parent_id")
    private Long parentId;

    /** 层级 (默认 1, 顶层为 1) */
    @Column(name = "category_level")
    private Integer categoryLevel;

    /** 分类路径 (可空, 如 "1/5/12/") */
    @Column(name = "category_path", length = 1000)
    private String categoryPath;

    /** 排序序号 (默认 0, 升序) */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /** 图标 (可空) */
    @Column(name = "icon", length = 100)
    private String icon;

    /** 颜色 (可空) */
    @Column(name = "color", length = 20)
    private String color;

    /** 文章数 (默认 0, 由 Service 同步) */
    @Column(name = "article_count")
    private Integer articleCount;

    /** 总浏览量 (默认 0, 由 Service 同步) */
    @Column(name = "total_views")
    private Integer totalViews;

    /** 总点赞数 (默认 0, 由 Service 同步) */
    @Column(name = "total_likes")
    private Integer totalLikes;

    /** 是否启用 (默认 TRUE) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 可见角色 (可空, 逗号分隔) */
    @Column(name = "visible_to_roles", length = 500)
    private String visibleToRoles;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
