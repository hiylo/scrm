/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTagEntity.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM 客户标签定义实体。
 * <p>
 * 标签体系的标签定义, 归属到某个分组 ({@link ScrmTagGroupEntity}), 标签编码 (tag_code)
 * 在同唯一。标签类型 (tag_type) 区分手动 (MANUAL) / 自动 (AUTO) / 计算 (COMPUTED),
 * 自动标签关联规则 (rule_id) 由规则引擎执行后写入。值类型 (value_type) 声明标签值的数据
 * 类型 (BOOLEAN/TEXT/NUMBER/DATE/ENUM), 用于前端表单渲染与值校验。
 * </p>
 * <p>
 * 系统内置标签 (is_system=true) 不可删除, 仅可禁用。客户数 (customer_count) 由 Service
 * 在打标/去标后刷新, 用于标签云与覆盖率统计。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_tag", schema = "scrm", indexes = {
        @Index(name = "idx_tag_code", columnList = "tag_code", unique = true),
        @Index(name = "idx_tag_group", columnList = "group_id"),
        @Index(name = "idx_tag_type", columnList = "tag_type"),
        @Index(name = "idx_tag_enabled", columnList = "enabled")
})
@Data
public class ScrmTagEntity {

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

    /** 所属分组 ID (可空, 表示未分组) */
    @Column(name = "group_id")
    private Long groupId;

    /** 标签名称 */
    @Column(name = "tag_name", nullable = false, length = 100)
    private String tagName;

    /** 标签编码 (全局唯一) */
    @Column(name = "tag_code", nullable = false, length = 50)
    private String tagCode;

    /** 标签类型: MANUAL 手动 / AUTO 自动 / COMPUTED 计算 */
    @Column(name = "tag_type", nullable = false, length = 20)
    private String tagType;

    /** 值类型: BOOLEAN / TEXT / NUMBER / DATE / ENUM (可空表示无值标签) */
    @Column(name = "value_type", length = 20)
    private String valueType;

    /** 标签值 (用于固定值标签, 可空) */
    @Column(name = "tag_value", length = 500)
    private String tagValue;

    /** 标签描述 */
    @Column(name = "description", length = 500)
    private String description;

    /** 标签颜色 (前端展示用, 如 #1890FF) */
    @Column(name = "color", length = 20)
    private String color;

    /** 标签图标 (前端展示用, 如 icon-tag) */
    @Column(name = "icon", length = 100)
    private String icon;

    /** 排序 (数字越小越靠前) */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /** 被打标客户数 (由 Service 刷新) */
    @Column(name = "customer_count")
    private Integer customerCount;

    /** 关联自动规则 ID (自动标签关联, 可空) */
    @Column(name = "rule_id")
    private Long ruleId;

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
