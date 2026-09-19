/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAttributionModelEntity.java
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
 * SCRM 营销效果归因模型实体。
 * <p>
 * 定义多触点归因分析的模型配置: 归因模型类型 {@link #modelType}
 * (FIRST_TOUCH / LAST_TOUCH / LINEAR / TIME_DECAY / POSITION_BASED / U_SHAPED / W_SHAPED / CUSTOM),
 * 回溯窗口 {@link #lookbackDays} (回溯多少天内的触点参与归因),
 * 转化窗口 {@link #conversionWindowDays} (触点至转化的有效窗口),
 * 位置权重 {@link #positionWeights} (JSON: {first, last, middle}, 用于 U 型/W 型/位置归因),
 * 时间衰减半衰期 {@link #timeDecayHalfLife} (时间衰减归因的半衰期天数),
 * 自定义权重规则 {@link #customWeights} (JSON 自定义权重规则)。
 * </p>
 * <p>
 * 一个账号可拥有多个模型, 同一时间仅一个 {@link #isDefault} 为 true; {@link #isPublished}
 * 控制模型是否对外可用。{@link #appliedCount} / {@link #lastAppliedAt} 跟踪应用次数与最近应用时间。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_attribution_model", schema = "scrm", indexes = {
        @Index(name = "idx_attribution_model_code", columnList = "model_code"),
        @Index(name = "idx_attribution_model_type", columnList = "model_type"),
        @Index(name = "idx_attribution_model_default", columnList = "is_default"),
        @Index(name = "idx_attribution_model_published", columnList = "is_published")
})
@Data
public class ScrmAttributionModelEntity {

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
        if (isDefault == null) {
            isDefault = false;
        }
        if (isPublished == null) {
            isPublished = false;
        }
        if (lookbackDays == null) {
            lookbackDays = 30;
        }
        if (timeDecayHalfLife == null) {
            timeDecayHalfLife = 7;
        }
        if (conversionWindowDays == null) {
            conversionWindowDays = 7;
        }
        if (appliedCount == null) {
            appliedCount = 0;
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

    /** 模型类型: FIRST_TOUCH / LAST_TOUCH / LINEAR / TIME_DECAY / POSITION_BASED / U_SHAPED / W_SHAPED / CUSTOM */
    @Column(name = "model_type", nullable = false, length = 30)
    private String modelType;

    /** 回溯天数 (默认 30, 仅回溯该天数内的触点参与归因) */
    @Column(name = "lookback_days", nullable = false)
    private Integer lookbackDays;

    /** 位置权重 JSON (可空): {first, last, middle}, 用于位置归因 / U 型 / W 型 */
    @Column(name = "position_weights", columnDefinition = "TEXT")
    private String positionWeights;

    /** 时间衰减半衰期天数 (默认 7) */
    @Column(name = "time_decay_half_life")
    private Integer timeDecayHalfLife;

    /** 自定义权重规则 JSON (可空) */
    @Column(name = "custom_weights", columnDefinition = "TEXT")
    private String customWeights;

    /** 转化窗口天数 (默认 7, 触点至转化的有效窗口) */
    @Column(name = "conversion_window_days")
    private Integer conversionWindowDays;

    /** 是否为默认模型 (默认 false) */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    /** 是否已发布 (默认 false) */
    @Column(name = "is_published", nullable = false)
    private Boolean isPublished;

    /** 应用次数 */
    @Column(name = "applied_count")
    private Integer appliedCount;

    /** 最近应用时间 (可空) */
    @Column(name = "last_applied_at")
    private LocalDateTime lastAppliedAt;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
