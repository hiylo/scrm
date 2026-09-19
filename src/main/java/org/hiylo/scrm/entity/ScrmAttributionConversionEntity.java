/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAttributionConversionEntity.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 营销效果归因转化实体。
 * <p>
 * 记录客户的一次转化事件: 转化类型 {@link #conversionType}
 * (PURCHASE / SIGNUP / FORM_SUBMIT / APPOINTMENT / DOWNLOAD / ADD_TO_CART / CHECKOUT /
 * UPGRADE / RENEWAL / CUSTOM), 转化时间, 转化价值, 转化次数, 关联订单号。
 * 归因计算后, {@link #totalTouchpoints} / {@link #attributedTouchpoints} 记录总触点数与归因触点数,
 * {@link #attributionDetails} (TEXT JSON 数组) 承载每个归因触点的明细
 * [{touchpointId, type, channel, weight, value}], {@link #firstTouchType} /
 * {@link #lastTouchType} 记录首末触点类型与渠道, {@link #timeToConversionHours} 记录首触点到转化的耗时。
 * </p>
 * <p>
 * {@link #modelId} / {@link #modelName} 记录归因所用模型, {@link #attributedAt} 记录归因时间。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_attribution_conversion", schema = "scrm", indexes = {
        @Index(name = "idx_attribution_conversion_customer", columnList = "customer_id"),
        @Index(name = "idx_attribution_conversion_type", columnList = "conversion_type"),
        @Index(name = "idx_attribution_conversion_model", columnList = "model_id"),
        @Index(name = "idx_attribution_conversion_order", columnList = "order_id"),
        @Index(name = "idx_attribution_conversion_time", columnList = "conversion_time"),
        @Index(name = "idx_attribution_conversion_attributed", columnList = "attributed_at")
})
@Data
public class ScrmAttributionConversionEntity {

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
        if (conversionValue == null) {
            conversionValue = 0.0;
        }
        if (conversionCount == null) {
            conversionCount = 1;
        }
        if (totalTouchpoints == null) {
            totalTouchpoints = 0;
        }
        if (attributedTouchpoints == null) {
            attributedTouchpoints = 0;
        }
        if (conversionWindowDays == null) {
            conversionWindowDays = 7;
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

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (冗余, 便于列表展示) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 转化类型: PURCHASE/SIGNUP/FORM_SUBMIT/APPOINTMENT/DOWNLOAD/ADD_TO_CART/CHECKOUT/UPGRADE/RENEWAL/CUSTOM */
    @Column(name = "conversion_type", nullable = false, length = 50)
    private String conversionType;

    /** 转化时间 */
    @Column(name = "conversion_time", nullable = false)
    private LocalDateTime conversionTime;

    /** 转化价值 (默认 0) */
    @Column(name = "conversion_value", nullable = false)
    private Double conversionValue;

    /** 转化次数 (默认 1) */
    @Column(name = "conversion_count")
    private Integer conversionCount;

    /** 关联订单号 (可空) */
    @Column(name = "order_id", length = 100)
    private String orderId;

    /** 总触点数 (归因计算后回填) */
    @Column(name = "total_touchpoints")
    private Integer totalTouchpoints;

    /** 归因触点数 (归因计算后回填) */
    @Column(name = "attributed_touchpoints")
    private Integer attributedTouchpoints;

    /** 归因模型 ID (可空, 归因计算后回填) */
    @Column(name = "model_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    /** 归因模型名称 (可空, 归因计算后回填) */
    @Column(name = "model_name", length = 200)
    private String modelName;

    /** JSON 归因详情 (可空): [{touchpointId, type, channel, weight, value}] */
    @Column(name = "attribution_details", columnDefinition = "TEXT")
    private String attributionDetails;

    /** 首次触点类型 (可空, 归因计算后回填) */
    @Column(name = "first_touch_type", length = 50)
    private String firstTouchType;

    /** 首次触点渠道 (可空, 归因计算后回填) */
    @Column(name = "first_touch_channel", length = 50)
    private String firstTouchChannel;

    /** 末次触点类型 (可空, 归因计算后回填) */
    @Column(name = "last_touch_type", length = 50)
    private String lastTouchType;

    /** 末次触点渠道 (可空, 归因计算后回填) */
    @Column(name = "last_touch_channel", length = 50)
    private String lastTouchChannel;

    /** 转化窗口天数 (默认 7) */
    @Column(name = "conversion_window_days")
    private Integer conversionWindowDays;

    /** 转化耗时小时 (首触点至转化的时长, 归因计算后回填) */
    @Column(name = "time_to_conversion_hours")
    private Integer timeToConversionHours;

    /** JSON 附加数据 (可空) */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /** 归因时间 (可空, 归因计算后回填) */
    @Column(name = "attributed_at")
    private LocalDateTime attributedAt;
}
