/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTemplateGroupEntity.java
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
 * SCRM 消息模板分组实体。
 * <p>
 * 消息模板中心的分组维度, 支持分组类型 (营销/服务/通知/验证/提醒/欢迎/跟进/自定义)、
 * 父分组层级、排序、模板数量统计、启用状态与展示样式 (颜色/图标)。{@code groupCode} 全局唯一,
 * 用于业务侧稳定引用。{@code templateCount} 为冗余统计字段, 由
 * {@code ScrmMessageTemplateCenterService.updateGroupStats} 维护。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_message_template_group", schema = "scrm", indexes = {
        @Index(name = "idx_message_template_group_code", columnList = "group_code"),
        @Index(name = "idx_message_template_group_parent", columnList = "parent_group_id"),
        @Index(name = "idx_message_template_group_type", columnList = "group_type"),
        @Index(name = "idx_message_template_group_enabled", columnList = "enabled")
})
@Data
public class ScrmMessageTemplateGroupEntity {

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
    @Column(name = "group_name", length = 200, nullable = false)
    private String groupName;

    /** 分组编码（全局唯一, 业务侧稳定引用） */
    @Column(name = "group_code", length = 50, nullable = false)
    private String groupCode;

    /** 分组描述（可空） */
    @Column(name = "description", length = 500)
    private String description;

    /** 分组类型: MARKETING/SERVICE/NOTIFICATION/VERIFICATION/REMINDER/WELCOME/FOLLOWUP/CUSTOM */
    @Column(name = "group_type", length = 50, nullable = false)
    private String groupType;

    /** 父分组 ID（可空, 空表示顶级分组） */
    @Column(name = "parent_group_id")
    private Long parentGroupId;

    /** 排序值（默认 0, 数字越小越靠前） */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /** 分组下模板数量（冗余统计字段, 由 updateGroupStats 维护） */
    @Column(name = "template_count")
    private Integer templateCount;

    /** 是否启用（默认 true） */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 展示颜色（可空） */
    @Column(name = "color", length = 20)
    private String color;

    /** 展示图标（可空） */
    @Column(name = "icon", length = 100)
    private String icon;

    /** 创建人（可空） */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
