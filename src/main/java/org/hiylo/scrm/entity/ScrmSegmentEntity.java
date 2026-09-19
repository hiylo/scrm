/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSegmentEntity.java
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
 * SCRM 客户分群定义实体。
 * <p>
 * 描述一个动态客户分群: 通过 {@link #conditions} (JSON 数组: [{field, operator, value, logic}])
 * 定义筛选条件, {@link #conditionType} (ALL/ANY/NONE) 控制条件间逻辑关系。区别于静态标签,
 * 分群成员由 {@code ScrmSegmentService.calculateSegment} 基于条件动态计算。
 * </p>
 * <p>
 * {@link #segmentType} 标注分群类型 (DYNAMIC/STATIC/HYBRID), {@link #category} 标注分类
 * (RFM/LIFECYCLE/VALUE/BEHAVIOR/CUSTOM)。{@link #calculationFrequency} 控制自动计算频率,
 * {@link #autoUpdate} 控制是否自动更新成员。{@link #memberCount} / {@link #lastCalculatedAt}
 * 记录当前成员数与最后计算时间。
 * </p>
 * <p>
 * conditions 中 field 支持: customer_name / order_count / total_amount / last_interaction_days /
 * registration_days / engagement_score / customer_level / tag / rfm_segment; operator 支持:
 * eq / ne / gt / lt / gte / lte / between / contains / in。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_segment", schema = "scrm", indexes = {
        @Index(name = "idx_segment_code", columnList = "segment_code", unique = true),
        @Index(name = "idx_segment_type", columnList = "segment_type"),
        @Index(name = "idx_segment_category", columnList = "category"),
        @Index(name = "idx_segment_status", columnList = "status")
})
@Data
public class ScrmSegmentEntity {

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

    /** 分群名称 */
    @Column(name = "segment_name", nullable = false, length = 200)
    private String segmentName;

    /** 分群编码 (唯一) */
    @Column(name = "segment_code", nullable = false, length = 50)
    private String segmentCode;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 分群类型: DYNAMIC 动态 / STATIC 静态 / HYBRID 混合 (默认 DYNAMIC) */
    @Column(name = "segment_type", nullable = false, length = 20)
    private String segmentType;

    /** 分群分类: RFM / LIFECYCLE / VALUE / BEHAVIOR / CUSTOM (可空) */
    @Column(name = "category", length = 100)
    private String category;

    /** 条件组合: ALL 全部满足 / ANY 任一满足 / NONE 全不满足 (默认 ALL) */
    @Column(name = "condition_type", nullable = false, length = 20)
    private String conditionType;

    /** 条件 JSON 数组: [{field, operator, value, logic}] */
    @Column(name = "conditions", nullable = false, columnDefinition = "TEXT")
    private String conditions;

    /** 状态: ACTIVE 激活 / INACTIVE 停用 / DRAFT 草稿 (默认 ACTIVE) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 当前成员数 */
    @Column(name = "member_count")
    private Integer memberCount;

    /** 最后计算时间 (可空) */
    @Column(name = "last_calculated_at")
    private LocalDateTime lastCalculatedAt;

    /** 计算频率: REALTIME / HOURLY / DAILY / WEEKLY / MANUAL (默认 DAILY) */
    @Column(name = "calculation_frequency", nullable = false, length = 20)
    private String calculationFrequency;

    /** 是否自动更新成员 (默认 true) */
    @Column(name = "auto_update", nullable = false)
    private Boolean autoUpdate;

    /** 颜色 (可空, 前端展示用) */
    @Column(name = "color", length = 20)
    private String color;

    /** 图标 (可空, 前端展示用) */
    @Column(name = "icon", length = 100)
    private String icon;

    /** 标签 (可空, 逗号分隔) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
