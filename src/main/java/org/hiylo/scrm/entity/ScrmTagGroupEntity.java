/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTagGroupEntity.java
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
 * SCRM 客户标签分组实体。
 * <p>
 * 标签体系顶层容器, 用于按业务维度组织标签 (如「消费行为」「兴趣偏好」「来源渠道」)。
 * 一个分组下挂载多个标签 ({@link ScrmTagEntity}), 分组编码 (group_code) 在同唯一。
 * 系统内置分组 (is_system=true) 不可删除, 仅可禁用。分组统计字段 (tag_count /
 * customer_count) 由 Service 层在标签变更后异步刷新。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_tag_group", schema = "scrm", indexes = {
        @Index(name = "idx_tag_group_code", columnList = "group_code", unique = true),
        @Index(name = "idx_tag_group_enabled", columnList = "enabled"),
        @Index(name = "idx_tag_group_sort", columnList = "sort_order")
})
@Data
public class ScrmTagGroupEntity {

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
    @Column(name = "group_name", nullable = false, length = 100)
    private String groupName;

    /** 分组编码 (全局唯一) */
    @Column(name = "group_code", nullable = false, length = 50)
    private String groupCode;

    /** 分组描述 */
    @Column(name = "description", length = 500)
    private String description;

    /** 分组颜色 (前端展示用, 如 #1890FF) */
    @Column(name = "color", length = 20)
    private String color;

    /** 分组图标 (前端展示用, 如 icon-tag-group) */
    @Column(name = "icon", length = 100)
    private String icon;

    /** 排序 (数字越小越靠前) */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /** 标签数 (分组下标签数量, 由 Service 刷新) */
    @Column(name = "tag_count")
    private Integer tagCount;

    /** 覆盖客户数 (分组下所有标签去重后的客户数, 由 Service 刷新) */
    @Column(name = "customer_count")
    private Integer customerCount;

    /** 是否系统内置 (true 不可删除, 仅可禁用) */
    @Column(name = "is_system", nullable = false)
    private Boolean isSystem;

    /** 是否启用 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
