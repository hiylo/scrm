/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmEngagementLevelEntity.java
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
 * SCRM 互动活跃度等级实体。
 * <p>
 * 定义互动评分到活跃等级的映射区间: {@link #levelCode} (INACTIVE/LOW/MEDIUM/HIGH/VERY_HIGH),
 * {@link #minScore} / {@link #maxScore} 构成半开区间 [minScore, maxScore), {@link #priority}
 * 数字越大等级越高 (用于排序与匹配)。{@link #recommendedAction} 提供对应等级的建议运营动作。
 * </p>
 * <p>
 * 由 {@code ScrmEngagementScoreService.determineLevel} 在评分计算后查询启用等级并按
 * {@link #priority} 倒序匹配首个 minScore <= score 的等级。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_engagement_level", schema = "scrm", indexes = {
        @Index(name = "idx_engagement_level_code", columnList = "level_code"),
        @Index(name = "idx_engagement_level_enabled", columnList = "enabled"),
        @Index(name = "idx_engagement_level_priority", columnList = "priority")
})
@Data
public class ScrmEngagementLevelEntity {

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

    /** 等级名称 */
    @Column(name = "level_name", nullable = false, length = 100)
    private String levelName;

    /** 等级编码: INACTIVE/LOW/MEDIUM/HIGH/VERY_HIGH */
    @Column(name = "level_code", nullable = false, length = 50)
    private String levelCode;

    /** 最低分 (含) */
    @Column(name = "min_score", nullable = false)
    private Double minScore;

    /** 最高分 (不含, 可空表示无上限) */
    @Column(name = "max_score")
    private Double maxScore;

    /** 等级颜色 (前端展示用, 如 #FFD700) */
    @Column(name = "color", length = 20)
    private String color;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 建议动作 (可空) */
    @Column(name = "recommended_action", length = 500)
    private String recommendedAction;

    /** 优先级 (数字越大等级越高, 用于排序与匹配) */
    @Column(name = "priority")
    private Integer priority;

    /** 是否启用 (默认 true) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
