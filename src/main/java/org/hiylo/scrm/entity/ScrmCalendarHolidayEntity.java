/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCalendarHolidayEntity.java
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
 * SCRM 营销日历节日/纪念日实体。
 * <p>
 * 维护一份可被日历引用的节日/纪念日库, 包括公历节日 ({@link #holidayDate} 为
 * 固定 MM-dd 或具体 yyyy-MM-dd) 与农历节日 ({@link #isLunar} 为 TRUE,
 * {@link #lunarDate} 记录农历日期), 同时附带营销建议 ({@link #marketingOpportunity} /
 * {@link #suggestedActions} / {@link #suggestedChannels}), 帮助运营在节日节点
 * 提前规划营销活动。
 * </p>
 * <p>
 * 节日类型 {@link #holidayType}: PUBLIC_HOLIDAY (公共假期) /
 * TRADITIONAL_FESTIVAL (传统节日) / E_COMMERCE (电商大促) / SEASONAL (季节性) /
 * CUSTOM (自定义)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_calendar_holiday", schema = "scrm", indexes = {
        @Index(name = "idx_calendar_holiday_type", columnList = "holiday_type"),
        @Index(name = "idx_calendar_holiday_active", columnList = "is_active"),
        @Index(name = "idx_calendar_holiday_date", columnList = "holiday_date")
})
@Data
public class ScrmCalendarHolidayEntity {

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

    /** 节日名称 */
    @Column(name = "holiday_name", nullable = false, length = 100)
    private String holidayName;

    /** 节日类型: PUBLIC_HOLIDAY / TRADITIONAL_FESTIVAL / E_COMMERCE / SEASONAL / CUSTOM */
    @Column(name = "holiday_type", nullable = false, length = 30)
    private String holidayType;

    /** 节日日期: 固定 MM-dd 或具体 yyyy-MM-dd */
    @Column(name = "holiday_date", nullable = false, length = 20)
    private String holidayDate;

    /** 农历日期 (可空, 仅农历节日使用) */
    @Column(name = "lunar_date", length = 20)
    private String lunarDate;

    /** 是否农历节日 (默认 FALSE) */
    @Column(name = "is_lunar", nullable = false)
    private Boolean isLunar;

    /** 持续天数 (默认 1) */
    @Column(name = "duration_days")
    private Integer durationDays;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 营销机会: HIGH / MEDIUM / LOW / NONE (默认 MEDIUM) */
    @Column(name = "marketing_opportunity", nullable = false, length = 20)
    private String marketingOpportunity;

    /** 建议营销动作 (可空) */
    @Column(name = "suggested_actions", length = 500)
    private String suggestedActions;

    /** 建议渠道 (可空) */
    @Column(name = "suggested_channels", length = 500)
    private String suggestedChannels;

    /** 国家 (默认 CN) */
    @Column(name = "country", nullable = false, length = 50)
    private String country;

    /** 地区 (可空) */
    @Column(name = "region", length = 100)
    private String region;

    /** 是否启用 (默认 TRUE) */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
