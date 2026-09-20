/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFeedbackCategoryEntity.java
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
 * SCRM 反馈分类实体。
 * <p>
 * 反馈分类配置: 分类名称、编码、适用反馈类型、默认优先级、默认处理人与团队、SLA 响应时长、
 * 自动标签、排序值、反馈计数与启用状态。applicableTypes 以逗号分隔存储适用的反馈类型,
 * 创建反馈时若匹配到分类则自动应用默认配置。分类按 sortOrder 升序展示。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_feedback_category", schema = "scrm", indexes = {
        @Index(name = "idx_feedback_category_enabled", columnList = "enabled")
})
@Data
public class ScrmFeedbackCategoryEntity {

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
    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    /** 分类编码 (唯一) */
    @Column(name = "category_code", nullable = false, length = 50, unique = true)
    private String categoryCode;

    /** 分类描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 适用反馈类型 (可空, 逗号分隔: SUGGESTION,COMPLAINT,...) */
    @Column(name = "applicable_types", length = 500)
    private String applicableTypes;

    /** 默认优先级: URGENT / HIGH / MEDIUM / LOW */
    @Column(name = "default_priority", nullable = false, length = 10)
    private String defaultPriority;

    /** 默认处理人 ID (可空) */
    @Column(name = "default_assignee_id", length = 100)
    private String defaultAssigneeId;

    /** 默认处理团队 ID (可空) */
    @Column(name = "default_team_id", length = 100)
    private String defaultTeamId;

    /** SLA 响应时长 (小时, 默认 48) */
    @Column(name = "sla_hours")
    private Integer slaHours;

    /** 自动标签 (可空, 逗号分隔) */
    @Column(name = "auto_tag", length = 200)
    private String autoTag;

    /** 排序值 (数字越小越靠前, 默认 0) */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /** 反馈计数 (默认 0) */
    @Column(name = "feedback_count")
    private Integer feedbackCount;

    /** 是否启用 (默认 TRUE) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
