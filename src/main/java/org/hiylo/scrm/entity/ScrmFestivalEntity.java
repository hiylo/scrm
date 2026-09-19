/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFestivalEntity.java
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
 * SCRM 节日配置实体。
 * <p>
 * 维护节日库供节日关怀任务生成使用。{@link #festivalType} 标注节日类型
 * (SOLAR 公历 / LUNAR 农历 / FIXED 固定 / CUSTOM 自定义), {@link #festivalDate}
 * 为日期字符串 (公历/农历 MM-dd 或 FIXED 完整日期), 农历节日由 {@link #lunarMonth} /
 * {@link #lunarDay} 冗余存储便于检索。
 * </p>
 * <p>
 * {@link #defaultGreeting} / {@link #defaultActionType} / {@link #defaultActionContent}
 * 提供默认祝福语与动作, 节日关怀任务生成时按规则覆盖; {@link #applicable} 限定适用范围
 * (ALL / VIP / CUSTOM)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_festival", schema = "scrm", indexes = {
        @Index(name = "idx_festival_type", columnList = "festival_type"),
        @Index(name = "idx_festival_enabled", columnList = "enabled"),
        @Index(name = "idx_festival_date", columnList = "festival_date")
})
@Data
public class ScrmFestivalEntity {

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
    @Column(name = "festival_name", nullable = false, length = 100)
    private String festivalName;

    /** 节日类型: SOLAR 公历 / LUNAR 农历 / FIXED 固定 / CUSTOM 自定义 */
    @Column(name = "festival_type", nullable = false, length = 30)
    private String festivalType;

    /** 节日日期: 公历 MM-dd / 农历 MM-dd / FIXED 完整日期 */
    @Column(name = "festival_date", nullable = false, length = 20)
    private String festivalDate;

    /** 农历月 (1-12, 仅 LUNAR 类型使用, 可空) */
    @Column(name = "lunar_month")
    private Integer lunarMonth;

    /** 农历日 (1-30, 仅 LUNAR 类型使用, 可空) */
    @Column(name = "lunar_day")
    private Integer lunarDay;

    /** 节日描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 默认祝福语 (可空) */
    @Column(name = "default_greeting", length = 500)
    private String defaultGreeting;

    /** 默认动作类型 (可空): SEND_MESSAGE / SEND_COUPON / SEND_GIFT / CALL / CREATE_TASK / NOTIFY_ASSIGNEE */
    @Column(name = "default_action_type", length = 30)
    private String defaultActionType;

    /** 默认动作内容 JSON (可空) */
    @Column(name = "default_action_content", columnDefinition = "TEXT")
    private String defaultActionContent;

    /** 适用范围: ALL / VIP / CUSTOM (默认 ALL) */
    @Column(name = "applicable", nullable = false, length = 20)
    private String applicable;

    /** 是否启用 (默认 true) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
