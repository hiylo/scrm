/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAbTestVariantEntity.java
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
 * SCRM A/B 测试变体实体。
 * <p>
 * 描述实验中的单一变体 (对照组 / 实验组)。{@link #contentConfig} (JSON) 承载变体差异化
 * 内容配置 (标题/正文/图片/CTA/模板等), {@link #trafficPercent} 控制流量分配比例,
 * {@link #participants} / {@link #conversions} / {@link #conversionRate} / {@link #revenue}
 * 等字段记录该变体的实验指标。{@link #isControl} 标记对照组, {@link #isWinner} 标记胜出者。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_ab_test_variant", schema = "scrm", indexes = {
        @Index(name = "idx_ab_variant_test", columnList = "test_id"),
        @Index(name = "idx_ab_variant_control", columnList = "is_control"),
        @Index(name = "idx_ab_variant_winner", columnList = "is_winner")
})
@Data
public class ScrmAbTestVariantEntity {

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

    /** 测试 ID */
    @Column(name = "test_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long testId;

    /** 变体名称 */
    @Column(name = "variant_name", nullable = false, length = 100)
    private String variantName;

    /** 变体编码 */
    @Column(name = "variant_code", nullable = false, length = 50)
    private String variantCode;

    /** 变体类型: CONTROL / VARIANT (默认 VARIANT) */
    @Column(name = "variant_type", nullable = false, length = 20)
    private String variantType;

    /** 变体描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 变体内容配置 JSON: {title, body, image, cta, templateId, ...} (可空) */
    @Column(name = "content_config", columnDefinition = "TEXT")
    private String contentConfig;

    /** 流量分配百分比 (默认 50) */
    @Column(name = "traffic_percent", nullable = false)
    private Integer trafficPercent;

    /** 是否对照组 (默认 FALSE) */
    @Column(name = "is_control", nullable = false)
    private Boolean isControl;

    /** 参与人数 (默认 0) */
    @Column(name = "participants")
    private Integer participants;

    /** 转化数 (默认 0) */
    @Column(name = "conversions")
    private Integer conversions;

    /** 转化率 (默认 0) */
    @Column(name = "conversion_rate")
    private Double conversionRate;

    /** 收入 (默认 0) */
    @Column(name = "revenue")
    private Double revenue;

    /** 平均订单价值 (默认 0) */
    @Column(name = "avg_order_value")
    private Double avgOrderValue;

    /** 互动评分 (默认 0) */
    @Column(name = "engagement_score")
    private Double engagementScore;

    /** 是否胜出 (默认 FALSE) */
    @Column(name = "is_winner", nullable = false)
    private Boolean isWinner;

    /** 变体颜色标识 (可空) */
    @Column(name = "color", length = 20)
    private String color;

    /** 排序序号 (默认 0) */
    @Column(name = "sort_order")
    private Integer sortOrder;
}
