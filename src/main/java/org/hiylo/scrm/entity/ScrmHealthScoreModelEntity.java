/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmHealthScoreModelEntity.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM 客户健康度评分模型实体。
 * <p>
 * 定义客户健康度评分的模型配置: 评分类型 {@link #scoringType} (SIMPLE / WEIGHTED / DYNAMIC),
 * 指标配置 {@link #metrics} (JSON 数组: [{metricCode, metricName, weight, maxScore, scoringType,
 * scoringRules}]), 健康阈值 {@link #healthThresholds} (JSON 数组: [{level, minScore, maxScore,
 * color, action}], level: CRITICAL / AT_RISK / NEUTRAL / HEALTHY / EXCELLENT)。
 * </p>
 * <p>
 * 一个账号可拥有多个模型, 同一时间仅一个 {@link #isDefault} 为 true; {@link #isPublished}
 * 控制模型是否对外可用。{@link #appliedCount} / {@link #lastAppliedAt} 跟踪应用次数与最近应用时间。
 * {@link #updateFrequency} 标识评分更新频率: REALTIME / DAILY / WEEKLY / MONTHLY。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_health_score_model", schema = "scrm", indexes = {
        @Index(name = "idx_health_score_model_code", columnList = "model_code"),
        @Index(name = "idx_health_score_model_type", columnList = "scoring_type"),
        @Index(name = "idx_health_score_model_default", columnList = "is_default"),
        @Index(name = "idx_health_score_model_published", columnList = "is_published")
})
@Data
public class ScrmHealthScoreModelEntity {

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

    /** 模型名称 */
    @Column(name = "model_name", nullable = false, length = 200)
    private String modelName;

    /** 模型编码 (唯一) */
    @Column(name = "model_code", nullable = false, length = 50)
    private String modelCode;

    /** 模型描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 适用客群 (可空) */
    @Column(name = "applicable_segment", length = 500)
    private String applicableSegment;

    /** 指标配置 JSON: [{metricCode, metricName, weight, maxScore, scoringType, scoringRules}] */
    @Column(name = "metrics", nullable = false, columnDefinition = "TEXT")
    private String metrics;

    /** 评分类型: SIMPLE / WEIGHTED / DYNAMIC (默认 WEIGHTED) */
    @Column(name = "scoring_type", nullable = false, length = 20)
    private String scoringType;

    /** 总分上限 (默认 100) */
    @Column(name = "total_max_score", nullable = false)
    private Integer totalMaxScore;

    /** 健康阈值 JSON: [{level, minScore, maxScore, color, action}] (可空) */
    @Column(name = "health_thresholds", columnDefinition = "TEXT")
    private String healthThresholds;

    /** 是否为默认模型 (默认 false) */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    /** 是否已发布 (默认 false) */
    @Column(name = "is_published", nullable = false)
    private Boolean isPublished;

    /** 模型版本号 (默认 1) */
    @Column(name = "version_no")
    private Integer versionNo;

    /** 应用次数 */
    @Column(name = "applied_count")
    private Integer appliedCount;

    /** 最近应用时间 (可空) */
    @Column(name = "last_applied_at")
    private LocalDateTime lastAppliedAt;

    /** 更新频率: REALTIME / DAILY / WEEKLY / MONTHLY (默认 DAILY) */
    @Column(name = "update_frequency", nullable = false, length = 20)
    private String updateFrequency;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
