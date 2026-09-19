/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechCategoryEntity.java
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
 * SCRM 话术分类实体。
 * <p>
 * 支持多级父子分类, 用于团队共享话术库的分类组织。{@code parentId} 为空表示顶级分类,
 * 分类按 {@code sortOrder} 升序展示 (数字越小越靠前), {@code status=INACTIVE} 的分类
 * 不出现在选用列表中。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_speech_category", schema = "scrm", indexes = {
        @Index(name = "idx_speech_category_parent", columnList = "parent_id")
})
@Data
public class ScrmSpeechCategoryEntity {

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
    @Column(name = "category_name", length = 100, nullable = false)
    private String categoryName;

    /** 父分类 ID（可空, 空表示顶级分类） */
    @Column(name = "parent_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    /** 排序值（数字越小越靠前, 默认 0） */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /** 分类描述 */
    @Column(name = "description", length = 500)
    private String description;

    /** 状态: ACTIVE / INACTIVE（默认 ACTIVE） */
    @Column(name = "status", length = 20, nullable = false)
    private String status;
}
