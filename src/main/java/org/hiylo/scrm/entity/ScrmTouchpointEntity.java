/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTouchpointEntity.java
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
 * SCRM 触点管理实体。
 * <p>
 * 描述客户可触达的渠道端点 (网站/APP/企微/抖音等), {@link #touchpointCode} 为唯一编码,
 * 与行为轨迹的 {@code touchpoint} 字段对齐。{@link #config} (TEXT JSON) 承载触点配置
 * (appId/secret/回调地址等)。
 * </p>
 * <p>
 * 统计字段 {@link #totalEvents} / {@link #uniqueVisitors} / {@link #conversionCount} /
 * {@link #lastEventAt} 由行为记录时增量更新, 用于触点效果对比与归因分析。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_touchpoint", schema = "scrm", indexes = {
        @Index(name = "idx_touchpoint_code", columnList = "touchpoint_code", unique = true),
        @Index(name = "idx_touchpoint_type", columnList = "touchpoint_type"),
        @Index(name = "idx_touchpoint_active", columnList = "is_active")
})
@Data
public class ScrmTouchpointEntity {

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
        if (isActive == null) {
            isActive = true;
        }
        if (totalEvents == null) {
            totalEvents = 0;
        }
        if (uniqueVisitors == null) {
            uniqueVisitors = 0;
        }
        if (conversionCount == null) {
            conversionCount = 0;
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

    /** 触点名称 */
    @Column(name = "touchpoint_name", nullable = false, length = 100)
    private String touchpointName;

    /** 触点编码 (唯一, 与行为轨迹 touchpoint 字段对齐) */
    @Column(name = "touchpoint_code", nullable = false, length = 50)
    private String touchpointCode;

/** 触点类型:
* WEBSITE/APP/WECHAT_OFFICIAL/WECHAT_MINI/WORK_WECHAT/DOUYIN/KUAISHOU/XIAOHONGSHU/STORE/PHONE/EMAIL/SMS/OTHER
          * */
    @Column(name = "touchpoint_type", nullable = false, length = 50)
    private String touchpointType;

    /** 触点 URL (可空) */
    @Column(name = "url", length = 500)
    private String url;

    /** 应用 ID (可空, 如小程序 appId) */
    @Column(name = "app_id", length = 200)
    private String appId;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 是否启用 (默认 true) */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    /** 累计事件数 (默认 0) */
    @Column(name = "total_events")
    private Integer totalEvents;

    /** 独立访客数 (默认 0) */
    @Column(name = "unique_visitors")
    private Integer uniqueVisitors;

    /** 转化数 (默认 0) */
    @Column(name = "conversion_count")
    private Integer conversionCount;

    /** 最近事件时间 (可空) */
    @Column(name = "last_event_at")
    private LocalDateTime lastEventAt;

    /** JSON 触点配置 (可空) */
    @Column(name = "config", columnDefinition = "TEXT")
    private String config;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
