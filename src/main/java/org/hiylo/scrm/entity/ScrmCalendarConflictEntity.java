/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCalendarConflictEntity.java
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
 * SCRM 营销日历冲突检测实体。
 * <p>
 * 记录两个事件之间的冲突信息: 冲突类型 ({@link #conflictType}) /
 * 严重程度 ({@link #severity}) / 冲突描述 ({@link #description}) /
 * 重叠的渠道与客群 ({@link #overlappingChannels} / {@link #overlappingAudience}),
 * 解决状态 ({@link #resolvedStatus}) 与解决记录 ({@link #resolvedBy} /
 * {@link #resolvedAt} / {@link #resolutionNote})。
 * </p>
 * <p>
 * 冲突类型: TIME_OVERLAP (时间重叠) / CHANNEL_CONFLICT (渠道冲突) /
 * RESOURCE_CONFLICT (资源冲突) / AUDIENCE_OVERLAP (客群重叠) /
 * BUDGET_EXCEED (预算超支)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_calendar_conflict", schema = "scrm", indexes = {
        @Index(name = "idx_calendar_conflict_event1", columnList = "event1_id"),
        @Index(name = "idx_calendar_conflict_event2", columnList = "event2_id"),
        @Index(name = "idx_calendar_conflict_status", columnList = "resolved_status"),
        @Index(name = "idx_calendar_conflict_severity", columnList = "severity"),
        @Index(name = "idx_calendar_conflict_detected", columnList = "detected_at")
})
@Data
public class ScrmCalendarConflictEntity {

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

    /** 冲突一方事件 ID */
    @Column(name = "event1_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long event1Id;

    /** 冲突另一方事件 ID */
    @Column(name = "event2_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long event2Id;

    /** 冲突类型: TIME_OVERLAP / CHANNEL_CONFLICT / RESOURCE_CONFLICT / AUDIENCE_OVERLAP / BUDGET_EXCEED */
    @Column(name = "conflict_type", nullable = false, length = 30)
    private String conflictType;

    /** 严重程度: WARNING / ERROR / INFO (默认 WARNING) */
    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    /** 冲突描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 重叠渠道 (逗号分隔, 可空) */
    @Column(name = "overlapping_channels", length = 500)
    private String overlappingChannels;

    /** 重叠客群 (可空) */
    @Column(name = "overlapping_audience", length = 500)
    private String overlappingAudience;

    /** 解决状态: UNRESOLVED / RESOLVED / IGNORED (默认 UNRESOLVED) */
    @Column(name = "resolved_status", nullable = false, length = 20)
    private String resolvedStatus;

    /** 解决人 (可空) */
    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    /** 解决时间 (可空) */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /** 解决备注 (可空) */
    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    /** 检测时间 */
    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;
}
